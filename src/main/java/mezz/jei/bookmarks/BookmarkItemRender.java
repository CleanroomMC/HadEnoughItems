package mezz.jei.bookmarks;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.CountUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.util.ITooltipFlag;

import javax.annotation.Nullable;
import java.util.List;

@SuppressWarnings("rawtypes")
public class BookmarkItemRender implements IIngredientRenderer<BookmarkItem> {
	@Override
	public void render(Minecraft minecraft, int xPosition, int yPosition, @Nullable BookmarkItem ingredient) {
		if (ingredient != null) {
			IngredientRegistry registry = Internal.getIngredientRegistry();
			IIngredientType<Object> ingredientType = registry.getIngredientType(ingredient.getIngredient());
			registry.getIngredientRenderer(ingredientType).render(minecraft, xPosition, yPosition, ingredient.getIngredient());

			FontRenderer fontRenderer = getFontRenderer(minecraft, ingredient);
			long displayAmount = ingredient.getDisplayAmount();
			if (displayAmount > 1L) {
				if (ingredient instanceof RecipeBookmarkItem && ((RecipeBookmarkItem<?>) ingredient).isExplicitlyRequested()) {
					CountUtil.renderStringAsCount(fontRenderer, 'x' + CountUtil.minifyCountString(displayAmount), xPosition, yPosition, 0xBBBBBBBB, true, true);
				} else {
					CountUtil.renderCountString(fontRenderer, displayAmount, xPosition, yPosition, true);
				}
			}
		}
		GlStateManager.disableLighting();
		GlStateManager.color(1, 1, 1, 1);
	}

	@Override
	public List<String> getTooltip(Minecraft minecraft, BookmarkItem ingredient, ITooltipFlag tooltipFlag) {
		return getIngredientRenderer(ingredient.getIngredient()).getTooltip(minecraft, ingredient.getIngredient(), tooltipFlag);
	}

	@Override
	public FontRenderer getFontRenderer(Minecraft minecraft, BookmarkItem ingredient) {
		return getIngredientRenderer(ingredient.getIngredient()).getFontRenderer(minecraft, ingredient.getIngredient());
	}

	private static <E> IIngredientRenderer<E> getIngredientRenderer(E ingredient) {
		return Internal.getIngredientRegistry().getIngredientRenderer(ingredient);
	}
}
