package mezz.jei.gui.ingredients;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.gui.GuiHelper;
import mezz.jei.gui.elements.ScrollBar;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.ingredients.IngredientListElement;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.render.IngredientRenderer;
import mezz.jei.startup.ForgeModIdHelper;
import mezz.jei.startup.IModIdHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;

import javax.annotation.Nullable;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

/**
 * A read-only preview of every ingredient that a single recipe slot accepts, laid out as a grid of
 * at most {@value #COLUMNS} columns and {@value #ROWS} rows. It can be then rendered inside a
 * tooltip by passing its {@link #getRenderer()} renderer to
 * {@link mezz.jei.gui.TooltipRenderer#drawHoveringTextAndItems}.
 * <p>
 * When the slot accepts more ingredients than fit, a {@link ScrollBar} is drawn in an extra column
 * on the right and the grid shows a moving window of them.
 */
public class IngredientListPreview {
	/** The most columns and rows the tooltip grid is allowed to use. */
	public static final int COLUMNS = 8;
	public static final int ROWS = 3;
	/**
	 * The footprint the grid keeps: what {@value #COLUMNS}x{@value #ROWS} 16x16 ingredients with
	 * padding take up. Bigger ingredients shrink the column/row counts instead of growing the tooltip.
	 */
	private static final int GRID_BUDGET_WIDTH = COLUMNS * IngredientGrid.INGREDIENT_WIDTH;
	private static final int GRID_BUDGET_HEIGHT = ROWS * IngredientGrid.INGREDIENT_HEIGHT;
	/** Ingredients drawn smaller than this keep getting the classic 16x16 cell. */
	private static final int MIN_CONTENT_SIZE = IngredientListSlot.DEFAULT_CONTENT_SIZE;

	private final List<IIngredientListElement<?>> elements;
	private final IngredientListBatchRenderer renderer;
	private final List<IngredientListSlot> slots;
	private final ScrollBar scrollBar;
	/** How many cells fit in the grid. */
	private final int columns;
	private final int rows;
	/** No bigger than {@link #columns} x {@link #rows} */
	private final int visibleCount;
	/** The grid's size, in pixels; also the width the tooltip reserves for it. */
	private final int gridWidth;
	/** {@code false} when the grid can show every ingredient at once. */
	private final boolean scrollable;
	private float scrollOffset;
	/** Where the tooltip was last drawn, so a pinned tooltip can claim the screen area it covers. */
	@Nullable
	private Rectangle tooltipBounds;

	/**
	 * Wraps the ingredients of a recipe slot into a preview sized like that slot, or returns null
	 * when there is nothing worth showing.
	 */
	@Nullable
	public static <T> IngredientListPreview create(GuiIngredient<T> guiIngredient) {
		List<T> ingredients = guiIngredient.displayIngredients;
		if (ingredients.size() < 2) {
			// A slot with a single option already shows it; an extra grid would just be noise.
			return null;
		}

		IIngredientHelper<T> ingredientHelper = guiIngredient.ingredientHelper;
		IIngredientRenderer<T> ingredientRenderer = guiIngredient.ingredientRenderer;
		IModIdHelper modIdHelper = ForgeModIdHelper.getInstance();

		List<IIngredientListElement<?>> elements = new ObjectArrayList<>(ingredients.size());
		for (T ingredient : ingredients) {
			if (ingredient == null) {
				continue;
			}
			IIngredientListElement<T> element = IngredientListElement.create(ingredient, ingredientHelper, ingredientRenderer, modIdHelper, 0);
			if (element != null) {
				elements.add(element);
			}
		}
		if (elements.size() < 2) {
			// Everything was null, or every element failed to wrap.
			return null;
		}

		int contentWidth = guiIngredient.getRect().width - (2 * guiIngredient.getXPadding());
		int contentHeight = guiIngredient.getRect().height - (2 * guiIngredient.getYPadding());
		// Slots drawn smaller than 16x16 have always had a 16x16 cell and look fine in one,
		// so only a slot that needs more room gets a bigger area.
		return new IngredientListPreview(elements, Math.max(MIN_CONTENT_SIZE, contentWidth), Math.max(MIN_CONTENT_SIZE, contentHeight));
	}

