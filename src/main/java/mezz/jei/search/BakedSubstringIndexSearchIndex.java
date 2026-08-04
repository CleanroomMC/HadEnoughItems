package mezz.jei.search;

import mezz.jei.search.bakedsubstring.BakedSubstringIndex;

import java.io.PrintWriter;
import java.util.Set;

public class BakedSubstringIndexSearchIndex<T> implements IPrintableSearchIndex<T> {

    private final BakedSubstringIndex<T> bakedIndex;
    private final GeneralizedSuffixTree<T> runtimeIndex = new GeneralizedSuffixTree<>();

    public BakedSubstringIndexSearchIndex(BakedSubstringIndex<T> bakedIndex) {
        this.bakedIndex = bakedIndex;
    }

    @Override
    public void getSearchResults(String token, Set<T> results) {
        results.addAll(bakedIndex.getSearchResults(token));
        runtimeIndex.getSearchResults(token, results);
    }

    @Override
    public void getAllElements(Set<T> results) {
        results.addAll(bakedIndex.getAllElements());
        runtimeIndex.getAllElements(results);
    }

    @Override
    public void put(String key, T value) {
        runtimeIndex.put(key, value);
    }

    @Override
    public String statistics() {
        return "BakedSubstringIndexSearchIndex: baked=" + bakedIndex +
                ", runtimeIndex=" + runtimeIndex.statistics();
    }

    @Override
    public void printTree(PrintWriter out, boolean includeSuffixLinks) {
        runtimeIndex.printTree(out, includeSuffixLinks);
    }
}
