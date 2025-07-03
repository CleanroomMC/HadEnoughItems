package mezz.jei.autocrafting;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class IngredientUtil {
    public static int getCount(Object ingredient) {
        if (ingredient instanceof ItemStack) {
            return ((ItemStack) ingredient).getCount();
        } else if (ingredient instanceof FluidStack) {
            return ((FluidStack) ingredient).amount;
        }
        return 0;
    }
}
