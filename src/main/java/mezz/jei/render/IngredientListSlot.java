package mezz.jei.render;

import javax.annotation.Nullable;
import java.awt.Rectangle;

public class IngredientListSlot {
	/** Ingredients that do not say otherwise are drawn at the vanilla 16x16 size. */
	public static final int DEFAULT_CONTENT_SIZE = 16;

	private final Rectangle area;
	private final int padding;
	private boolean blocked = false;
	@Nullable
	private IngredientRenderer ingredientRenderer;

	public IngredientListSlot(int xPosition, int yPosition, int padding) {
		this(xPosition, yPosition, padding, DEFAULT_CONTENT_SIZE, DEFAULT_CONTENT_SIZE);
	}

	/**
	 *
	 * @param padding note that padding is applied twice, at the left and right
	 */
	public IngredientListSlot(int xPosition, int yPosition, int padding, int contentWidth, int contentHeight) {
		this.padding = padding;
		this.area = new Rectangle(xPosition, yPosition, contentWidth + (2 * padding), contentHeight + (2 * padding));
	}

	@Nullable
	public IngredientRenderer getIngredientRenderer() {
		return ingredientRenderer;
	}

	public void clear() {
		this.ingredientRenderer = null;
	}

	public boolean isMouseOver(int mouseX, int mouseY) {
		return (this.ingredientRenderer != null) && area.contains(mouseX, mouseY);
	}

	public void setIngredientRenderer(IngredientRenderer ingredientRenderer) {
		this.ingredientRenderer = ingredientRenderer;
		ingredientRenderer.setArea(area);
		ingredientRenderer.setPadding(padding);
	}

	public Rectangle getArea() {
		return area;
	}

	/**
	 * Set true if this ingredient is blocked by an extra gui area from a mod.
	 */
	public void setBlocked(boolean blocked) {
		this.blocked = blocked;
	}

	public boolean isBlocked() {
		return blocked;
	}

	public boolean isFree() {
		return !blocked;
	}
}
