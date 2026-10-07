package mezz.jei.api;

import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IIngredientType;

import java.util.function.Predicate;

/**
 * Registry for mods to define collapsible groups in the HEI ingredient list.
 * They can be toggled on/off by the user but cannot be edited or deleted.
 * Obtain an instance via {@link IModPlugin#registerCollapsibleGroups(ICollapsibleGroupRegistry)}.
 * EXAMPLE 1 — Group with filtered items:
 * <pre><code>
 * registry.newGroup("mymod:my_group", "group.mymod.my_group")
 * 		.addAny(VanillaTypes.ITEM, stack -> stack.getItem() instanceof ItemFood);
 * </code></pre>
 * EXAMPLE 2 — Group mixing exact items, fluids, and all of a type:
 * <pre><code>
 *     registry.newGroup("mymod:my_group", "group.mymod.my_group")
 *             .add(new ItemStack(Items.WOODEN_PICKAXE))              // add one exact item
 *             .add(new ItemStack(Blocks.BEDROCK, 1, 1))              // add a specific block variant
 *             .add(FluidRegistry.getFluidStack("water", 1000))       // add an exact fluid
 *             .addAllOf(VanillaTypes.FLUID);                              // add every ingredient of an ingredient type
 * </code></pre>
 * @since HEI 4.30.0
 */
public interface ICollapsibleGroupRegistry {

	/**
	 * Creates (or retrieves) a collapsible group builder
	 * Multiple calls with the same {@code id} return a builder for the same logical group.
	 * Call {@link Builder#build()} to finalize and register the group to the registry
	 *
	 * @param id      unique group ID, should be namespaced with your mod ID.
	 * @param langKey translation key for the group name.
	 */
	Builder newGroup(String id, String langKey);

	interface Builder {

		/**
		 * Add ingredients to the group
		 *
		 * @param ingredients ingredients to add
		 * @return this builder for chaining
		 */
		Builder add(Object... ingredients);

		/**
		 * Add every ingredient of the given type(s) to the group — no filtering applied.
		 * Use this for custom ingredient types where you want to collapse all registered
		 * instances into one group. Third-party ingredient types registered via
		 * {@link IIngredientRegistry} are supported.
		 *
		 * <p>Note: passing {@code VanillaTypes.ITEM} or {@code VanillaTypes.FLUID} will match
		 * <em>every</em> item or fluid in the game. Prefer {@link #addAny} with a predicate
		 * when you only want a subset.
		 *
		 * @param types the ingredient type(s) where all ingredients of the type would be included
		 * @return this builder for chaining
		 */
		Builder addAllOf(IIngredientType<?>... types);

		/**
		 * Add any ingredient of a given type that matches the provided predicate filter.
		 * Use this when you need to match items by condition (e.g. "all tools", "all ores" etc.).
		 *
		 * @param type   ingredient type (e.g. {@code VanillaTypes.ITEM}).
		 * @param filter filter to match ingredients of a type to be included in the group
		 * @return this builder for chaining
		 */
		<V> Builder addAny(IIngredientType<V> type, Predicate<V> filter);

		/**
		 * Set the color for the collapsable group.
		 *
		 * @param backgroundColor the background color for the group
		 * @param borderColor the color displayed on the border of the group
		 * @return this builder for chaining
		 */
		Builder color(int backgroundColor, int borderColor);

		/**
		 * Finalizes the current builder and registers it
		 */
		void build();

	}

}
