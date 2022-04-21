package mezz.jei.search;

import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.Translator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class PrefixInfo {

    public static final PrefixInfo NO_PREFIX = new PrefixInfo(
            '\0',
            "default",
            () -> Config.SearchMode.ENABLED, i -> Collections.singleton(Translator.toLowercaseWithLocale(i.getDisplayName())),
            GeneralizedSuffixTree::new,
            true);

    private static final Char2ObjectMap<PrefixInfo> instances = new Char2ObjectArrayMap<>(6);

    static {
        addPrefix(new PrefixInfo('&', "resource_id", Config::getResourceIdSearchMode, e -> Collections.singleton(e.getResourceId()), GeneralizedSuffixTree::new, true));
        addPrefix(new PrefixInfo('#', "tooltip", Config::getTooltipSearchMode, IIngredientListElement::getTooltipStrings, GeneralizedSuffixTree::new, true));
        addPrefix(new PrefixInfo('^', "color", Config::getColorSearchMode, IIngredientListElement::getColorStrings, LimitedStringStorage::new, true));
        addPrefix(new PrefixInfo('@', "mod_name", Config::getModNameSearchMode, IIngredientListElement::getModNameStrings, LimitedStringStorage::new, false));
        addPrefix(new PrefixInfo('$', "oredict", Config::getOreDictSearchMode, IIngredientListElement::getOreDictStrings, LimitedStringStorage::new, false));
        addPrefix(new PrefixInfo('%', "creative_tab", Config::getCreativeTabSearchMode, IIngredientListElement::getCreativeTabsStrings, LimitedStringStorage::new, false));
    }

    private static void addPrefix(PrefixInfo info) {
        instances.put(info.getPrefix(), info);
    }

    public static Collection<PrefixInfo> all() {
        List<PrefixInfo> values = new ArrayList<>(instances.values());
        values.add(PrefixInfo.NO_PREFIX);
        return values;
    }

    public static PrefixInfo get(char ch) {
        return instances.get(ch);
    }

    private final char prefix;
    private final String desc;
    private final IModeGetter modeGetter;
    private final IStringsGetter stringsGetter;
    private final Supplier<ISearchStorage<IIngredientListElement<?>>> storage;
    private final boolean async;

    public PrefixInfo(char prefix, String desc, IModeGetter modeGetter, IStringsGetter stringsGetter, Supplier<ISearchStorage<IIngredientListElement<?>>> storage, boolean async) {
        this.prefix = prefix;
        this.desc = desc;
        this.modeGetter = modeGetter;
        this.stringsGetter = stringsGetter;
        this.storage = storage;
        this.async = Config.isSearchTreeBuildingAsync() && async;
    }

    public char getPrefix() {
        return prefix;
    }

    public String getDesc() {
        return desc;
    }

    public Config.SearchMode getMode() {
        return modeGetter.getMode();
    }

    public boolean canBeAsync() {
        return async;
    }

    public ISearchStorage<IIngredientListElement<?>> createStorage() {
        return this.storage.get();
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
        Config.SearchMode getMode();
    }

    @Override
    public String toString() {
        return "PrefixInfo{" + desc + '}';
    }

}
