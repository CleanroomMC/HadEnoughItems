package mezz.jei.render;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.CollapsedStack;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a collapsed group as a single ingredient list slot.
 * Shows the first item with a count badge indicating total group size,
 * plus a semi-transparent background to distinguish it from normal items.
 */
public class CollapsedStackRenderer {
	private static final int COLLAPSED_BG_COLOR = 0x33FFFFFF;
	private static final int COLLAPSED_BORDER_COLOR = 0x55AAAAFF;

	private final CollapsedStack collapsedStack;
	private Rectangle area = new Rectangle(0, 0, 16, 16);
	private int padding;

	public CollapsedStackRenderer(CollapsedStack collapsedStack) {
		this.collapsedStack = collapsedStack;
	}

	public void setArea(Rectangle area) {
		this.area = area;
	}

	public void setPadding(int padding) {
		this.padding = padding;
	}

	public CollapsedStack getCollapsedStack() {
		return collapsedStack;
	}

	public Rectangle getArea() {
		return area;
	}

	/**
	 * Renders the collapsed group as a single slot.
	 * Draws a tinted background, the first item, and a count badge.
	 */
	public void render(Minecraft minecraft) {
		List<IIngredientListElement<?>> ingredients = collapsedStack.getIngredients();
		if (ingredients.isEmpty()) {
			return;
		}

		int x = area.x + padding;
		int y = area.y + padding;

		// Draw background tint to visually distinguish collapsed groups
		GuiScreen.drawRect(x, y, x + 16, y + 16, COLLAPSED_BG_COLOR);

		// Render the first item as the representative
		IIngredientListElement<?> firstElement = ingredients.get(0);
		Object ingredient = firstElement.getIngredient();
		if (ingredient instanceof ItemStack) {
			ItemStack itemStack = (ItemStack) ingredient;
			RenderHelper.enableGUIStandardItemLighting();
			RenderItem renderItem = minecraft.getRenderItem();
			renderItem.renderItemAndEffectIntoGUI(itemStack, x, y);
			RenderHelper.disableStandardItemLighting();
		} else {
			try {
				renderIngredient(minecraft, x, y, firstElement);
			} catch (RuntimeException | LinkageError e) {
				// Silently ignore render errors for collapsed preview
			}
		}

		// Draw count badge in bottom-right
		int count = collapsedStack.size();
		if (count > 1) {
			String countStr = String.valueOf(count);
			FontRenderer fontRenderer = minecraft.fontRenderer;
			GlStateManager.disableLighting();
			GlStateManager.disableDepth();
			GlStateManager.disableBlend();

			// Draw count text with shadow, right-aligned in the slot
			int textWidth = fontRenderer.getStringWidth(countStr);
			int textX = x + 17 - textWidth;
			int textY = y + 9;

			fontRenderer.drawStringWithShadow(countStr, textX, textY, 0xFFFFFF);

			GlStateManager.enableDepth();
		}

		// Draw a subtle border to indicate this is a collapsible group
		drawCollapsedBorder(x, y);
	}

	private void drawCollapsedBorder(int x, int y) {
		// Small triangle indicator in the top-left corner to show it's collapsible
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GuiScreen.drawRect(x, y, x + 4, y + 1, COLLAPSED_BORDER_COLOR);
		GuiScreen.drawRect(x, y, x + 1, y + 4, COLLAPSED_BORDER_COLOR);
		GlStateManager.enableDepth();
	}

	public void drawHighlight() {
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.colorMask(true, true, true, false);
		GuiUtils.drawGradientRect(0, area.x, area.y, area.x + area.width, area.y + area.height, 0x80FFFFFF, 0x80FFFFFF);
		GlStateManager.colorMask(true, true, true, true);
		GlStateManager.enableDepth();
	}

