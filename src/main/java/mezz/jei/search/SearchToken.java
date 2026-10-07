package mezz.jei.search;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.gui.ingredients.IIngredientListElement;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SearchToken {

    public static final SearchToken EMPTY = new SearchToken(Collections.emptyList(), Collections.emptyList());

    /**
     * Parses filter text into one {@link SearchToken} per OR clause.
     * Splitting on {@code |} is done by the tokenizer, allowing quoted and escaped pipes to stay as part of their token.
     */
    public static List<SearchToken> parseSearchTokens(String filterText) {
        return SearchTokenizer.tokenize(filterText).stream()
                .map(SearchToken::parseSearchToken)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toList());
    }

    public static SearchToken parseSearchToken(List<SearchTokenizer.Token> rawTokens) {
        if (rawTokens.isEmpty()) {
            return EMPTY;
        }
        SearchToken searchTokens = new SearchToken(new ArrayList<>(), new ArrayList<>());
        for (SearchTokenizer.Token rawToken : rawTokens) {
            TokenInfo token = TokenInfo.parseRawToken(rawToken.text);
            if (token != null) {
                if (rawToken.exclusion) {
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

    public boolean isEmpty() {
        return this.search.isEmpty() && this.remove.isEmpty();
    }

    public Set<IIngredientListElement<?>> getSearchResults(IElementSearch elementSearch) {
        Set<IIngredientListElement<?>> results = intersection(search.stream().map(elementSearch::getSearchResults));
        if (results.isEmpty() && !remove.isEmpty() && search.isEmpty()) {
            results.addAll(elementSearch.getAllIngredients());
        }
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
        Set<T> results = new ReferenceOpenHashSet<>(smallestSet);
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
