package mezz.jei.api;

import net.minecraft.item.ItemStack;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Registry for mods to define collapsible ingredient groups in the JEI ingredient list.
 * Groups registered here appear with a "Mod" source tag in the Manage Groups screen.
 * They can be toggled on/off by the user but cannot be edited or deleted.
 * <p>
 * Obtain an instance via {@link IModPlugin#registerCollapsibleGroups(ICollapsibleGroupRegistry)}.
 * <p>
 * Group IDs should include your mod ID to avoid conflicts (e.g. {@code "matteroverdrive:matter_dusts"}).
 *
 * @since HEI 4.30.4
 */
public interface ICollapsibleGroupRegistry {

	/**
	 * Register a collapsible group for ItemStack ingredients using a predicate.
	 * Non-ItemStack ingredients are automatically excluded.
	 *
	 * @param id          unique group ID, should be namespaced with your mod ID
	 * @param displayName localized display name shown in the groups screen
	 * @param matcher     predicate that returns true for ItemStacks belonging to this group
	 */
	void addGroup(String id, String displayName, Predicate<ItemStack> matcher);

	/**
	 * Register a collapsible group that matches any ingredient type using a predicate.
	 * Use this for fluids, custom ingredient types, or mixed groups.
	 *
	 * @param id          unique group ID, should be namespaced with your mod ID
	 * @param displayName localized display name shown in the groups screen
	 * @param matcher     predicate on the raw ingredient object
	 */
	void addGroupForType(String id, String displayName, Predicate<Object> matcher);

	/**
	 * Register a collapsible group containing specific ingredients.
	 * The collection may contain any mix of ItemStacks, FluidStacks, or other
	 * registered ingredient types. Each ingredient is resolved to its unique identifier
	 * at registration time.
	 *
	 * @param id          unique group ID, should be namespaced with your mod ID
	 * @param displayName localized display name shown in the groups screen
	 * @param ingredients the specific ingredients belonging to this group
	 */
	void addGroup(String id, String displayName, Collection<?> ingredients);
}
