package mezz.jei.runtime;

import mezz.jei.api.IAdvancedSearchRegistry;
import mezz.jei.api.search.ISearchIndexBuilder;
import mezz.jei.api.search.ISearchIndexBuilderFactory;
import mezz.jei.api.search.ISearchIndexFactory;
import mezz.jei.search.SearchIndexBuilder;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.Log;

import javax.annotation.Nullable;

public class AdvancedSearchRegistry implements IAdvancedSearchRegistry {

	private final ISearchIndexBuilderFactory defaultIndexBuilderFactory;

	@Nullable
	private ISearchIndexBuilderFactory indexBuilderFactoryOverride;

	public AdvancedSearchRegistry(ISearchIndexBuilderFactory defaultIndexBuilderFactory) {
		this.defaultIndexBuilderFactory = ErrorUtil.checkNotNull(defaultIndexBuilderFactory, "defaultIndexBuilderFactory");
	}

	@Override
	public void replaceIndex(ISearchIndexFactory searchIndexFactory) {
		ErrorUtil.checkNotNull(searchIndexFactory, "searchIndexFactory");

		Log.get().info("Replaced search index factory: {}", searchIndexFactory);
		this.indexBuilderFactoryOverride = new ISearchIndexBuilderFactory() {
			@Override
			public <T> ISearchIndexBuilder<T> create() {
				return new SearchIndexBuilder<>(searchIndexFactory.createSearchIndex());
			}
		};
	}

	@Override
	public void replaceIndexBuilder(ISearchIndexBuilderFactory searchIndexBuilderFactory) {
		ErrorUtil.checkNotNull(searchIndexBuilderFactory, "searchIndexBuilderFactory");

		Log.get().info("Replaced search index builder factory: {}", searchIndexBuilderFactory);
		this.indexBuilderFactoryOverride = searchIndexBuilderFactory;
	}

	public ISearchIndexBuilderFactory getSearchIndexBuilderFactory() {
		return indexBuilderFactoryOverride == null ? defaultIndexBuilderFactory : indexBuilderFactoryOverride;
	}
}
