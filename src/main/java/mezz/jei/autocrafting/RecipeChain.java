package mezz.jei.autocrafting;

import com.google.common.graph.ElementOrder;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.autocrafting.toposort.TopologicalSort;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.util.Log;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class RecipeChain {
    // noinspection
    public final MutableValueGraph<RecipeBookmarkItem<?>, Integer> graphStorage = ValueGraphBuilder.directed()
            .allowsSelfLoops(false)
            .nodeOrder(ElementOrder.unordered())
            .expectedNodeCount(128)
            .build();

    public final List<RecipeBookmarkItem<?>> outputs;

    public RecipeChain(RecipeBookmarkItem<?> output) {
        this.outputs = new ObjectArrayList<>();
        this.outputs.add(output);
        expandNodeFirst(output);
    }

    public void recheck() {
        for (RecipeBookmarkItem<?> node : graphStorage.nodes()) {
            if (!node.isPopulated()) {
                node.populateWithFavorite();
            }
        }
        this.outputs.forEach(this::expandNode);
    }

    private void expandNodeFirst(RecipeBookmarkItem<?> requester) {
        if (!requester.isPopulated()) {
            requester.populateWithFavorite();
            if (!requester.isPopulated()) {
                return;
            }
        }
        for (Object input : requester.inputs.keySet()) {
            RecipeBookmarkItem<?> needed = getRecipeOutput(input);
            // If it's already in the graph, it would have been populated if possible.
            if (needed == null) {
                needed = new RecipeBookmarkItem<>(input);
                requester.populateWithFavorite();
                expandNodeFirst(needed);

                BookmarkItem<?> possiblePrimaryOutput = findSameOutputRecipe(needed);
                if (possiblePrimaryOutput != null) {
                    needed.secondaryTo = possiblePrimaryOutput;
                }
            }
            try {
                graphStorage.putEdgeValue(requester, needed, requester.inputs.get(input));
            } catch (IllegalArgumentException e) {
                Log.get().error("Failed to add edge from {} to {}.", requester, needed, e);
            }
        }
    }

    public void expandNode(RecipeBookmarkItem<?> recipeOutput) {
        if (!graphStorage.nodes().contains(recipeOutput)) {
            expandNodeFirst(recipeOutput);
        } else for (RecipeBookmarkItem<?> node : graphStorage.successors(recipeOutput)) {
            expandNode(node);
        }
    }

    public RecipeBookmarkItem<?> getRecipeOutput(Object output) {
        return graphStorage.nodes().stream()
                .filter(node -> node.ingredient.equals(output))
                .findFirst()
                .orElse(null);
    }

    public RecipeBookmarkItem<?> findSameOutputRecipe(RecipeBookmarkItem<?> output) {
        return graphStorage.nodes().stream()
                .filter(node -> node.recipe.equals(output.recipe))
                .findFirst()
                .orElse(null);
    }

    public void update() {
        TopologicalSort.topologicalSort(graphStorage, (r, r1) -> {
            if (r.equals(r1.secondaryTo)) {
                return 1;
            } else if (r1.equals(r.secondaryTo)) {
                return -1;
            }
            return 0; // Primary ordering still applies.
        }).forEach(this::update);
    }

    public void update(RecipeBookmarkItem<?> needed) {
        if (graphStorage.predecessors(needed).isEmpty())
            return;
        for (RecipeBookmarkItem<?> requester : graphStorage.predecessors(needed)) {
            if (requester.outputAmount == 0) {
                Log.get().warn("Requester {} is apparently not made by its own recipe? Curious.", requester);
                continue;
            }
            // Divide the amount of the item used in the recipe by how many of the requested item it produces (rounding up).
            needed.amount += (graphStorage.edgeValue(requester, needed) + requester.outputAmount - 1) / requester.outputAmount;
        }
        if (needed.secondaryTo != null) {
            needed.secondaryTo.amount = Math.max(needed.secondaryTo.amount, needed.amount);
        }
    }

}
