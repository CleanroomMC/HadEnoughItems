package mezz.jei.autocrafting;

import mezz.jei.Internal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
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

    public void startAutocrafting(RecipeChain chain) {
        this.currentChain = chain;

        recipesToAutocraft = new Stack<>();
        chain.calculateMissingIngredients(recipesToAutocraft);
        if (recipesToAutocraft.isEmpty()) {
            reset();
            return;
        }
        chain.calculateCrafting(); // Reset the displayed amounts.
        autocraftLoop();
    }

    // Returns false if the autocrafting cannot continue (either if it failed or if we're waiting on a recipe to complete).
    // Returns true if the autocrafting can continue (if a recipe isn't craftable, we just continue to something else).
    private boolean autocraft() {
        if (this.currentRequester == null) {
            reset();
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.player;
        if (player == null) {
            reset();
            return false;
        }
        Container openContainer = player.openContainer;
        RecipeRegistry recipeRegistry = Internal.getRuntime().getRecipeRegistry();
        if (openContainer == null) {
            reset();
            return false;
        }
        IRecipeCategory recipeCategory = currentRequester.category;
        IRecipeLayout recipeLayout = currentRequester.createLayout();
        IRecipeTransferHandler recipeTransferHandler = recipeRegistry.getRecipeTransferHandler(openContainer, recipeCategory);
        if (recipeTransferHandler == null) {
            return true;
        }
        if (recipeTransferHandler.craft(openContainer, recipeLayout, player, (int) this.currentRequester.getMultiplier(), false) == null) {
            recipeTransferHandler.craft(openContainer, recipeLayout, player, (int) this.currentRequester.getMultiplier(), true);
            return false; // This "false" return is different from the others; it just means we're waiting for the recipe to complete
        }
        return true;
    }

    public void autocraftLoop() {
        if (recipesToAutocraft.isEmpty()) {
            reset();
            return;
        }
        do {
            this.currentRequester = recipesToAutocraft.pop();
        } while (autocraft() && !recipesToAutocraft.isEmpty());
    }

    @Override
    public void informOfAutocrafting(boolean success, int amount) {
        if (this.recipesToAutocraft == null) {
            return;
        }
        if (amount != this.currentRequester.amount) {
            this.currentRequester.amount -= amount;
            this.recipesToAutocraft.push(this.currentRequester);
        }
        autocraftLoop();
    }

    private void reset() {
        this.currentChain = null;
        this.currentRequester = null;
        this.recipesToAutocraft = null;
    }
}
