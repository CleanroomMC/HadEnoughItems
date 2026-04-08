package mezz.jei.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.bookmarks.group.BookmarkGroupOrganizer;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;

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
        renderCollapsed.clear();
        collapsedStackIndexed.clear();
        size = 0;

        // We need to clear all of them anyway.
        for (List<IngredientListSlot> row : slots) {
            for (IngredientListSlot slot : row) {
                slot.clear();
            }
        }
        if (!ingredientList.isEmpty()) {
            int i = startIndex;
            int slotIndex = 0;
            int currentGroup = ingredientList.get(i).getGroupIndex();
            IntList groupIndices = new IntArrayList();
            for (List<IngredientListSlot> row : slots) {
                for (int column = 0; column < row.size(); column++) {
                    IngredientListSlot ingredientListSlot = row.get(column);
                    if (ingredientListSlot.isBlocked()) {
                        if (column == 0) {
                            groupIndices.add(-1);
                        }
                        slotIndex++;
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

                    Object ingredient = element.getIngredient();
                    if (ingredient instanceof BookmarkItem && ((BookmarkItem<?>) ingredient).ingredient instanceof CollapsedGroupIngredient) {
                        CollapsedGroupIngredient collapsed = (CollapsedGroupIngredient) ((BookmarkItem<?>) ingredient).ingredient;
                        CollapsedGroupRenderer renderer = new CollapsedGroupRenderer(collapsed);
                        renderer.setArea(ingredientListSlot.getArea());
                        renderer.setPadding(1);
                        renderCollapsed.add(renderer);
                        collapsedStackIndexed.put(slotIndex, collapsed);
                    } else {
                        set(ingredientListSlot, element);
                    }
                    size++;
                    i++;
                    slotIndex++;
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
