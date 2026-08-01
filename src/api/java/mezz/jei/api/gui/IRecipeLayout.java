package mezz.jei.api.gui;

import javax.annotation.Nullable;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.IModIngredientRegistration;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;

/**
 * Represents the layout of one recipe on-screen.
 * Plugins interpret a recipe wrapper to set the properties here.
 * It is passed to plugins in {@link IRecipeCategory#setRecipe(IRecipeLayout, IRecipeWrapper, IIngredients)}.
 *
 * @see IRecipeLayoutDrawable
 */
public interface IRecipeLayout {
	/**
	 * Contains all the itemStacks displayed on this recipe layout.
	 * Init and set them in your recipe category.
	 */
	IGuiItemStackGroup getItemStacks();

	/**
	 * Contains all the fluidStacks displayed on this recipe layout.
	 * Init and set them in your recipe category.
	 */
	IGuiFluidStackGroup getFluidStacks();

	/**
	 * Get all the ingredients of one type that are displayed on this recipe layout.
	 * Init and set them in your recipe category.
	 * <p>
	 * This method is for handling custom item types, registered with {@link IModIngredientRegistration}.
	 *
	 * @see #getItemStacks()
	 * @see #getFluidStacks()
	 * @since JEI 4.12.0
	 */
	<T> IGuiIngredientGroup<T> getIngredientsGroup(IIngredientType<T> ingredientType);

	/**
	 * The current search focus. Set by the player when they look up the recipe. The object being looked up is the focus.
	 *
	 * @since JEI 3.11.0
	 */
	@Nullable
	IFocus<?> getFocus();

	/**
	 * The current recipe category.
	 *
	 * @since JEI 4.7.6
	 */
	IRecipeCategory<?> getRecipeCategory();

	/**
	 * Moves the recipe transfer button's position relative to the recipe layout.
	 * By default, the recipe transfer button is at the bottom, to the right of the recipe.
	 * If it doesn't fit there, you can use this to move it when you init the recipe layout.
	 * This calls {@link IRecipeLayout#setRecipeTransferButton(int, int, boolean)} with moveAll = true.
	 */
	void setRecipeTransferButton(int posX, int posY);

	/**
	 * Adds a shapeless icon to the top right of the recipe, that shows a tooltip saying "shapeless" when hovered over.
	 *
	 * @since JEI 4.0.2
	 */
	void setShapeless();

	/**
	 * Moves the recipe transfer button's position relative to the recipe layout.
	 * <p>
	 * If moveAll is true, it also moves the recipe favorite button and recipe bookmark button
	 * to the right of the recipe transfer button.
	 *
	 * @since HEI 4.29.5
	 */
	default void setRecipeTransferButton(int posX, int posY, boolean moveAll) {
		setRecipeTransferButton(posX, posY);
	}

	/**
	 * Sets the recipe favourite button's position.
	 * <p>
	 * Positioning it is optional. A layout that does not place it leaves it where HEI puts it by default.
	 *
	 * @since HEI 4.29.5
	 */
	default void setRecipeFavoriteButton(int posX, int posY) {
	}

	/**
	 * Sets the recipe bookmark button's position.
	 * <p>
	 * Positioning it is optional. A layout that does not place it leaves it where HEI puts it by default.
	 *
	 * @since HEI 4.29.5
	 */
	default void setRecipeBookmarkButton(int posX, int posY) {
	}

	/**
	 * Get all the ingredients of one class that are displayed on this recipe layout.
	 * Init and set them in your recipe category.
	 * <p>
	 * This method is for handling custom item types, registered with {@link IModIngredientRegistration}.
	 *
	 * @see #getItemStacks()
	 * @see #getFluidStacks()
	 * @deprecated since JEI 4.12.0. Use {@link #getIngredientsGroup(IIngredientType)}
	 */
	@Deprecated
	<T> IGuiIngredientGroup<T> getIngredientsGroup(Class<T> ingredientClass);
}
