package mezz.jei.bookmarks;

import mezz.jei.Internal;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientListElementFactory;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.startup.ForgeModIdHelper;

import javax.annotation.Nullable;

public class BookmarkItem<I> {
    @SuppressWarnings("rawtypes")
    public static final IIngredientType<BookmarkItem> TYPE = () -> BookmarkItem.class;

    public I ingredient;
    public long amount = 0L;
    @Nullable
    public BookmarkGroup group;

    public BookmarkItem(I ingredient) {
        this.ingredient = ingredient;
    }

    public BookmarkItem<I> copy() {
        return new BookmarkItem<>(ingredient);
    }

    @Nullable
    public IIngredientListElement<I> getSavedElement() {
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        IIngredientType<I> ingredientType = ingredientRegistry.getIngredientType(ingredient);
        return IngredientListElementFactory.createUnorderedElement(
            ingredientRegistry,
            ingredientType,
            ingredient,
            ForgeModIdHelper.getInstance());
    }

    public int getGroupIndex() {
        return group == null ? 0 : group.id;
    }

    public void changeAmount(long delta) {
        this.amount = Math.max(0L, this.amount + delta);
    }

    public boolean startsNewRow() {
        return false;
    }

    public long getDisplayAmount() {
        return amount;
    }
}
