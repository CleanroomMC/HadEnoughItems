package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import mezz.jei.Internal;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.Log;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The dependency graph behind a recipe bookmark group.
 * <p>
 * Each node is one step of a chain: an ingredient, plus the recipe that makes it once one is known. An
 * edge from a recipe to an ingredient records how much of that ingredient a single application of the
 * recipe consumes.
 * <p>
 * This class only ever changes the <em>shape</em> of the chain. Everything quantitative — how much of
 * each step is needed, what to craft, what is missing — is worked out by {@link ChainSolution} and
 * {@link CraftingPlan} and handed back as values, so nothing here writes running totals onto the nodes
 * the overlay is drawing.
 */
public class RecipeChain {
	private final RecipeGraph graph = new RecipeGraph();
	private final RecipeBookmarkGroup group;
	private final AliasIndex aliasIndex = new AliasIndex();
	/** primary output -> the other outputs its recipe yields at the same time */
	private final Map<RecipeBookmarkItem<?>, List<RecipeBookmarkItem<?>>> secondaryOutputs = new Reference2ObjectLinkedOpenHashMap<>();

	@Nullable
	private ChainSolution solution;

	public RecipeChain(RecipeBookmarkGroup group) {
		this.group = group;
	}

	/** The current amounts, worked out on demand and held until the chain's shape changes. */
	public ChainSolution solution() {
		if (solution == null) {
			solution = ChainSolution.solve(graph);
		}
		return solution;
	}

	/** What is left to do given what the player is carrying. Never cached: the inventory moves constantly. */
	public CraftingPlan plan() {
		return CraftingPlan.compute(graph, solution());
	}

	/** Called whenever the chain changes, in shape or in what the player asked of it. */
	void invalidate() {
		this.solution = null;
		this.group.onChainChanged();
	}

	/**
	 * True when nothing in the chain consumes this node, so it is only here because the player asked for
	 * it. Such a node at zero would be a row that plans nothing, which is what removing it is for.
	 */
	boolean isRoot(RecipeBookmarkItem<?> node) {
		return graph.predecessors(node).isEmpty();
	}

	/**
	 * Adds an output the player has asked for.
	 *
	 * @return true if it was folded into a node that already existed rather than added as a new one
	 */
	public boolean addOutput(RecipeBookmarkItem<?> recipeOutput) {
		// If a node for this same item is already present — usually as some other recipe's ingredient — the
		// player is asking to craft it rather than supply it, so teach that node the recipe in place.
		// Resolved before anything is changed, since expanding it will add nodes to the graph.
		RecipeBookmarkItem<?> existing = findNodeMatching(recipeOutput.getIngredient());
		if (existing != null) {
			existing.setIngredient(recipeOutput.getIngredient());
			existing.populateWith(recipeOutput.recipe, recipeOutput.category);
			aliasIndex.reindex(existing);
			expandNode(existing, true);
			removeDanglingNodes();
			invalidate();
			return true;
		}

		recipeOutput.selfOutputAmount = recipeOutput.outputAmount;
		expandNode(recipeOutput, true);
		invalidate();
		return false;
	}

	@Nullable
	private RecipeBookmarkItem<?> findNodeMatching(Object ingredient) {
		for (RecipeBookmarkItem<?> node : graph.nodes()) {
			if (IngredientUtil.aliasesContains(node.aliases(), ingredient)) {
				return node;
			}
		}
		return null;
	}

