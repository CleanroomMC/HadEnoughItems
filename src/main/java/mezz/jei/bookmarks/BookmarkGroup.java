package mezz.jei.bookmarks;

import mezz.jei.Internal;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.autocrafting.RecipeBookmarkItem;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientListElementFactory;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.startup.ForgeModIdHelper;
import mezz.jei.util.Log;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class BookmarkGroup {
    protected final List<BookmarkItem<?>> items = new ArrayList<>();
    private final List<IIngredientListElement<?>> ingredientListElements = new LinkedList<>();
    public int id;
    
    public BookmarkGroup(int id) {
        this.id = id;
    }
    
    public List<BookmarkItem<?>> getItems() {
        return items;
    }

    public List<IIngredientListElement<?>> getIngredientListElements() {
        return ingredientListElements;
    }
    
    public boolean addItem(BookmarkItem<?> item) {
        if (canAddItem(item)) {
            addItemInternal(item);
            return true;
        }
        return false;
    }

    public List<BookmarkItem<?>> getItemsInternal() {
        return items;
    }

    public void addItemInternal(BookmarkItem<?> item) {
        IIngredientListElement<?> element = getIngredientListElement(item);
        if (element == null) {
            Log.get().error("Failed to create ingredient list element for bookmark item: {}", item.ingredient);
            return;
        }
        items.add(item);
        ingredientListElements.add(element);
        item.setGroup(this);
    }

    public boolean canAddItem(BookmarkItem<?> item) {
        return !(item instanceof RecipeBookmarkItem);
    }

    public void removeItem(BookmarkItem<?> item) {
        int index = items.indexOf(item);
        if (index != -1) {
            items.remove(index);
            ingredientListElements.remove(index);
        }
    }
    
    public boolean addItem(BookmarkItem<?> item, boolean toFront) {
        IIngredientListElement<?> element = getIngredientListElement(item);
        if (element == null) {
            return false;
        }
        if (toFront) {
            items.add(0, item);
            ingredientListElements.add(0, element);
        } else {
            items.add(item);
            ingredientListElements.add(element);
        }
        item.setGroup(this);
        return true;
    }

    @Nullable
    protected <T> IIngredientListElement<T> getIngredientListElement(T ingredient) {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        IIngredientType<T> ingredientType = ingredientRegistry.getIngredientType(ingredient);
        return IngredientListElementFactory.createUnorderedElement(ingredientRegistry, ingredientType, ingredient, ForgeModIdHelper.getInstance());
    }

    public boolean acceptsChanges() {
        return true;
    }

    public int getColor() {
        return 0x7FFFFFFF;
    }

    public void finishLoading() {
    }
}
