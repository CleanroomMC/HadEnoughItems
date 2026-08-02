package mezz.jei.search;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.collect.SetMultiMap;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Builds a {@link LimitedStringStorage} whose backing storage is itself built from a
 * {@link ISearchStorageBuilder}, so the shared value sets can be baked into an immutable index.
 */
public class LimitedStringStorageBuilder<T> implements ISearchStorageBuilder<T> {

    private final SetMultiMap<String, T> multiMap = new SetMultiMap<>(ReferenceOpenHashSet::new);
    private final ISearchStorageBuilder<Set<T>> backingStorageBuilder;

    public LimitedStringStorageBuilder(Supplier<ISearchStorageBuilder<Set<T>>> backingStorageBuilderFactory) {
        this.backingStorageBuilder = backingStorageBuilderFactory.get();
    }

    @Override
    public void put(String key, T value) {
        boolean isNewKey = !multiMap.containsKey(key);
        multiMap.put(key, value);
        if (isNewKey) {
            Set<T> set = multiMap.get(key);
            backingStorageBuilder.put(key, set);
        }
    }

    @Override
    public ISearchStorage<T> build() {
        return new LimitedStringStorage<>(backingStorageBuilder.build(), multiMap);
    }

}
