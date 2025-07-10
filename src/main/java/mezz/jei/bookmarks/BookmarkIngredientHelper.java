package mezz.jei.bookmarks;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.Collection;

@SuppressWarnings("rawtypes")
public class BookmarkIngredientHelper implements IIngredientHelper<BookmarkItem> {
    @Nullable
    @Override
    public BookmarkItem getMatch(Iterable<BookmarkItem> ingredients, BookmarkItem ingredientToMatch) {
        return null;
    }

    @Override
    public String getDisplayName(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getDisplayName(ingredient.ingredient);
    }

    @Override
    public String getUniqueId(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getUniqueId(ingredient.ingredient);
    }

    @Override
    public String getWildcardId(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getWildcardId(ingredient.ingredient);
    }

    @Override
    public String getModId(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getModId(ingredient.ingredient);
    }

    @Override
    public String getDisplayModId(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getDisplayModId(ingredient.ingredient);
    }

    @Override
    public Iterable<Color> getColors(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getColors(ingredient.ingredient);
    }

    @Override
    public String getResourceId(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getResourceId(ingredient.ingredient);
    }

    @Override
    public ItemStack getCheatItemStack(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getCheatItemStack(ingredient.ingredient);
    }

    @Override
    public BookmarkItem copyIngredient(BookmarkItem ingredient) {
        return ingredient.copy();
    }

    @Override
    public boolean isValidIngredient(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).isValidIngredient(ingredient.ingredient);
    }

    @Override
    public boolean isIngredientOnServer(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).isIngredientOnServer(ingredient.ingredient);
    }

    @Override
    public Collection<String> getOreDictNames(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getOreDictNames(ingredient.ingredient);
    }

    @Override
    public Collection<String> getCreativeTabNames(BookmarkItem ingredient) {
        return getIngredientHelper(ingredient.ingredient).getCreativeTabNames(ingredient.ingredient);
    }

    @Override
    public String getErrorInfo(@Nullable BookmarkItem ingredient) {
        if (ingredient == null) {
            return "null";
        }
        return getIngredientHelper(ingredient.ingredient).getErrorInfo(ingredient.ingredient);
    }

    private static <E> IIngredientHelper<E> getIngredientHelper(E ingredient) {
        return Internal.getIngredientRegistry().getIngredientHelper(ingredient);
    }
}
