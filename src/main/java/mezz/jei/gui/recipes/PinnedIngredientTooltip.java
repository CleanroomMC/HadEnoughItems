package mezz.jei.gui.recipes;

import mezz.jei.gui.ingredients.GuiIngredient;
import mezz.jei.gui.ingredients.IngredientListPreview;
import mezz.jei.input.ClickedIngredient;
import mezz.jei.input.IClickedIngredient;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.render.IngredientRenderer;
import net.minecraft.client.Minecraft;

import javax.annotation.Nullable;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * A recipe slot tooltip that the player pinned in place by holding Shift, so that the ingredient
 * grid inside it can be hovered and clicked.
 * <p>
 * The tooltip keeps the screen position it had at the moment it was pinned; only the mouse moves.
 * Everything about which ingredient is under the pointer is derived from the grid the renderer laid
 * out, so this keeps working unchanged when the grid gains a scrollbar.
 */
public class PinnedIngredientTooltip {
	private final RecipeLayout layout;
	private final GuiIngredient<?> slot;
	private final IngredientListPreview preview;
	private final int screenMouseX;
	private final int screenMouseY;
	/**
	 * The screen area the tooltip covers. Captured once, because it is reported to the ingredient
	 * overlays as an exclusion area and letting it drift with the tooltip's text would make the
	 * list relayout on every frame.
	 */
	@Nullable
	private Rectangle bounds;

	public PinnedIngredientTooltip(RecipeLayout layout, GuiIngredient<?> slot, IngredientListPreview preview, int screenMouseX, int screenMouseY) {
		this.layout = layout;
		this.slot = slot;
		this.preview = preview;
		this.screenMouseX = screenMouseX;
		this.screenMouseY = screenMouseY;
	}

	public RecipeLayout getLayout() {
		return layout;
	}

	public GuiIngredient<?> getSlot() {
		return slot;
	}

	/** The mouse position the tooltip is pinned at, in screen coordinates. */
	public int getScreenMouseX() {
		return screenMouseX;
	}

	public int getScreenMouseY() {
		return screenMouseY;
	}

	public void drawHighlight(int mouseX, int mouseY) {
		preview.drawHighlight(mouseX, mouseY);
	}

	// --- Scrolling the grid ---

	/** Scrolls the grid by one wheel step. Returns whether the grid consumed it. */
	public boolean scrollBy(double scrollDelta) {
		return preview.scrollBy(scrollDelta);
	}

	/** Starts dragging the grid's scrollbar, if it has one. Takes screen coordinates. */
	public boolean startScrollDrag(int mouseX, int mouseY) {
		return preview.startScrollDrag(mouseX, mouseY);
	}

	/** Continues a scrollbar drag. Takes a screen Y coordinate. */
	public boolean dragScrollTo(int mouseY) {
		return preview.dragScrollTo(mouseY);
	}

	public void stopScrollDrag() {
		preview.stopScrollDrag();
	}

	/**
	 * Remembers the screen area the tooltip was drawn into. Called right after the tooltip render,
	 * and only the first call takes effect.
	 */
	public void captureBounds() {
		if (bounds != null) {
			return;
		}
		Rectangle current = preview.getTooltipBounds();
		if (current != null) {
			bounds = new Rectangle(current);
		}
	}

	@Nullable
	public Rectangle getBounds() {
		return bounds == null ? null : new Rectangle(bounds);
	}

	/**
	 * Whether the given screen position is anywhere inside the pinned tooltip, text as well as grid.
	 */
	public boolean isMouseOver(int mouseX, int mouseY) {
		return bounds != null && bounds.contains(mouseX, mouseY);
	}

	/**
	 * Draws the tooltip of the grid ingredient under the pointer, next to the pointer, exactly the
	 * way hovering that ingredient anywhere else would.
	 */
	public void drawHoveredIngredientTooltip(Minecraft minecraft, int mouseX, int mouseY) {
		IngredientListSlot slot = preview.getRenderer().getSlotAtScreen(mouseX, mouseY);
		if (slot == null) {
			return;
		}
		IngredientRenderer<?> slotRenderer = slot.getIngredientRenderer();
		if (slotRenderer != null) {
			slotRenderer.drawTooltip(minecraft, mouseX, mouseY);
		}
	}

	/**
	 * The grid ingredient under the given screen position, as something the player can click, or
	 * null when the pointer is not over a grid cell.
	 */
	@Nullable
	@SuppressWarnings("rawtypes")
	public IClickedIngredient<?> getIngredientUnderMouse(int mouseX, int mouseY) {
		IngredientListBatchRenderer renderer = preview.getRenderer();
		Point origin = renderer.getRenderOrigin();
		if (origin == null) {
			return null;
		}
		IngredientListSlot slot = renderer.getSlotAtScreen(mouseX, mouseY);
		if (slot == null) {
			return null;
		}
		IngredientRenderer slotRenderer = slot.getIngredientRenderer();
		if (slotRenderer == null) {
			return null;
		}
		Rectangle area = slot.getArea();
		Rectangle screenArea = new Rectangle(origin.x + area.x, origin.y + area.y, area.width, area.height);
		return ClickedIngredient.create(slotRenderer.getElement().getIngredient(), screenArea);
	}
}
