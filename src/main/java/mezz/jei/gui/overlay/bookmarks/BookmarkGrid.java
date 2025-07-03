package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.Config;
import mezz.jei.gui.BookmarkUpdateEvent;
import mezz.jei.gui.overlay.GridAlignment;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.util.MathUtil;
import net.minecraftforge.common.MinecraftForge;

import java.awt.*;
import java.util.Collection;

public class BookmarkGrid extends IngredientGrid {
    private static final int INGREDIENT_PADDING = 1;
    private final GridAlignment alignment;
    private Rectangle area = new Rectangle();
    private boolean rowOrder = true;

    public BookmarkGrid(GridAlignment alignment) {
        super(alignment);
        this.alignment = alignment;
    }

    protected void changeOrder() {
        this.rowOrder = !this.rowOrder;
        MinecraftForge.EVENT_BUS.post(new BookmarkUpdateEvent());
    }

    public boolean updateBounds(Rectangle availableArea, int minWidth, Collection<Rectangle> exclusionAreas,
                                BookmarkList bookmarkList) {
        final int columns = Math.min(availableArea.width / INGREDIENT_WIDTH, Config.getMaxColumns());
        final int rows = availableArea.height / INGREDIENT_HEIGHT;

        final int ingredientsWidth = columns * INGREDIENT_WIDTH;
        final int width = Math.max(ingredientsWidth, minWidth);
        final int height = rows * INGREDIENT_HEIGHT;
        final int x;
        if (this.alignment == GridAlignment.LEFT) {
            x = availableArea.x + (availableArea.width - width);
        } else {
            x = availableArea.x;
        }
        final int y = availableArea.y + (availableArea.height - height) / 2;
        final int xOffset = x + Math.max(0, (width - ingredientsWidth) / 2);

        this.area = new Rectangle(x, y, width, height);
        this.guiIngredientSlots.clear();

        if (rows == 0 || columns < Config.smallestNumColumns) {
            return false;
        }

        if (rowOrder) {
            for (int row = 0; row < rows; row++) {
                int y1 = y + (row * INGREDIENT_HEIGHT);
                for (int column = 0; column < columns; column++) {
                    int x1 = xOffset + (column * INGREDIENT_WIDTH);
                    IngredientListSlot ingredientListSlot = new IngredientListSlot(x1, y1, INGREDIENT_PADDING);
                    Rectangle stackArea = ingredientListSlot.getArea();
                    final boolean blocked = MathUtil.intersects(exclusionAreas, stackArea);
                    ingredientListSlot.setBlocked(blocked);
                    this.guiIngredientSlots.add(ingredientListSlot);
                }
            }
        } else {
            for (int row = 0; row < rows; row++) {
                int y1 = y + (row * INGREDIENT_HEIGHT);
                IngredientListSlot ingredientListSlot = new IngredientListSlot(x, y1, INGREDIENT_PADDING);
                Rectangle stackArea = ingredientListSlot.getArea();
                final boolean blocked = MathUtil.intersects(exclusionAreas, stackArea);
                ingredientListSlot.setBlocked(blocked);
                this.guiIngredientSlots.add(ingredientListSlot);
            }
        }
        return true;
    }

    @Override
    public Rectangle getArea() {
        return area;
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return area.contains(mouseX, mouseY);
    }

    protected IngredientListBatchRenderer getGuiIngredientSlots() {
        return guiIngredientSlots;
    }
}