	/**
	 * Links {@code requester} to a node for each ingredient its recipe needs, creating those nodes where
	 * the chain does not have them yet.
	 *
	 * @param recurse whether newly created ingredient nodes should themselves be expanded
	 */
	private void expandNode(RecipeBookmarkItem<?> requester, boolean recurse) {
		if (!requester.isPopulated()) {
			requester.populateWithFavorite();
			if (!requester.isPopulated()) {
				return;
			}
		}
		graph.addNode(requester);
		aliasIndex.index(requester);
		for (RecipeBookmarkItem<?> input : requester.inputs()) {
			// The input describes what the recipe consumes; the graph node carries the chain-wide total. They
			// must stay separate objects, and the node must not share the input's alias list.
			RecipeBookmarkItem<?> needed = findNodeForIngredient(input);
			if (needed == null) {
				needed = input.asChainNode();
				group.addItemInternal(needed);
				graph.addNode(needed);
				aliasIndex.index(needed);
				if (recurse) {
					needed.populateWithFavorite();
					expandNode(needed, true);
				}

				// This recipe may already be in the chain making something else, in which case this node is
				// one of its by-products rather than a step in its own right.
				RecipeBookmarkItem<?> primaryOutput = findOutputWithSameRecipe(needed);
				if (primaryOutput != null) {
					needed.secondaryTo = primaryOutput;
					secondaryOutputs.computeIfAbsent(primaryOutput, k -> new ObjectArrayList<>()).add(needed);
				}
			}
			addDependency(requester, needed, input.inputAmount(), input.reusableInCrafting);
			needed.setGroup(group); // May not be set yet if the recipe was only just dragged in.
		}
	}

	private void addDependency(RecipeBookmarkItem<?> requester, RecipeBookmarkItem<?> needed, long amount, boolean reusable) {
		if (!graph.putEdge(requester, needed, amount, reusable)) {
			Log.get().warn("Skipped cyclic recipe bookmark dependency from {} to {}.", requester, needed);
		}
	}

	/**
	 * Finds the node standing for the same ingredient as {@code wanted}, matching through ore dictionary
	 * aliases. On a match both alias lists are narrowed to their intersection, so a node reached from
	 * several recipes ends up accepting only what all of them will take.
	 */
	@Nullable
	public RecipeBookmarkItem<?> findNodeForIngredient(RecipeBookmarkItem<?> wanted) {
		IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
		List<String> aliasIds = new ObjectArrayList<>(wanted.aliases().size());
		for (Object alias : wanted.aliases()) {
			aliasIds.add(ingredientRegistry.getUniqueId(alias));
		}
		for (String uniqueId : aliasIds) {
			RecipeBookmarkItem<?> node = aliasIndex.get(uniqueId);
			if (node == null) {
				continue;
			}
			if (!node.foundAliases) {
				node.foundAliases = true;
				node.adoptAliasesOf(wanted);
			}
			node.retainAliases(aliasIds);
			aliasIndex.reindex(node);
			return node;
		}
		return null;
	}

	/**
	 * The node an ingredient resolved to, without changing anything.
	 * <p>
	 * Used for display, so that the slot drawn under a recipe shows the ingredient the chain actually
	 * settled on rather than whichever ore dictionary variant the recipe happened to list first.
	 */
	@Nullable
	RecipeBookmarkItem<?> resolvedNodeFor(RecipeBookmarkItem<?> input) {
		IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
		for (Object alias : input.aliases()) {
			RecipeBookmarkItem<?> node = aliasIndex.get(ingredientRegistry.getUniqueId(alias));
			if (node != null) {
				return node;
			}
		}
		return null;
	}

	/**
	 * Finds a node for a <em>different</em> item made by the same recipe as {@code output}, meaning
	 * {@code output} falls out of that recipe as a by-product.
	 */
	@Nullable
	public RecipeBookmarkItem<?> findOutputWithSameRecipe(RecipeBookmarkItem<?> output) {
		if (output.recipe == null) {
			return null;
		}
		IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
		String uniqueId = ingredientRegistry.getUniqueId(output.getIngredient());
		for (BookmarkItem<?> item : group.getItemsInternal()) {
			if (!(item instanceof RecipeBookmarkItem)) {
				continue;
			}
			RecipeBookmarkItem<?> node = (RecipeBookmarkItem<?>) item;
			if (node == output || node.recipe == null) {
				continue;
			}
			// A by-product shares the recipe but is a different item.
			if (node.recipe.equals(output.recipe) && !uniqueId.equals(ingredientRegistry.getUniqueId(node.getIngredient()))) {
				return node;
			}
		}
		return null;
	}

