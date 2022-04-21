package mezz.jei.search;

import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.util.NonNullList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ElementSearchLowMem implements IElementSearch {

    private static final Logger LOGGER = LogManager.getLogger();

    private final NonNullList<IIngredientListElement<?>> elementInfoList;

    public ElementSearchLowMem() {
        this.elementInfoList = NonNullList.create();
    }

    @Override
    public Set<IIngredientListElement<?>> getSearchResults(TokenInfo tokenInfo) {
        String token = tokenInfo.token;
        if (token.isEmpty()) {
            return Collections.emptySet();
        }
        PrefixInfo prefixInfo = tokenInfo.prefixInfo;
        return this.elementInfoList.stream().filter(elementInfo -> matches(token, prefixInfo, elementInfo)).collect(Collectors.toSet());
    }

    private static boolean matches(String word, PrefixInfo prefixInfo, IIngredientListElement<?> elementInfo) {
        if (elementInfo.isVisible()) {
            Collection<String> strings = prefixInfo.getStrings(elementInfo);
            for (String string : strings) {
                if (string.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void add(IIngredientListElement<?> ingredient) {
        this.elementInfoList.add(ingredient);
    }

    @Override
    public void addAll(NonNullList<IIngredientListElement> ingredients) {
        for (IIngredientListElement ingredient : ingredients) {
            add(ingredient);
        }
    }

    @Override
    public List<IIngredientListElement<?>> getAllIngredients() {
        return Collections.unmodifiableList(this.elementInfoList);
    }

    @Override
    public void logStatistics() {
        LOGGER.info("ElementSearchLowMem Element Count: {}", this.elementInfoList.size());
    }
}