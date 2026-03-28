package mezz.jei.api;

import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IIngredientType;

import java.util.function.Predicate;

/**
 * Registry for mods to define collapsible groups in the HEI ingredient list.
 * They can be toggled on/off by the user but cannot be edited or deleted.
 * First two items are shown based on registration order, position of group is based on first item.
 * 
 * Obtain an instance via {@link IModPlugin#registerCollapsibleGroups(ICollapsibleGroupRegistry)}.
 * 
 * To create a group spanning multiple ingredient types, call {@link #addGroup} once per type
 * using the same {@code id}.
 * 
 * EXAMPLE :
 * @Override
 * public void registerCollapsibleGroups(ICollapsibleGroupRegistry registry) {
 *		registry.addGroup(
 *				"matteroverdrive:colored_floor_tile",
 *				I18n.format("tile.decorative.floor_tile.name"),
 *				VanillaTypes.ITEM,
 *				stack -> Block.getBlockFromItem(stack.getItem()) == MatterOverdrive.BLOCKS.decorative_floor_tile);
 *
 * @since HEI 4.30.5
 */
public interface ICollapsibleGroupRegistry {

	/**
	 * Use {@code VanillaTypes.ITEM} for ItemStack groups and {@code VanillaTypes.FLUID} for
	 * FluidStack groups. Third-party ingredient types registered via {@link IIngredientRegistry}
	 * are also supported.
	 *
	 * @param id          Unique group ID, should be namespaced with your mod ID.
	 * @param displayName Localized display name shown in the groups screen.
	 * @param type        The ingredient type (e.g. {@code VanillaTypes.ITEM}).
	 * @param matcher     Predicate receiving a fully-typed {@code V} — no casting needed.
	 */
	<V> void addGroup(String id, String displayName, IIngredientType<V> type, Predicate<V> matcher);
}
