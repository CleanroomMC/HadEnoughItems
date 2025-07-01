package mezz.jei.autocrafting.favorites;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FavoriteRecipes {

    private static final Map<String, IRecipeWrapper> ingredients = new Object2ObjectOpenHashMap<>();
    private static IngredientRegistry ingredientRegistry;
    public static final Map<IRecipeWrapper, Integer> recipeIds = new Object2IntOpenHashMap<>(8192);
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
        Map<Integer, String> rawRecipes = new Int2ObjectOpenHashMap<>(8192);
        IRecipeCategory<?> currentCategory = null;
        RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();

        for (String string : strings) {
            if (string.charAt(0) == '#') {
                addRecipesForCategory(currentCategory, rawRecipes, recipeRegistry);
                currentCategory = recipeRegistry.getRecipeCategory(string.substring(1));
                continue;
            }
            String[] split = string.split("%");
            int recipeIdString = Integer.parseInt(split[0]);
            String ingredientString = split[1];
            rawRecipes.put(recipeIdString, ingredientString);
        }
        addRecipesForCategory(currentCategory, rawRecipes, recipeRegistry);
    }

    public static void addRecipesForCategory(IRecipeCategory<?> category, Map<Integer, String> rawRecipes, RecipeRegistry recipeRegistry) {
        if (category != null && !rawRecipes.isEmpty()) {
            for (IRecipeWrapper recipe : recipeRegistry.getRecipeWrappers(category)) {
                int id = calculateId(recipe);
                if (rawRecipes.containsKey(id)) {
                    ingredients.put(rawRecipes.get(id), recipe);
                    recipeCategories.put(recipe, category);
                }
                rawRecipes.remove(id);
                if (rawRecipes.isEmpty()) {
                    break;
                }
            }
        }
        rawRecipes.clear();
    }

    public static int calculateId(IRecipeWrapper recipe) {
        Ingredients ings = new Ingredients();
        recipe.getIngredients(ings);
        int step = 1;
        int hash = 0;
        for (IIngredientType<?> type : ings.getInputIngredients().keySet()) {
            for (Object ingredient : ings.getInputIngredients().get(type)) {
                hash += ingredientRegistry.getIngredientHelper(ingredient).getHash(ingredient) * step;
                step++;
            }
        }
        for (IIngredientType<?> type : ings.getOutputIngredients().keySet()) {
            for (Object ingredient : ings.getOutputIngredients().get(type)) {
                hash += ingredientRegistry.getIngredientHelper(ingredient).getHash(ingredient) * step;
                step++;
            }
        }
        recipeIds.put(recipe, hash);
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

    public static void setFavorite(Object ingredient, IRecipeWrapper recipe, IRecipeCategory<?> category) {
        String id = ingredientRegistry.getIngredientHelper(ingredient).getUniqueId(ingredient);
        ingredients.put(id, recipe);
        recipeCategories.put(recipe, category);
        if (!recipeIds.containsKey(recipe)) {
            calculateId(recipe);
        }
        save();
    }

    public static void removeFavorite(IRecipeWrapper data) {
        ingredients.entrySet().removeIf(entry -> entry.getValue() == data);
        save();
    }

}
