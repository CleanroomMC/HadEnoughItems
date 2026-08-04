package mezz.jei.api;

import mezz.jei.api.search.ISearchIndex;
import mezz.jei.api.search.ISearchIndexBuilder;
import mezz.jei.api.search.ISearchIndexBuilderFactory;
import mezz.jei.api.search.ISearchIndexFactory;

/**
 * The advanced search registry is passed to mod plugins in
 * {@link IModPlugin#registerAdvancedSearch(IAdvancedSearchRegistry)}.
 *
 * @since HEI 4.33.0
 */
public interface IAdvancedSearchRegistry {

	/**
	 * Replace HEI's default ingredient search index with a custom live index implementation.
	 *
	 * <p>
	 * The factory must create a new empty index each time it is called. HEI uses the replacement for every indexed
	 * ingredient field, including backing indices used by the limited-string index. The index receives both the
	 * initial ingredient data and ingredients added at runtime through
	 * {@link ISearchIndex#put(String, Object)}.
	 * If multiple plugins replace the index, the last replacement is used.
	 * </p>
	 *
	 * @since HEI 4.33.0
	 */
	void replaceIndex(ISearchIndexFactory searchIndexFactory);

	/**
	 * Replace HEI's default ingredient search index with a custom builder implementation.
	 *
	 * <p>
	 * This is an advanced hook for implementations that need different indexing or matching behavior than HEI's
	 * default substring search. For example, a custom index can support language-aware matching where users type
	 * phonetic, transliterated, or otherwise normalized search terms that should match localized ingredient names.
	 * </p>
	 *
	 * <p>
	 * The factory must create a new empty builder each time it is called. HEI uses the replacement for every indexed
	 * ingredient field, including backing indices used by the limited-string index. Builders are single-use: HEI adds
	 * the initial data, calls {@link ISearchIndexBuilder#build()}, and then uses the returned index for searches and
	 * runtime additions. If multiple plugins replace the index, the last replacement is used.
	 * </p>
	 *
	 * @since HEI 4.33.0
	 */
	void replaceIndexBuilder(ISearchIndexBuilderFactory searchIndexBuilderFactory);
}
