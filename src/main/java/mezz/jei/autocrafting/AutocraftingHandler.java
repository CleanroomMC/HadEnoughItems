package mezz.jei.autocrafting;

import mezz.jei.Internal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.recipes.RecipeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Stack;

public class AutocraftingHandler implements IAutocraftingHandler {
    @Nullable
    private RecipeChain currentChain;
    @Nullable
    private RecipeBookmarkItem<?> currentRequester;
    private Stack<RecipeBookmarkItem<?>> recipesToAutocraft;

    public void startAutocrafting(RecipeChain chain) {
        this.currentChain = chain;
        Map<String, Long> nodes = chain.getNodeSet();
        IngredientRegistry registry = Internal.getIngredientRegistry();
        InventoryPlayer inv = Minecraft.getMinecraft().player.inventory;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            String uniqueId = registry.getUniqueId(inv.getStackInSlot(i));
            int finalI = i;
            nodes.computeIfPresent(uniqueId, (k, v) -> {
                long count = v - inv.getStackInSlot(finalI).getCount();
                if (count <= 0) {
                    return null; // remove
                }
                return count;
            });
        }
        recipesToAutocraft = chain.getOutputsInAutocraftingOrder(nodes);
        this.currentRequester = recipesToAutocraft.pop();
        autocraft();
    }

    private void autocraft() {
        if (this.currentRequester == null) {
            reset();
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            return;
        }
        Container openContainer = player.openContainer;
        RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
        if (openContainer == null) {
            return;
        }
        /*if (openContainer instanceof ContainerPlayer) {
            return;
        }*/
        IRecipeCategory recipeCategory = currentRequester.category;
        IRecipeLayout recipeLayout = currentRequester.createLayout();
        IRecipeTransferHandler recipeTransferHandler = recipeRegistry.getRecipeTransferHandler(openContainer, recipeCategory);
        if (recipeTransferHandler == null || recipeTransferHandler.craft(openContainer, recipeLayout, player, (int) this.currentRequester.getMultiplier(), false) != null) {
            reset();
            return;
        }
        recipeTransferHandler.craft(openContainer, recipeLayout, player, (int) this.currentRequester.getMultiplier(), true);
    }

    @Override
    public void informOfAutocrafting(boolean success, int amount) {
        if (this.currentChain == null || this.currentRequester == null) {
            return;
        }
        if (success) {
            this.currentRequester = this.recipesToAutocraft.pop();
            autocraft();
        } else {
            reset();
        }
    }

    private void reset() {
        this.currentChain = null;
        this.currentRequester = null;
        this.recipesToAutocraft = null;
    }
}
