package mezz.jei.render;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.util.CollapsedClickAction;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.util.ITooltipFlag;
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
public class CollapsedGroupRenderer implements IIngredientRenderer<CollapsedGroupIngredient> {
	/** Singleton registered with the ingredient type system — {@code collapsedStack} is null. */
	public static final CollapsedGroupRenderer INSTANCE = new CollapsedGroupRenderer(null);

	private final CollapsedGroupIngredient collapsedStack;
	private Rectangle area = new Rectangle(0, 0, 16, 16);
	private int padding;

	public CollapsedGroupRenderer(CollapsedGroupIngredient collapsedStack) {
		this.collapsedStack = collapsedStack;
	}

	public void setArea(Rectangle area) {
		this.area = area;
	}

	public void setPadding(int padding) {
		this.padding = padding;
	}

	public CollapsedGroupIngredient getCollapsedStack() {
		return collapsedStack;
	}

	public Rectangle getArea() {
		return area;
	}

	/** Grid overlay render — uses this instance's stack and area+padding. */
	public void render(Minecraft minecraft) {
		if (collapsedStack == null || collapsedStack.isEmpty()) {
			return;
		}
		renderAt(minecraft, collapsedStack, area.x + padding, area.y + padding);
	}

	/**
	 * Stateless render at an arbitrary position — shared by the instance render and
	 * the {@link IIngredientRenderer} contract.
	 * For groups with 2+ items, mimics REI's stacked-card icon: each item is rendered at
	 * 0.75× scale (12 px), offset 4 px so both stay entirely within the 16×16 slot area.
	 *   Back  item (upper-right): screen origin (x+4, y+0), occupies (x+4..x+16, y..y+12)
	 *   Front item (lower-left) : screen origin (x+0, y+4), occupies (x..x+12, y+4..y+16)
	 * Count badge is drawn at 0.75× scale in orange in the bottom-right corner.
	 */
	private static void renderAt(Minecraft minecraft, CollapsedGroupIngredient ingredient, int x, int y) {
		List<IIngredientListElement<?>> ingredients = ingredient.getDisplayIngredients();
		if (ingredients.isEmpty()) {
			return;
		}

		GlStateManager.disableLighting();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(
				GlStateManager.SourceFactor.SRC_ALPHA,
				GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SourceFactor.ONE,
				GlStateManager.DestFactor.ZERO
		);
		// Draw background tint to visually distinguish collapsed groups
		GuiScreen.drawRect(x, y, x + 16, y + 16, ingredient.getBackgroundColor());
		GlStateManager.disableBlend();

		if (ingredients.size() == 1) {
			// Single item: render at full size
			renderElementAt(minecraft, ingredients.get(0), x, y, 1.0f);
		} else {
			// 0.75 scale → 12 px icon.
			// Back  (upper-right): origin at (x+4, y+0) → occupies x+4..x+16, y..y+12
			// Front (lower-left) : origin at (x+0, y+4) → occupies x..x+12,   y+4..y+16
			RenderItem renderItem = minecraft.getRenderItem();
			renderElementAt(minecraft, ingredients.get(1), x + 4, y + 0, 0.75f); // back
			// Elevate zLevel so the front item's depth values are naturally in front of the
			// back item's geometry. Using GL_LEQUAL (normal) keeps the front item's own
			// internal face culling intact — GL_ALWAYS would break tile-entity models.
			float prevZLevel = renderItem.zLevel;
			renderItem.zLevel += 100;
			renderElementAt(minecraft, ingredients.get(0), x + 0, y + 4, 0.75f); // front
			renderItem.zLevel = prevZLevel;
		}

		// Count badge: 0.75× scale, orange, right-aligned at the bottom of the slot
		int count = ingredient.size();
		if (count > 1) {
			FontRenderer fontRenderer = minecraft.fontRenderer;
			String countStr = String.valueOf(count);
			GlStateManager.disableLighting();
			GlStateManager.disableDepth();
			GlStateManager.disableBlend();
			final float badgeScale = 0.75f;
			// Convert desired screen position to scaled-coordinate space.
			// Screen right edge: x+16 → scaled coord (x+16)/badgeScale
			// Screen top of text: y+10 → scaled coord (y+10)/badgeScale
			int textWidth = fontRenderer.getStringWidth(countStr);
			int scaledRight = (int) ((x + 16) / badgeScale);
			int scaledTop  = (int) ((y + 10) / badgeScale);
			GlStateManager.pushMatrix();
			GlStateManager.scale(badgeScale, badgeScale, 1.0f);
			fontRenderer.drawStringWithShadow(countStr, scaledRight - textWidth, scaledTop, 0xFFAA00);
			GlStateManager.popMatrix();
			GlStateManager.enableDepth();
		}

		drawCollapsedBorder(x, y, ingredient.getBorderColor());
	}

