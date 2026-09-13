package mezz.jei.gui.ingredients;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.ingredients.IngredientListElement;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.render.IngredientRenderer;
import mezz.jei.startup.IModIdHelper;
import net.minecraft.client.renderer.GlStateManager;

import javax.annotation.Nullable;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

/**
 * A read-only preview of every ingredient that a single recipe slot accepts, laid out as a fixed
 * {@value #COLUMNS}-column, {@value #ROWS}-row grid so that it can be rendered inside a tooltip by
 * {@link mezz.jei.gui.TooltipRenderer#drawHoveringTextAndItems}.
 * <p>
 * The ingredients are wrapped in {@link IngredientListElement}s and drawn through the very same
 * renderers they use inside the recipe slot, so every registered ingredient type is supported
 * without any type-specific handling here.
 * <p>
 * Build once (the wrapping is not free), then render every frame. The grid is truncated to
 * {@value #MAX_VISIBLE} entries; {@link #getTotalCount()} reports how many the slot really accepts.
 */
public class IngredientListPreview {
	/** The collapsed-group tooltip grid uses 8 columns and 3 rows; here the last row is full too. */
	public static final int COLUMNS = 8;
	public static final int ROWS = 3;
	public static final int MAX_VISIBLE = COLUMNS * ROWS;
	/** Forces a {@value #COLUMNS}-column layout regardless of how wide the tooltip ends up. */
	public static final int GRID_WIDTH = COLUMNS * IngredientGrid.INGREDIENT_WIDTH;

	private final IngredientListBatchRenderer renderer;
	private final List<IngredientListSlot> slots;
	private final int totalCount;
	/** Where the tooltip was last drawn, so a pinned tooltip can claim the screen area it covers. */
	@Nullable
	private Rectangle tooltipBounds;

	/**
	 * Wraps the given ingredients into a preview, or returns null when there is nothing worth showing.
	 *
	 * @param ingredients every ingredient the slot accepts, in display order.
	 */
	@Nullable
	public static <T> IngredientListPreview create(
		List<T> ingredients,
		IIngredientHelper<T> ingredientHelper,
		IIngredientRenderer<T> ingredientRenderer,
		IModIdHelper modIdHelper
	) {
		if (ingredients.size() < 2) {
			// A slot with a single option already shows it; an extra grid would just be noise.
			return null;
		}

		int shown = Math.min(ingredients.size(), MAX_VISIBLE);
		List<IIngredientListElement<?>> elements = new ObjectArrayList<>(shown);
		for (int i = 0; i < shown; i++) {
			T ingredient = ingredients.get(i);
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

		return new IngredientListPreview(elements, ingredients.size());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private IngredientListPreview(List<IIngredientListElement<?>> elements, int totalCount) {
		this.totalCount = totalCount;

		List<IngredientListSlot> slots = new ObjectArrayList<>(elements.size());
		for (int i = 0; i < elements.size(); i++) {
			slots.add(new IngredientListSlot(0, 0, IngredientGrid.INGREDIENT_PADDING));
		}
		this.slots = Collections.unmodifiableList(slots);

		// Tooltips are drawn once per frame on top of everything else, so the framebuffer
		// optimization the ingredient list uses would only add overhead here.
		this.renderer = new IngredientListBatchRenderer(false);
		// One flat row of slots: moveSlotsToFit wraps it into the grid.
		this.renderer.add(slots);
		// IngredientListBatchRenderer.set takes a raw list, matching how the bookmark organizer feeds it.
		this.renderer.set(0, (List<IIngredientListElement>) (List<?>) elements);
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
		return totalCount;
	}

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
}