	/** The by-products produced alongside {@code primaryOutput}, if any. */
	public List<RecipeBookmarkItem<?>> getSecondaryOutputs(RecipeBookmarkItem<?> primaryOutput) {
		List<RecipeBookmarkItem<?>> outputs = secondaryOutputs.get(primaryOutput);
		return outputs == null ? Collections.emptyList() : outputs;
	}

	public void removeNode(RecipeBookmarkItem<?> node) {
		if (!graph.contains(node)) {
			return;
		}
		graph.removeNode(node);
		aliasIndex.remove(node);
		promoteSecondaryOutputs(node);
		detachSecondary(node);
		removeDanglingNodes();
		// Removing a step can leave the recipes that fed it needing to be relinked.
		for (RecipeBookmarkItem<?> remaining : new ObjectArrayList<>(graph.nodes())) {
			expandNode(remaining, false);
		}
		invalidate();
	}

	/**
	 * Detaches the by-products of a removed node, handing the role of primary to the first of them so the
	 * rest stay grouped together.
	 */
	private void promoteSecondaryOutputs(RecipeBookmarkItem<?> removed) {
		List<RecipeBookmarkItem<?>> orphaned = secondaryOutputs.remove(removed);
		if (orphaned == null || orphaned.isEmpty()) {
			return;
		}
		RecipeBookmarkItem<?> newPrimary = orphaned.remove(0);
		newPrimary.secondaryTo = null;
		if (orphaned.isEmpty()) {
			return;
		}
		for (RecipeBookmarkItem<?> secondary : orphaned) {
			secondary.secondaryTo = newPrimary;
		}
		secondaryOutputs.put(newPrimary, orphaned);
	}

	/** Unlinks a by-product from the output it was listed under. */
	private void detachSecondary(RecipeBookmarkItem<?> node) {
		if (node.secondaryTo instanceof RecipeBookmarkItem) {
			List<RecipeBookmarkItem<?>> siblings = secondaryOutputs.get(node.secondaryTo);
			if (siblings != null) {
				siblings.remove(node);
				if (siblings.isEmpty()) {
					secondaryOutputs.remove(node.secondaryTo);
				}
			}
		}
		node.secondaryTo = null;
	}

	/**
	 * Drops nodes nothing asks for any more, from the group as well as the graph.
	 * <p>
	 * They have to leave the group too: a populated node is written to the bookmark file, so a node left
	 * behind after the chain stopped needing it would be loaded back in on the next launch and rebuild the
	 * fragment the player had just done away with.
	 */
	private void removeDanglingNodes() {
		ChainSolution current = ChainSolution.solve(graph);
		List<RecipeBookmarkItem<?>> dangling = new ObjectArrayList<>();
		for (RecipeBookmarkItem<?> node : graph.nodes()) {
			// Something the player asked for stays even at zero; it is theirs to scroll back up.
			if (node.selfOutputAmount == 0L && current.requiredOf(node) == 0L) {
				dangling.add(node);
			}
		}
		for (RecipeBookmarkItem<?> node : dangling) {
			graph.removeNode(node);
			aliasIndex.remove(node);
			promoteSecondaryOutputs(node);
			detachSecondary(node);
			group.removeItemInternal(node);
		}
	}

	/** Rebuilds every edge from the group's saved items, after bookmarks are loaded from disk. */
	public void rebuildGraph() {
		aliasIndex.clear();
		for (BookmarkItem<?> item : group.getItemsInternal()) {
			if (item instanceof RecipeBookmarkItem) {
				RecipeBookmarkItem<?> node = (RecipeBookmarkItem<?>) item;
				graph.addNode(node);
				aliasIndex.index(node);
			}
		}
		for (BookmarkItem<?> item : group.getItemsInternal()) {
			if (!(item instanceof RecipeBookmarkItem)) {
				continue;
			}
			RecipeBookmarkItem<?> requester = (RecipeBookmarkItem<?>) item;
			requester.populateSelf(); // Finds the recipe's inputs again and sets their aliases.
			for (RecipeBookmarkItem<?> input : requester.inputs()) {
				RecipeBookmarkItem<?> needed = findNodeForIngredient(input);
				if (needed == null) {
					Log.get().warn("Failed to get connections for {}", input);
					needed = input.asChainNode();
					graph.addNode(needed);
					aliasIndex.index(needed);
				}
				addDependency(requester, needed, input.inputAmount(), input.reusableInCrafting);
			}
		}
		rebuildSecondaryOutputs();
		invalidate();
	}

