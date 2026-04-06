package mezz.jei.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
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
            IntList groupIndices = new IntArrayList();
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

    public IntList sizePages(List<IIngredientListElement> ingredientList) {
        IntList pages = new IntArrayList();
        pages.add(0);
        if (ingredientList.isEmpty() || slots.isEmpty()) {
            return pages;
        }

        int ingredientIndex = 0;
        int currentGroup = ingredientList.get(ingredientIndex).getGroupIndex();

        while (ingredientIndex < ingredientList.size()) {
            int pageStartIndex = ingredientIndex;
            boolean hasUsableSlot = false;

            for (int rowIndex = 0; rowIndex < slots.size() && ingredientIndex < ingredientList.size(); rowIndex++) {
                List<IngredientListSlot> row = slots.get(rowIndex);
                if (row.isEmpty()) {
                    continue;
                }

                for (int column = 0; column < row.size() && ingredientIndex < ingredientList.size(); column++) {
                    IngredientListSlot ingredientListSlot = row.get(column);
                    if (ingredientListSlot.isBlocked()) {
                        continue;
                    }

                    hasUsableSlot = true;

                    IIngredientListElement<?> element = ingredientList.get(ingredientIndex);
                    if (element.getGroupIndex() != currentGroup || element.startsNewRow()) {
                        currentGroup = element.getGroupIndex();
                        if (column > 0) {
                            break;
                        }
                    }

                    ingredientIndex++;
                }
            }

            if (!hasUsableSlot || ingredientIndex == pageStartIndex) {
                return pages;
            }

            if (ingredientIndex < ingredientList.size()) {
                pages.add(ingredientIndex);
                currentGroup = ingredientList.get(ingredientIndex).getGroupIndex();
            }
        }

        return pages;
    }
}
