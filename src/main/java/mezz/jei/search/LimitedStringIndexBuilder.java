package mezz.jei.search;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.api.search.ISearchIndex;
import mezz.jei.api.search.ISearchIndexBuilder;
import mezz.jei.api.search.ISearchIndexBuilderFactory;
import mezz.jei.collect.SetMultiMap;

import java.util.Set;

public class LimitedStringIndexBuilder<T> implements ISearchIndexBuilder<T> {
    private final SetMultiMap<String, T> multiMap = new SetMultiMap<>(ReferenceOpenHashSet::new);
    private final ISearchIndexBuilder<Set<T>> backingIndexBuilder;

    public LimitedStringIndexBuilder(ISearchIndexBuilderFactory factory, String id) {
        this.backingIndexBuilder = factory.create(id);
    }

    @Override
    public void put(String key, T value) {
        boolean isNewKey = !multiMap.containsKey(key);
        multiMap.put(key, value);
        if (isNewKey) {
            backingIndexBuilder.put(key, multiMap.get(key));
        }
    }

    @Override
    public ISearchIndex<T> build() {
        return new LimitedStringIndex<>(backingIndexBuilder.build(), multiMap);
    }
}
