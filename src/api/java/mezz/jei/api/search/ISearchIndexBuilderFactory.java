package mezz.jei.api.search;

import java.util.Objects;

/**
 * Creates search index builders for HEI's ingredient search.
 *
 * @since HEI 4.33.0
 */
public interface ISearchIndexBuilderFactory {

	/**
	 * Create a new empty search index builder.
	 *
	 * @param <T> the type of values stored in the search index
	 * @since HEI 4.33.0
	 */
	<T> ISearchIndexBuilder<T> create();

	/**
	 * Create a new empty builder with a stable id for the logical search index.
	 *
	 * <p>
	 * The default implementation delegates to {@link #create()} for implementations that do not need the id.
	 * Example ids include {@code default}, {@code tooltip}, and {@code mod_name}.
	 * </p>
	 *
	 * @param id stable id for the logical search index
	 * @param <T> the type of values stored in the search index
	 * @since HEI 4.33.0
	 */
	default <T> ISearchIndexBuilder<T> create(String id) {
		Objects.requireNonNull(id, "id");
		return create();
	}
}
