package mezz.jei.search;

import mezz.jei.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CombinedSearchables<T> implements ISearchable<T> {

    private final List<ISearchable<T>> searchables = new ArrayList<>();

    @Override
    public void getSearchResults(String word, Set<T> results) {
        for (ISearchable<T> searchable : this.searchables) {
            if (searchable.getMode() == Config.SearchMode.ENABLED) {
                searchable.getSearchResults(word, results);
            }
        }
    }

    @Override
    public void getAllElements(Set<T> results) {
        for (ISearchable<T> searchable : this.searchables) {
            if (searchable.getMode() == Config.SearchMode.ENABLED) {
                searchable.getAllElements(results);
            }
        }
    }

    public void addSearchable(ISearchable<T> searchable) {
        this.searchables.add(searchable);
    }
}