	/**
	 * Renders one ingredient at (x, y) at the given scale using the GL matrix stack.
	 * Delegates to renderItemAndEffectIntoGUI so all item types (2D, 3D, built-in) render correctly.
	 */
	private static void renderElementAt(Minecraft minecraft, IIngredientListElement<?> element, int x, int y, float scale) {
		Object ingredient = element.getIngredient();
		try {
			RenderHelper.enableGUIStandardItemLighting();
			GlStateManager.pushMatrix();
			GlStateManager.translate(x, y, 0);
			GlStateManager.scale(scale, scale, scale);
			if (ingredient instanceof ItemStack) {
				minecraft.getRenderItem().renderItemAndEffectIntoGUI((ItemStack) ingredient, 0, 0);
			} else {
				renderIngredient(minecraft, 0, 0, element);
			}
			GlStateManager.popMatrix();
		} catch (RuntimeException | LinkageError ignored) {
			GlStateManager.popMatrix();
		}
	}

	private static void drawCollapsedBorder(int x, int y, int borderColor) {
		GlStateManager.disableLighting();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(
				GlStateManager.SourceFactor.SRC_ALPHA,
				GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SourceFactor.ONE,
				GlStateManager.DestFactor.ZERO
		);
		// Small triangle indicator in the top-left corner to show it's collapsible
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GuiScreen.drawRect(x, y, x + 4, y + 1, borderColor);
		GuiScreen.drawRect(x, y + 1, x + 1, y + 4, borderColor);
		GlStateManager.enableDepth();
		GlStateManager.disableBlend();
	}

	// --- IIngredientRenderer<CollapsedStack> implementation ---
	// INSTANCE (null stack) is registered with the ingredient type system.

	@Override
	public void render(Minecraft minecraft, int xPosition, int yPosition, @Nullable CollapsedGroupIngredient ingredient) {
		if (ingredient == null || ingredient.isEmpty()) {
			return;
		}
		renderAt(minecraft, ingredient, xPosition, yPosition);
	}

	@Override
	public List<String> getTooltip(Minecraft minecraft, CollapsedGroupIngredient ingredient, ITooltipFlag tooltipFlag) {
		List<String> tooltip = new ArrayList<>();
		tooltip.add(TextFormatting.GOLD + ingredient.getDisplayName()
				+ TextFormatting.GRAY + " (" + ingredient.size() + " items)");
		return tooltip;
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
		List<IIngredientListElement<?>> ingredients = collapsedStack.getDisplayIngredients();
		if (ingredients.isEmpty()) return;

		// Single-item group (e.g. search filtered to one result): show the item's native tooltip
		if (ingredients.size() == 1) {
			new IngredientRenderer<>(ingredients.get(0)).drawTooltip(minecraft, mouseX, mouseY);
			return;
		}

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
		// In OPEN_GROUP mode, alt+click uses first item; show that as the hint.
		// In FIRST_ITEM mode, alt+click expands; show that instead.
		String hint = TextFormatting.YELLOW + Translator.translateToLocal(
			Config.getCollapsedClickAction() == CollapsedClickAction.OPEN_GROUP
				? "hei.tooltip.collapsed.expand.firstItem"
				: "hei.tooltip.collapsed.expand");

		int tw = Math.max(Math.max(font.getStringWidth(header), font.getStringWidth(hint)), gridW);
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
	 * Returns the CollapsedStack as the clicked ingredient — registered as IIngredientType
	 * for addon compatibility. Recipe lookups are delegated via translateFocus on the helper.
	 */
	@Nullable
	public ClickedIngredient<?> getClickedIngredient() {
		List<IIngredientListElement<?>> ingredients = collapsedStack.getIngredients();
		if (ingredients.isEmpty()) {
			return null;
		}
		// Return CollapsedStack directly — it is a registered IIngredientType
		return ClickedIngredient.create(collapsedStack, area);
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
