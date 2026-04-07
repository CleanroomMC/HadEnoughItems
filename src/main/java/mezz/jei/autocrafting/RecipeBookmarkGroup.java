package mezz.jei.autocrafting;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import mezz.jei.Internal;
import mezz.jei.api.recipe.transfer.IAutocraftingHandler;
import mezz.jei.bookmarks.BookmarkGroup;
import mezz.jei.bookmarks.BookmarkItem;
import mezz.jei.config.Config;
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

    public void addItemInternal(BookmarkItem<?> item) {
        super.addItemInternal(item);
    }

    public boolean addItem(BookmarkItem<?> item) {
        if (canAddItem(item)) {
            addItemInternal(item);
            chain.addOutput((RecipeBookmarkItem<?>) item); // From canAddItem
            return true;
        }
        return false;
    }
    
    public boolean addItem(BookmarkItem<?> item, boolean toFront) {
        if (super.addItem(item, toFront)) {
            chain.addOutput((RecipeBookmarkItem<?>) item); // From canAddItem
            
            return true;
        }

        return false;
    }

    public boolean canAddItem(BookmarkItem<?> item) {
        return item instanceof RecipeBookmarkItem;
    }

    @Override
    public List<BookmarkItem<?>> getItems() {
        List<BookmarkItem<?>> list = new ArrayList<>();
        if (!Config.areRecipeBookmarksEnabled()) {
            return list;
        }
        for (RecipeBookmarkItem<?> item : chain.getDisplayOutputs()) {
            if (item.secondaryTo == null && item.inputs != null && !item.inputs.isEmpty()) {
                list.add(item);
                if (chain.secondaryOutputs.containsKey(item)) {
                    list.addAll(chain.secondaryOutputs.get(item));
                }
                list.addAll(item.getInputs());
            }
        }
        return list;
    }

    public void finishLoading() {
        chain.rebuildGraph();
    }

    public List<IIngredientListElement<?>> getIngredientListElements() {
        return getItems().stream().map(this::getIngredientListElement).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Override
    public boolean acceptsChanges() {
        return false;
    }

    public void update() {
        chain.calculateCrafting();
    }

    @Override
    public void removeItem(BookmarkItem<?> item) {
        super.removeItem(item);
        if (item instanceof RecipeBookmarkItem) {
            chain.removeNode((RecipeBookmarkItem<?>) item);
            // NOTE: there may be a bug here with removing certain intermediate steps.
            // I happened upon a glitch like it once, but ten hours later, I can't reproduce it.
        }
    }

    public void autocraft() {
        IAutocraftingHandler handler = Internal.getRuntime().getAutocraftingHandler();
        if (!handler.isActive()) {
            ((AutocraftingHandler) Internal.getRuntime().getAutocraftingHandler()).start(chain);
        }
    }

    public int getColor() {
        return Config.getRecipeBookmarkGroupColor();
    }

    public List<IIngredientListElement> getMissingIngredients() {
        List<BookmarkItem<?>> missing = new ObjectArrayList<>();
        chain.calculateMissingIngredients(null, missing);
        return missing.stream().map(this::getIngredientListElement).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
