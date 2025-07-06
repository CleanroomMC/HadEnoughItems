package mezz.jei.gui.overlay.bookmarks.group;

import mezz.jei.Internal;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.autocrafting.RecipeBookmarkGroup;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.overlay.bookmarks.BookmarkGridWithNavigation;
import mezz.jei.input.MouseHelper;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_HEIGHT;

public class BookmarkGroupOrganizer {
    private Rectangle area = new Rectangle();
    private final List<BookmarkGroupDisplay> groups = new ArrayList<>(); // Equivalence between group area and group id
    public final int GROUP_PADDING_Y = INGREDIENT_HEIGHT / 2 - 5;
    public final int GROUP_PADDING_X = BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH / 2 - 1;

    public BookmarkGroupOrganizer() {
    }

    public void updateBounds(Rectangle availableArea) {
        this.area = availableArea;
    }

    public void setBookmarkGroupIds(List<Integer> bookmarkGroupIds) {
        // Find contiguous groups
        this.groups.clear();
        int startOfSequence = 0;
        int contiguousGroupId = bookmarkGroupIds.get(0);
        for (int i = 0; i < bookmarkGroupIds.size(); i++) {
            int groupId = bookmarkGroupIds.get(i);
            if (groupId == contiguousGroupId) {
                continue;
            }
            addGroup(startOfSequence, i - 1, contiguousGroupId);

            startOfSequence = i;
            contiguousGroupId = groupId;
        }
        addGroup(startOfSequence, bookmarkGroupIds.size() - 1, contiguousGroupId);
    }

    private void addGroup(int start, int end, int groupId) {
        if (groupId == -1) {
            return;
        }
        Rectangle groupArea = getGroupArea(start, end, area);
        groups.add(new BookmarkGroupDisplay(groupArea, groupId));
    }

    private Rectangle getGroupArea(int rowStart, int rowEnd, Rectangle availableArea) {
        final int rows = availableArea.height / INGREDIENT_HEIGHT;
        final int height = rows * INGREDIENT_HEIGHT;
        final int y = availableArea.y + (availableArea.height - height) / 2;


        return new Rectangle(0,
                INGREDIENT_HEIGHT * rowStart + y,
                availableArea.width,
                INGREDIENT_HEIGHT * (rowEnd - rowStart + 1));
    }

    public void draw(Minecraft minecraft, int mouseX, int mouseY) {
        for (BookmarkGroupDisplay groupDisplay : groups) {
            this.drawGroup(minecraft, mouseX, mouseY, groupDisplay);
        }
    }

    private void drawGroup(Minecraft minecraft, int mouseX, int mouseY, BookmarkGroupDisplay display) {
        Rectangle groupArea = display.area;
        BookmarkGroup group = display.group;
        int color = group.getColor();
        // Rectangle 1: a rectangle going down the left edge of the group area
        int top = groupArea.y + GROUP_PADDING_Y;
        int bottom = groupArea.y + groupArea.height - GROUP_PADDING_Y;
        int left = groupArea.x + GROUP_PADDING_X;
        int right = groupArea.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH - GROUP_PADDING_X;
        GuiScreen.drawRect(left, top, right, bottom, color);

        // Rectangle 2: a rectangle pointing right from the top edge of the group area, making a left bracket
        GuiScreen.drawRect(left, top - 2, groupArea.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH, top, color);
        // Rectangle 3: a rectangle pointing right from the bottom edge of the group area
        GuiScreen.drawRect(left, bottom, groupArea.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH, bottom + 2, color);
    }

    public void drawTooltips(Minecraft minecraft, int mouseX, int mouseY) {
        if (mouseX > area.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH) {
            return;
        }

        for (BookmarkGroupDisplay group : groups) {
            if (mouseY < group.area.y || mouseY > group.area.y + group.area.height) {
                continue;
            }
            List<String> tooltips = new ArrayList<>();
            if (Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)) {

            } else {
                tooltips.add(Translator.translateToLocal("hei.tooltip.press_alt"));
            }

            TooltipRenderer.drawHoveringText(minecraft, tooltips, mouseX, mouseY);
            break;
        }
    }

    public <I> List<IGhostIngredientHandler.Target<I>> getTargets(I ingredient) {
        List<IGhostIngredientHandler.Target<I>> targets = new ArrayList<>();
        for (BookmarkGroupDisplay groupDisplay : groups) {
            if (groupDisplay.group instanceof RecipeBookmarkGroup ^ ingredient instanceof RecipeBookmarkItem) {
                continue;
            }
            targets.add(groupDisplay);
        }
        return targets;
    }

    public boolean onKeyPressed(char typedChar, int eventKey) {
        int mouseX = MouseHelper.getX();
        int mouseY = MouseHelper.getY();
        if (mouseX > area.x + BookmarkGridWithNavigation.BOOKMARK_TAB_WIDTH) {
            return false;
        }
        for (BookmarkGroupDisplay group : groups) {
            if (mouseY < group.area.y || mouseY > group.area.y + group.area.height) {
                continue;
            }
            BookmarkList bookmarkList = Internal.getBookmarkList();
            if (Keyboard.isKeyDown(Keyboard.KEY_UP)) {
                if (bookmarkList.moveGroup(group.group, true)) {
                    bookmarkList.saveBookmarks();
                    bookmarkList.notifyListenersOfChange();
                    return true;
                }
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
                if (bookmarkList.moveGroup(group.group, false)) {
                    bookmarkList.saveBookmarks();
                    bookmarkList.notifyListenersOfChange();
                    return true;
                }
            }
        }
        return false;
    }
}
