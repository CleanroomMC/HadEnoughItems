package mezz.jei.bookmarks;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.overlay.bookmarks.BookmarkGroupOrganizer;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.LegacyUtil;
import mezz.jei.util.Log;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class BookmarkList implements IIngredientGridSource {
    private static final String MARKER_OTHER = "O:";
    private static final String MARKER_STACK = "T:";

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
        BookmarkItem<T> normalized = normalize(ingredient);
        if (!contains(normalized)) {
            if (addToLists(normalized, forceFront || Config.isAddingBookmarksToFront())) {
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

    protected <T> BookmarkItem<T> normalize(BookmarkItem<T> ingredient) {
        IIngredientHelper<BookmarkItem<T>> ingredientHelper = ingredientRegistry.getIngredientHelper(ingredient);
        BookmarkItem<T> copy = LegacyUtil.getIngredientCopy(ingredient, ingredientHelper);
        if (copy.ingredient instanceof ItemStack) {
            ((ItemStack) copy.ingredient).setCount(1);
        } else if (copy.ingredient instanceof FluidStack) {
            ((FluidStack) copy.ingredient).amount = 1000;
        }
        return copy;
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
            if (!group.acceptsChanges()) {
                continue;
            }
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
        List<IIngredientListElement> ingredientListElements = getIngredientList();
        for (IIngredientListElement<?> element : ingredientListElements) {
            BookmarkItem item = (BookmarkItem) element.getIngredient();
            if (item.ingredient instanceof ItemStack) {
                strings.add(MARKER_STACK + item.amount + ":" + ((ItemStack) item.ingredient).writeToNBT(new NBTTagCompound()));
            } else {
                IIngredientListElement<?> listElement = item.getSavedElement();
                if (listElement != null) {
                    strings.add(MARKER_OTHER + item.amount + ":" + getUid(listElement));
                }
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
        for (String ingredientJsonString : ingredientJsonStrings) {
            if (ingredientJsonString.startsWith(MARKER_STACK)) {
                ParsedIngredient parsed = parseIngredientString(ingredientJsonString, MARKER_STACK);
                if (parsed != null) {
                    try {
                        NBTTagCompound itemStackAsNbt = JsonToNBT.getTagFromJson(parsed.content);
                        ItemStack itemStack = new ItemStack(itemStackAsNbt);
                        if (!itemStack.isEmpty()) {
                            BookmarkItem<ItemStack> normalized = normalize(new BookmarkItem<>(itemStack));
                            normalized.amount = parsed.amount;
                            addToLists(normalized, false);
                        } else {
                            Log.get().warn("Failed to load bookmarked ItemStack, the item no longer exists:\n{}", parsed.content);
                        }
                    } catch (NBTException e) {
                        Log.get().error("Failed to parse bookmarked ItemStack from JSON:\n{}", parsed.content, e);
                    }
                }
            } else if (ingredientJsonString.startsWith(MARKER_OTHER)) {
                ParsedIngredient parsed = parseIngredientString(ingredientJsonString, MARKER_OTHER);
                if (parsed != null) {
                    Object ingredient = getUnknownIngredientByUid(otherIngredientTypes, parsed.content);
                    if (ingredient != null) {
                        BookmarkItem<?> normalized = normalize(new BookmarkItem<>(ingredient));
                        normalized.amount = parsed.amount;
                        addToLists(normalized, false);
                    }
                }
            } else {
                Log.get().error("Failed to load unknown bookmarked ingredient:\n{}", ingredientJsonString);
            }
        }
        notifyListenersOfChange();
    }

    public BookmarkGroup getBookmarkGroup(int id) {
        for (BookmarkGroup group : list) {
            if (group.id == id) {
                return group;
            }
        }
        return null;
    }

    private static class ParsedIngredient {
        public final long amount;
        public final String content;

        public ParsedIngredient(long amount, String content) {
            this.amount = amount;
            this.content = content;
        }
    }

    @Nullable
    private ParsedIngredient parseIngredientString(String ingredientString, String marker) {
        int colonAfterMarker = ingredientString.indexOf(':', marker.length());
        if (colonAfterMarker < 0) {
            Log.get().error("Bookmark ingredient parsing error: missing amount separator ':' in bookmark string:\n{}", ingredientString);
            return null;
        }
        try {
            String amountPart = ingredientString.substring(marker.length(), colonAfterMarker);
            long amount = Long.parseLong(amountPart);
            if (amount < 0) {
                Log.get().error("Bookmark ingredient parsing error: amount must be non-negative in bookmark string:\n{}", ingredientString);
                return null;
            }
            String content = ingredientString.substring(colonAfterMarker + 1);
            return new ParsedIngredient(amount, content);
        } catch (NumberFormatException e) {
            Log.get().error("Bookmark ingredient parsing error: invalid number format in bookmark string:\n{}", ingredientString, e);
            return null;
        }
    }

    @Nullable
    private Object getUnknownIngredientByUid(Collection<IIngredientType> ingredientTypes, String uid) {
        for (IIngredientType<?> ingredientType : ingredientTypes) {
            Object ingredient = ingredientRegistry.getIngredientByUid(ingredientType, uid);
            if (ingredient != null) {
                return ingredient;
            }
        }
        return null;
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
}
