package mezz.jei.search;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.api.search.ISearchIndex;
import mezz.jei.collect.SetMultiMap;

import java.io.PrintWriter;
import java.util.Set;

public class LimitedStringIndex<T> implements IPrintableSearchIndex<T> {
    private final SetMultiMap<String, T> multiMap;
    private final ISearchIndex<Set<T>> backingIndex;

    public LimitedStringIndex(ISearchIndex<Set<T>> backingIndex, SetMultiMap<String, T> multiMap) {
        this.backingIndex = backingIndex;
        this.multiMap = multiMap;
    }

    @Override
    public void getSearchResults(String token, Set<T> results) {
        Set<Set<T>> intermediateResults = new ReferenceOpenHashSet<>();
        backingIndex.getSearchResults(token, intermediateResults);
        for (Set<T> set : intermediateResults) {
            results.addAll(set);
        }
    }

    @Override
    public void getAllElements(Set<T> results) {
        multiMap.valueStream().forEach(results::add);
    }

    @Override
    public void put(String key, T value) {
        boolean isNewKey = !multiMap.containsKey(key);
        multiMap.put(key, value);
        if (isNewKey) {
            backingIndex.put(key, multiMap.get(key));
        }
    }

    @Override
    public String statistics() {
        return "LimitedStringIndex: " + backingIndex.statistics();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void printTree(PrintWriter out, boolean includeSuffixLinks) {
        if (backingIndex instanceof IPrintableSearchIndex) {
            IPrintableSearchIndex<Set<T>> printableIndex = (IPrintableSearchIndex<Set<T>>) backingIndex;
            printableIndex.printTree(out, includeSuffixLinks);
        }
    }
}
