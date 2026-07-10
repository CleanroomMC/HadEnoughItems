package mezz.jei.autocrafting;

import com.google.common.base.Preconditions;
import com.google.common.graph.ElementOrder;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import mezz.jei.Internal;
import mezz.jei.autocrafting.toposort.TopologicalSort;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.DummyBookmarkItem;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.*;
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

    private final RecipeBookmarkGroup group;
    private final Map<RecipeBookmarkItem<?>, Set<RecipeBookmarkItem<?>>> reusableEdges = new Object2ObjectOpenHashMap<>();

    public RecipeChain(RecipeBookmarkGroup group) {
        this.group = group;
    }

    public boolean addOutput(RecipeBookmarkItem<?> recipeOutput) {
        // We need to check if it overlaps an existing node (usually an input).
        for (RecipeBookmarkItem<?> input : graphStorage.nodes()) {
            if (IngredientUtil.aliasesContains(input.aliases, recipeOutput.ingredient)) {
                input.setIngredient(recipeOutput.ingredient);
                input.populateWith(recipeOutput.recipe, recipeOutput.category);
                expandNodeFirst(input);
                removeDanglingNodes();
                return true;
            }
        }
        recipeOutput.selfOutputAmount = recipeOutput.outputAmount;
        expandNodeFirst(recipeOutput); // This also can look for matching inputs!
        return false;
    }

    private void expandNodeFirst(RecipeBookmarkItem<?> requester) {
        expandNodeFirst(requester, true);
    }

    private void expandNodeFirst(RecipeBookmarkItem<?> requester, boolean recurse) {
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
                this.group.addItemInternal(needed); // Don't add it as an output (as would occur with the normal addItem method).
                if (recurse) {
                    needed.populateWithFavorite();
                    expandNodeFirst(needed);
                }

                // Maybe this recipe is being used to make something else, so we should connect it to that.
                RecipeBookmarkItem<?> possiblePrimaryOutput = findOutputWithSameRecipe(needed);
                if (possiblePrimaryOutput != null) {
                    needed.secondaryTo = possiblePrimaryOutput;
                    secondaryOutputs.computeIfAbsent(possiblePrimaryOutput, k -> new ObjectArrayList<>())
                            .add(needed);
                }
            }
            addDependency(requester, needed, input.amount, input.reusableInCrafting);
            needed.setGroup(group); // May not be the case if we're dragging in a new recipe.
        }
    }

    private void addDependency(RecipeBookmarkItem<?> requester, RecipeBookmarkItem<?> needed, long amount, boolean reusable) {
        if (wouldCreateCycle(requester, needed)) {
            Log.get().warn("Skipped cyclic recipe bookmark dependency from {} to {}.", requester, needed);
            return;
        }
        try {
            graphStorage.putEdgeValue(requester, needed, amount);
            setReusableEdge(requester, needed, reusable);
        } catch (IllegalArgumentException e) {
            Log.get().error("Failed to add edge from {} to {}.", requester, needed, e);
        }
    }

    private boolean wouldCreateCycle(RecipeBookmarkItem<?> requester, RecipeBookmarkItem<?> needed) {
        if (requester.equals(needed)) {
            return true;
        }
        if (!graphStorage.nodes().contains(requester) || !graphStorage.nodes().contains(needed)) {
            return false;
        }
        Set<RecipeBookmarkItem<?>> visited = new ObjectOpenHashSet<>();
        Deque<RecipeBookmarkItem<?>> pending = new ArrayDeque<>();
        pending.add(needed);
        while (!pending.isEmpty()) {
            RecipeBookmarkItem<?> node = pending.removeFirst();
            if (!visited.add(node)) {
                continue;
            }
            if (node.equals(requester)) {
                return true;
            }
            pending.addAll(graphStorage.successors(node));
        }
        return false;
    }

    private void setReusableEdge(RecipeBookmarkItem<?> requester, RecipeBookmarkItem<?> needed, boolean reusable) {
        if (reusable) {
            reusableEdges.computeIfAbsent(requester, k -> new ObjectOpenHashSet<>()).add(needed);
            return;
        }
        Set<RecipeBookmarkItem<?>> inputs = reusableEdges.get(requester);
        if (inputs != null) {
            inputs.remove(needed);
        }
    }

    private boolean isReusableEdge(RecipeBookmarkItem<?> requester, RecipeBookmarkItem<?> needed) {
        Set<RecipeBookmarkItem<?>> inputs = reusableEdges.get(requester);
        return inputs != null && inputs.contains(needed);
    }

    private void removeReusableEdges(RecipeBookmarkItem<?> node) {
        reusableEdges.remove(node);
        reusableEdges.values().forEach(inputs -> inputs.remove(node));
    }

    private Map<String, RecipeBookmarkItem<?>> getAliasMap() {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        Map<String, RecipeBookmarkItem<?>> aliasToNode = new Object2ObjectOpenHashMap<>();
        group.getItemsInternal().forEach(node -> ((RecipeBookmarkItem) node).aliases.forEach(alias -> aliasToNode.put(ingredientRegistry.getUniqueId(alias),
                ((RecipeBookmarkItem) node))));
        return aliasToNode;
    }

    public RecipeBookmarkItem<?> findOutputUsingAnAlias(RecipeBookmarkItem<?> output) {
        Map<String, RecipeBookmarkItem<?>> aliasToNode = getAliasMap();
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        List<String> aliasIds = output.aliases.stream().map(ingredientRegistry::getUniqueId).collect(Collectors.toList());
        for (String uniqueId : aliasIds) {
            RecipeBookmarkItem<?> node = aliasToNode.get(uniqueId);
            if (node != null) {
                if (!node.foundAliases) {
                    node.foundAliases = true;
                    node.aliases = (List) new ObjectArrayList<>(output.aliases);
                    node.setIngredient(output.ingredient);
                }
                // Take the intersection of the two lists.
                node.aliases.removeIf(a -> !aliasIds.contains(ingredientRegistry.getUniqueId(a)));
                return node;
            }
        }
        return null;
    }

    public RecipeBookmarkItem<?> findOutputWithSameRecipe(RecipeBookmarkItem<?> output) {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        String uniqueId = ingredientRegistry.getUniqueId(output.ingredient);
        return (RecipeBookmarkItem<?>) group.getItemsInternal().stream()
                .filter(node -> ((RecipeBookmarkItem) node).recipe != null && !uniqueId.equals(ingredientRegistry.getUniqueId(output.ingredient)) && ((RecipeBookmarkItem) node).recipe.equals(output.recipe))
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
            long inputAmount = edgeValue(requester, needed);
            if (isReusableEdge(requester, needed)) {
                needed.amount += inputAmount;
                continue;
            }
            // Divide the amount of the item used in the recipe by how many of the requested item it produces (rounding up).
            needed.amount += ((requester.amount + requester.outputAmount - 1) / requester.outputAmount) * inputAmount;
        }
        if (needed.secondaryTo != null) {
            needed.secondaryTo.amount = Math.max(needed.secondaryTo.amount, needed.amount);
        }
    }

    private Long edgeValue(RecipeBookmarkItem<?> nodeU, RecipeBookmarkItem<?> nodeV) {
        Long value = graphStorage.edgeValueOrDefault(nodeU, nodeV, null);
        if (value == null) {
            Preconditions.checkArgument(graphStorage.nodes().contains(nodeU), "Node %s is not an element of this graph.", nodeU);
            Preconditions.checkArgument(graphStorage.nodes().contains(nodeV), "Node %s is not an element of this graph.", nodeV);
            throw new IllegalArgumentException(String.format("Edge connecting %s to %s is not present in this graph.", nodeU, nodeV));
        }
        return value;
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
        if (!graphStorage.nodes().contains(node)) {
            Log.get().warn("Tried to remove node that's not in the graph: {}", node);
            return;
        }
        graphStorage.removeNode(node);
        removeReusableEdges(node);
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
        // We do need to check for dead nodes now.
        removeDanglingNodes();
        for (RecipeBookmarkItem<?> predecessor : new ObjectOpenHashSet<>(graphStorage.nodes())) {
            expandNodeFirst(predecessor, false);
        }
        // Update once more.
        calculateCrafting();
    }

    public void removeDanglingNodes() {
        calculateCrafting();
        List<RecipeBookmarkItem> nodesToRemove = new ArrayList<>();
        for (RecipeBookmarkItem otherNode : graphStorage.nodes()) {
            if (otherNode.amount == 0) {
                nodesToRemove.add(otherNode);
            }
        }
        for (RecipeBookmarkItem otherNode : nodesToRemove) {
            graphStorage.removeNode(otherNode);
            removeReusableEdges(otherNode);
        }
    }

    public void calculateMissingIngredients(Stack<RecipeBookmarkItem<?>> recipeList, List<BookmarkItem<?>> missing) {
        for (RecipeBookmarkItem<?> node : graphStorage.nodes()) {
            node.amount = node.selfOutputAmount;
        }

        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        InventoryPlayer inv = Minecraft.getMinecraft().player.inventory;
        Map<String, Long> invCounts = new Object2LongOpenHashMap<>(inv.getSizeInventory() * 2);
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            addStackToCounts(inv.getStackInSlot(i), ingredientRegistry, invCounts);
        }

        Container openContainer = Minecraft.getMinecraft().player.openContainer;
        if (openContainer != null) {
            for (Slot slot : openContainer.inventorySlots) {
                // The crafting grid is cleared before each autocrafting step, and its contents are returned to the player.
                // Count those inputs here so that an item already in the grid is not crafted unnecessarily first.
                if (slot.inventory instanceof InventoryCrafting) {
                    addStackToCounts(slot.getStack(), ingredientRegistry, invCounts);
                }
            }
        }

        final Map<String, BookmarkItem<?>> lookup = missing == null ? null : new HashMap<>();
        TopologicalSort.topologicalSort(graphStorage, (r, r1) -> {
            if (r.equals(r1.secondaryTo)) {
                return 1;
            } else if (r1.equals(r.secondaryTo)) {
                return -1;
            }
            return 0; // Primary ordering still applies.
        }).forEach(ingredient -> calculateMissingIngredients(ingredient, invCounts, recipeList, lookup));
        if (missing != null) {
            for (Map.Entry<String, BookmarkItem<?>> entry : lookup.entrySet()) {
                missing.add(entry.getValue());
            }
        }
        calculateCrafting(); // Reset the displayed amounts.
    }

    private static void addStackToCounts(ItemStack stack, IngredientRegistry ingredientRegistry, Map<String, Long> invCounts) {
        if (stack.isEmpty()) {
            return;
        }
        String uniqueId = ingredientRegistry.getUniqueId(stack);
        invCounts.compute(uniqueId, (k, v) -> v == null ? stack.getCount() : v + stack.getCount());
    }

    public void calculateMissingIngredients(RecipeBookmarkItem<?> needed, Map<String, Long> invCounts,
                                            Stack<RecipeBookmarkItem<?>> recipeList, Map<String, BookmarkItem<?>> lookup) {
        calculateCrafting(needed);
        if (needed.amount <= 0) {
            return;
        }
        String uniqueId = null;
        if (needed.selfOutputAmount == 0) {
            uniqueId = Internal.getIngredientRegistry().getUniqueId(needed.ingredient);
            invCounts.computeIfPresent(uniqueId, (k, v) -> {
                needed.amount = Math.max(0L, needed.amount - v);
                return Math.max(0L, v - needed.amount);
            });
        }
        if (recipeList != null && needed.amount > 0 && needed.category != null) {
            // If we're preparing for autocrafting and this can be crafted, add it.
            recipeList.add(needed.copy());
        } else if (lookup != null && needed.amount > 0 && graphStorage.successors(needed).isEmpty()) {
            if (uniqueId == null) {
                uniqueId = Internal.getIngredientRegistry().getUniqueId(needed.ingredient);
            }
            // If we're preparing just to show the missing items, we can add it.
            lookup.compute(uniqueId, (k, v) -> {
                if (v == null) {
                    final long staticAmount = (int) needed.amount;
                    return new DummyBookmarkItem<>(needed, null, () -> staticAmount);
                } else {
                    v.amount += needed.amount;
                }
                return v;
            });
        }
    }

    public void rebuildGraph() {
        for (BookmarkItem<?> node : group.getItemsInternal()) {
            if (node instanceof RecipeBookmarkItem) {
                RecipeBookmarkItem<?> requester = (RecipeBookmarkItem<?>) node;
                requester.populateSelf(this); // This looks for new inputs and sets input aliases.
                for (RecipeBookmarkItem<?> input : requester.inputs) {
                    RecipeBookmarkItem<?> other = findOutputUsingAnAlias(input);
                    if (other == null && input.inputs != null) {
                        Log.get().warn("Failed to get connections for {}", input);
                    }
                    RecipeBookmarkItem<?> needed = other != null ? other : new RecipeBookmarkItem<>(input.aliases);
                    addDependency(requester, needed, input.amount, input.reusableInCrafting);
                }
            }
        }
    }

}
