package mezz.jei.api.gui;

import javax.annotation.Nullable;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Lets a mod tell JEI what a slot's item actually represents.
 * <p>
 * Some GUIs put a placeholder item in a normal {@link Slot} to display something that is not an item at all,
 * for example a fluid. JEI reads the slot's {@link ItemStack} directly, so without this it looks up recipes,
 * bookmarks and tooltips for the placeholder instead of the thing the player is looking at.
 * <p>
 * An {@link IAdvancedGuiHandler} cannot be used for this: it is only consulted when the mouse is not over a
 * slot with something in it.
 *
 * @since HEI 4.33.0
 */
public interface ISlotIngredientProvider<T extends GuiContainer> {
	/**
	 * @param guiContainer the gui the slot belongs to
	 * @param slot         the slot the mouse is over
	 * @param stack        what the slot currently holds, never empty
	 *
	 * @return the ingredient this slot really represents, or null to use {@code stack} as-is
	 * @since HEI 4.33.0
	 */
	@Nullable
	Object getSlotIngredient(T guiContainer, Slot slot, ItemStack stack);
}
