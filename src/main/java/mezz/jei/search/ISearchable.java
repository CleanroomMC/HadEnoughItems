package mezz.jei.search;

import mezz.jei.config.Config;

import java.util.Set;

public interface ISearchable<T> {

    void getSearchResults(String token, Set<T> results);

    void getAllElements(Set<T> results);

    default Config.SearchMode getMode() {
        return Config.SearchMode.ENABLED;
    }

}
