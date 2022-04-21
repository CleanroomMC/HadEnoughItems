package mezz.jei.search;

import mezz.jei.gui.ingredients.IIngredientListElement;

import java.util.Collection;
import java.util.Set;

public interface IElementSearch {

    void start();

    void stop();

    void add(IIngredientListElement<?> info);

    Collection<IIngredientListElement<?>> getAllIngredients();

    Set<IIngredientListElement<?>> getSearchResults(TokenInfo tokenInfo);

    @SuppressWarnings("unused") // used for debugging
    void logStatistics();

}
