package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * A directed acyclic graph of recipe nodes, where an edge from a requester to an ingredient it needs
 * carries how much of that ingredient one round of the requester's recipe consumes.
 * <p>
 * Nodes are compared by identity, two bookmarks of the same item are
 * different steps in a chain and must not collapse into one node.
 * <p>
 * The graph refuses edges that would introduce a cycle, so {@link #topologicalOrder} always succeeds.
 * Iteration order of nodes, edges and the resulting sort is insertion-stable keeping the bookmarks from reshuffling itself.
 */
final class RecipeGraph {

	private final Map<RecipeBookmarkItem<?>, Map<RecipeBookmarkItem<?>, Edge>> outgoing = new Reference2ObjectLinkedOpenHashMap<>();
	private final Map<RecipeBookmarkItem<?>, Set<RecipeBookmarkItem<?>>> incoming = new Reference2ObjectLinkedOpenHashMap<>();

	static final class Edge {

		final long amount;
		/**
		 * True when the ingredient survives the craft and is handed back.
		 * A reusable ingredient is needed once no matter how many times the recipe runs.
		 */
		final boolean reusable;

		Edge(long amount, boolean reusable) {
			this.amount = amount;
			this.reusable = reusable;
		}

	}

	Set<RecipeBookmarkItem<?>> nodes() {
		return outgoing.keySet();
	}

	boolean contains(RecipeBookmarkItem<?> node) {
		return outgoing.containsKey(node);
	}

	void addNode(RecipeBookmarkItem<?> node) {
		outgoing.computeIfAbsent(node, k -> new Reference2ObjectLinkedOpenHashMap<>());
		incoming.computeIfAbsent(node, k -> new ReferenceLinkedOpenHashSet<>());
	}

	Set<RecipeBookmarkItem<?>> successors(RecipeBookmarkItem<?> node) {
		Map<RecipeBookmarkItem<?>, Edge> edges = outgoing.get(node);
		return edges == null ? Collections.emptySet() : edges.keySet();
	}

	Set<RecipeBookmarkItem<?>> predecessors(RecipeBookmarkItem<?> node) {
		Set<RecipeBookmarkItem<?>> requesters = incoming.get(node);
		return requesters == null ? Collections.emptySet() : requesters;
	}

	@Nullable
	Edge edge(RecipeBookmarkItem<?> requester, RecipeBookmarkItem<?> needed) {
		Map<RecipeBookmarkItem<?>, Edge> edges = outgoing.get(requester);
		return edges == null ? null : edges.get(needed);
	}

	/**
	 * Records that the {@code requester} needs {@code amount} of {@code needed} per round.
	 * Replacing any existing edge between the two.
	 *
	 * @return false if the edge was refused because it would have closed a cycle
	 */
	boolean putEdge(RecipeBookmarkItem<?> requester, RecipeBookmarkItem<?> needed, long amount, boolean reusable) {
		if (requester == needed || wouldCreateCycle(requester, needed)) {
			return false;
		}
		addNode(requester);
		addNode(needed);
		outgoing.get(requester).put(needed, new Edge(amount, reusable));
		incoming.get(needed).add(requester);
		return true;
	}

	void removeNode(RecipeBookmarkItem<?> node) {
		Map<RecipeBookmarkItem<?>, Edge> needs = outgoing.remove(node);
		if (needs != null) {
			for (RecipeBookmarkItem<?> needed : needs.keySet()) {
				Set<RecipeBookmarkItem<?>> requesters = incoming.get(needed);
				if (requesters != null) {
					requesters.remove(node);
				}
			}
		}
		Set<RecipeBookmarkItem<?>> requesters = incoming.remove(node);
		if (requesters != null) {
			for (RecipeBookmarkItem<?> requester : requesters) {
				Map<RecipeBookmarkItem<?>, Edge> edges = outgoing.get(requester);
				if (edges != null) {
					edges.remove(node);
				}
			}
		}
	}

	private boolean wouldCreateCycle(RecipeBookmarkItem<?> requester, RecipeBookmarkItem<?> needed) {
		if (!contains(requester) || !contains(needed)) {
			return false;
		}
		Set<RecipeBookmarkItem<?>> visited = new ReferenceLinkedOpenHashSet<>();
		Deque<RecipeBookmarkItem<?>> pending = new ArrayDeque<>();
		pending.add(needed);
		while (!pending.isEmpty()) {
			RecipeBookmarkItem<?> node = pending.removeFirst();
			if (node == requester) {
				return true;
			}
			if (visited.add(node)) {
				pending.addAll(successors(node));
			}
		}
		return false;
	}

	/**
	 * Orders the graph so every recipe comes before the ingredients it needs.
	 * <p>
	 * {@link #putEdge} rejects cycles hence this is a complete ordering of the graph.
	 * Should a cycle ever somehow enter, the nodes it traps are appended in insertion order rather than being dropped.
	 */
	List<RecipeBookmarkItem<?>> topologicalOrder(Comparator<? super RecipeBookmarkItem<?>> tieBreaker) {
		Map<RecipeBookmarkItem<?>, Integer> remainingDependents = new Reference2ObjectLinkedOpenHashMap<>();
		Queue<RecipeBookmarkItem<?>> ready = new PriorityQueue<>(Math.max(1, outgoing.size()), tieBreaker);
		for (RecipeBookmarkItem<?> node : outgoing.keySet()) {
			int inDegree = predecessors(node).size();
			if (inDegree == 0) {
				ready.add(node);
			} else {
				remainingDependents.put(node, inDegree);
			}
		}

		List<RecipeBookmarkItem<?>> sorted = new ArrayList<>(outgoing.size());
		while (!ready.isEmpty()) {
			RecipeBookmarkItem<?> current = ready.remove();
			sorted.add(current);
			for (RecipeBookmarkItem<?> needed : successors(current)) {
				Integer remaining = remainingDependents.get(needed);
				if (remaining == null) {
					continue;
				}
				if (remaining == 1) {
					remainingDependents.remove(needed);
					ready.add(needed);
				} else {
					remainingDependents.put(needed, remaining - 1);
				}
			}
		}

		if (!remainingDependents.isEmpty()) {
			sorted.addAll(remainingDependents.keySet());
		}
		return sorted;
	}
}
