package mezz.jei.gui.overlay.bookmarks.group;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.autocrafting.RecipeBookmarkGroup;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkList;
import mezz.jei.config.KeyBindings;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.bookmarks.BookmarkGridWithNavigation;
import mezz.jei.input.MouseHelper;
import mezz.jei.render.IngredientListBatchRenderer;
import mezz.jei.render.IngredientListSlot;
import mezz.jei.util.Translator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_HEIGHT;
import static mezz.jei.gui.overlay.IngredientGrid.INGREDIENT_PADDING;

public class BookmarkGroupOrganizer {
    private Rectangle area = new Rectangle();
    private final List<BookmarkGroupDisplay> groups = new ArrayList<>();
    private IngredientListBatchRenderer missingIngredientRenderer = new IngredientListBatchRenderer();
    private int hoveredGroupId = -1;
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
        if (bookmarkGroupIds.isEmpty()) {
            return;
        }
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
        BookmarkGroup group = Internal.getBookmarkList().getBookmarkGroup(groupId);
        if (group == null) {
            return;
        }
        Rectangle groupArea = getGroupArea(start, end, area);
        groups.add(new BookmarkGroupDisplay(groupArea, group));
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
            hoveredGroupId = -1;
            return;
        }

        boolean hovered = false;
        for (BookmarkGroupDisplay group : groups) {
            if (mouseY < group.area.y || mouseY > group.area.y + group.area.height) {
                continue;
            }
            List<Object> tooltips = new ArrayList<>();
            if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
                tooltips.add(Translator.translateToLocal("hei.tooltip.organizer.1"));
                if (group.group instanceof RecipeBookmarkGroup) {
                    tooltips.add(Translator.translateToLocal("hei.tooltip.organizer.2"));
                    tooltips.add(Translator.translateToLocal("hei.tooltip.organizer.3"));
                }
            } else {
                hovered = true;
                tooltips.add(Translator.translateToLocal("hei.tooltip.press_alt"));
                if (group.group instanceof RecipeBookmarkGroup) {
                    tooltips.add(Translator.translateToLocal("hei.tooltip.missing_ingredients"));
                    if (group.group.id != hoveredGroupId) {
                        List<IIngredientListElement> missing = ((RecipeBookmarkGroup) group.group).getMissingIngredients();
                        this.missingIngredientRenderer.clear();
                        List<IngredientListSlot> slots = new ObjectArrayList<>();
                        for (IIngredientListElement a : missing) {
                            slots.add(new IngredientListSlot(0, 0, INGREDIENT_PADDING));
                        }
                        this.missingIngredientRenderer.add(slots);
                        this.missingIngredientRenderer.set(0, missing);
                    }
                    tooltips.add(this.missingIngredientRenderer);
                }
            }
            TooltipRenderer.drawHoveringTextAndItems(minecraft, tooltips, mouseX, mouseY);
            break;
        }
        if (!hovered) {
            hoveredGroupId = -1;
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
                    return true;
                }
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
                if (bookmarkList.moveGroup(group.group, false)) {
                    return true;
                }
            }
            if (KeyBindings.bookmark.isActiveAndMatches(eventKey)) {
                if (bookmarkList.removeGroup(group.group)) {
                    return true;
                }
            }
            if (KeyBindings.crafting.isActiveAndMatches(eventKey)) {
                if (group.group instanceof RecipeBookmarkGroup) {
                    ((RecipeBookmarkGroup) group.group).autocraft();
                    return true;
                }
            }
        }
        return false;
    }
}
