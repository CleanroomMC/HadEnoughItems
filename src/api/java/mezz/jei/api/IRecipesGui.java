package mezz.jei.api;

import javax.annotation.Nullable;
import java.util.List;

import mezz.jei.api.recipe.IFocus;

/**
 * JEI's gui for displaying recipes. Use this interface to open recipes.
 * Get the instance from {@link IJeiRuntime#getRecipesGui()}.
 *
 * @since JEI 3.2.12
 */
public interface IRecipesGui {
	/**
	 * Show recipes for an {@link IFocus}.
	 * Opens the {@link IRecipesGui} if it is closed.
	 *
	 * @see IRecipeRegistry#createFocus(IFocus.Mode, Object)
	 * @since JEI 3.11.0
	 */
	<V> void show(IFocus<V> focus);

	/**
	 * Show entire categories of recipes.
	 *
	 * @param recipeCategoryUids a list of categories to display, in order. Must not be empty.
	 */
	void showCategories(List<String> recipeCategoryUids);

	/**
	 * @return the ingredient that's currently under the mouse in this gui, or null if there is none.
	 * @since JEI 4.13.1
	 */
	@Nullable
	Object getIngredientUnderMouse();

	/**
	 * @return the text of the search filter
	 * @since HEI 4.31.0
	 */
	String getSearchFilter();

	/**
	 * Set the text of the search filter
	 *
	 * @return if the search filter was changed as a result
	 * @since HEI 4.31.0
	 */
	boolean setSearchFilter(String searchFilter);

	/**
	 * @return the search mode being used
	 * @since HEI 4.31.0
	 */
	RecipeSearchMode getSearchMode();

	/**
	 * Set the search mode being used
	 *
	 * @return if the search mode was changed as a result
	 * @since HEI 4.31.0
	 */
	boolean setSearchMode(RecipeSearchMode searchMode);

	/**
	 * What ingredients are being searched by the search filter.
	 *
	 * @since HEI 4.31.0
	 */
	enum RecipeSearchMode {
		NONE, INPUT, OUTPUT, BOTH;
	}
}
