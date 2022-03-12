package mezz.jei.search;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.gui.ingredients.IIngredientListElement;

import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static mezz.jei.ingredients.IngredientFilter.FILTER_SPLIT_PATTERN;
import static mezz.jei.ingredients.IngredientFilter.QUOTE_PATTERN;

public class SearchToken {

    public static final SearchToken EMPTY = new SearchToken(Collections.emptyList(), Collections.emptyList());

    public static SearchToken parseSearchToken(String filterText) {
        if (filterText.isEmpty()) {
            return EMPTY;
        }
        SearchToken searchTokens = new SearchToken(new ArrayList<>(), new ArrayList<>());
        Matcher filterMatcher = FILTER_SPLIT_PATTERN.matcher(filterText);
        while (filterMatcher.find()) {
            String string = filterMatcher.group(1);
            final boolean remove = string.startsWith("-");
            if (remove) {
                string = string.substring(1);
            }
            string = QUOTE_PATTERN.matcher(string).replaceAll("");
            if (string.isEmpty()) {
                continue;
            }
            TokenInfo token = TokenInfo.parseRawToken(string);
            if (token != null) {
                if (remove) {
                    searchTokens.remove.add(token);
                } else {
                    searchTokens.search.add(token);
                }
            }
        }
        return searchTokens;
    }

    public final List<TokenInfo> search, remove;

    public SearchToken(List<TokenInfo> search, List<TokenInfo> remove) {
        this.search = search;
        this.remove = remove;
    }

    public Set<IIngredientListElement<?>> getSearchResults(IElementSearch elementSearch) {
        Set<IIngredientListElement<?>> results = intersection(search.stream().map(elementSearch::getSearchResults));
        if (!results.isEmpty() && !remove.isEmpty()) {
            for (TokenInfo tokenInfo : remove) {
                Set<IIngredientListElement<?>> resultsToRemove = elementSearch.getSearchResults(tokenInfo);
                results.removeAll(resultsToRemove);
                if (results.isEmpty()) {
                    break;
                }
            }
        }
        return results;
    }

    private <T> Set<T> intersection(Stream<Set<T>> stream) {
        List<Set<T>> sets = stream.collect(Collectors.toList());
        Set<T> smallestSet = sets.stream().min(Comparator.comparing(Set::size)).orElseGet(Collections::emptySet);
        Set<T> results = new ReferenceOpenHashSet<>();
        results.addAll(smallestSet);
        for (Set<T> set : sets) {
            if (set == smallestSet) {
                continue;
            }
            if (results.retainAll(set) && results.isEmpty()) {
                break;
            }
        }
        return results;
    }

}
