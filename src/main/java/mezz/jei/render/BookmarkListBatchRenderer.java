package mezz.jei.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.bookmarks.group.BookmarkGroupOrganizer;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.input.ClickedIngredient;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BookmarkListBatchRenderer extends IngredientListBatchRenderer {
    private final BookmarkGroupOrganizer groupOrganizer;
    private final Set<BookmarkItem> bookmarkExpandedItems = new ObjectOpenHashSet<>();
    private final List<Runnable> bookmarkCollapseListeners = new ArrayList<>();
    private List<IIngredientListElement> cachedDisplayElements = Collections.emptyList();
    private IntList cachedDisplayGroupIndices = new IntArrayList();
    private final Map<CollapsedGroupRenderer, BookmarkItem> collapsedRendererToBookmark = new Reference2ObjectOpenHashMap<>();
    private final Map<IIngredientListElement, BookmarkItem> expandedElementToBookmark = new Reference2ObjectOpenHashMap<>();

    public BookmarkListBatchRenderer(BookmarkGroupOrganizer groupOrganizer) {
        super();
        this.groupOrganizer = groupOrganizer;
    }

    public void toggleBookmarkItemExpanded(BookmarkItem item) {
        if (!bookmarkExpandedItems.remove(item)) {
            bookmarkExpandedItems.add(item);
        }
        for (Runnable listener : bookmarkCollapseListeners) {
            listener.run();
        }
    }

    public boolean isBookmarkItemExpanded(BookmarkItem item) {
        return bookmarkExpandedItems.contains(item);
    }

    @Nullable
    public BookmarkItem getBookmarkItemForRenderer(CollapsedGroupRenderer renderer) {
        return collapsedRendererToBookmark.get(renderer);
    }

    @Nullable
    public BookmarkItem getBookmarkItemForExpandedElement(IIngredientListElement element) {
        return expandedElementToBookmark.get(element);
    }

    public void addBookmarkCollapseListener(Runnable listener) {
        bookmarkCollapseListeners.add(listener);
    }

    public int getDisplaySize() {
        return cachedDisplayElements.size();
    }

    /**
     * Returns the BookmarkItem for every slot type so that bookmark-key handling can identify
     * bookmark-grid items by value instanceof BookmarkItem, and remove the correct instance.
     */
    @Override
    public ClickedIngredient<?> getIngredientUnderMouse(int mouseX, int mouseY) {
        CollapsedGroupRenderer collapsedHovered = getHoveredCollapsed(mouseX, mouseY);
        if (collapsedHovered != null) {
            BookmarkItem<?> item = collapsedRendererToBookmark.get(collapsedHovered);
            if (item != null) {
                return ClickedIngredient.create(item, collapsedHovered.getArea());
            }
            return collapsedHovered.getClickedIngredient();
        }
        IngredientRenderer<?> hovered = getHovered(mouseX, mouseY);
        if (hovered != null) {
            IIngredientListElement<?> element = hovered.getElement();
            BookmarkItem<?> parentItem = expandedElementToBookmark.get(element);
            if (parentItem != null) {
                return ClickedIngredient.create(parentItem, hovered.getArea());
            }
            return ClickedIngredient.create(element.getIngredient(), hovered.getArea());
        }
        return null;
    }

    @Override
    protected void setSlots(int startIndex, List<IIngredientListElement> ingredientList) {
        if (!Config.areRecipeBookmarksEnabled()) {
            super.setSlots(startIndex, ingredientList);
            return;
        }
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
    }

    @Override
    public void setCollapsed(int startIndex, List<IIngredientListElement> collapsedList) {
        if (!Config.areRecipeBookmarksEnabled()) {
            super.setCollapsed(startIndex, collapsedList);
            return;
        }

        renderItems2d.clear();
        renderItems3d.clear();
        renderOther.clear();
        renderCollapsed.clear();
        collapsedStackIndexed.clear();
        expandedElementToGroup.clear();
        expandedGroupSlots.clear();
        collapsedRendererToBookmark.clear();
        expandedElementToBookmark.clear();
        maxSize = 0;
        size = 0;

        for (List<IngredientListSlot> row : slots) {
            for (IngredientListSlot slot : row) {
                slot.clear();
            }
        }

        if (collapsedList.isEmpty()) {
            cachedDisplayElements = Collections.emptyList();
            cachedDisplayGroupIndices = new IntArrayList();
            groupOrganizer.setBookmarkGroupIds(new IntArrayList());
            invalidateBuffer();
            return;
        }

        // Flatten: BookmarkItem<CollapsedGroupIngredient> → sub-elements (expanded) or single slot (collapsed).
        // itemToGroupIndex preserves the bookmark's group index for sub-elements that don't carry it themselves.
        List<IIngredientListElement> displayItems = new ArrayList<>();
        Map<IIngredientListElement, CollapsedGroupIngredient> itemToCollapsed = new HashMap<>();
        Map<IIngredientListElement, Integer> itemToGroupIndex = new HashMap<>();

        for (IIngredientListElement element : collapsedList) {
            Object ingredient = element.getIngredient();
            if (ingredient instanceof BookmarkItem && ((BookmarkItem<?>) ingredient).ingredient instanceof CollapsedGroupIngredient) {
                BookmarkItem<?> bookmarkItem = (BookmarkItem<?>) ingredient;
                CollapsedGroupIngredient collapsed = (CollapsedGroupIngredient) bookmarkItem.ingredient;
                int groupIndex = element.getGroupIndex();
                if (isBookmarkItemExpanded(bookmarkItem)) {
                    for (IIngredientListElement<?> subElement : collapsed.getIngredients()) {
                        displayItems.add(subElement);
                        itemToCollapsed.put(subElement, collapsed);
                        itemToGroupIndex.put(subElement, groupIndex);
                        expandedElementToBookmark.put(subElement, bookmarkItem);
                    }
                } else {
                    displayItems.add(element);
                    itemToGroupIndex.put(element, groupIndex);
                }
            } else {
                displayItems.add(element);
                itemToGroupIndex.put(element, element.getGroupIndex());
            }
        }

        // Cache for sizePages — must happen before any early return so it's always current.
        this.cachedDisplayElements = displayItems;
        this.cachedDisplayGroupIndices = new IntArrayList(displayItems.size());
        for (IIngredientListElement item : displayItems) {
            cachedDisplayGroupIndices.add(itemToGroupIndex.getOrDefault(item, item.getGroupIndex()));
        }

        if (startIndex >= displayItems.size()) {
            groupOrganizer.setBookmarkGroupIds(new IntArrayList());
            invalidateBuffer();
            return;
        }

        int i = startIndex;
        int slotIndex = 0;
        int currentGroup = itemToGroupIndex.getOrDefault(displayItems.get(i), displayItems.get(i).getGroupIndex());
        IntList groupIndices = new IntArrayList();

        for (List<IngredientListSlot> row : slots) {
            maxSize += (int) row.stream().filter(IngredientListSlot::isFree).count();
            for (int column = 0; column < row.size(); column++) {
                IngredientListSlot ingredientListSlot = row.get(column);
                if (ingredientListSlot.isBlocked()) {
                    if (column == 0) {
                        groupIndices.add(-1);
                    }
                    slotIndex++;
                    continue;
                }
                if (i >= displayItems.size()) {
                    break;
                }
                IIngredientListElement displayItem = displayItems.get(i);
                int elemGroup = itemToGroupIndex.getOrDefault(displayItem, displayItem.getGroupIndex());
                if (elemGroup != currentGroup || displayItem.startsNewRow()) {
                    currentGroup = elemGroup;
                    if (column > 0) {
                        break;
                    }
                }
                if (column == 0) {
                    groupIndices.add(currentGroup);
                }

                Object ingredient = displayItem.getIngredient();
                if (ingredient instanceof BookmarkItem && ((BookmarkItem<?>) ingredient).ingredient instanceof CollapsedGroupIngredient) {
                    BookmarkItem<?> bookmarkItem = (BookmarkItem<?>) ingredient;
                    CollapsedGroupIngredient collapsed = (CollapsedGroupIngredient) bookmarkItem.ingredient;
                    CollapsedGroupRenderer renderer = new CollapsedGroupRenderer(collapsed);
                    renderer.setArea(ingredientListSlot.getArea());
                    renderer.setPadding(1);
                    renderCollapsed.add(renderer);
                    collapsedStackIndexed.put(slotIndex, collapsed);
                    collapsedRendererToBookmark.put(renderer, bookmarkItem);
                } else {
                    set(ingredientListSlot, displayItem);
                    CollapsedGroupIngredient parentCollapsed = itemToCollapsed.get(displayItem);
                    if (parentCollapsed != null) {
                        collapsedStackIndexed.put(slotIndex, parentCollapsed);
                        expandedElementToGroup.put(displayItem, parentCollapsed);
                        expandedGroupSlots.computeIfAbsent(parentCollapsed, k -> new ArrayList<>())
                            .add(new Rectangle(ingredientListSlot.getArea()));
                    }
                }
                size++;
                i++;
                slotIndex++;
            }
        }

        groupOrganizer.setBookmarkGroupIds(groupIndices);
        invalidateBuffer();
    }

    /**
     * Computes page start indices into the flattened display list last built by {@link #setCollapsed}.
     * Must be called after setCollapsed so the cache is populated.
     */
    public IntList sizePages() {
        IntList pages = new IntArrayList();
        pages.add(0);
        List<IIngredientListElement> displayList = cachedDisplayElements;
        if (displayList.isEmpty() || slots.isEmpty()) {
            return pages;
        }

        int idx = 0;
        int currentGroup = cachedDisplayGroupIndices.getInt(0);

        while (idx < displayList.size()) {
            int pageStartIdx = idx;
            boolean hasUsableSlot = false;

            for (int rowIndex = 0; rowIndex < slots.size() && idx < displayList.size(); rowIndex++) {
                List<IngredientListSlot> row = slots.get(rowIndex);
                if (row.isEmpty()) {
                    continue;
                }

                for (int column = 0; column < row.size() && idx < displayList.size(); column++) {
                    if (row.get(column).isBlocked()) {
                        continue;
                    }

                    hasUsableSlot = true;

                    int elemGroup = cachedDisplayGroupIndices.getInt(idx);
                    if (elemGroup != currentGroup || displayList.get(idx).startsNewRow()) {
                        currentGroup = elemGroup;
                        if (column > 0) {
                            break;
                        }
                    }

                    idx++;
                }
            }

            if (!hasUsableSlot || idx == pageStartIdx) {
                return pages;
            }

            if (idx < displayList.size()) {
                pages.add(idx);
                currentGroup = cachedDisplayGroupIndices.getInt(idx);
            }
        }

        return pages;
    }
}
