package mezz.jei.api;

import mezz.jei.api.recipe.IIngredientType;

import java.util.function.Predicate;

/**
 * Registry for mods to define collapsible ingredient groups in the JEI ingredient list.
 * Groups registered here appear with a "Mod" source tag in the Manage Groups screen.
 * They can be toggled on/off by the user but cannot be edited or deleted.
 * <p>
 * Obtain an instance via {@link IModPlugin#registerCollapsibleGroups(ICollapsibleGroupRegistry)}.
 * <p>
 * Group IDs should include your mod ID to avoid conflicts (e.g. {@code "matteroverdrive:matter_dusts"}).
 * To create a group spanning multiple ingredient types, call {@link #addGroup} once per type
 * using the same {@code id}.
 *
 * @since HEI 4.30.5
 */
public interface ICollapsibleGroupRegistry {

	/**
	 * Register a collapsible group for a specific ingredient type using a type-safe predicate.
	 * <p>
	 * Use {@code VanillaTypes.ITEM} for ItemStack groups and {@code VanillaTypes.FLUID} for
	 * FluidStack groups. Third-party ingredient types registered via {@link IIngredientRegistry}
	 * are also supported.
	 *
	 * @param id          unique group ID, should be namespaced with your mod ID
	 * @param displayName localized display name shown in the groups screen
	 * @param type        the ingredient type (e.g. {@code VanillaTypes.ITEM})
	 * @param matcher     predicate receiving a fully-typed {@code V} — no casting needed
	 */
	<V> void addGroup(String id, String displayName, IIngredientType<V> type, Predicate<V> matcher);
}
