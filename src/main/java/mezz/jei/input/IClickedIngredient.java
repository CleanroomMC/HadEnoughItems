package mezz.jei.input;

import javax.annotation.Nullable;
import java.awt.Rectangle;

import net.minecraft.item.ItemStack;

public interface IClickedIngredient<V> {

	V getValue();

	@Nullable
	Rectangle getArea();

	ItemStack getCheatItemStack();

	default ItemStack replaceWithCheatItemStack(ItemStack clickedWithStack) {
		return ItemStack.EMPTY;
	}

	void onClickHandled();

	void setOnClickHandler(IOnClickHandler onClickHandler);

	@FunctionalInterface
	interface IOnClickHandler {
		void onClick();
	}
}
