package mezz.jei.search;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.util.NonNullList;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class UltraLowMemoryElementSearch implements IElementSearch {
    private final NonNullList<IIngredientListElement<?>> elementInfoList;

    public UltraLowMemoryElementSearch() {
        this.elementInfoList = NonNullList.create();
    }

    @Nullable
    @Override
    public IntSet getSearchResults(String token, PrefixInfo prefixInfo) {
        if (token.isEmpty()) {
            return null;
        }
        return new IntArraySet(IntStream.range(0, elementInfoList.size())
                .parallel() // sequential performance is actually decent here
                .filter(i -> matches(token, prefixInfo, elementInfoList.get(i)))
                .toArray());
    }

    private static boolean matches(String word, PrefixInfo prefixInfo, IIngredientListElement<?> element) {
        if (element.isVisible()) {
            for (String string : prefixInfo.getStrings(element)) {
                if (string.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public <V> void add(IIngredientListElement<V> info) {
        this.elementInfoList.add(info);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> IIngredientListElement<V> get(int index) {
        return (IIngredientListElement<V>) this.elementInfoList.get(index);
    }

    @Override
    public <V> int indexOf(IIngredientListElement<V> ingredient) {
        return this.elementInfoList.indexOf(ingredient);
    }

    @Override
    public int size() {
        return this.elementInfoList.size();
    }

    @Override
    public List<IIngredientListElement<?>> getAllIngredients() {
        return Collections.unmodifiableList(this.elementInfoList);
    }

    @Override
    public void start() {}

    @Override
    public void registerPrefix(PrefixInfo prefixInfo) {}

}