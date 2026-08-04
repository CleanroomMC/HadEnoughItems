package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import mezz.jei.Internal;
import mezz.jei.ingredients.IngredientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CraftingPlan {

	private static final CraftingPlan EMPTY = new CraftingPlan(Collections.emptyList(), Collections.emptyList());

	private final List<Step> steps;
	private final List<Need> missing;

	private CraftingPlan(List<Step> steps, List<Need> missing) {
		this.steps = steps;
		this.missing = missing;
	}

	public static CraftingPlan empty() {
		return EMPTY;
	}

	public static final class Step {

		private final RecipeBookmarkItem<?> recipe;
		private final long crafts;

		Step(RecipeBookmarkItem<?> recipe, long crafts) {
			this.recipe = recipe;
			this.crafts = crafts;
		}

		public RecipeBookmarkItem<?> recipe() {
			return recipe;
		}

		public long crafts() {
			return crafts;
		}

	}

	public static final class Need {

		private final RecipeBookmarkItem<?> ingredient;
		private long amount;

		Need(RecipeBookmarkItem<?> ingredient, long amount) {
			this.ingredient = ingredient;
			this.amount = amount;
		}

		public RecipeBookmarkItem<?> ingredient() {
			return ingredient;
		}

		public long amount() {
			return amount;
		}

	}

	/**
	 * Works out which steps still needs running and which ingredients are missing outright.
	 * <p>
	 * The solution's amounts are gross: what the chain would need starting from nothing.
	 * Then the plan works in net amounts, settling each node in turn and
	 * spending what the player has against it before its own ingredients are visited.
	 * As the order puts a recipe ahead of everything it needs, that reduction carries all the way down.
	 *
	 * @param solution the amounts the chain needs, ignoring what the player has
	 */
	static CraftingPlan compute(RecipeGraph graph, ChainSolution solution) {
		if (solution.isEmpty()) {
			return EMPTY;
		}

		Object2LongOpenHashMap<String> available = collectAvailableIngredients();
		List<Step> steps = new ObjectArrayList<>();
		Map<String, Need> needs = new Object2ObjectLinkedOpenHashMap<>();
		IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();

		List<RecipeBookmarkItem<?>> order = solution.order();
		Reference2LongOpenHashMap<RecipeBookmarkItem<?>> netNeeded = new Reference2LongOpenHashMap<>(order.size());
		netNeeded.defaultReturnValue(0L);
		for (RecipeBookmarkItem<?> node : order) {
			netNeeded.put(node, node.selfOutputAmount);
		}

		for (RecipeBookmarkItem<?> node : order) {
			long stillNeeded = netNeeded.getLong(node) + ChainSolution.demandOn(graph, netNeeded, node);
			if (node.selfOutputAmount == 0L) {
				stillNeeded = drawFromInventory(node, stillNeeded, available, ingredientRegistry);
			}
			netNeeded.put(node, stillNeeded);
			if (stillNeeded <= 0L) {
				continue;
			}

			if (node.category != null && node.recipe != null) {
				steps.add(new Step(node, ChainSolution.craftsFor(stillNeeded, node.outputAmount)));
			} else if (graph.successors(node).isEmpty()) {
				String uniqueId = ingredientRegistry.getUniqueId(node.getIngredient());
				Need shortfall = needs.get(uniqueId);
				if (shortfall == null) {
					needs.put(uniqueId, new Need(node, stillNeeded));
				} else {
					shortfall.amount += stillNeeded;
				}
			}
		}

		if (steps.isEmpty() && needs.isEmpty()) {
			return EMPTY;
		}
		return new CraftingPlan(steps, new ObjectArrayList<>(needs.values()));
	}

	/**
	 * Spends what the player already has against what a step needs, and returns the remainder.
	 * <p>
	 * Every ingredient the step accepts is tried, not just the one the chain happens to display. An ore
	 * dictionary entry such as planks is satisfied by any kind of plank the player is carrying, so a chest
	 * must not be reported as needing oak simply because oak is what the recipe listed first.
	 */
	private static long drawFromInventory(
		RecipeBookmarkItem<?> node,
		long stillNeeded,
		Object2LongOpenHashMap<String> available,
		IngredientRegistry ingredientRegistry
	) {
		for (Object alias : node.aliases()) {
			if (stillNeeded <= 0L) {
				break;
			}
			String aliasId = ingredientRegistry.getUniqueId(alias);
			long inStock = available.getLong(aliasId);
			if (inStock <= 0L) {
				continue;
			}
			long taken = Math.min(inStock, stillNeeded);
			stillNeeded -= taken;
			available.put(aliasId, inStock - taken);
		}
		return stillNeeded;
	}

	/** What the player can draw on: their inventory, plus whatever is already laid out in the crafting grid. */
	private static Object2LongOpenHashMap<String> collectAvailableIngredients() {
		Object2LongOpenHashMap<String> available = new Object2LongOpenHashMap<>();
		available.defaultReturnValue(0L);

		EntityPlayer player = Minecraft.getMinecraft().player;
		if (player == null) {
			return available;
		}
		IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();

		InventoryPlayer inventory = player.inventory;
		for (int i = 0; i < inventory.getSizeInventory(); i++) {
			countStack(inventory.getStackInSlot(i), ingredientRegistry, available);
		}

		Container openContainer = player.openContainer;
		if (openContainer != null) {
			for (Slot slot : openContainer.inventorySlots) {
				// The grid is emptied back to the player before each step, so what is sitting in it counts as
				// available; without this the chain would craft a second set of what is already laid out.
				if (slot.inventory instanceof InventoryCrafting) {
					countStack(slot.getStack(), ingredientRegistry, available);
				}
			}
		}
		return available;
	}

	private static void countStack(ItemStack stack, IngredientRegistry ingredientRegistry, Object2LongOpenHashMap<String> available) {
		if (!stack.isEmpty()) {
			available.addTo(ingredientRegistry.getUniqueId(stack), stack.getCount());
		}
	}

	/** Steps in dependency order; craft them back to front. */
	public List<Step> steps() {
		return steps;
	}

	public List<Need> need() {
		return missing;
	}

	public boolean isEmpty() {
		return steps.isEmpty() && missing.isEmpty();
	}
}
