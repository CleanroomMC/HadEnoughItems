package mezz.jei.api.recipe.transfer;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import mezz.jei.api.gui.IRecipeLayout;

import javax.annotation.Nullable;

/**
 * A reason that a recipe transfer couldn't happen.
 * <p>
 * Recipe transfer errors can be created with {@link IRecipeTransferHandlerHelper} or you can implement your own.
 * These errors are returned from {@link IRecipeTransferHandler#transferRecipe(Container, IRecipeLayout, EntityPlayer, boolean, boolean)}.
 */
public interface IRecipeTransferError {
	enum Type {
		/**
		 * Errors where the Transfer handler is broken or does not work.
		 * These errors will hide the recipe transfer button, and do not display anything to the user.
		 */
		INTERNAL,

		/**
		 * Errors that the player can fix. Missing items, inventory full, etc.
		 * Something informative will be shown to the player.
		 */
		USER_FACING
	}

	Type getType();

	/**
	 * Called on {@link Type#USER_FACING} errors.
	 */
	void showError(Minecraft minecraft, int mouseX, int mouseY, IRecipeLayout recipeLayout, int recipeX, int recipeY);

	/**
	 * A reason that a recipe transfer couldn't happen, as a string.
	 * <p>
	 * Nullable as it is a later addition to the API.
	 *
	 * @since HEI 4.33.0
	 * @return reason of why the error has occurred
	 */
	@Nullable
	default String getSimpleReason() {
		return null;
	}
}
