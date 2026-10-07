package mezz.jei.search;

import mezz.jei.api.search.ISearchIndex;
import mezz.jei.api.search.ISearchIndexBuilder;
import mezz.jei.search.bakedsubstring.BakedSubstringIndex;

public class BakedSubstringIndexBuilder<T> implements ISearchIndexBuilder<T> {

    private final BakedSubstringIndex.Builder<T> builder = BakedSubstringIndex.builder();

    @Override
    public void put(String key, T value) {
        builder.put(key, value);
    }

    @Override
    public ISearchIndex<T> build() {
        return new BakedSubstringIndexSearchIndex<>(builder.build());
    }

}