	public IngredientListPreview(List<IIngredientListElement<?>> elements, int contentWidth, int contentHeight) {
		this.elements = elements;

		// A cell is big enough for one ingredient at the size the recipe slot draws it, plus padding.
		int cellWidth = contentWidth + (2 * IngredientGrid.INGREDIENT_PADDING);
		int cellHeight = contentHeight + (2 * IngredientGrid.INGREDIENT_PADDING);
		// Ingredients that are drawn larger than 16x16 get fewer cells rather than bigger ones, so
		// the tooltip never grows past the footprint the 8x3 grid of items has always had.
		this.columns = Math.max(1, Math.min(COLUMNS, GRID_BUDGET_WIDTH / cellWidth));
		this.rows = Math.max(1, Math.min(ROWS, GRID_BUDGET_HEIGHT / cellHeight));
		this.visibleCount = columns * rows;
		this.gridWidth = columns * cellWidth;
		int gridHeight = rows * cellHeight;
		this.scrollable = elements.size() > visibleCount;

		int displaySize = Math.min(visibleCount, elements.size());
		List<IngredientListSlot> slots = new ObjectArrayList<>(displaySize);
		for (int i = 0; i < displaySize; i++) {
			slots.add(new IngredientListSlot(0, 0, IngredientGrid.INGREDIENT_PADDING, contentWidth, contentHeight));
		}
		this.slots = Collections.unmodifiableList(slots);

		GuiHelper guiHelper = Internal.getHelpers().getGuiHelper();
		this.scrollBar = new ScrollBar(gridWidth, 0, gridHeight, guiHelper.getScrollbarBackground(), guiHelper.getScrollbarMarker());

		// Tooltips are drawn once per frame on top of everything else, so the framebuffer
		// optimization the ingredient list uses would only add overhead here.
		this.renderer = new PreviewRenderer();
		// One flat row of slots: moveSlotsToFit wraps it into the grid.
		this.renderer.add(slots);
		@SuppressWarnings({"unchecked", "rawtypes"})
		List<IIngredientListElement> castedVisibleElements = (List<IIngredientListElement>) (List<?>) visibleElements();
		this.renderer.set(0, castedVisibleElements);
	}

	public IngredientListBatchRenderer getRenderer() {
		return renderer;
	}

	/**
	 * The grid cells. Their areas are only meaningful after the renderer has laid them out for a
	 * tooltip width, which is why the tooltip rectangle is worth keeping for hit-testing.
	 */
	public List<IngredientListSlot> getSlots() {
		return slots;
	}

	/** How many ingredients the slot accepts, which may exceed the number actually displayed. */
	public int getTotalCount() {
		return elements.size();
	}

	public boolean isScrollable() {
		return scrollable;
	}

	// --- Scrolling ---

	public float getScrollOffset() {
		return scrollOffset;
	}

	public void setScrollOffset(float offset) {
		float clamped = Math.max(0.0F, Math.min(1.0F, offset));
		int previousStart = startIndex();
		this.scrollOffset = clamped;
		if (startIndex() != previousStart) {
			//noinspection unchecked
			renderer.set(0, (List<IIngredientListElement>) (List<?>) visibleElements());
		}
	}

	/** Scrolls by one wheel step. Returns whether the preview consumed it. */
	public boolean scrollBy(double scrollDelta) {
		if (!scrollable) {
			return false;
		}
		ScrollBar.ScrollResult result = scrollBar.scrollBy(scrollDelta, visibleCount, hiddenCount(), scrollOffset);
		if (result.isHandled()) {
			setScrollOffset(result.getScrollOffsetY());
			return true;
		}
		return false;
	}

