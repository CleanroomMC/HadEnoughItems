package mezz.jei.autocrafting;

import mezz.jei.Internal;
import mezz.jei.api.recipe.IIngredientType;
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

    public static <A, B> boolean equals(A o1, B o2) {
        // Does not account for the size of the ingredients, which is actually fine if, as usual,
        // we assume that aliasable ingredients are based on OreDictionary.
        IIngredientType<A> type1 = Internal.getIngredientRegistry().getIngredientType(o1);
        IIngredientType<B> type2 = Internal.getIngredientRegistry().getIngredientType(o2);
        if (type1 == null || type2 == null || type1 != type2) {
            return false;
        }
        return Internal.getIngredientRegistry().getIngredientHelper(type1).getUniqueId(o1).equals(
            Internal.getIngredientRegistry().getIngredientHelper(type2).getUniqueId(o2));
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
}
