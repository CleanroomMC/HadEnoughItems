package mezz.jei.gui.overlay.bookmarks;

import mezz.jei.Internal;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.ghost.IGhostIngredientDragSource;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.GridAlignment;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.input.IClickedIngredient;
import mezz.jei.input.IMouseHandler;
import mezz.jei.input.IPaged;
import mezz.jei.input.IShowsRecipeFocuses;
import mezz.jei.util.MathUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.Set;

public class BookmarkGridWithNavigation implements IShowsRecipeFocuses, IMouseHandler, IGhostIngredientDragSource {
    private static final int NAVIGATION_HEIGHT = 20;

    private int firstItemIndex = 0;
    private final IPaged pageDelegate;
    private final BookmarkPageNavigation navigation;
    private final GuiScreenHelper guiScreenHelper;
    private final BookmarkGrid ingredientGrid;
    private final IIngredientGridSource ingredientSource;
    private Rectangle area = new Rectangle();

    public BookmarkGridWithNavigation(IIngredientGridSource ingredientSource, GuiScreenHelper guiScreenHelper, GridAlignment alignment) {
        this.ingredientGrid = new BookmarkGrid(alignment);
        this.ingredientSource = ingredientSource;
        this.guiScreenHelper = guiScreenHelper;
        this.pageDelegate = new BookmarkGridWithNavigation.IngredientGridPaged();
        this.navigation = new BookmarkPageNavigation(this.pageDelegate, this.ingredientGrid::changeOrder, false);
    }

    public void updateLayout(boolean resetToFirstPage) {
        if (resetToFirstPage) {
            firstItemIndex = 0;
        }
        @SuppressWarnings("rawtypes")
        List<IIngredientListElement> ingredientList = ingredientSource.getIngredientList();
        if (firstItemIndex >= ingredientList.size()) {
            firstItemIndex = 0;
        }
        this.ingredientGrid.getGuiIngredientSlots().set(firstItemIndex, ingredientList);
        this.navigation.updatePageState();
    }

    public boolean updateBounds(Rectangle availableArea, Set<Rectangle> guiExclusionAreas,
                                BookmarkList bookmarkList, int minWidth) {
        Rectangle estimatedNavigationArea = new Rectangle(
            availableArea.x,
            availableArea.y,
            availableArea.width,
            NAVIGATION_HEIGHT
        );
        Rectangle movedNavigationArea = MathUtil.moveDownToAvoidIntersection(guiExclusionAreas, estimatedNavigationArea);
        int navigationMaxY = movedNavigationArea.y + movedNavigationArea.height;
        Rectangle boundsWithoutNavigation = new Rectangle(
            availableArea.x,
            navigationMaxY,
            availableArea.width,
            availableArea.height - navigationMaxY
        );
        boolean gridHasRoom = this.ingredientGrid.updateBounds(boundsWithoutNavigation, minWidth, guiExclusionAreas, bookmarkList);
        if (!gridHasRoom) {
            return false;
        }
        Rectangle displayArea = this.ingredientGrid.getArea();
        Rectangle navigationArea = new Rectangle(displayArea.x, movedNavigationArea.y, displayArea.width, NAVIGATION_HEIGHT);
        this.navigation.updateBounds(navigationArea);
        this.area = displayArea.union(navigationArea);
        return true;
    }

    public Rectangle getArea() {
        return this.area;
    }

    public void draw(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        this.ingredientGrid.draw(minecraft, mouseX, mouseY);
        this.navigation.draw(minecraft, mouseX, mouseY, partialTicks);
    }

    public void drawTooltips(Minecraft minecraft, int mouseX, int mouseY) {
        if (!this.guiScreenHelper.isInGuiExclusionArea(mouseX, mouseY)) {
            this.ingredientGrid.drawTooltips(minecraft, mouseX, mouseY);
        }
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return this.area.contains(mouseX, mouseY) &&
            !guiScreenHelper.isInGuiExclusionArea(mouseX, mouseY);
    }

    @Override
    public boolean handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        return !guiScreenHelper.isInGuiExclusionArea(mouseX, mouseY) &&
            (this.ingredientGrid.handleMouseClicked(mouseX, mouseY) ||
                this.navigation.handleMouseClickedButtons(mouseX, mouseY));
    }

    @Override
    public boolean handleMouseScrolled(int mouseX, int mouseY, int scrollDelta) {
        IIngredientListElement<?> element = this.getElementUnderMouse();
        if ((Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) && element != null) {
            BookmarkItem<?> item = (BookmarkItem<?>) element.getIngredient();
            item.changeAmount(scrollDelta < 0 ? -1 : 1);
            Internal.getBookmarkList().saveBookmarks();
            return true;
        } else {
            if (scrollDelta < 0) {
                this.pageDelegate.nextPage();
                return true;
            } else if (scrollDelta > 0) {
                this.pageDelegate.previousPage();
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public IClickedIngredient<?> getIngredientUnderMouse(int mouseX, int mouseY) {
        return this.ingredientGrid.getIngredientUnderMouse(mouseX, mouseY);
    }

    @SuppressWarnings("rawtypes")
    @Nullable
    @Override
    public IIngredientListElement getElementUnderMouse() {
        return this.ingredientGrid.getElementUnderMouse();
    }

    @Override
    public boolean canSetFocusWithMouse() {
        return this.ingredientGrid.canSetFocusWithMouse();
    }

    private class IngredientGridPaged implements IPaged {
        @Override
        public boolean nextPage() {
            final int itemsCount = ingredientSource.size();
            if (itemsCount > 0) {
                firstItemIndex += ingredientGrid.size();
                if (firstItemIndex >= itemsCount) {
                    firstItemIndex = 0;
                }
                updateLayout(false);
                return true;
            } else {
                firstItemIndex = 0;
                updateLayout(false);
                return false;
            }
        }

        @Override
        public boolean previousPage() {
            final int itemsPerPage = ingredientGrid.size();
            if (itemsPerPage == 0) {
                firstItemIndex = 0;
                updateLayout(false);
                return false;
            }
            final int itemsCount = ingredientSource.size();

            int pageNum = firstItemIndex / itemsPerPage;
            if (pageNum == 0) {
                pageNum = itemsCount / itemsPerPage;
            } else {
                pageNum--;
            }

            firstItemIndex = itemsPerPage * pageNum;
            if (firstItemIndex > 0 && firstItemIndex == itemsCount) {
                pageNum--;
                firstItemIndex = itemsPerPage * pageNum;
            }
            updateLayout(false);
            return true;
        }

        @Override
        public boolean hasNext() {
            // true if there is more than one page because this wraps around
            int itemsPerPage = ingredientGrid.size();
            return itemsPerPage > 0 && ingredientSource.size() > itemsPerPage;
        }

        @Override
        public boolean hasPrevious() {
            // true if there is more than one page because this wraps around
            int itemsPerPage = ingredientGrid.size();
            return itemsPerPage > 0 && ingredientSource.size() > itemsPerPage;
        }

        @Override
        public int getPageCount() {
            final int itemCount = ingredientSource.size();
            final int stacksPerPage = ingredientGrid.size();
            if (stacksPerPage == 0) {
                return 1;
            }
            int pageCount = MathUtil.divideCeil(itemCount, stacksPerPage);
            pageCount = Math.max(1, pageCount);
            return pageCount;
        }

        @Override
        public int getPageNumber() {
            final int stacksPerPage = ingredientGrid.size();
            if (stacksPerPage == 0) {
                return 0;
            }
            return firstItemIndex / stacksPerPage;
        }
    }
}

