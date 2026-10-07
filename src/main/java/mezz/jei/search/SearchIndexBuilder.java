package mezz.jei.search;

import mezz.jei.api.search.ISearchIndex;
import mezz.jei.api.search.ISearchIndexBuilder;

/**
 * Adapts a live search index to the builder lifecycle.
 */
public class SearchIndexBuilder<T> implements ISearchIndexBuilder<T> {

	private final ISearchIndex<T> searchIndex;

	public SearchIndexBuilder(ISearchIndex<T> searchIndex) {
		this.searchIndex = searchIndex;
	}

	@Override
	public void put(String key, T value) {
		searchIndex.put(key, value);
	}

	@Override
	public ISearchIndex<T> build() {
		return searchIndex;
	}
}
