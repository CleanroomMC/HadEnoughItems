package mezz.jei.api.search;

import java.util.Set;

/**
 * An index used by HEI's ingredient search.
 *
 * @param <T> the type of values stored in the search index
 * @since HEI 4.33.0
 */
public interface ISearchIndex<T> {

	/**
	 * Add all values matching the token to the results.
	 *
	 * @param token the token to search for
	 * @param results the set to receive matching results
	 * @since HEI 4.33.0
	 */
	void getSearchResults(String token, Set<T> results);

	/**
	 * Add every indexed value to the results.
	 *
	 * @param results the set to receive all indexed values
	 * @since HEI 4.33.0
	 */
	void getAllElements(Set<T> results);

	/**
	 * Add a searchable key and value to the index.
	 *
	 * @param key the searchable string
	 * @param value the indexed value
	 * @since HEI 4.33.0
	 */
	void put(String key, T value);

	/**
	 * Get implementation-specific statistics for logging.
	 *
	 * @since HEI 4.33.0
	 */
	String statistics();
}
