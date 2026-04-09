package mezz.jei.gui.overlay.bookmarks;

import it.unimi.dsi.fastutil.ints.IntList;
import mezz.jei.Internal;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.config.Config;
import mezz.jei.gui.GuiScreenHelper;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.ghost.IGhostIngredientDragSource;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.GridAlignment;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.overlay.bookmarks.group.BookmarkGroupOrganizer;
import mezz.jei.input.IClickedIngredient;
import mezz.jei.input.IMouseHandler;
import mezz.jei.input.IPaged;
import mezz.jei.input.IShowsRecipeFocuses;
import mezz.jei.render.BookmarkListBatchRenderer;
import mezz.jei.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class BookmarkGridWithNavigation implements IShowsRecipeFocuses, IMouseHandler, IGhostIngredientDragSource {
    private static final int NAVIGATION_HEIGHT = 20;
    public static final int BOOKMARK_TAB_WIDTH = 10;

    private int firstItemIndex = 0;
    private final IPaged pageDelegate;
    private IntList pageBoundaries;
    private final PageNavigation navigation;

    private BookmarkGroupOrganizer groupOrganizer;
    private final GuiScreenHelper guiScreenHelper;
    private final BookmarkGrid bookmarkGrid;
    private final IIngredientGridSource ingredientSource;
    private Rectangle area = new Rectangle();

    public BookmarkGridWithNavigation(IIngredientGridSource ingredientSource, GuiScreenHelper guiScreenHelper, GridAlignment alignment) {
        this.groupOrganizer = new BookmarkGroupOrganizer();
        this.bookmarkGrid = new BookmarkGrid(alignment, groupOrganizer);
        this.ingredientSource = ingredientSource;
        this.guiScreenHelper = guiScreenHelper;
        this.pageDelegate = new BookmarkGridPaged();
        this.navigation = new PageNavigation(this.pageDelegate, false);
        ((BookmarkListBatchRenderer) this.bookmarkGrid.getGuiIngredientSlots())
            .addBookmarkCollapseListener(() -> this.updateLayout(false));
    }

    public void updateLayout(boolean resetToFirstPage) {
        if (resetToFirstPage) {
            firstItemIndex = 0;
        }
        @SuppressWarnings("rawtypes")
        List<IIngredientListElement> ingredientList = ingredientSource.getIngredientList();
        BookmarkListBatchRenderer renderer = (BookmarkListBatchRenderer) this.bookmarkGrid.getGuiIngredientSlots();
        // Bounds check
        int prevDisplaySize = renderer.getDisplaySize();
        int boundsLimit = prevDisplaySize > 0 ? prevDisplaySize : ingredientList.size();
        if (firstItemIndex >= boundsLimit) {
            firstItemIndex = 0;
        }
        List<IIngredientListElement> collapsedList = ingredientSource.getCollapsedIngredientList();
        this.bookmarkGrid.getGuiIngredientSlots().setCollapsed(firstItemIndex, collapsedList);
        // Re-clamp if the display list shrank (e.g. group expanded) and firstItemIndex is now past the end.
        if (firstItemIndex > 0 && firstItemIndex >= renderer.getDisplaySize()) {
            firstItemIndex = 0;
            this.bookmarkGrid.getGuiIngredientSlots().setCollapsed(0, collapsedList);
        }
        this.pageBoundaries = renderer.sizePages();
        this.navigation.updatePageState();
    }

    public boolean updateBounds(Rectangle availableArea, Set<Rectangle> guiExclusionAreas, int minWidth) {
        Rectangle estimatedNavigationArea = new Rectangle(
                availableArea.x,
                availableArea.y,
                availableArea.width,
                NAVIGATION_HEIGHT
        );
        Rectangle movedNavigationArea = MathUtil.moveDownToAvoidIntersection(guiExclusionAreas, estimatedNavigationArea);
        int navigationMaxY = movedNavigationArea.y + movedNavigationArea.height;
        Rectangle boundsWithoutNavigation = new Rectangle(
                availableArea.x + (Config.areRecipeBookmarksEnabled() ? BOOKMARK_TAB_WIDTH : 0),
                navigationMaxY,
                availableArea.width - (Config.areRecipeBookmarksEnabled() ? BOOKMARK_TAB_WIDTH : 0),
                availableArea.height - navigationMaxY
        );
        Rectangle groupOrganizerBounds = new Rectangle(
                availableArea.x,
                navigationMaxY,
                availableArea.width,
                availableArea.height - navigationMaxY
        );
        boolean gridHasRoom = this.bookmarkGrid.updateBounds(boundsWithoutNavigation, minWidth, guiExclusionAreas);
        if (!gridHasRoom) {
            return false;
        }
        Rectangle displayArea = this.bookmarkGrid.getArea();
        Rectangle navigationArea = new Rectangle(2, movedNavigationArea.y, displayArea.width, NAVIGATION_HEIGHT);
        this.navigation.updateBounds(navigationArea);
        this.groupOrganizer.updateBounds(groupOrganizerBounds);
        this.area = displayArea.union(navigationArea);
        return true;
    }

    public Rectangle getArea() {
        return this.area;
    }

    public void draw(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        this.bookmarkGrid.draw(minecraft, mouseX, mouseY);
        this.navigation.draw(minecraft, mouseX, mouseY, partialTicks);
        this.groupOrganizer.draw(minecraft, mouseX, mouseY);
    }

    public void drawTooltips(Minecraft minecraft, int mouseX, int mouseY) {
        if (!this.guiScreenHelper.isInGuiExclusionArea(mouseX, mouseY)) {
            this.bookmarkGrid.drawTooltips(minecraft, mouseX, mouseY);
            this.groupOrganizer.drawTooltips(minecraft, mouseX, mouseY);
        }
    }

    @Override
    public boolean isMouseOver(int mouseX, int mouseY) {
        return this.area.contains(mouseX, mouseY) && !guiScreenHelper.isInGuiExclusionArea(mouseX, mouseY);
    }

    @Override
    public boolean handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        return !guiScreenHelper.isInGuiExclusionArea(mouseX, mouseY)
            && (this.groupOrganizer.handleMouseClicked(mouseX, mouseY, mouseButton)
                || this.bookmarkGrid.handleMouseClicked(mouseX, mouseY)
                || this.navigation.handleMouseClickedButtons(mouseX, mouseY));

    }

    // TODO: Add to interface?
    public boolean handleMouseReleased(int mouseX, int mouseY, int mouseButton) {
        return this.groupOrganizer.handleMouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean handleMouseScrolled(int mouseX, int mouseY, int scrollDelta) {
        IIngredientListElement<?> element = this.getElementUnderMouse();
        if ((Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) && element != null) {
            BookmarkItem<?> item = (BookmarkItem<?>) element.getIngredient();
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                if (item.ingredient instanceof ItemStack) {
                    int stackSize = ((ItemStack) item.ingredient).getMaxStackSize();
                    item.changeAmount(scrollDelta < 0 ? -stackSize : stackSize);
                } else if (item.ingredient instanceof FluidStack) {
                    item.changeAmount(scrollDelta < 0 ? -1000 : 1000);
                } else {
                    item.changeAmount(scrollDelta < 0 ? -1 : 1);
                }
            } else {
                item.changeAmount(scrollDelta < 0 ? -1 : 1);
            }
            Internal.getBookmarkList().saveBookmarks();
            bookmarkGrid.getGuiIngredientSlots().invalidateBuffer();
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
        return this.bookmarkGrid.getIngredientUnderMouse(mouseX, mouseY);
    }

    @SuppressWarnings("rawtypes")
    @Nullable
    @Override
    public IIngredientListElement getElementUnderMouse() {
        return this.bookmarkGrid.getElementUnderMouse();
    }

    @Override
    public boolean canSetFocusWithMouse() {
        return this.bookmarkGrid.canSetFocusWithMouse();
    }

    public BookmarkGroupOrganizer getBookmarkGroupOrganizer() {
        return groupOrganizer;
    }

    private class BookmarkGridPaged implements IPaged {

        @Override
        public boolean nextPage() {
            int pageNum = getPageNumber();
            if (pageNum == getPageCount() - 1) {
                updateLayout(true);
                return true;
            }
            firstItemIndex = pageBoundaries.getInt(pageNum + 1);
            updateLayout(false);
            return true;
        }

        @Override
        public boolean previousPage() {
            int pageNum = getPageNumber();
            firstItemIndex = pageBoundaries.getInt(pageNum == 0 ? pageBoundaries.size() - 1 : pageNum - 1);
            updateLayout(false);
            return true;
        }

        @Override
        public boolean hasNext() {
            return true;
        }

        @Override
        public boolean hasPrevious() {
            return true;
        }

        @Override
        public int getPageCount() {
            return pageBoundaries.size();
        }

        @Override
        public int getPageNumber() {
            if (pageBoundaries.isEmpty()) {
                firstItemIndex = 0;
                return 0;
            }
            // Binary search on page boundaries to find the index of the page boundary that is closest to firstItemIndex without going over it
            int index = Collections.binarySearch(pageBoundaries, firstItemIndex);
            if (index < 0) { // This is just how Collections.binarySearch returns if it doesn't find an exact match
                index = -index - 1;
                if (index == pageBoundaries.size()) { // And here's what it does if it's larger than everything in it.
                    index--;
                }
            }
            firstItemIndex = pageBoundaries.getInt(index); // This side effect is fine.
            return index;
        }
    }
}

