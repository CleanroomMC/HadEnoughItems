package mezz.jei.runtime;

import javax.annotation.Nullable;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import mezz.jei.api.ingredients.IIngredientSubtypeInterpreter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import mezz.jei.api.ISubtypeRegistry;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.Log;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

public class SubtypeRegistry implements ISubtypeRegistry {
	private final Map<Object, IIngredientSubtypeInterpreter<?>> interpreters = new Reference2ObjectOpenHashMap<>();

	@Override
	public void useNbtForSubtypes(Item... items) {
		for (Item item : items) {
			registerSubtypeInterpreter(item, ItemAllNbt.INSTANCE);
		}
	}

	@Override
	public void useNbtForSubtypes(Fluid... fluids) {
		for (Fluid fluid : fluids) {
			registerSubtypeInterpreter(fluid, FluidAllNbt.INSTANCE);
		}
	}

	@Override
	public void registerNbtInterpreter(Item item, ISubtypeInterpreter interpreter) {
		registerSubtypeInterpreter(item, interpreter);
	}

	@Override
	public void registerSubtypeInterpreter(Item item, ISubtypeInterpreter interpreter) {
		ErrorUtil.checkNotNull(item, "item");
		ErrorUtil.checkNotNull(interpreter, "interpreter");

		if (interpreters.containsKey(item)) {
			Log.get().error("An interpreter is already registered for this item: {}", item, new IllegalArgumentException());
			return;
		}

		interpreters.put(item, interpreter);
	}

	@Override
	public void registerSubtypeInterpreter(Fluid fluid, IFluidSubtypeInterpreter interpreter) {
		ErrorUtil.checkNotNull(fluid, "fluid");
		ErrorUtil.checkNotNull(interpreter, "interpreter");

		if (interpreters.containsKey(fluid)) {
			Log.get().error("An interpreter is already registered for this fluid: {}", fluid, new IllegalArgumentException());
			return;
		}

		interpreters.put(fluid, interpreter);
	}

	@Nullable
	@Override
	public String getSubtypeInfo(ItemStack itemStack) {
		ErrorUtil.checkNotEmpty(itemStack);
		Item item = itemStack.getItem();
		IIngredientSubtypeInterpreter interpreter = interpreters.get(item);
		if (interpreter != null) {
			return interpreter.apply(itemStack);
		}
		return null;
	}

	@Nullable
	@Override
	public String getSubtypeInfo(FluidStack fluidStack) {
		ErrorUtil.checkNotNull(fluidStack, "fluid");
		Fluid fluid = fluidStack.getFluid();
		IIngredientSubtypeInterpreter interpreter = interpreters.get(fluid);
		if (interpreter != null) {
			return interpreter.apply(fluidStack);
		}
		return null;
	}

	@Override
	public boolean hasSubtypeInterpreter(ItemStack itemStack) {
		ErrorUtil.checkNotEmpty(itemStack);
		return interpreters.containsKey(itemStack.getItem());
	}

	@Override
	public boolean hasSubtypeInterpreter(FluidStack fluidStack) {
		ErrorUtil.checkNotNull(fluidStack, "fluidStack");
		return interpreters.containsKey(fluidStack.getFluid());
	}

	private static final class ItemAllNbt implements ISubtypeInterpreter {
		public static final ItemAllNbt INSTANCE = new ItemAllNbt();

		@Override
		public String apply(ItemStack itemStack) {
			NBTTagCompound nbtTagCompound = itemStack.getTagCompound();
			if (nbtTagCompound == null || nbtTagCompound.isEmpty()) {
				return ISubtypeInterpreter.NONE;
			}
			return nbtTagCompound.toString();
		}
	}

	private static final class FluidAllNbt implements IFluidSubtypeInterpreter {
		public static final FluidAllNbt INSTANCE = new FluidAllNbt();

		@Override
		public String apply(FluidStack fluidStack) {
			if (fluidStack.tag == null || fluidStack.tag.isEmpty()) {
				return ISubtypeInterpreter.NONE;
			}
			return fluidStack.tag.toString();
		}
	}
}
