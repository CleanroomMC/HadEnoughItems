package mezz.jei.autocrafting;

import mezz.jei.Internal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.api.recipe.transfer.IRecipeCraftingHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.recipes.RecipeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;

import javax.annotation.Nullable;
import java.util.Stack;

public class AutocraftingHandler implements IAutocraftingHandler {
    @Nullable
    private RecipeChain currentChain;
    @Nullable
    private RecipeBookmarkItem<?> currentRequester;
    private Stack<RecipeBookmarkItem<?>> recipesToAutocraft;
    private boolean waitingForCraftResult = false;

    public void start(RecipeChain chain) {
        this.currentChain = chain;

        recipesToAutocraft = new Stack<>();
        chain.calculateMissingIngredients(recipesToAutocraft, null);
        if (recipesToAutocraft.isEmpty()) {
            stop();
            return;
        }
        chain.calculateCrafting(); // Reset the displayed amounts.
        autocraftLoop();
    }

    private boolean autocraft() {
        if (this.currentRequester == null) {
            stop();
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            stop();
            return false;
        }
        Container openContainer = player.openContainer;
        RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
        if (openContainer == null) {
            stop();
            return false;
        }
        IRecipeCategory<?> recipeCategory = currentRequester.category;
        IRecipeLayout recipeLayout = currentRequester.createLayout();
        IRecipeTransferHandler<?> recipeTransferHandler = recipeRegistry.getRecipeTransferHandler(openContainer, recipeCategory);
        if (!(recipeTransferHandler instanceof IRecipeCraftingHandler)) {
            return true;
        }
        IRecipeCraftingHandler craftingHandler = (IRecipeCraftingHandler) recipeTransferHandler;
        int craftAmount = (int) this.currentRequester.getMultiplier();
        if (craftingHandler.craft(openContainer, recipeLayout, player, craftAmount, false) == null) {
            waitingForCraftResult = true;
            craftingHandler.craft(openContainer, recipeLayout, player, craftAmount, true);
            return false;
        }
        return true;
    }

    private void autocraftLoop() {
        if (recipesToAutocraft.isEmpty()) {
            stop();
            return;
        }
        do {
            this.currentRequester = recipesToAutocraft.pop();
        } while (autocraft() && !recipesToAutocraft.isEmpty());
        if (!waitingForCraftResult && recipesToAutocraft != null && recipesToAutocraft.isEmpty()) {
            stop();
        }
    }

    @Override
    public void stepFinished(boolean success, int amount) {
        waitingForCraftResult = false;
        if (this.recipesToAutocraft == null) {
            return;
        }
        if (amount < this.currentRequester.amount) {
            this.currentRequester.amount -= amount;
            this.recipesToAutocraft.push(this.currentRequester);
        }
        autocraftLoop();
    }

    @Override
    public void stop() {
        this.waitingForCraftResult = false;
        this.currentChain = null;
        this.currentRequester = null;
        this.recipesToAutocraft = null;
    }

    @Override
    public boolean isActive() {
        return this.currentChain != null;
    }

}