	/**
	 * Works out afresh which saved outputs are by-products of another, after a load.
	 * <p>
	 * Deliberately one-directional: each output looks only at the ones already settled ahead of it, so the
	 * first of a recipe's outputs is its primary and the rest hang off that one. Letting every output scan
	 * the whole group instead made two outputs of the same recipe each name the other as its primary, and
	 * since the overlay draws only primaries, both then vanished on the next launch.
	 */
	private void rebuildSecondaryOutputs() {
		secondaryOutputs.clear();
		IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
		List<RecipeBookmarkItem<?>> settled = new ObjectArrayList<>();
		for (BookmarkItem<?> item : group.getItemsInternal()) {
			if (!(item instanceof RecipeBookmarkItem)) {
				continue;
			}
			RecipeBookmarkItem<?> output = (RecipeBookmarkItem<?>) item;
			output.secondaryTo = null;
			if (output.recipe == null) {
				continue;
			}
			String uniqueId = ingredientRegistry.getUniqueId(output.getIngredient());
			for (RecipeBookmarkItem<?> earlier : settled) {
				// Only ever attach to a primary, so by-products never form a chain of their own.
				if (earlier.secondaryTo == null
					&& earlier.recipe.equals(output.recipe)
					&& !uniqueId.equals(ingredientRegistry.getUniqueId(earlier.getIngredient()))) {
					output.secondaryTo = earlier;
					secondaryOutputs.computeIfAbsent(earlier, k -> new ObjectArrayList<>()).add(output);
					break;
				}
			}
			settled.add(output);
		}
	}

	/**
	 * Maps every alias of every node to that node, so a node can be found by any ingredient it accepts
	 * without rescanning the group.
	 * <p>
	 * The keys a node occupies are tracked alongside it, because alias lists get narrowed as the chain
	 * discovers what all the recipes involved will actually take. Where two nodes share an alias the most
	 * recently indexed one wins, matching how a full rescan would have resolved it.
	 */
	private static final class AliasIndex {
		private final Map<String, RecipeBookmarkItem<?>> nodesByAlias = new Object2ObjectLinkedOpenHashMap<>();
		private final Map<RecipeBookmarkItem<?>, List<String>> aliasesByNode = new Reference2ObjectLinkedOpenHashMap<>();

		@Nullable
		RecipeBookmarkItem<?> get(String uniqueId) {
			return nodesByAlias.get(uniqueId);
		}

		void index(RecipeBookmarkItem<?> node) {
			if (!aliasesByNode.containsKey(node)) {
				put(node);
			}
		}

		void reindex(RecipeBookmarkItem<?> node) {
			remove(node);
			put(node);
		}

		private void put(RecipeBookmarkItem<?> node) {
			IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
			List<String> keys = new ObjectArrayList<>(node.aliases().size());
			for (Object alias : node.aliases()) {
				String uniqueId = ingredientRegistry.getUniqueId(alias);
				nodesByAlias.put(uniqueId, node);
				keys.add(uniqueId);
			}
			aliasesByNode.put(node, keys);
		}

		void remove(RecipeBookmarkItem<?> node) {
			List<String> keys = aliasesByNode.remove(node);
			if (keys == null) {
				return;
			}
			for (String key : keys) {
				// Only give up a key still pointing at this node; another node may have taken it over.
				if (nodesByAlias.get(key) == node) {
					nodesByAlias.remove(key);
				}
			}
		}

		void clear() {
			nodesByAlias.clear();
			aliasesByNode.clear();
		}
	}
}
