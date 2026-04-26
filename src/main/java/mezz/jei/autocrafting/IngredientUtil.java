package mezz.jei.autocrafting;

import mezz.jei.Internal;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.ingredients.IngredientRegistry;
import mezz.jei.util.LegacyUtil;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class IngredientUtil {
    public static int getCount(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            return ((ItemStack) ingredient).getCount();
        } else if (ingredient instanceof FluidStack) {
            return ((FluidStack) ingredient).amount;
        }
        return 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes", "ConstantValue"})
    public static <A, B> boolean equals(A o1, B o2) {
        // Does not account for the size of the ingredients, which is actually fine if, as usual,
        // we assume that aliasable ingredients are based on OreDictionary.
        IngredientRegistry ingredientRegistry = Internal.getIngredientRegistry();
        IIngredientType<A> type1 = ingredientRegistry.getIngredientType(o1);
        IIngredientType<B> type2 = ingredientRegistry.getIngredientType(o2);
        if (type1 == null || type2 == null || type1 != type2) {
            return false;
        }
        IIngredientHelper helper = ingredientRegistry.getIngredientHelper(type1);
        return helper.getUniqueId(o1).equals(helper.getUniqueId(o2));
    }

    public static <A, B> boolean aliasesContains(List<A> l1, B o2) {
        for (A a : l1) {
            if (equals(a, o2)) {
                return true;
            }
        }
        return false;
    }

    public static <A, B> boolean aliasesEquals(List<A> l1, List<B> l2) {
        // I do not care enough to allow for order permutations. Really, no mod should do that.
        if (l1.size() != l2.size()) {
            return false;
        }
        for (int i = 0; i < l1.size(); i++) {
            if (!equals(l1.get(i), l2.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static <T> BookmarkItem<T> normalizeBookmark(BookmarkItem<T> ingredient) {
        IIngredientHelper<BookmarkItem<T>> ingredientHelper = Internal.getIngredientRegistry().getIngredientHelper(ingredient);
        BookmarkItem<T> copy = LegacyUtil.getIngredientCopy(ingredient, ingredientHelper);
        copy.ingredient = normalizeCopy(copy.ingredient);
        return copy;
    }

    public static <T> void normalize(T ingredient) {
        if (ingredient instanceof ItemStack) {
            ((ItemStack) ingredient).setCount(1);
        } else if (ingredient instanceof FluidStack) {
            ((FluidStack) ingredient).amount = 1000;
        }
    }

    public static <T> T normalizeCopy(T orig) {
        T ingredient = LegacyUtil.getIngredientCopy(orig, Internal.getIngredientRegistry().getIngredientHelper(orig));
        if (ingredient instanceof ItemStack) {
            ((ItemStack) ingredient).setCount(1);
        } else if (ingredient instanceof FluidStack) {
            ((FluidStack) ingredient).amount = 1000;
        }
        return ingredient;
    }
}
