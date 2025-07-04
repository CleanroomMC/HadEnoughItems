package mezz.jei.bookmarks;

import java.util.ArrayList;
import java.util.List;

public class BookmarkGroup {
    protected final List<BookmarkItem<?>> items = new ArrayList<>();
    public int id;
    
    public BookmarkGroup(int id) {
        this.id = id;
    }
    
    public List<BookmarkItem<?>> getItems() {
        return items;
    }
    
    public boolean addItem(BookmarkItem<?> item) {
        items.add(item);
        item.group = this;
        return true;
    }
    
    public void addItem(BookmarkItem<?> item, boolean toFront) {
        if (toFront) {
            items.add(0, item);
        } else {
            items.add(item);
        }
        item.group = this;
    }

    public boolean acceptsChanges() {
        return true;
    }
}
