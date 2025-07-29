package mezz.jei.autocrafting;

import com.google.common.graph.ElementOrder;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.autocrafting.toposort.TopologicalSort;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.DummyBookmarkItem;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
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
        outputs.add(recipeOutput);
        recipeOutput.selfOutputAmount = recipeOutput.outputAmount;
        expandNodeFirst(recipeOutput); // This also can look for matching inputs!
        return false;
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
                this.group.addItemInternal(needed); // Don't add it as an output (as would occur with the normal addItem method).
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
                needed.setGroup(group); // May not be the case if we're dragging in a new recipe.
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
            if (aliasToNode.containsKey(uniqueId)) {
                if (!aliasToNode.get(uniqueId).foundAliases) {
                    aliasToNode.get(uniqueId).foundAliases = true;
                    aliasToNode.get(uniqueId).aliases = (List) new ObjectArrayList<>(output.aliases);
                    aliasToNode.get(uniqueId).setIngredient(output.ingredient);
                }
                // Take the intersection of the two lists.
                aliasToNode.get(uniqueId).aliases.removeIf(a -> !aliasIds.contains(ingredientRegistry.getUniqueId(a)));
                return aliasToNode.get(uniqueId);
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
            // Divide the amount of the item used in the recipe by how many of the requested item it produces (rounding up).
            needed.amount += ((requester.amount + requester.outputAmount - 1) / requester.outputAmount) * graphStorage.edgeValue(requester, needed);
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
        // We do need to check for dead nodes now.
        removeDanglingNodes();
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
        }
    }

    public void calculateMissingIngredients(Stack<RecipeBookmarkItem<?>> recipeList, List<BookmarkItem<?>> missing) {
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
                missing.add(new DummyBookmarkItem(entry.getValue(), null, () -> entry.getValue().amount));
            }
        }
    }

    public void calculateMissingIngredients(RecipeBookmarkItem<?> needed, Map<String, Long> invCounts,
                                            Stack<RecipeBookmarkItem<?>> recipeList, Map<String, BookmarkItem<?>> lookup) {
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
        if (recipeList != null && needed.amount > 0 && needed.category != null) {
            // If we're preparing for autocrafting and this can be crafted, add it.
            recipeList.add(needed);
        } else if (lookup != null && needed.amount > 0 && graphStorage.successors(needed).isEmpty()) {
            IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
            String uniqueId = ingredientRegistry.getUniqueId(needed.ingredient);
            // If we're preparing just to show the missing items, we can add it.
            lookup.compute(uniqueId, (k, v) -> {
                if (v == null) {
                    return needed;
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
                if (((RecipeBookmarkItem<?>) node).selfOutputAmount > 0) {
                    outputs.add(((RecipeBookmarkItem<?>) node));
                }
                for (RecipeBookmarkItem<?> input : requester.inputs) {
                    RecipeBookmarkItem<?> other = findOutputUsingAnAlias(input);
                    if (other == null && input.inputs != null) {
                        Log.get().warn("Failed to get connections for {}", input);
                    }
                    try {
                        graphStorage.putEdgeValue(requester, other != null ? other : new RecipeBookmarkItem<>(input.aliases), input.amount);
                    } catch (IllegalArgumentException e) {
                        Log.get().error("Failed to add edge from {} to {}.", requester, input, e);
                    }
                }
            }
        }
    }

}
