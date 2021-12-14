package mezz.jei.suffixtree.search;

import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.config.Config.SearchMode;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.suffixtree.ISearchable;
import mezz.jei.suffixtree.PrefixInfo;

import java.util.Collection;

public class PrefixedSearchable<T extends ISearchable> implements ISearchable {

    private final T searchable;
    private final PrefixInfo prefixInfo;

    public PrefixedSearchable(T searchable, PrefixInfo prefixInfo) {
        this.searchable = searchable;
        this.prefixInfo = prefixInfo;
    }

    public T getSearchable() {
        return searchable;
    }

    public Collection<String> getStrings(IIngredientListElement<?> element) {
        return prefixInfo.getStrings(element);
    }

    @Override
    public SearchMode getMode() {
        return prefixInfo.getMode();
    }

    @Override
    public IntSet search(String word) {
        return searchable.search(word);
    }

}
