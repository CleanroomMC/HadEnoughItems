package mezz.jei.startup;

import java.util.Set;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import mezz.jei.util.Log;

public class UniqueItemStackListBuilder {
	private final StackHelper stackHelper;
	private final NonNullList<ItemStack> ingredients = NonNullList.create();
	private final Set<String> ingredientUids = new ObjectOpenHashSet<>();

	public UniqueItemStackListBuilder(StackHelper stackHelper) {
		this.stackHelper = stackHelper;
	}

	public void add(ItemStack itemStack) {
		if (itemStack.isEmpty()) {
			return;
		}
		try {
			String uid = stackHelper.getUniqueIdentifierForStack(itemStack);
			if (!ingredientUids.contains(uid)) {
				ingredientUids.add(uid);
				ingredients.add(itemStack);
			}
		} catch (RuntimeException | LinkageError e) {
			Log.get().error("Failed to get unique identifier for stack.", e);
		}
	}

	public NonNullList<ItemStack> build() {
		return ingredients;
	}
}
