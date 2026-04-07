package mezz.jei.gui.overlay.bookmarks.group;

import mezz.jei.Internal;
import mezz.jei.api.gui.IGhostIngredientHandler;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.config.Config;

import java.awt.*;

public class BookmarkGroupDisplay implements IGhostIngredientHandler.Target {
    Rectangle area;
    BookmarkGroup group;

    public BookmarkGroupDisplay(Rectangle area, BookmarkGroup group) {
        this.area = area;
        this.group = group;
    }

    @Override
    public Rectangle getArea() {
        return area;
    }

    @Override
    public void accept(Object ingredient) {
        accept(ingredient, false); 
    }

    public void accept(Object ingredient, boolean toFront) {
        if (ingredient instanceof BookmarkItem) {
            BookmarkGroup oldGroup = ((BookmarkItem<?>) ingredient).getGroup();
            boolean canAdd = group.addItem((BookmarkItem<?>) ingredient, toFront);
            if (canAdd) {
                if (oldGroup != null) {
                    oldGroup.removeItem((BookmarkItem<?>) ingredient);
                }
                Internal.getBookmarkList().saveBookmarks();
                Internal.getBookmarkList().notifyListenersOfChange();
            }
        } else {
            BookmarkItem<?> item = new BookmarkItem<>(ingredient);
            if (group.addItem(item, toFront)) {
                if (!Config.isBookmarkOverlayEnabled())
                    Config.toggleBookmarkEnabled();
                Internal.getBookmarkList().saveBookmarks();
                Internal.getBookmarkList().notifyListenersOfChange();
            }
        }
    }
}
