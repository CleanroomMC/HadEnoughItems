package mezz.jei.api.search;

/**
 * Creates live indices for HEI's ingredient search.
 *
 * @since HEI 4.33.0
 */
@FunctionalInterface
public interface ISearchIndexFactory {

	/**
	 * Create a new empty search index.
	 *
	 * @param <T> the type of values stored in the search index
	 * @since HEI 4.33.0
	 */
	<T> ISearchIndex<T> createSearchIndex();
}
