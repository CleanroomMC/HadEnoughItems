package mezz.jei.bookmarks;

import java.util.function.Supplier;

public class DummyBookmarkItem<I> extends BookmarkItem<I> {
    private Supplier<Long> displayAmountSupplier;
    public DummyBookmarkItem(I ingredient, BookmarkGroup group, Supplier<Long> displayAmountSupplier) {
        super(ingredient);
        this.group = group;
        this.displayAmountSupplier = displayAmountSupplier;
    }

    @Override
    public int getGroupIndex() {
        return super.getGroupIndex();
    }

    @Override
    public void changeAmount(long delta) {

    }

    @Override
    public long getDisplayAmount() {
        return displayAmountSupplier.get();
    }
}
