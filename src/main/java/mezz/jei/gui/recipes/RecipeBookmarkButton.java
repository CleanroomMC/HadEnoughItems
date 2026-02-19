package mezz.jei.gui.recipes;

import mezz.jei.Internal;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.autocrafting.RecipeBookmarkGroup;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.Config;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;

public class RecipeBookmarkButton extends GuiIconButtonSmall {
    private final IRecipeCategory<?> category;
    private final IRecipeWrapper recipe;
    private RecipeLayout recipeLayout;

    public RecipeBookmarkButton(int id, int width, int height, IDrawable icon, IRecipeCategory<?> category, IRecipeWrapper recipe, RecipeLayout recipeLayout) {
        super(id, 0, 0, width, height, icon);
        this.category = category;
        this.recipe = recipe;
        this.recipeLayout = recipeLayout;
    }

    public void init(RecipeLayout recipeLayout) {
        this.recipeLayout = recipeLayout;
        // Propagates the state of the favorite button if there are no outputs to the recipe.
        this.enabled = this.visible = recipeLayout.getRecipeFavoriteButton().enabled;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        visible = enabled && Config.areRecipeBookmarksEnabled();
        super.drawButton(mc, mouseX, mouseY, partialTicks);
    }

    public void drawToolTip(Minecraft mc, int mouseX, int mouseY) {
        if (hovered && visible) {
            String tooltipTransfer = Translator.translateToLocal("hei.tooltip.recipe_bookmark");
            TooltipRenderer.drawHoveringText(mc, tooltipTransfer, mouseX, mouseY);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!super.mousePressed(mc, mouseX, mouseY)) {
            return false;
        }
        if (!Config.isBookmarkOverlayEnabled()) {
            Config.toggleBookmarkEnabled();
        }
       
        return recipeLayout.addToBookmarks();
    }
}
