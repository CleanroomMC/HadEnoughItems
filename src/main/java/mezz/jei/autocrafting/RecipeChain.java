package mezz.jei.autocrafting;

import com.google.common.graph.ElementOrder;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.autocrafting.toposort.TopologicalSort;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class RecipeChain {
    // noinspection
    public final MutableValueGraph<RecipeBookmarkItem<?>, Long> graphStorage = ValueGraphBuilder.directed()
            .allowsSelfLoops(false)
            .nodeOrder(ElementOrder.unordered())
            .expectedNodeCount(128)
            .build();

    public final Map<RecipeBookmarkItem<?>, List<RecipeBookmarkItem<?>>> secondaryOutputs = new Object2ObjectOpenHashMap<>();

    private final List<RecipeBookmarkItem<?>> outputs = new ObjectArrayList<>();

    private final RecipeBookmarkGroup group;

    public RecipeChain(RecipeBookmarkGroup group) {
        this.group = group;
    }

    public void recheck() {
        for (RecipeBookmarkItem<?> node : graphStorage.nodes()) {
            if (!node.isPopulated()) {
                node.populateWithFavorite();
            }
        }
        this.outputs.forEach(this::expandNode);
    }

    public void addOutput(RecipeBookmarkItem<?> recipeOutput) {
        outputs.add(recipeOutput);
        recipeOutput.selfOutputAmount = recipeOutput.outputAmount;
        expandNode(recipeOutput);
    }

    private void expandNodeFirst(RecipeBookmarkItem<?> requester) {
        if (!requester.isPopulated()) {
            requester.populateWithFavorite();
            if (!requester.isPopulated()) {
                return;
            }
        }
        for (RecipeBookmarkItem<?> input : requester.inputs) {
            // First, see if it's already in the graph under some alias.
            RecipeBookmarkItem<?> needed = findOutputUsingAnAlias(input);
            // If it's already in the graph, it would have been populated if possible.
            if (needed == null) {
                needed = new RecipeBookmarkItem<>(input.aliases); // Make a copy of the input; don't modify the original amounts!
                needed.group = group;
                needed.populateWithFavorite();
                expandNodeFirst(needed);

                // Maybe this recipe is being used to make something else, so we should connect it to that.
                RecipeBookmarkItem<?> possiblePrimaryOutput = findOutputWithSameRecipe(needed);
                if (possiblePrimaryOutput != null) {
                    needed.secondaryTo = possiblePrimaryOutput;
                    secondaryOutputs.computeIfAbsent(possiblePrimaryOutput, k -> new ObjectArrayList<>())
                            .add(needed);
                }
            }
            try {
                graphStorage.putEdgeValue(requester, needed, input.amount);
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

    public RecipeBookmarkItem<?> findOutputUsingAnAlias(RecipeBookmarkItem<?> output) {
        return graphStorage.nodes().stream()
                .filter(node -> output.aliases.contains(node.ingredient))
                .findFirst()
                .orElse(null);
    }

    public RecipeBookmarkItem<?> findOutputWithSameRecipe(RecipeBookmarkItem<?> output) {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        String uniqueId = ingredientRegistry.getUniqueId(output.ingredient);
        return graphStorage.nodes().stream()
                .filter(node -> node.recipe != null && !uniqueId.equals(ingredientRegistry.getUniqueId(output.ingredient)) && node.recipe.equals(output.recipe))
                .findFirst()
                .orElse(null);
    }

    public void calculateCrafting() {
        for (RecipeBookmarkItem<?> node : graphStorage.nodes()) {
            node.amount = node.selfOutputAmount;
        }
        TopologicalSort.topologicalSort(graphStorage, (r, r1) -> {
            if (r.equals(r1.secondaryTo)) {
                return 1;
            } else if (r1.equals(r.secondaryTo)) {
                return -1;
            }
            return 0; // Primary ordering still applies.
        }).forEach(this::calculateCrafting);
    }

    public void calculateCrafting(RecipeBookmarkItem<?> needed) {
        if (graphStorage.predecessors(needed).isEmpty())
            return;
        for (RecipeBookmarkItem<?> requester : graphStorage.predecessors(needed)) {
            if (requester.outputAmount == 0) {
                Log.get().warn("Requester {} is apparently not made by its own recipe? Curious.", requester);
                continue;
            }
            // Divide the amount of the item used in the recipe by how many of the requested item it produces (rounding up).
            needed.amount += (requester.amount * graphStorage.edgeValue(requester, needed) + requester.outputAmount - 1) / requester.outputAmount;
        }
        if (needed.secondaryTo != null) {
            needed.secondaryTo.amount = Math.max(needed.secondaryTo.amount, needed.amount);
        }
    }

    public List<RecipeBookmarkItem<?>> getDisplayOutputs() {
        // Sort the graph in topological order, and then resort it based on the recipe wrapper.
        return TopologicalSort.topologicalSort(graphStorage, (r, r1) -> {
            if (r.equals(r1.secondaryTo)) {
                return 1;
            } else if (r1.equals(r.secondaryTo)) {
                return -1;
            }
            return 0; // Primary ordering still applies.
        });
    }

    public void removeNode(RecipeBookmarkItem<?> node) {
        graphStorage.removeNode(node);
        outputs.remove(node);
        List<RecipeBookmarkItem<?>> affectedSecondaries = secondaryOutputs.remove(node);
        if (affectedSecondaries != null && !affectedSecondaries.isEmpty()) {
            if (affectedSecondaries.size() == 1) {
                affectedSecondaries.get(0).secondaryTo = null;
            } else {
                for (int i = 1; i < affectedSecondaries.size(); i++) {
                    affectedSecondaries.get(i).secondaryTo = affectedSecondaries.get(0);
                }
                affectedSecondaries.remove(0);
                secondaryOutputs.put(affectedSecondaries.get(0), affectedSecondaries);
            }
        }
    }

    public Map<String, Long> getNodeSet() {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        return graphStorage.nodes().stream().filter(node -> node.secondaryTo == null).collect(
                Collectors.toMap(ingredientRegistry::getUniqueId, node -> node.amount));
    }

    public Map<String, Long> getOutputSet() {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        return outputs.stream().collect(
                Collectors.toMap(ingredientRegistry::getUniqueId, node -> node.selfOutputAmount));
    }

    public void calculateMissingIngredients(Stack<RecipeBookmarkItem<?>> recipeList) {
        for (RecipeBookmarkItem<?> node : graphStorage.nodes()) {
            node.amount = node.selfOutputAmount;
        }

        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        InventoryPlayer inv = Minecraft.getMinecraft().player.inventory;
        Map<String, Long> invCounts = new HashMap<>();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            String uniqueId = ingredientRegistry.getUniqueId(inv.getStackInSlot(i));
            invCounts.put(uniqueId, invCounts.getOrDefault(uniqueId, 0L) + inv.getStackInSlot(i).getCount());
        }

        TopologicalSort.topologicalSort(graphStorage, (r, r1) -> {
            if (r.equals(r1.secondaryTo)) {
                return 1;
            } else if (r1.equals(r.secondaryTo)) {
                return -1;
            }
            return 0; // Primary ordering still applies.
        }).forEach(ingredient -> calculateMissingIngredients(ingredient, invCounts, recipeList));
    }

    public void calculateMissingIngredients(RecipeBookmarkItem<?> needed, Map<String, Long> invCounts, Stack<RecipeBookmarkItem<?>> recipeList) {
        calculateCrafting(needed);
        if (needed.amount <= 0) {
            return;
        }
        if (needed.selfOutputAmount == 0) {
            String uniqueId = Internal.getIngredientRegistry().getUniqueId(needed.ingredient);
            if (invCounts.containsKey(uniqueId)) {
                needed.amount = Math.max(0L, needed.amount - invCounts.get(uniqueId));
                invCounts.put(uniqueId, Math.max(0L, invCounts.get(uniqueId) - needed.amount));
            }
        }
        if (recipeList != null && needed.amount > 0 && needed.category != null) { // If we're preparing for autocrafting and this can be crafted, add it.
            recipeList.add(needed);
        }
    }

    public Stack<RecipeBookmarkItem<?>> getOutputsInAutocraftingOrder(Map<String, Long> missingIngredients) {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        Stack<RecipeBookmarkItem<?>> outputs = TopologicalSort.topologicalSort(graphStorage, null).stream()
                .filter(node -> node.secondaryTo == null && missingIngredients.get(ingredientRegistry.getUniqueId(node.ingredient)) != null)
                .collect(Collectors.toCollection(Stack::new));
        return outputs;
    }

    public void test(int t) {
        if (t == 0) {
            return;
        }
        test(t - 1);
    }

}
