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
    // True while a dispatched craft is awaiting its stepFinished callback; prevents autocraftLoop
    // from tearing down the queue before the last recipe's shortfall can be re-queued.
    private boolean waitingForStep = false;

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

    // Returns false if the autocrafting cannot continue (either if it failed or if we're waiting on a recipe to complete).
    // Returns true if the autocrafting can continue (if a recipe isn't craftable, we just continue to something else).
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
        IRecipeCategory recipeCategory = currentRequester.category;
        IRecipeLayout recipeLayout = currentRequester.createLayout();
        IRecipeTransferHandler recipeTransferHandler = recipeRegistry.getRecipeTransferHandler(openContainer, recipeCategory);
        if (recipeTransferHandler == null || !(recipeTransferHandler instanceof IRecipeCraftingHandler)) {
            return true;
        }
        IRecipeCraftingHandler craftingHandler = (IRecipeCraftingHandler) recipeTransferHandler;
        if (craftingHandler.craft(openContainer, recipeLayout, player, (int) this.currentRequester.getMultiplier(), false) == null) {
            waitingForStep = true;
            craftingHandler.craft(openContainer, recipeLayout, player, (int) this.currentRequester.getMultiplier(), true);
            return false; // This "false" return is different from the others; it just means we're waiting for the recipe to complete
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
        if (!waitingForStep && recipesToAutocraft != null && recipesToAutocraft.isEmpty()) {
            stop();
        }
    }

    @Override
    public void stepFinished(boolean success, int amount) {
        waitingForStep = false;
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
        this.waitingForStep = false;
        this.currentChain = null;
        this.currentRequester = null;
        this.recipesToAutocraft = null;
    }

    @Override
    public boolean isActive() {
        return this.currentChain != null;
    }

}
