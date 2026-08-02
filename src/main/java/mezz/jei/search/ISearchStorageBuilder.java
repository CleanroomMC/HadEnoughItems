package mezz.jei.search;

/**
 * Collects key/value pairs and bakes them into an {@link ISearchStorage}.
 * <p>
 * A storage that is mutable at runtime can implement this itself and return {@code this} from
 * {@link #build()}, forwarding puts straight to its own storage.
 */
public interface ISearchStorageBuilder<T> {

    void put(String key, T value);

    ISearchStorage<T> build();

}
