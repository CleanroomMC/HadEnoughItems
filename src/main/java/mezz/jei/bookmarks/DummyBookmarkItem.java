package mezz.jei.bookmarks;

import mezz.jei.autocrafting.IngredientUtil;
import net.minecraft.nbt.NBTTagCompound;

import java.util.function.Supplier;

public class DummyBookmarkItem<I> extends BookmarkItem<I> {
    private final Supplier<Long> displayAmountSupplier;
    public DummyBookmarkItem(I ingredient, BookmarkGroup group, Supplier<Long> displayAmountSupplier) {
        super(ingredient);
        this.setGroup(group);
        this.displayAmountSupplier = displayAmountSupplier;
        IngredientUtil.normalizeCopy(this.ingredient);
    }

    @Override
    public int getGroupIndex() {
        return super.getGroupIndex();
    }

    @Override
    public void changeAmount(long delta) {
        // It would be rather weird to change the amount of a dummy item in a recipe...
    }

    @Override
    public long getDisplayAmount() {
        return displayAmountSupplier.get();
    }

    @Override
    public String serialize() {
        return null;
    }

    @Override
    public boolean deserialize(NBTTagCompound ingredientJsonString) {
        return false;
    }
}
