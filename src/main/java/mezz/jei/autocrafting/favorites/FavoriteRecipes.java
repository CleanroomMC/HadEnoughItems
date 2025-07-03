package mezz.jei.autocrafting.favorites;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mezz.jei.Internal;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.config.Config;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.ingredients.Ingredients;
import mezz.jei.recipes.RecipeRegistry;
import mezz.jei.util.Log;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FavoriteRecipes {

    private static final Map<String, IRecipeWrapper> ingredients = new Object2ObjectOpenHashMap<>();
    private static IngredientRegistry ingredientRegistry;
    public static final BiMap<IRecipeWrapper, Long> recipeIds = HashBiMap.create(128);
    private static final Map<IRecipeWrapper, IRecipeCategory<?>> recipeCategories = new Object2ObjectOpenHashMap<>(8192);


    public static void load() {
        ingredients.clear();
        recipeIds.clear();
        recipeCategories.clear();
        ingredientRegistry = Internal.getIngredientRegistry();
        File file = Config.getFavoriteFile();
        if (file == null || !file.exists()) {
            return;
        }
        List<String> strings;
        try (FileReader reader = new FileReader(file)) {
            strings = IOUtils.readLines(reader);
        } catch (IOException e) {
            Log.get().error("Failed to load favorite recipes from file {}", file, e);
            return;
        }
        // Break the strings apart into recipeId:ingredient (int to string)
        Map<Long, String> rawRecipes = new Long2ObjectOpenHashMap<>(8192);
        IRecipeCategory<?> currentCategory = null;
        RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();

        for (String string : strings) {
            if (string.charAt(0) == '#') {
                addRecipesForCategory(currentCategory, rawRecipes, recipeRegistry);
                currentCategory = recipeRegistry.getRecipeCategory(string.substring(1));
                continue;
            }
            String[] split = string.split("%");
            long recipeIdString = Long.parseLong(split[0]);
            String ingredientString = split[1];
            rawRecipes.put(recipeIdString, ingredientString);
        }
        addRecipesForCategory(currentCategory, rawRecipes, recipeRegistry);
    }

    public static void addRecipesForCategory(IRecipeCategory<?> category, Map<Long, String> rawRecipes, RecipeRegistry recipeRegistry) {
        if (category != null && !rawRecipes.isEmpty()) {
            for (IRecipeWrapper recipe : recipeRegistry.getRecipeWrappers(category)) {
                long id = calculateId(recipe, category);
                if (rawRecipes.containsKey(id)) {
                    ingredients.put(rawRecipes.get(id), recipe);
                    recipeCategories.put(recipe, category);
                    rawRecipes.remove(id);
                    if (rawRecipes.isEmpty()) {
                        break;
                    }
                }
            }
        }
        rawRecipes.clear();
    }

    public static long calculateId(IRecipeWrapper recipe, IRecipeCategory<?> category) {
        Ingredients ings = new Ingredients();
        recipe.getIngredients(ings);
        long step = 1;
        long hash = 0;
        for (IIngredientType<?> type : ings.getInputIngredients().keySet()) {
            for (Object ingredient : ings.getInputIngredients().get(type)) {
                hash += (long) ingredientRegistry.getIngredientHelper(ingredient).getHash(ingredient) * step;
                step++;
            }
        }
        for (IIngredientType<?> type : ings.getOutputIngredients().keySet()) {
            for (Object ingredient : ings.getOutputIngredients().get(type)) {
                hash += (long) ingredientRegistry.getIngredientHelper(ingredient).getHash(ingredient) * step;
                step++;
            }
        }
        hash += category.getUid().hashCode() * step;
        if (!recipeIds.containsValue(hash)) { // Yes, this actually happens sometimes.
            recipeIds.put(recipe, hash);
        }
        return hash;
    }

    public static void save() {
        File file = Config.getFavoriteFile();
        List<String> strings = new ArrayList<>();
        Map<IRecipeCategory<?>, Map<String, IRecipeWrapper>> categoryMap = ingredients.entrySet().stream().collect(Object2ObjectOpenHashMap::new,
                (map, entry) -> {
                    IRecipeCategory<?> category = recipeCategories.get(entry.getValue());
                    if (!map.containsKey(category)) {
                        map.put(category, new Object2ObjectOpenHashMap<>());
                    }
                    map.get(category).put(entry.getKey(), entry.getValue());
                }, Object2ObjectOpenHashMap::putAll);
        for (Map.Entry<IRecipeCategory<?>, Map<String, IRecipeWrapper>> categoryEntry : categoryMap.entrySet()) {
            strings.add("#" + categoryEntry.getKey().getUid());
            for (Map.Entry<String, IRecipeWrapper> ingredientAndRecipe : categoryEntry.getValue().entrySet()) {
                strings.add(recipeIds.get(ingredientAndRecipe.getValue()) + "%" + ingredientAndRecipe.getKey());
            }
        }

        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                IOUtils.writeLines(strings, "\n", writer);
            } catch (IOException e) {
                Log.get().error("Failed to save favorite recipes to file {}", file, e);
            }
        }
    }

    public static boolean isFavorite(IRecipeWrapper recipe) {
        return ingredients.containsValue(recipe);
    }

    public static boolean isFavoriteFor(IRecipeWrapper recipe, Object ingredient) {
        // The below throws an error if the object isn't a supported type. Hopefully I got that right!
        String id = ingredientRegistry.getIngredientHelper(ingredient).getUniqueId(ingredient);
        return ingredients.containsKey(id) && ingredients.get(id) == recipe;
    }

    public static void toggleFavorite(Object ingredient, IRecipeWrapper recipe, IRecipeCategory<?> category) {
        String id = ingredientRegistry.getIngredientHelper(ingredient).getUniqueId(ingredient);
        if (ingredients.containsKey(id) && ingredients.get(id) == recipe) {
            ingredients.remove(id);
        } else {
            ingredients.put(id, recipe);
            recipeCategories.put(recipe, category);
            if (!recipeIds.containsKey(recipe)) {
                calculateId(recipe, category);
            }
        }
        save();
    }

    public static void removeFavorite(IRecipeWrapper data) {
        ingredients.entrySet().removeIf(entry -> entry.getValue() == data);
        save();
    }

    public static IRecipeWrapper getFavorite(Object ingredient) {
        String id = ingredientRegistry.getIngredientHelper(ingredient).getUniqueId(ingredient);
        return ingredients.get(id);
    }

    public static IRecipeCategory<?> getFavoriteCategory(Object ingredient) {
        IRecipeWrapper recipe = getFavorite(ingredient);
        return recipe != null ? recipeCategories.get(recipe) : null;
    }

}
