package mezz.jei.search;

import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.gui.ingredients.IIngredientListElement;

import javax.annotation.Nullable;
import java.util.List;

public interface IElementSearch {

    <V> void add(IIngredientListElement<V> info);

    <V> IIngredientListElement<V> get(int index);

    <V> int indexOf(IIngredientListElement<V> ingredient);

    int size();

    List<IIngredientListElement<?>> getAllIngredients();

    @Nullable
    IntSet getSearchResults(String token, PrefixInfo prefixInfo);

    void registerPrefix(PrefixInfo prefixInfo);

    void start();

}