	/** Starts dragging the scrollbar. Takes screen coordinates. */
	public boolean startScrollDrag(int screenMouseX, int screenMouseY) {
		if (!scrollable) {
			return false;
		}
		Point local = toLocal(screenMouseX, screenMouseY);
		if (local == null) {
			return false;
		}
		ScrollBar.ScrollResult result = scrollBar.startDrag(local.x, local.y, visibleCount, hiddenCount(), scrollOffset);
		if (result.isHandled()) {
			setScrollOffset(result.getScrollOffsetY());
			return true;
		}
		return false;
	}

	/** Continues a scrollbar drag. Takes a screen Y coordinate. */
	public boolean dragScrollTo(int screenMouseY) {
		if (!scrollBar.isDragging()) {
			return false;
		}
		Point origin = renderer.getRenderOrigin();
		if (origin == null) {
			return false;
		}
		ScrollBar.ScrollResult result = scrollBar.dragTo(screenMouseY - origin.y, visibleCount, hiddenCount(), scrollOffset);
		if (result.isHandled()) {
			setScrollOffset(result.getScrollOffsetY());
			return true;
		}
		return false;
	}

	public void stopScrollDrag() {
		scrollBar.stopDrag();
	}

	private int hiddenCount() {
		return Math.max(0, elements.size() - visibleCount);
	}

	private int startIndex() {
		return Math.round(scrollOffset * hiddenCount());
	}

	private List<IIngredientListElement<?>> visibleElements() {
		int from = startIndex();
		int to = Math.min(elements.size(), from + visibleCount);
		return elements.subList(from, to);
	}

	@Nullable
	private Point toLocal(int screenMouseX, int screenMouseY) {
		Point origin = renderer.getRenderOrigin();
		return origin == null ? null : new Point(screenMouseX - origin.x, screenMouseY - origin.y);
	}

	// --- Rendering ---

	/**
	 * Records the screen rectangle the tooltip was drawn into. Only a pinned tooltip needs this, to
	 * know which part of the screen it is covering.
	 */
	public void setTooltipBounds(@Nullable Rectangle tooltipBounds) {
		this.tooltipBounds = tooltipBounds;
	}

	@Nullable
	public Rectangle getTooltipBounds() {
		return tooltipBounds;
	}

	/**
	 * Highlights the grid cell under the given screen position. Needed by the pinned tooltip, where
	 * the mouse travels over the grid while the tooltip itself stays put.
	 */
	public void drawHighlight(int screenMouseX, int screenMouseY) {
		Point origin = renderer.getRenderOrigin();
		if (origin == null) {
			return;
		}
		IngredientListSlot slot = renderer.getSlotAtScreen(screenMouseX, screenMouseY);
		if (slot == null) {
			return;
		}
		IngredientRenderer<?> slotRenderer = slot.getIngredientRenderer();
		if (slotRenderer == null) {
			return;
		}
		GlStateManager.pushMatrix();
		GlStateManager.translate(origin.x, origin.y, 300.0F);
		slotRenderer.drawHighlight();
		GlStateManager.popMatrix();
	}

	/**
	 * Draws the grid and, when there is more to show than fits, the scrollbar in the column to its
	 * right. The bar is drawn through this subclass so that the extra column is included in the
	 * width the tooltip reserves for the grid.
	 */
	private class PreviewRenderer extends IngredientListBatchRenderer {
		PreviewRenderer() {
			super(false);
		}

		/**
		 * The tooltip lays the grids out into whatever width it has available, which would let the
		 * number of columns depend on the screen size. This grid always lays out at the width its
		 * cell size was decided at, and it is narrow enough to always fit.
		 */
		@Override
		public void moveSlotsToFit(int maxWidth) {
			super.moveSlotsToFit(gridWidth);
		}

		@Override
		public int getWidth() {
			return super.getWidth() + (scrollable ? ScrollBar.WIDTH : 0);
		}

		@Override
		public void render(Minecraft minecraft) {
			super.render(minecraft);
			if (!scrollable) {
				return;
			}
			// Slot areas are relative to the grid origin, and so is this.
			scrollBar.updateBounds(new Rectangle(gridWidth, 0, ScrollBar.WIDTH, getHeight()));
			scrollBar.draw(minecraft, visibleCount, hiddenCount(), scrollOffset);
		}
	}
}
