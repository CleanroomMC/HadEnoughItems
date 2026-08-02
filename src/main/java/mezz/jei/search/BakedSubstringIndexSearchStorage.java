package mezz.jei.search;

import mezz.jei.search.bakedsubstring.BakedSubstringIndex;

import java.io.PrintWriter;
import java.util.Set;

/**
 * A {@link BakedSubstringIndex} holding everything that was indexed at build time,
 * plus a {@link GeneralizedSuffixTree} holding anything added afterwards.
 * <p>
 * The baked index cannot be mutated, so ingredients that mods register at runtime
 * (long after the initial build) go into the mutable overlay instead of forcing a rebuild.
 * The overlay is expected to stay tiny compared to the baked index.
 */
public class BakedSubstringIndexSearchStorage<T> implements ISearchStorage<T> {

    private final BakedSubstringIndex<T> bakedStorage;
    private final GeneralizedSuffixTree<T> mutableStorage = new GeneralizedSuffixTree<>();

    public BakedSubstringIndexSearchStorage(BakedSubstringIndex<T> bakedStorage) {
        this.bakedStorage = bakedStorage;
    }

    @Override
    public void getSearchResults(String token, Set<T> results) {
        results.addAll(bakedStorage.getSearchResults(token));
        mutableStorage.getSearchResults(token, results);
    }

    @Override
    public void getAllElements(Set<T> results) {
        results.addAll(bakedStorage.getAllElements());
        mutableStorage.getAllElements(results);
    }

    @Override
    public void put(String key, T value) {
        mutableStorage.put(key, value);
    }

    @Override
    public String statistics() {
        return "BakedSubstringIndexSearchStorage: baked=" + bakedStorage +
                ", runtimeStorage=" + mutableStorage.statistics();
    }

    @Override
    public void printTree(PrintWriter out, boolean includeSuffixLinks) {
        mutableStorage.printTree(out, includeSuffixLinks);
    }

}
