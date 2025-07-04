package mezz.jei.bookmarks;

public class DummyBookmarkItem<I> extends BookmarkItem<I> {
    public DummyBookmarkItem(I ingredient, BookmarkGroup group, long amount) {
        super(ingredient);
        this.group = group;
        this.amount = amount;
    }
}
