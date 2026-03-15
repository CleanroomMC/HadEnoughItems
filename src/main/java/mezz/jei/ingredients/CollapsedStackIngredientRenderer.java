package mezz.jei.ingredients;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * IIngredientRenderer for the CollapsedStack ingredient type.
 * Handles the type-system 16×16 render contract; the grid-specific overlay
 * rendering (area/padding/tooltip/highlight) remains in CollapsedStackRenderer.
 */
public class CollapsedStackIngredientRenderer implements IIngredientRenderer<CollapsedStack> {
	@Override
	public void render(Minecraft minecraft, int xPosition, int yPosition, @Nullable CollapsedStack ingredient) {
		if (ingredient == null || ingredient.isEmpty()) {
			return;
		}

		List<IIngredientListElement<?>> ingredients = ingredient.getIngredients();

		// Subtle background tint to visually distinguish collapsed groups
		GuiScreen.drawRect(xPosition, yPosition, xPosition + 16, yPosition + 16, 0x33FFFFFF);

		if (ingredients.size() == 1) {
			renderElementAt(minecraft, ingredients.get(0), xPosition, yPosition, 1.0f);
		} else {
			// Stacked-card icon: back item upper-right, front item lower-left, both at 0.75× scale
			RenderItem renderItem = minecraft.getRenderItem();
			renderElementAt(minecraft, ingredients.get(1), xPosition + 4, yPosition, 0.75f);
			float prevZLevel = renderItem.zLevel;
			renderItem.zLevel += 100;
			renderElementAt(minecraft, ingredients.get(0), xPosition, yPosition + 4, 0.75f);
			renderItem.zLevel = prevZLevel;
		}

		// Count badge
		int count = ingredient.size();
		if (count > 1) {
			FontRenderer fontRenderer = minecraft.fontRenderer;
			String countStr = String.valueOf(count);
			GlStateManager.disableLighting();
			GlStateManager.disableDepth();
			GlStateManager.disableBlend();
			final float badgeScale = 0.75f;
			int textWidth = fontRenderer.getStringWidth(countStr);
			int scaledRight = (int) ((xPosition + 16) / badgeScale);
			int scaledTop = (int) ((yPosition + 10) / badgeScale);
			GlStateManager.pushMatrix();
			GlStateManager.scale(badgeScale, badgeScale, 1.0f);
			fontRenderer.drawStringWithShadow(countStr, scaledRight - textWidth, scaledTop, 0xFFAA00);
			GlStateManager.popMatrix();
			GlStateManager.enableDepth();
		}

		GlStateManager.disableLighting();
		GlStateManager.color(1, 1, 1, 1);
	}

	@Override
	public List<String> getTooltip(Minecraft minecraft, CollapsedStack ingredient, ITooltipFlag tooltipFlag) {
		List<String> tooltip = new ArrayList<>();
		tooltip.add(net.minecraft.util.text.TextFormatting.GOLD + ingredient.getDisplayName()
			+ net.minecraft.util.text.TextFormatting.GRAY + " (" + ingredient.size() + " items)");
		return tooltip;
	}

	@Override
	public FontRenderer getFontRenderer(Minecraft minecraft, CollapsedStack ingredient) {
		return minecraft.fontRenderer;
	}

	@SuppressWarnings("unchecked")
	private static void renderElementAt(Minecraft minecraft, IIngredientListElement<?> element, int x, int y, float scale) {
		Object ingredient = element.getIngredient();
		try {
			GlStateManager.pushMatrix();
			GlStateManager.translate(x, y, 0);
			GlStateManager.scale(scale, scale, scale);
			if (ingredient instanceof ItemStack) {
				minecraft.getRenderItem().renderItemAndEffectIntoGUI((ItemStack) ingredient, 0, 0);
			} else {
				IIngredientRenderer renderer = element.getIngredientRenderer();
				renderer.render(minecraft, 0, 0, ingredient);
			}
			GlStateManager.popMatrix();
		} catch (RuntimeException | LinkageError ignored) {
			GlStateManager.popMatrix();
		}
	}
}
