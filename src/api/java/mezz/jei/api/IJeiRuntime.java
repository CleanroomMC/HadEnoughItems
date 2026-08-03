package mezz.jei.api;

import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.api.search.ISearchIndexBuilderFactory;

/**
 * Gives access to JEI functions that are available once everything has loaded.
 * The IJeiRuntime instance is passed to your mod plugin in {@link IModPlugin#onRuntimeAvailable(IJeiRuntime)}.
 */
public interface IJeiRuntime {
	IRecipeRegistry getRecipeRegistry();

	/**
	 * @since JEI 3.2.12
	 */
	IRecipesGui getRecipesGui();

	/**
	 * @since JEI 4.2.2
	 */
	IIngredientFilter getIngredientFilter();

	/**
	 * @since JEI 4.2.2
	 */
	IIngredientListOverlay getIngredientListOverlay();

	/**
	 * @since JEI 4.15.0
	 */
	IBookmarkOverlay getBookmarkOverlay();

	/**
	 * @since HEI 4.29.0
	 */
	IAutocraftingHandler getAutocraftingHandler();

	/**
	 * Get the factory used to build HEI's ingredient search indices.
	 *
	 * @since HEI 5.0.0
	 */
	ISearchIndexBuilderFactory getSearchIndexBuilderFactory();

	/**
	 * @deprecated since JEI 4.5.0. Use {@link #getIngredientListOverlay()}
	 */
	@Deprecated
	IItemListOverlay getItemListOverlay();
}
