package mezz.jei.search;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import mezz.jei.config.Config.SearchMode;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientFilterBackgroundBuilder;
import net.minecraft.util.NonNullList;

import javax.annotation.Nullable;
import java.util.*;

public class MemoryHogElementSearch implements IElementSearch {

    private final GeneralizedSuffixTree noPrefixSearchable;
    private final Map<PrefixInfo, PrefixedSearchable<GeneralizedSuffixTree>> prefixedSearchables = new Reference2ObjectOpenHashMap<>();
    private final IngredientFilterBackgroundBuilder backgroundBuilder;
    private final CombinedSearchables combinedSearchables = new CombinedSearchables();
    /**
     * indexed list of ingredients for use with the suffix trees
     * includes all elements (even hidden ones) for use when rebuilding
     */
    private final NonNullList<IIngredientListElement<?>> elementInfoList;

    public MemoryHogElementSearch() {
        this.elementInfoList = NonNullList.create();
        this.noPrefixSearchable = new GeneralizedSuffixTree();
        this.backgroundBuilder = new IngredientFilterBackgroundBuilder(prefixedSearchables, elementInfoList);
        this.combinedSearchables.addSearchable(noPrefixSearchable);
    }

    @Override
    public void start() {
        this.backgroundBuilder.start();
    }

    @Nullable
    @Override
    public IntSet getSearchResults(String token, PrefixInfo prefixInfo) {
        if (token.isEmpty()) {
            return null;
        }
        final ISearchable searchable = this.prefixedSearchables.get(prefixInfo);
        if (searchable != null && searchable.getMode() != SearchMode.DISABLED) {
            return searchable.search(token);
        } else {
            return combinedSearchables.search(token);
        }
    }

    @Override
    public <V> void add(IIngredientListElement<V> info) {
        int index = this.elementInfoList.size();
        this.elementInfoList.add(info);

        for (String string : PrefixInfo.NO_PREFIX.getStrings(info)) {
            this.noPrefixSearchable.put(string, index);
        }

        for (PrefixedSearchable<GeneralizedSuffixTree> prefixedSearchable : this.prefixedSearchables.values()) {
            SearchMode searchMode = prefixedSearchable.getMode();
            if (searchMode != SearchMode.DISABLED) {
                GeneralizedSuffixTree searchable = prefixedSearchable.getSearchable();
                for (String string : prefixedSearchable.getStrings(info)) {
                    searchable.put(string, index);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> IIngredientListElement<V> get(int index) {
        return (IIngredientListElement<V>) this.elementInfoList.get(index);
    }

    @Override
    public <V> int indexOf(IIngredientListElement<V> ingredient) {
        return this.elementInfoList.indexOf(ingredient);
    }

    @Override
    public int size() {
        return this.elementInfoList.size();
    }

    @Override
    public List<IIngredientListElement<?>> getAllIngredients() {
        return Collections.unmodifiableList(this.elementInfoList);
    }

    @Override
    public void registerPrefix(PrefixInfo prefixInfo) {
        final GeneralizedSuffixTree searchable = new GeneralizedSuffixTree();
        final PrefixedSearchable<GeneralizedSuffixTree> prefixedSearchable = new PrefixedSearchable<>(searchable, prefixInfo);
        this.prefixedSearchables.put(prefixInfo, prefixedSearchable);
        this.combinedSearchables.addSearchable(prefixedSearchable);
    }
}
