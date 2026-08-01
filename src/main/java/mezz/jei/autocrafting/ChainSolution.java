package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * How much of every step in a chain is needed, worked out in one pass.
 * <p>
 * A solution is a value: reads the graph and produces amounts, rather than writing running totals back onto the nodes.
 * That matters because the nodes are also what the bookmark overlay draws, with the totals living on them
 * it would mean the overlay showing whatever the last traversal happened to leave behind,
 * and every caller having to restore them afterward.
 */
public final class ChainSolution {

	private static final ChainSolution EMPTY = new ChainSolution(new Reference2LongOpenHashMap<>(), Collections.emptyList());

	/**
	 * Orders a by-product after the output it is produced alongside, leaving everything else to the
	 * graph's own insertion-stable ordering.
	 */
	static final Comparator<RecipeBookmarkItem<?>> SECONDARY_OUTPUTS_LAST = (left, right) -> {
		if (left == right.secondaryTo) {
			return 1;
		}
		if (right == left.secondaryTo) {
			return -1;
		}
		return 0;
	};

	private final Reference2LongMap<RecipeBookmarkItem<?>> required;
	/** Every recipe before the ingredients it needs but reversed, as it is the order of crafting. */
	private final List<RecipeBookmarkItem<?>> order;

	private ChainSolution(Reference2LongMap<RecipeBookmarkItem<?>> required, List<RecipeBookmarkItem<?>> order) {
		this.required = required;
		this.order = order;
	}

	public static ChainSolution empty() {
		return EMPTY;
	}

	/**
	 * Works out the total needed of every node.
	 */
	static ChainSolution solve(RecipeGraph graph) {
		List<RecipeBookmarkItem<?>> order = graph.topologicalOrder(SECONDARY_OUTPUTS_LAST);
		if (order.isEmpty()) {
			return EMPTY;
		}

		Reference2LongOpenHashMap<RecipeBookmarkItem<?>> required = new Reference2LongOpenHashMap<>(order.size());
		required.defaultReturnValue(0L);
		for (RecipeBookmarkItem<?> node : order) {
			required.put(node, node.selfOutputAmount);
		}

		// Topological order guarantees a recipe's own total is final before its ingredients are visited
		for (RecipeBookmarkItem<?> node : order) {
			accumulateDemand(graph, required, node);
		}
		return new ChainSolution(required, Collections.unmodifiableList(order));
	}

	/**
	 * How much of {@code needed} the recipes that consume it call for, given the amounts already settled
	 * for those recipes.
	 * <p>
	 * Shared with {@link CraftingPlan}, which walks the same order over its own net amounts so that stock
	 * spent at one step reduces what the steps below it have to make.
	 *
	 * @param settled how much is wanted of each node, final for everything before {@code needed} in
	 *                topological order
	 */
	static long demandOn(RecipeGraph graph, Reference2LongMap<RecipeBookmarkItem<?>> settled, RecipeBookmarkItem<?> needed) {
		long consumed = 0L;
		long heldAside = 0L;
		for (RecipeBookmarkItem<?> requester : graph.predecessors(needed)) {
			if (requester.outputAmount == 0) {
				continue; // Not actually produced by its own recipe; nothing sensible to scale by.
			}
			RecipeGraph.Edge edge = graph.edge(requester, needed);
			if (edge == null) {
				continue;
			}
			if (edge.reusable) {
				// A tool is handed back after every craft, so the same one serves every recipe that calls
				// for it. What is needed is enough to satisfy the single hungriest recipe, not the sum of
				// them — otherwise a hammer used at two steps of a chain reads as two hammers.
				heldAside = Math.max(heldAside, edge.amount);
			} else {
				consumed += craftsFor(settled.getLong(requester), requester.outputAmount) * edge.amount;
			}
		}
		// An ingredient can be consumed by one recipe and used as a tool by another, and then both are
		// needed at once.
		return consumed + heldAside;
	}

	private static void accumulateDemand(RecipeGraph graph, Reference2LongOpenHashMap<RecipeBookmarkItem<?>> required, RecipeBookmarkItem<?> needed) {
		long total = required.getLong(needed) + demandOn(graph, required, needed);
		required.put(needed, total);

		if (needed.secondaryTo instanceof RecipeBookmarkItem) {
			// A by-product comes out in step with its primary, so the primary has to cover the larger demand.
			RecipeBookmarkItem<?> primary = (RecipeBookmarkItem<?>) needed.secondaryTo;
			required.put(primary, Math.max(required.getLong(primary), total));
		}
	}

	/** Times a recipe yielding {@code perCraft} must run to produce at least {@code required}. */
	static long craftsFor(long required, long perCraft) {
		if (perCraft <= 0L) {
			return 0L;
		}
		return (required + perCraft - 1L) / perCraft;
	}

	/** Total of this ingredient the whole chain needs. */
	public long requiredOf(RecipeBookmarkItem<?> node) {
		return required.getLong(node);
	}

	/** Times this node's recipe has to run to cover {@link #requiredOf}. */
	public long craftsOf(RecipeBookmarkItem<?> node) {
		return craftsFor(required.getLong(node), node.outputAmount);
	}

	/**
	 * What the chain will actually produce of this node, which is a whole number of crafts and so may
	 * overshoot what is strictly needed.
	 */
	public long producedOf(RecipeBookmarkItem<?> node) {
		if (node.outputAmount == 0L) {
			return required.getLong(node);
		}
		return craftsOf(node) * node.outputAmount;
	}

	public List<RecipeBookmarkItem<?>> order() {
		return order;
	}

	public boolean isEmpty() {
		return order.isEmpty();
	}
}
