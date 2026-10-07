package mezz.jei.autocrafting.favorites;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import mezz.jei.Internal;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.config.Config;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.recipes.RecipeRegistry;
import mezz.jei.util.Log;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The recipe a player has chosen as the default way to make a given ingredient.
 * <p>
 * Recipe chains consult this if they do not know how to craft ingredients inside a recipe bookmark.
 * <p>
 * Favourites are stored as a flat text file, grouped under a {@code #categoryUid} header so
 * a recipe id only has to be resolved against the one category it belongs to:
 * <pre>
 * #minecraft.crafting
 * 1234%minecraft:chest
 * </pre>
 */
public class FavoriteRecipes {

	private static final char CATEGORY_MARKER = '#';
	private static final String FIELD_SEPARATOR = "%";
	private static final Map<String, IRecipeWrapper> recipesByIngredient = new Object2ObjectLinkedOpenHashMap<>();
	private static final Map<IRecipeWrapper, IRecipeCategory<?>> recipeCategories = new Object2ObjectLinkedOpenHashMap<>();
	private static final Object2IntOpenHashMap<IRecipeWrapper> favoriteCounts = new Object2IntOpenHashMap<>();

	private FavoriteRecipes() {
	}

	public static void load() {
		clear();
		File file = Config.getFavoriteFile();
		if (file == null || !file.exists()) {
			return;
		}

		List<String> lines;
		try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			lines = new ArrayList<>();
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				lines.add(line);
			}
		} catch (IOException e) {
			Log.get().error("Failed to load favourite recipes from file {}", file, e);
			return;
		}

		RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
		Long2ObjectMap<String> pendingRecipes = new Long2ObjectOpenHashMap<>();
		IRecipeCategory<?> currentCategory = null;

		for (String line : lines) {
			if (line.isEmpty()) {
				continue;
			}
			if (line.charAt(0) == CATEGORY_MARKER) {
				resolveCategory(currentCategory, pendingRecipes, recipeRegistry);
				currentCategory = recipeRegistry.getRecipeCategory(line.substring(1));
				continue;
			}
			String[] split = line.split(FIELD_SEPARATOR, 2);
			if (split.length != 2) {
				Log.get().warn("Skipping malformed favorite recipe entry: {}", line);
				continue;
			}
			try {
				pendingRecipes.put(Long.parseLong(split[0]), split[1]);
			} catch (NumberFormatException e) {
				Log.get().warn("Skipping favorite recipe entry with an unreadable recipe id: {}", line);
			}
		}
		resolveCategory(currentCategory, pendingRecipes, recipeRegistry);
	}

	private static void resolveCategory(@Nullable IRecipeCategory<?> category, Long2ObjectMap<String> pendingRecipes, RecipeRegistry recipeRegistry) {
		if (category == null || pendingRecipes.isEmpty()) {
			pendingRecipes.clear();
			return;
		}
		for (Long2ObjectMap.Entry<String> entry : pendingRecipes.long2ObjectEntrySet()) {
			IRecipeWrapper recipe = recipeRegistry.getRecipeById(entry.getLongKey(), category);
			if (recipe == null) {
				Log.get().warn("Could not find recipe with id {} in category {}!", entry.getLongKey(), category.getUid());
				continue;
			}
			addFavorite(entry.getValue(), recipe, category);
		}
		pendingRecipes.clear();
	}

	public static void save() {
		File file = Config.getFavoriteFile();
		if (file == null) {
			return;
		}

		Map<IRecipeCategory<?>, Map<String, IRecipeWrapper>> byCategory = new Object2ObjectLinkedOpenHashMap<>();
		for (Map.Entry<String, IRecipeWrapper> entry : recipesByIngredient.entrySet()) {
			IRecipeCategory<?> category = recipeCategories.get(entry.getValue());
			if (category == null) {
				continue;
			}
			byCategory.computeIfAbsent(category, k -> new Object2ObjectLinkedOpenHashMap<>()).put(entry.getKey(), entry.getValue());
		}

		RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
		try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
			for (Map.Entry<IRecipeCategory<?>, Map<String, IRecipeWrapper>> categoryEntry : byCategory.entrySet()) {
				writer.write(CATEGORY_MARKER + categoryEntry.getKey().getUid());
				writer.write('\n');
				for (Map.Entry<String, IRecipeWrapper> favorite : categoryEntry.getValue().entrySet()) {
					writer.write(recipeRegistry.getRecipeId(favorite.getValue()) + FIELD_SEPARATOR + favorite.getKey());
					writer.write('\n');
				}
			}
		} catch (IOException e) {
			Log.get().error("Failed to save favourite recipes to file {}", file, e);
		}
	}

	public static boolean isFavorite(IRecipeWrapper recipe) {
		return favoriteCounts.getInt(recipe) > 0;
	}

	public static boolean isFavoriteFor(IRecipeWrapper recipe, Object ingredient) {
		return getFavorite(ingredient) == recipe;
	}

	public static void toggleFavorite(Object ingredient, IRecipeWrapper recipe, IRecipeCategory<?> category) {
		String uniqueId = uniqueIdOf(ingredient);
		if (recipe.equals(recipesByIngredient.get(uniqueId))) {
			removeFavorite(uniqueId);
		} else {
			addFavorite(uniqueId, recipe, category);
		}
		save();
	}

	public static void removeFavorite(IRecipeWrapper recipe) {
		List<String> ingredients = new ArrayList<>();
		for (Map.Entry<String, IRecipeWrapper> entry : recipesByIngredient.entrySet()) {
			if (entry.getValue().equals(recipe)) {
				ingredients.add(entry.getKey());
			}
		}
		for (String uniqueId : ingredients) {
			removeFavorite(uniqueId);
		}
		save();
	}

	@Nullable
	public static IRecipeWrapper getFavorite(Object ingredient) {
		return recipesByIngredient.get(uniqueIdOf(ingredient));
	}

	@Nullable
	public static IRecipeCategory<?> getFavoriteCategory(Object ingredient) {
		IRecipeWrapper recipe = getFavorite(ingredient);
		return recipe == null ? null : recipeCategories.get(recipe);
	}

	private static void addFavorite(String ingredientUniqueId, IRecipeWrapper recipe, IRecipeCategory<?> category) {
		removeFavorite(ingredientUniqueId);
		recipesByIngredient.put(ingredientUniqueId, recipe);
		recipeCategories.put(recipe, category);
		favoriteCounts.addTo(recipe, 1);
	}

	private static void removeFavorite(String ingredientUniqueId) {
		IRecipeWrapper previous = recipesByIngredient.remove(ingredientUniqueId);
		if (previous == null) {
			return;
		}
		int remaining = favoriteCounts.addTo(previous, -1) - 1;
		if (remaining <= 0) {
			favoriteCounts.removeInt(previous);
			recipeCategories.remove(previous);
		}
	}

	private static void clear() {
		recipesByIngredient.clear();
		recipeCategories.clear();
		favoriteCounts.clear();
	}

	private static String uniqueIdOf(Object ingredient) {
		IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
		return ingredientRegistry.getIngredientHelper(ingredient).getUniqueId(ingredient);
	}
}