	public void drawTooltip(Minecraft minecraft, int mouseX, int mouseY) {
		List<IIngredientListElement<?>> ingredients = collapsedStack.getIngredients();
		if (ingredients.isEmpty()) return;

		FontRenderer font = minecraft.fontRenderer;
		final int COLS = 8;
		final int SLOT = 18; // 16px icon + 1px padding each side
		final int MAX_VISIBLE = COLS * 2 + 7; // 23 = rows of 8, 8, 7

		int total = ingredients.size();
		int shown = Math.min(total, MAX_VISIBLE);
		int overflow = total - shown;
		int numRows = shown <= COLS ? 1 : shown <= COLS * 2 ? 2 : 3;
		int gridCols = numRows > 1 ? COLS : shown;
		int gridW = gridCols * SLOT;
		int gridH = numRows * SLOT;

		String header = TextFormatting.GOLD + collapsedStack.getDisplayName()
			+ TextFormatting.GRAY + " (" + total + " items)";
		String hint = TextFormatting.YELLOW + Translator.translateToLocal("jei.tooltip.collapsed.expand");

		int tw = Math.max(font.getStringWidth(header), gridW);
		int th = 12 + gridH + 10;

		ScaledResolution sr = new ScaledResolution(minecraft);
		int tx = mouseX + 12;
		if (tx + tw + 6 > sr.getScaledWidth()) tx = mouseX - 16 - tw;
		int ty = mouseY - 12;
		if (ty + th + 4 > sr.getScaledHeight()) ty = sr.getScaledHeight() - th - 4;
		if (ty < 4) ty = 4;

		GlStateManager.disableRescaleNormal();
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();

		// Draw tooltip background (MC-style dark purple box with gradient border)
		final int z = 300;
		int bg = 0xF0100010, bs = 0x505000FF, be = (bs & 0xFEFEFE) >> 1 | (bs & 0xFF000000);
		GuiUtils.drawGradientRect(z, tx-3, ty-4, tx+tw+3, ty-3, bg, bg);
		GuiUtils.drawGradientRect(z, tx-3, ty+th+3, tx+tw+3, ty+th+4, bg, bg);
		GuiUtils.drawGradientRect(z, tx-3, ty-3, tx+tw+3, ty+th+3, bg, bg);
		GuiUtils.drawGradientRect(z, tx-4, ty-3, tx-3, ty+th+3, bg, bg);
		GuiUtils.drawGradientRect(z, tx+tw+3, ty-3, tx+tw+4, ty+th+3, bg, bg);
		GuiUtils.drawGradientRect(z, tx-3, ty-2, tx-2, ty+th+2, bs, be);
		GuiUtils.drawGradientRect(z, tx+tw+2, ty-2, tx+tw+3, ty+th+2, bs, be);
		GuiUtils.drawGradientRect(z, tx-3, ty-3, tx+tw+3, ty-2, bs, bs);
		GuiUtils.drawGradientRect(z, tx-3, ty+th+2, tx+tw+3, ty+th+3, be, be);

		// Title
		font.drawStringWithShadow(header, tx, ty, -1);

		// Item icon grid
		int itemsY = ty + 12;
		GlStateManager.pushMatrix();
		GlStateManager.translate(0.0f, 0.0f, 300.0f);
		RenderHelper.enableGUIStandardItemLighting();
		GlStateManager.enableDepth();
		RenderItem renderItem = minecraft.getRenderItem();
		for (int i = 0; i < shown; i++) {
			IIngredientListElement<?> element = ingredients.get(i);
			int ix = tx + (i % COLS) * SLOT + 1;
			int iy = itemsY + (i / COLS) * SLOT + 1;
			Object ing = element.getIngredient();
			if (ing instanceof ItemStack) {
				renderItem.renderItemAndEffectIntoGUI((ItemStack) ing, ix, iy);
			} else {
				try { renderIngredient(minecraft, ix, iy, element); }
				catch (RuntimeException | LinkageError ignored) {}
			}
		}
		RenderHelper.disableStandardItemLighting();
		GlStateManager.popMatrix();

		// "+N" overflow indicator in 8th slot of row 3 (only when there are hidden items)
		GlStateManager.disableDepth();
		GlStateManager.disableLighting();
		if (overflow > 0) {
			String overStr = "+" + overflow;
			int ox = tx + 7 * SLOT + 2;
			int oy = itemsY + 2 * SLOT + (SLOT - 8) / 2 + 1;
			font.drawStringWithShadow(overStr, ox, oy, 0xAAAAAA);
		}
		font.drawStringWithShadow(hint, tx, itemsY + gridH + 2, -1);

		GlStateManager.enableLighting();
		GlStateManager.enableDepth();
		RenderHelper.enableStandardItemLighting();
		GlStateManager.enableRescaleNormal();
	}

	/**
	 * Gets a ClickedIngredient for the first item in the group,
	 * so that recipe lookups still work for the representative item.
	 */
	@Nullable
	public ClickedIngredient<?> getClickedIngredient() {
		List<IIngredientListElement<?>> ingredients = collapsedStack.getIngredients();
		if (ingredients.isEmpty()) {
			return null;
		}
		IIngredientListElement<?> first = ingredients.get(0);
		return ClickedIngredient.create(first.getIngredient(), area);
	}

	public boolean isMouseOver(int mouseX, int mouseY) {
		return area.contains(mouseX, mouseY);
	}

	@SuppressWarnings("unchecked")
	private static <T> void renderIngredient(Minecraft minecraft, int x, int y, IIngredientListElement<T> element) {
		IIngredientRenderer<T> renderer = element.getIngredientRenderer();
		T ingredient = element.getIngredient();
		renderer.render(minecraft, x, y, ingredient);
	}
}
