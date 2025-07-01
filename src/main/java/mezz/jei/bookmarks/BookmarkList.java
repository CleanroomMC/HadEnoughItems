package mezz.jei.bookmarks;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.ingredients.IngredientListElementFactory;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.startup.ForgeModIdHelper;
import mezz.jei.util.LegacyUtil;
import mezz.jei.util.Log;
import org.apache.commons.io.IOUtils;

@SuppressWarnings("rawtypes")
public class BookmarkList implements IIngredientGridSource {
	private static final String MARKER_OTHER = "O:";
	private static final String MARKER_STACK = "T:";

	private final List<Object> list = new LinkedList<>();
	private final List<IIngredientListElement> ingredientListElements = new LinkedList<>();
	private final IngredientRegistry ingredientRegistry;
	private final List<IIngredientGridSource.Listener> listeners = new ArrayList<>();

	public BookmarkList(IngredientRegistry ingredientRegistry) {
		this.ingredientRegistry = ingredientRegistry;
	}

	public <T> boolean add(BookmarkItem<T> ingredient) {
		return add(ingredient, false);
	}

	public <T> boolean add(BookmarkItem<T> ingredient, boolean forceFront) {
		Object normalized = normalize(ingredient);
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
		for (Object existing : list) {
			if (ingredient == existing) {
				return true;
			}
			if (existing != null && existing.getClass() == ingredient.getClass()) {
				if (ingredientHelper.getUniqueId(existing).equals(ingredientHelper.getUniqueId(ingredient))) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean remove(Object ingredient) {
		return remove(ingredient, false);
	}

	public boolean remove(Object ingredient, boolean looseEqualCheck) {
		int index = 0;
		for (Object existing : list) {
			if (looseEqualCheck) {
				String id1 = ingredientRegistry.getIngredientHelper(ingredient).getUniqueId(ingredient);
				String id2 = ingredientRegistry.getIngredientHelper(existing).getUniqueId(existing);
				if (id1.equals(id2)) {
					list.remove(index);
					ingredientListElements.remove(index);
					notifyListenersOfChange();
					saveBookmarks();
					return true;
				}
			}
			if (ingredient == existing) {
				list.remove(index);
				ingredientListElements.remove(index);
				notifyListenersOfChange();
				saveBookmarks();
				return true;
			}
			index++;
		}
		return false;
	}

	public void saveBookmarks() {
		List<String> strings = new ArrayList<>();
		for (IIngredientListElement<?> element : ingredientListElements) {
			BookmarkItem item = (BookmarkItem) element.getIngredient();
			if (item.ingredient instanceof ItemStack) {
				strings.add(MARKER_STACK + item.amount + ":" + ((ItemStack) item.ingredient).writeToNBT(new NBTTagCompound()));
			} else {
				IIngredientListElement<?> listElement = item.getListElement();
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
		ingredientListElements.clear();
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

	private <T> boolean addToLists(T ingredient, boolean addToFront) {
		IIngredientType<T> ingredientType = ingredientRegistry.getIngredientType(ingredient);
		IIngredientListElement<T> element = IngredientListElementFactory.createUnorderedElement(ingredientRegistry, ingredientType, ingredient, ForgeModIdHelper.getInstance());
		if (element != null) {
			if (addToFront) {
				list.add(0, ingredient);
				ingredientListElements.add(0, element);
			} else {
				list.add(ingredient);
				ingredientListElements.add(element);
			}
			return true;
		}
		return false;
	}

	@Override
	public List<IIngredientListElement> getIngredientList() {
		return ingredientListElements;
	}

	@Override
	public int size() {
		return ingredientListElements.size();
	}

	public boolean isEmpty() {
		return ingredientListElements.isEmpty();
	}

	@Override
	public void addListener(IIngredientGridSource.Listener listener) {
		listeners.add(listener);
	}

	private void notifyListenersOfChange() {
		for (IIngredientGridSource.Listener listener : listeners) {
			listener.onChange();
		}
	}
}
