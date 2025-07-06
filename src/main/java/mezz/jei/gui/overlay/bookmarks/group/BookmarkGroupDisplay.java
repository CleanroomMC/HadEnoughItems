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

    public BookmarkGroupDisplay(Rectangle area, int groupId) {
        this.area = area;
        this.group = Internal.getBookmarkList().getBookmarkGroup(groupId);
    }

    @Override
    public Rectangle getArea() {
        return area;
    }

    @Override
    public void accept(Object ingredient) {
        if (ingredient instanceof BookmarkItem) {
            BookmarkGroup oldGroup = ((BookmarkItem<?>) ingredient).group;
            boolean canAdd = group.addItem((BookmarkItem<?>) ingredient);
            if (canAdd) {
                if (oldGroup != null) {
                    oldGroup.removeItem((BookmarkItem<?>) ingredient);
                }
                Internal.getBookmarkList().saveBookmarks();
                Internal.getBookmarkList().notifyListenersOfChange();
            }
        } else {
            BookmarkItem<?> item = new BookmarkItem<>(ingredient);
            if (group.addItem(item)) {
                if (!Config.isBookmarkOverlayEnabled())
                    Config.toggleBookmarkEnabled();
                Internal.getBookmarkList().saveBookmarks();
                Internal.getBookmarkList().notifyListenersOfChange();
            }
        }
    }
}
