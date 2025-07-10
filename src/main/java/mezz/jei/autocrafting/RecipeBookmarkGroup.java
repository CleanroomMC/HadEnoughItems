package mezz.jei.autocrafting;

import mezz.jei.Internal;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.gui.ingredients.IIngredientListElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class RecipeBookmarkGroup extends BookmarkGroup {
    private final RecipeChain chain = new RecipeChain(this);

    public RecipeBookmarkGroup(int id) {
        super(id);
    }

    protected void addItemInternal(BookmarkItem<?> item) {
        super.addItemInternal(item);
        if (item instanceof RecipeBookmarkItem) {
            chain.addOutput((RecipeBookmarkItem<?>) item);
        }
    }

    public boolean canAddItem(BookmarkItem<?> item) {
        return item instanceof RecipeBookmarkItem;
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

    public List<IIngredientListElement<?>> getIngredientListElements() {
        return getItems().stream().map(this::getIngredientListElement).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public boolean acceptsChanges() {
        return false;
    }

    public void update() {
        chain.recheck();
        chain.calculateCrafting();
    }

    @Override
    public void removeItem(BookmarkItem<?> item) {
        if (item instanceof RecipeBookmarkItem) {
            chain.removeNode((RecipeBookmarkItem<?>) item);
        }
        super.removeItem(item);
    }

    public void autocraft() {
        ((AutocraftingHandler) Internal.getRuntime().getAutocraftingHandler()).startAutocrafting(chain);
    }

    public int getColor() {
        return 0x9F00FF00;
    }
}
