package mezz.jei.bookmarks;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.CountUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.util.ITooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("rawtypes")
public class BookmarkItemRender implements IIngredientRenderer<BookmarkItem> {
    @Override
    public void render(Minecraft minecraft, int xPosition, int yPosition, @Nullable BookmarkItem ingredient) {
        if (ingredient != null) {
            IngredientRegistry registry = Internal.getIngredientRegistry();
            IIngredientType<Object> ingredientType = registry.getIngredientType(ingredient.ingredient);
            registry.getIngredientRenderer(ingredientType).render(minecraft, xPosition, yPosition, ingredient.ingredient);

            FontRenderer fontRenderer = getFontRenderer(minecraft, ingredient);
            if (ingredient instanceof RecipeBookmarkItem) {
                RecipeBookmarkItem<?> recipeBookmarkItem = (RecipeBookmarkItem<?>) ingredient;
                if (recipeBookmarkItem.selfOutputAmount > 1L) {
                    CountUtil.renderStringAsCount(fontRenderer, 'x' + CountUtil.minifyCountString(recipeBookmarkItem.selfOutputAmount), xPosition, yPosition, 0xBBBBBBBB, true, true);
                }
            } else if (ingredient.getDisplayAmount() > 1L) {
                CountUtil.renderCountString(fontRenderer, ingredient.getDisplayAmount(), xPosition, yPosition, true);
            }
        }
    }

    @Override
    public List<String> getTooltip(Minecraft minecraft, BookmarkItem ingredient, ITooltipFlag tooltipFlag) {
        return getIngredientRenderer(ingredient.ingredient).getTooltip(minecraft, ingredient.ingredient, tooltipFlag);
    }

    @Override
    public FontRenderer getFontRenderer(Minecraft minecraft, BookmarkItem ingredient) {
        return getIngredientRenderer(ingredient.ingredient).getFontRenderer(minecraft, ingredient.ingredient);
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<String> getTooltip(Minecraft minecraft, BookmarkItem ingredient, boolean advanced) {
        return getIngredientRenderer(ingredient.ingredient).getTooltip(minecraft, ingredient.ingredient, advanced);
    }

    @SuppressWarnings("deprecation")
    @Override
    public List<String> getTooltip(Minecraft minecraft, BookmarkItem ingredient) {
        return getIngredientRenderer(ingredient.ingredient).getTooltip(minecraft, ingredient.ingredient);
    }

    private static <E> IIngredientRenderer<E> getIngredientRenderer(E ingredient) {
        return Internal.getIngredientRegistry().getIngredientRenderer(ingredient);
    }
}
