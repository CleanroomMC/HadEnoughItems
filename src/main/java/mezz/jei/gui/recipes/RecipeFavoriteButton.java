package mezz.jei.gui.recipes;

import mezz.jei.Internal;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.autocrafting.favorites.FavoriteRecipes;
import mezz.jei.gui.elements.GuiIconButton;
import mezz.jei.ingredients.Ingredients;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import javax.annotation.Nullable;
import java.util.List;

public class RecipeFavoriteButton extends GuiIconButton {
    private final IRecipeWrapper wrapper;

    public RecipeFavoriteButton(int index, int width, int height, IDrawable offIcon, IDrawable onIcon, IRecipeWrapper wrapper) {
        super(index, null, null); // We're going to replace these, but it doesn't let me pass in lambdas referring to the object yet.
        this.tooltipCallback = this::getTooltips;
        this.iconSupplier = () -> isIconToggledOn() ? onIcon : offIcon;
        this.mouseClickCallback = this::onMouseClicked;
        this.wrapper = wrapper;
        this.width = width;
        this.height = height;
    }

    public void init(@Nullable Container container, EntityPlayer player) {
    }

    protected void getTooltips(List<String> tooltip) {
        if (isIconToggledOn()) {
            tooltip.add(Translator.translateToLocal("jei.tooltip.unfavorite"));
        } else {
            tooltip.add(Translator.translateToLocal("jei.tooltip.favorite"));
        }
    }

    protected boolean isIconToggledOn() {
        return FavoriteRecipes.isFavorite(wrapper);
    }

    protected boolean onMouseClicked(Minecraft mc, int mouseX, int mouseY) {
        Ingredients ings = Internal.getRuntime().getRecipeRegistry().getIngredients(wrapper);
        if (isIconToggledOn()) {
            FavoriteRecipes.removeFavorite(wrapper);
        } else {
            for (IIngredientType<?> type : ings.getOutputIngredients().keySet()) {
                FavoriteRecipes.setFavorite(ings.getOutputIngredients().get(type).get(0), wrapper);
                return true;
            }
        }
        return false;
    }
}
