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
		return getIngredientHelper(ingredient.getIngredient()).getDisplayName(ingredient.getIngredient());
	}

	@Override
	public String getUniqueId(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getUniqueId(ingredient.getIngredient());
	}

	@Override
	public String getFullUniqueId(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getFullUniqueId(ingredient.getIngredient());
	}

	@Override
	public String getWildcardId(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getWildcardId(ingredient.getIngredient());
	}

	@Override
	public String getModId(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getModId(ingredient.getIngredient());
	}

	@Override
	public String getDisplayModId(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getDisplayModId(ingredient.getIngredient());
	}

	@Override
	public Iterable<Color> getColors(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getColors(ingredient.getIngredient());
	}

	@Override
	public String getResourceId(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getResourceId(ingredient.getIngredient());
	}

	@Override
	public ItemStack getCheatItemStack(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getCheatItemStack(ingredient.getIngredient());
	}

	@Override
	public BookmarkItem copyIngredient(BookmarkItem ingredient) {
		return ingredient.copy();
	}

	@Override
	public boolean isValidIngredient(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).isValidIngredient(ingredient.getIngredient());
	}

	@Override
	public boolean isIngredientOnServer(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).isIngredientOnServer(ingredient.getIngredient());
	}

	@Override
	public Collection<String> getOreDictNames(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getOreDictNames(ingredient.getIngredient());
	}

	@Override
	public Collection<String> getCreativeTabNames(BookmarkItem ingredient) {
		return getIngredientHelper(ingredient.getIngredient()).getCreativeTabNames(ingredient.getIngredient());
	}

	@Override
	public String getErrorInfo(@Nullable BookmarkItem ingredient) {
		if (ingredient == null) {
			return "A bookmark ingredient is itself null!";
		}
		return getIngredientHelper(ingredient.getIngredient()).getErrorInfo(ingredient.getIngredient());
	}

	private static <E> IIngredientHelper<E> getIngredientHelper(E ingredient) {
		return Internal.getIngredientRegistry().getIngredientHelper(ingredient);
	}
}
