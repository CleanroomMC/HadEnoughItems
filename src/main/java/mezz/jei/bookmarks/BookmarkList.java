package mezz.jei.bookmarks;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.autocrafting.IngredientUtil;
import mezz.jei.autocrafting.RecipeBookmarkGroup;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.overlay.bookmarks.group.BookmarkGroupOrganizer;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.util.Log;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class BookmarkList implements IIngredientGridSource {
    private static final String MARKER_GROUP = "B:";
    private static final String MARKER_RECIPE_GROUP = "R:";

    private final List<BookmarkGroup> list = new LinkedList<>();
    private final IngredientRegistry ingredientRegistry;
    private final List<IIngredientGridSource.Listener> listeners = new ArrayList<>();
    private int nextId = 0;
    private BookmarkGroupOrganizer bookmarkGroupOrganizer;

    public BookmarkList(IngredientRegistry ingredientRegistry) {
        this.ingredientRegistry = ingredientRegistry;
    }

    public <T> boolean add(BookmarkItem<T> ingredient) {
        return add(ingredient, false);
    }

    public boolean add(BookmarkGroup group) {
        list.add(group);
        notifyListenersOfChange();
        saveBookmarks();
        return true;
    }

    public <T> boolean add(BookmarkItem<T> ingredient, boolean forceFront) {
        BookmarkItem<T> normalized = IngredientUtil.normalizeBookmark(ingredient);
        boolean addToFront = forceFront || Config.isAddingBookmarksToFront();
        boolean alreadyExists = normalized.ingredient instanceof CollapsedGroupIngredient
                ? groupContains(getAddingGroup(addToFront), normalized)
                : contains(normalized);
        if (!alreadyExists) {
            if (addToLists(normalized, addToFront)) {
                notifyListenersOfChange();
                saveBookmarks();
                return true;
            }
        } else if (forceFront) {
            // avoid boolean expression short-circuiting
            boolean flag1 = remove(normalized, true);
            boolean flag2 = addToLists(normalized, true);
            if (flag1 || flag2) {
                notifyListenersOfChange();
                saveBookmarks();
                return true;
            }
        }
        return false;
    }

    @Deprecated
    public <T> boolean add(T ingredient) {
        return add(ingredient, false);
    }

    @Deprecated
    public <T> boolean add(T ingredient, boolean forceFront) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
        Log.get().error("Deprecated BookmarkList#add method called. Use BookmarkList#add(BookmarkItem<T>) instead. Caller: {}#{}", caller.getClassName(), caller.getMethodName());
        return add(new BookmarkItem<>(ingredient), forceFront);
    }


    private boolean groupContains(BookmarkGroup group, BookmarkItem<?> item) {
        IIngredientHelper<Object> ingredientHelper = ingredientRegistry.getIngredientHelper(item);
        String uid = ingredientHelper.getUniqueId(item);
        for (BookmarkItem<?> existing : group.getItems()) {
            if (item == existing) {
                return true;
            }
            if (existing != null && existing.getClass() == item.getClass()) {
                if (uid.equals(ingredientHelper.getUniqueId(existing))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean contains(Object ingredient) {
        // We cannot assume that ingredients have a working equals() implementation. Even ItemStack doesn't have one...
        IIngredientHelper<Object> ingredientHelper = ingredientRegistry.getIngredientHelper(ingredient);

        for (BookmarkGroup group : list) {
            for (BookmarkItem existing : group.getItems()) {
                if (ingredient == existing) {
                    return true;
                }
                if (existing != null && existing.getClass() == ingredient.getClass()) {
                    if (ingredientHelper.getUniqueId(existing).equals(ingredientHelper.getUniqueId(ingredient))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean remove(Object ingredient) {
        return remove(ingredient, false);
    }

    public boolean remove(Object ingredient, boolean looseEqualCheck) {
        for (BookmarkGroup group : list) {
            for (int i = 0; i < group.getItems().size(); i++) {
                BookmarkItem existing = group.getItems().get(i);
                if (looseEqualCheck) {
                    String id1 = ingredientRegistry.getIngredientHelper(ingredient).getUniqueId(ingredient);
                    String id2 = ingredientRegistry.getIngredientHelper(existing).getUniqueId(existing);
                    if (id1.equals(id2)) {
                        removeItemFromGroup(group, existing);
                        return true;
                    }
                }
                if (ingredient == existing) {
                    removeItemFromGroup(group, existing);
                    return true;
                }
            }
        }
        return false;
    }

    private void removeItemFromGroup(BookmarkGroup group, BookmarkItem<?> item) {
        group.removeItem(item);
        if (group.items.isEmpty() && !containsAnyAddableGroups()) {
            list.remove(group);
        }
        notifyListenersOfChange();
        saveBookmarks();
    }

    private boolean containsAnyAddableGroups() {
        return this.list.stream().anyMatch(BookmarkGroup::acceptsChanges);
    }

    public void saveBookmarks() {
        List<String> strings = new ArrayList<>();
        for (BookmarkGroup group : list) {
            if (group instanceof RecipeBookmarkGroup) {
                strings.add(MARKER_RECIPE_GROUP);
            } else {
                strings.add(MARKER_GROUP);
            }
            for (BookmarkItem<?> item : group.getItems()) {
                String serialized = item.serialize();
                if (serialized == null) {
                    continue;
                }
                strings.add(serialized);
            }
        }

        File file = Config.getBookmarkFile();
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                IOUtils.writeLines(strings, "\n", writer);
            } catch (IOException e) {
                Log.get().error("Failed to save bookmarks list to file {}", file, e);
            }
        }
    }

    private static <T> String getUid(IIngredientListElement<T> element) {
        IIngredientHelper<T> ingredientHelper = element.getIngredientHelper();
        return ingredientHelper.getUniqueId(element.getIngredient());
    }

    public void loadBookmarks() {
        File file = Config.getBookmarkFile();
        if (file == null || !file.exists()) {
            return;
        }
        List<String> ingredientJsonStrings;
        try (FileReader reader = new FileReader(file)) {
            ingredientJsonStrings = IOUtils.readLines(reader);
        } catch (IOException e) {
            Log.get().error("Failed to load bookmarks from file {}", file, e);
            return;
        }

        Collection<IIngredientType> otherIngredientTypes = new ArrayList<>(ingredientRegistry.getRegisteredIngredientTypes());
        otherIngredientTypes.remove(VanillaTypes.ITEM);

        list.clear();
        BookmarkGroup group = new BookmarkGroup(nextId++);
        for (String ingredientJsonString : ingredientJsonStrings) {
            BookmarkItem<?> item = BookmarkItem.deserialize(ingredientJsonString, otherIngredientTypes);
            if (item != null) {
                group.addItemInternal(item); // Don't cause recipe chains to update
            } else if (ingredientJsonString.equals(MARKER_GROUP)) {
                if (!group.items.isEmpty()) {
                    list.add(group);
                }
                group = new BookmarkGroup(nextId++);
            } else if (ingredientJsonString.equals(MARKER_RECIPE_GROUP)) {
                if (!group.items.isEmpty()) {
                    list.add(group);
                }
                group = new RecipeBookmarkGroup(nextId++);
            } else {
                Log.get().error("Failed to load unknown bookmarked ingredient:\n{}", ingredientJsonString);
            }
        }
        if (!group.items.isEmpty()) {
            list.add(group);
        }
        for (BookmarkGroup newGroup : list) {
            newGroup.finishLoading();
        }
        //notifyListenersOfChange();
    }

    public BookmarkGroup getBookmarkGroup(int id) {
        for (BookmarkGroup group : list) {
            if (group.id == id) {
                return group;
            }
        }
        return null;
    }

    public boolean removeGroup(BookmarkGroup group) {
        if (list.remove(group)) {
            notifyListenersOfChange();
            saveBookmarks();
            return true;
        }
        return false;
    }


    private boolean addToLists(BookmarkItem<?> ingredient, boolean addToFront) { // false = stackT ingredient, boolean addToFront) {
        return getAddingGroup(addToFront).addItem(ingredient, addToFront);
    }

    private BookmarkGroup getAddingGroup(boolean front) {
        if (list.isEmpty()) {
            list.add(new BookmarkGroup(nextId++));
        }
        if (front) {
            BookmarkGroup group = list.get(0);
            if (group.acceptsChanges()) {
                return group;
            } else {
                list.add(0, new BookmarkGroup(nextId++));
                return list.get(0);
            }
        } else {
            BookmarkGroup group = list.get(list.size() - 1);
            if (group.acceptsChanges()) {
                return group;
            }
            list.add(new BookmarkGroup(nextId++));
            return list.get(list.size() - 1);
        }
    }

    @Override
    public List<IIngredientListElement> getIngredientList() {
        return this.list.stream()
                .flatMap(group -> group.getIngredientListElements().stream())
                .collect(Collectors.toList());
    }

    @Override
    public int size() {
        return getIngredientList().size();
    }

    public boolean isEmpty() {
        return getIngredientList().isEmpty();
    }

    @Override
    public void addListener(IIngredientGridSource.Listener listener) {
        listeners.add(listener);
    }

    public void notifyListenersOfChange() {
        for (IIngredientGridSource.Listener listener : listeners) {
            listener.onChange();
        }
    }

    public int nextId() {
        return nextId++;
    }

    @Nullable
    public BookmarkGroupOrganizer getGroupOrganizer() {
        return bookmarkGroupOrganizer;
    }

    public void setGroupOrganizer(BookmarkGroupOrganizer bookmarkGroupOrganizer) {
        this.bookmarkGroupOrganizer = bookmarkGroupOrganizer;
    }

    public int getBookmarkIndex(int id) {
        for (int index = 0; index < list.size(); index++) {
            if (list.get(index).id == id) {
                return index;
            }
        }
        return -1;
    }

    public boolean moveGroup(BookmarkGroup group, boolean up) {
        int groupIndex = getBookmarkIndex(group.id);
        if (up && groupIndex > 0) {
            Collections.swap(list, groupIndex, groupIndex - 1);
            notifyListenersOfChange();
            saveBookmarks();
            return true;
        } else if (!up && groupIndex < list.size() - 1) {
            Collections.swap(list, groupIndex, groupIndex + 1);
            notifyListenersOfChange();
            saveBookmarks();
            return true;
        }
        return false;
    }

    public void swapGroups(int first, int second) {
        int firstIndex = getBookmarkIndex(first);
        int secondIndex = getBookmarkIndex(second);
        Collections.swap(list, firstIndex, secondIndex);
        notifyListenersOfChange();
        saveBookmarks();
    }
}
