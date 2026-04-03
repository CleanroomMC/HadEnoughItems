package mezz.jei.api;

import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IIngredientType;

import java.util.function.Predicate;

/**
 * Registry for mods to define collapsible groups in the HEI ingredient list.
 * They can be toggled on/off by the user but cannot be edited or deleted.
 *
 * Obtain an instance via {@link IModPlugin#registerCollapsibleGroups(ICollapsibleGroupRegistry)}.
 *
 * EXAMPLE 1 — Group with filtered items:
 * 
 * @Override
 * public void registerCollapsibleGroups(ICollapsibleGroupRegistry registry) {
 *     registry.newGroup("matteroverdrive:colored_floor_tile", "tile.decorative.floor_tile.name")
 *             .addAny(VanillaTypes.ITEM,
 *                     stack -> Block.getBlockFromItem(stack.getItem()) == MatterOverdrive.BLOCKS.decorative_floor_tile);
 * }
 * 
 *
 * EXAMPLE 2 — Group mixing exact items, fluids, and all of a type:
 * 
 * @Override
 * public void registerCollapsibleGroups(ICollapsibleGroupRegistry registry) {
 *     registry.newGroup("mymod:my_group", "group.mymod.my_group")
 *             .add(new ItemStack(MyMod.Items.PICKAXE))              // add one exact item
 *             .add(new ItemStack(MyMod.Blocks.MY_BLOCK, 1, 1))      // add a specific block variant (meta=1)
 *             .add(FluidRegistry.getFluidStack("water", 1000))      // add an exact fluid
 *             .addAllOf(MyMod.CUSTOM_INGREDIENT_TYPE);              // add every ingredient of a custom type
 * }
 * 
 *
 * EXAMPLE 3 — Multiple groups from one plugin:
 * 
 * @Override
 * public void registerCollapsibleGroups(ICollapsibleGroupRegistry registry) {
 *     registry.newGroup("mymod:ores", "group.mymod.ores")
 *             .addAny(VanillaTypes.ITEM, stack -> isOre(stack));
 *
 *     registry.newGroup("mymod:gems", "group.mymod.gems")
 *             .addAny(VanillaTypes.ITEM, stack -> isGem(stack));
 * }
 * 
 *
 * @since HEI 4.30.0
 */
public interface ICollapsibleGroupRegistry {

	/**
	 * Creates (or retrieves) a mod collapsible group builder.
	 *
	 * Multiple calls with the same {@code id} return a builder for the same logical group.
	 *
	 * @param id      Unique group ID, should be namespaced with your mod ID.
	 * @param langKey Unlocalized translation key for the group name (e.g. {@code "tile.mymod.name"}).
	 */
	CollapsibleGroupBuilder newGroup(String id, String langKey);

	interface CollapsibleGroupBuilder {
		/**
		 * Add one exact ingredient to the group.
		 *
		 * The backend resolves this ingredient to its unique id and matches by id.
		 * Works with ItemStacks, FluidStacks, or any registered ingredient type.
		 *
		 * @param ingredient the ingredient to add (e.g. new ItemStack(Items.APPLE))
		 * @return this builder for chaining
		 */
		CollapsibleGroupBuilder add(Object ingredient);

		/**
		 * Add multiple exact ingredients to the group. Equivalent to calling {@link #add(Object)}
		 * for each element in order.
		 *
		 * @param ingredients varargs list of ingredients to add
		 * @return this builder for chaining
		 */
		CollapsibleGroupBuilder add(Object... ingredients);

		/**
		 * Add every ingredient of the given type(s) to the group — no filtering applied.
		 *
		 * Use this for custom ingredient types where you want to collapse all registered
		 * instances into one group. Third-party ingredient types registered via
		 * {@link IIngredientRegistry} are supported.
		 *
		 * <p>Note: passing {@code VanillaTypes.ITEM} or {@code VanillaTypes.FLUID} will match
		 * <em>every</em> item or fluid in the game. Prefer {@link #addAny} with a predicate
		 * when you only want a subset.
		 *
		 * @param types the ingredient type(s) whose every instance should be included
		 * @return this builder for chaining
		 */
		CollapsibleGroupBuilder addAllOf(IIngredientType<?>... types);

		/**
		 * Add any ingredient of a given type that matches the provided predicate filter.
		 *
		 * Use this when you need to match items by condition (e.g. "all tools", "all ores", etc).
		 *
		 * @param type   The ingredient type (e.g. {@code VanillaTypes.ITEM}).
		 * @param filter Predicate receiving a fully-typed {@code V} — no casting needed.
		 * @return this builder for chaining
		 */
		<V> CollapsibleGroupBuilder addAny(IIngredientType<V> type, Predicate<V> filter);
	}
}
