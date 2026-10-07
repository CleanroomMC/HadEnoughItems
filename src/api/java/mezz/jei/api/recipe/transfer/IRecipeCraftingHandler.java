package mezz.jei.api.recipe.transfer;

import mezz.jei.api.gui.IRecipeLayout;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import javax.annotation.Nullable;

public interface IRecipeCraftingHandler<C extends Container> extends IRecipeTransferHandler<C> {
	/**
	 * Implementations of this method must lead to {@link IAutocraftingHandler#stepFinished} being called at some point!
	 * @param container    the container to act on
	 * @param recipeLayout the layout of the recipe, with information about the ingredients
	 * @param player       the player, to do the slot manipulation
	 * @param amount       number of sets of items to transfer
	 * @param doTransfer   if true, do the transfer. if false, check for errors but do not actually transfer the items
	 * @return a recipe transfer error if the recipe can't be transferred. Return null on success.
	 * @since HEI 4.29.0
	 */
	@Nullable
	IRecipeTransferError craft(C container, IRecipeLayout recipeLayout, EntityPlayer player, int amount, boolean doTransfer);
}
