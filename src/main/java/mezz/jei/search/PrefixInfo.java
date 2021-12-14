package mezz.jei.search;

import mezz.jei.config.Config.SearchMode;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.Translator;

import java.util.Collection;
import java.util.Collections;

public class PrefixInfo {

    public static final PrefixInfo NO_PREFIX = new PrefixInfo();

    private final IModeGetter modeGetter;
    private final IStringsGetter stringsGetter;

    private PrefixInfo() {
        this.modeGetter = () -> SearchMode.ENABLED;
        this.stringsGetter = element -> Collections.singleton(Translator.toLowercaseWithLocale(element.getDisplayName()));
    }

    public PrefixInfo(IModeGetter modeGetter, IStringsGetter stringsGetter) {
        this.modeGetter = modeGetter;
        this.stringsGetter = stringsGetter;
    }

    public SearchMode getMode() {
        return modeGetter.getMode();
    }

    public Collection<String> getStrings(IIngredientListElement<?> element) {
        return this.stringsGetter.getStrings(element);
    }

    @FunctionalInterface
    public interface IStringsGetter {
        Collection<String> getStrings(IIngredientListElement<?> element);
    }

    @FunctionalInterface
    public interface IModeGetter {
        SearchMode getMode();
    }

}
