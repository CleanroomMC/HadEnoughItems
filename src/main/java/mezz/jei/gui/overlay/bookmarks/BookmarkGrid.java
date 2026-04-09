package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.config.Config;
import mezz.jei.gui.overlay.GridAlignment;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.gui.overlay.bookmarks.group.BookmarkGroupOrganizer;
import mezz.jei.render.*;
import mezz.jei.util.CollapsedClickAction;
import mezz.jei.util.MathUtil;
import net.minecraft.client.gui.GuiScreen;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BookmarkGrid extends IngredientGrid {
    private static final int INGREDIENT_PADDING = 1;
    private final GridAlignment alignment;
    private Rectangle area = new Rectangle();

    public BookmarkGrid(GridAlignment alignment, BookmarkGroupOrganizer groupOrganizer) {
        super(new BookmarkListBatchRenderer(groupOrganizer), alignment);
        this.alignment = alignment;
    }

    public boolean updateBounds(Rectangle availableArea, int minWidth, Collection<Rectangle> exclusionAreas) {
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

        for (int row = 0; row < rows; row++) {
            int y1 = y + (row * INGREDIENT_HEIGHT);
            List<IngredientListSlot> ingredientRow = new ArrayList<>();
            for (int column = 0; column < columns; column++) {
                int x1 = xOffset + (column * INGREDIENT_WIDTH);
                IngredientListSlot ingredientListSlot = new IngredientListSlot(x1, y1, INGREDIENT_PADDING);
                Rectangle stackArea = ingredientListSlot.getArea();
                final boolean blocked = MathUtil.intersects(exclusionAreas, stackArea);
                ingredientListSlot.setBlocked(blocked);
                ingredientRow.add(ingredientListSlot);
            }
            this.guiIngredientSlots.add(ingredientRow);
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

    public IngredientListBatchRenderer getGuiIngredientSlots() {
        return guiIngredientSlots;
    }

    @Override
    protected boolean handleCollapsedGroupClicked(int mouseX, int mouseY) {
        BookmarkListBatchRenderer renderer = (BookmarkListBatchRenderer) guiIngredientSlots;
        boolean firstItemMode = Config.getCollapsedClickAction() == CollapsedClickAction.FIRST_ITEM;
        boolean altDown = GuiScreen.isAltKeyDown();
        boolean expandKeyDown = firstItemMode == altDown;
        if (expandKeyDown) {
            CollapsedGroupRenderer collapsedHovered = renderer.getHoveredCollapsed(mouseX, mouseY);
            if (collapsedHovered != null) {
                BookmarkItem item = renderer.getBookmarkItemForRenderer(collapsedHovered);
                if (item != null) {
                    renderer.toggleBookmarkItemExpanded(item);
                    return true;
                }
            }
        }
        if (altDown) {
            IngredientRenderer<?> hovered = renderer.getHovered(mouseX, mouseY);
            if (hovered != null) {
                BookmarkItem item = renderer.getBookmarkItemForExpandedElement(hovered.getElement());
                if (item != null) {
                    renderer.toggleBookmarkItemExpanded(item);
                    return true;
                }
            }
        }
        return false;
    }

}
