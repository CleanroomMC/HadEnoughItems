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
    public static final Map<IRecipeWrapper, Integer> recipeIds = new Object2IntOpenHashMap<>();

    public static void load() {
        ingredients.clear();
        recipeIds.clear();
        ingredientRegistry = Internal.getIngredientRegistry();
        RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
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
        Map<Integer, IRecipeWrapper> recipesIdsMap = new Int2ObjectOpenHashMap<>(8192);
        for (String string : strings) {
            String[] split = string.split("%");
            int recipeIdString = Integer.parseInt(split[0]);
            String ingredientString = split[1];
            rawRecipes.put(recipeIdString, ingredientString);
        }
        for (IRecipeCategory<?> category : recipeRegistry.getRecipeCategories()) {

            for (IRecipeWrapper recipe : recipeRegistry.getRecipeWrappers(category)) {
                int id = calculateId(recipe);
                if (rawRecipes.containsKey(id)) {
                    recipesIdsMap.put(id, recipe);
                }
            }
        }
        for (Map.Entry<Integer, String> entry : rawRecipes.entrySet()) {
            int recipeId = entry.getKey();
            String ingredientString = entry.getValue();
            IRecipeWrapper recipe = recipesIdsMap.get(recipeId);
            if (recipe == null) {
                continue;
            }
            ingredients.put(ingredientString, recipe);
        }
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
        for (Map.Entry<String, IRecipeWrapper> entry : ingredients.entrySet()) {
            int recipeId = recipeIds.get(entry.getValue());
            strings.add(recipeId + "%" + entry.getKey());
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

    public static void setFavorite(Object ingredient, IRecipeWrapper recipe) {
        String id = ingredientRegistry.getIngredientHelper(ingredient).getUniqueId(ingredient);
        ingredients.put(id, recipe);
        if (!recipeIds.containsKey(recipe)) {
            calculateId(recipe);
        }
        save();
    }

    public static void removeFavorite(IRecipeWrapper recipe) {
        ingredients.entrySet().removeIf(entry -> entry.getValue() == recipe);
        save();
    }


}
