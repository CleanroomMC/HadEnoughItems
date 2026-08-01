package mezz.jei.search;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.collect.SetMultiMap;

import java.io.PrintWriter;
import java.util.Set;

/**
 * This is more memory-efficient than storing each value directly in an {@link ISearchStorage}
 * when there are many values for each key.
 *
 * It stores a map of keys to a set of values.
 * The set values are shared with the backing {@link ISearchStorage} to index and find them.
 * The sets values are modified directly when values with the same key are added.
 */
public class LimitedStringStorage<T> implements ISearchStorage<T> {

    private final SetMultiMap<String, T> multiMap;
    private final ISearchStorage<Set<T>> backingStorage;

    public LimitedStringStorage(ISearchStorage<Set<T>> backingStorage) {
        this(backingStorage, new SetMultiMap<>(ReferenceOpenHashSet::new));
    }

    public LimitedStringStorage(ISearchStorage<Set<T>> backingStorage, SetMultiMap<String, T> multiMap) {
        this.backingStorage = backingStorage;
        this.multiMap = multiMap;
    }

    @Override
    public void getSearchResults(String token, Set<T> results) {
        Set<Set<T>> intermediateResults = new ReferenceOpenHashSet<>();
        backingStorage.getSearchResults(token, intermediateResults);
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
            Set<T> set = multiMap.get(key);
            backingStorage.put(key, set);
        }
    }

    @Override
    public String statistics() {
        return "LimitedStringStorage: " + backingStorage.statistics();
    }

    @Override
    public void printTree(PrintWriter out, boolean includeSuffixLinks) {
        backingStorage.printTree(out, includeSuffixLinks);
    }

}
