package mezz.jei.autocrafting;

import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;

import java.util.ArrayList;
import java.util.List;

public class RecipeBookmarkGroup extends BookmarkGroup {
    private final RecipeChain chain = new RecipeChain();

    public RecipeBookmarkGroup(int id) {
        super(id);
    }

    public boolean addItem(BookmarkItem<?> item) {
        if (item instanceof RecipeBookmarkItem) {
            RecipeBookmarkItem<?> recipeBookmarkItem = (RecipeBookmarkItem<?>) item;
            chain.addOutput(recipeBookmarkItem);
            super.addItem(item);
        }
        return false;
    }

    @Override
    public List<BookmarkItem<?>> getItems() {
        List<BookmarkItem<?>> list = new ArrayList<>();
        for (RecipeBookmarkItem<?> item : chain.getDisplayOutputs()) {
            if (item.secondaryTo == null && item.inputs != null) {
                list.add(item);
                if (chain.secondaryOutputs.containsKey(item)) {
                    list.addAll(chain.secondaryOutputs.get(item));
                }
                list.addAll(item.getInputs());
            }
        }
        return list;
    }

    @Override
    public boolean acceptsChanges() {
        return false;
    }

    public void update() {
        chain.recheck();
        chain.calculateCrafting();
    }
}
