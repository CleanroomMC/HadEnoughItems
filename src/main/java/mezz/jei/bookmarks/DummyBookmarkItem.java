package mezz.jei.bookmarks;

import net.minecraft.nbt.NBTTagCompound;

import java.util.function.Supplier;

/**
 * The dummy bookmark item is used to represent the inputs of a recipe.
 * These are generated automatically by the recipe bookmark group and are not saved to disk, nor are they editable.
 * This lack of implementation warrants the name "dummy".
 *
 * @param <I> The type of the internal ingredient.
 */
public class DummyBookmarkItem<I> extends BookmarkItem<I> {
    private final Supplier<Long> displayAmountSupplier;
    public DummyBookmarkItem(I ingredient, BookmarkGroup group, Supplier<Long> displayAmountSupplier) {
        super(ingredient);
        this.setGroup(group);
        this.displayAmountSupplier = displayAmountSupplier;
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
