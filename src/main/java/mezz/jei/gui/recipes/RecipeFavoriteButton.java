package mezz.jei.gui.recipes;

import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.transfer.RecipeTransferErrorInternal;
import mezz.jei.transfer.RecipeTransferUtil;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import javax.annotation.Nullable;
import java.util.List;

public class RecipeFavoriteButton extends GuiIconToggleButton {
    private final RecipeLayout recipeLayout;

    public RecipeFavoriteButton(IDrawable offIcon, IDrawable onIcon, RecipeLayout recipeLayout) {
        super(offIcon, onIcon);
        this.recipeLayout = recipeLayout;
    }

    public void init(@Nullable Container container, EntityPlayer player) {
    }

    @Override
    protected void getTooltips(List<String> tooltip) {
        if (isIconToggledOn()) {
            tooltip.add(Translator.translateToLocal("jei.tooltip.unfavorite"));
        } else {
            tooltip.add(Translator.translateToLocal("jei.tooltip.favorite"));
        }
    }

    @Override
    protected boolean isIconToggledOn() {
        return false;
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY) {
        return false;
    }


}
