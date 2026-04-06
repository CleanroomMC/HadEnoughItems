package mezz.jei.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.bookmarks.group.BookmarkGroupOrganizer;

import java.util.List;

public class BookmarkListBatchRenderer extends IngredientListBatchRenderer {
    private final BookmarkGroupOrganizer groupOrganizer;
    public BookmarkListBatchRenderer(BookmarkGroupOrganizer groupOrganizer) {
        super();
        this.groupOrganizer = groupOrganizer;
    }

    public void set(final int startIndex, List<IIngredientListElement> ingredientList) {
        if (!Config.areRecipeBookmarksEnabled()) {
            super.set(startIndex, ingredientList);
            return;
        }
        renderItems2d.clear();
        renderItems3d.clear();
        renderOther.clear();
        size = 0;

        // We need to clear all of them anyway.
        for (List<IngredientListSlot> row : slots) {
            for (IngredientListSlot slot : row) {
                slot.clear();
            }
        }
        if (!ingredientList.isEmpty()) {
            int i = startIndex;
            int currentGroup = ingredientList.get(i).getGroupIndex();
            List<Integer> groupIndices = new IntArrayList();
            for (List<IngredientListSlot> row : slots) {
                for (int column = 0; column < row.size(); column++) {
                    IngredientListSlot ingredientListSlot = row.get(column);
                    if (ingredientListSlot.isBlocked()) {
                        if (column == 0) {
                            groupIndices.add(-1);
                        }
                        continue;
                    }
                    if (i >= ingredientList.size()) {
                        break;
                    }
                    IIngredientListElement<?> element = ingredientList.get(i);
                    if (element.getGroupIndex() != currentGroup || element.startsNewRow()) {
                        currentGroup = element.getGroupIndex();
                        if (column > 0) {
                            break;
                        }
                    }
                    if (column == 0) {
                        groupIndices.add(currentGroup);
                    }

                    set(ingredientListSlot, element);
                    size++;
                    i++;
                }
            }

            groupOrganizer.setBookmarkGroupIds(groupIndices);
        }

        invalidateBuffer();
    }

    public List<Integer> sizePages(List<IIngredientListElement> ingredientList) {
        List<Integer> pages = new IntArrayList();
        pages.add(0);
        if (ingredientList.isEmpty() || slots.isEmpty()) {
            return pages;
        }

        int ingredientIndex = 0;
        int currentGroup = ingredientList.get(ingredientIndex).getGroupIndex();
        while (true) {
            for (int rowIndex = 0; rowIndex < slots.size(); rowIndex++) {
                List<IngredientListSlot> row = slots.get(rowIndex);
                for (int column = 0; column < row.size(); column++) {
                    IngredientListSlot ingredientListSlot = row.get(column);
                    if (ingredientListSlot.isBlocked()) {
                        continue;
                    }
                    IIngredientListElement<?> element = ingredientList.get(ingredientIndex);
                    if (element.getGroupIndex() != currentGroup || element.startsNewRow()) {
                        currentGroup = element.getGroupIndex();
                        if (column > 0) {
                            break;
                        }
                    }
                    if (ingredientList.size() <= ++ingredientIndex) {
                        return pages;
                    }
                }
            }
            pages.add(ingredientIndex);
        }
    }
}
