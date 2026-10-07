package mezz.jei.search;

import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import mezz.jei.api.search.ISearchIndexBuilder;
import mezz.jei.api.search.ISearchIndexBuilderFactory;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.StringUtil;
import mezz.jei.util.Translator;

import java.util.*;

public class PrefixInfo implements Comparable<PrefixInfo> {

    public static final PrefixInfo NO_PREFIX;

    private static final Char2ObjectMap<PrefixInfo> instances = new Char2ObjectArrayMap<>(6);

    static {
        NO_PREFIX = new PrefixInfo('\0', -1, true, "default", () -> Config.SearchMode.ENABLED, i -> Collections.singleton(Translator.toLowercaseWithLocale(i.getDisplayName())),
                false);
        addPrefix(new PrefixInfo('#', 0, true, false, "tooltip", Config::getTooltipSearchMode, IIngredientListElement::getTooltipStrings, false));
        addPrefix(new PrefixInfo('&', 1, false, "resource_id", Config::getResourceIdSearchMode,
                e -> Collections.singleton(Translator.toLowercaseWithLocale(e.getResourceId())), false));
        addPrefix(new PrefixInfo('^', 2, true, "color", Config::getColorSearchMode, IIngredientListElement::getColorStrings, true));
        addPrefix(new PrefixInfo('$', 3, false, "oredict", Config::getOreDictSearchMode, IIngredientListElement::getOreDictStrings, true));
        addPrefix(new PrefixInfo('@', 4, false, "mod_name", Config::getModNameSearchMode, IIngredientListElement::getModNameStrings, true));
        addPrefix(new PrefixInfo('%', 5, true, "creative_tab", Config::getCreativeTabSearchMode, IIngredientListElement::getCreativeTabsStrings, true));
    }

    private static void addPrefix(PrefixInfo info) {
        instances.put(info.getPrefix(), info);
    }

    public static Collection<PrefixInfo> all() {
        return Collections.unmodifiableCollection(instances.values());
    }

    public static PrefixInfo get(char ch) {
        return instances.get(ch);
    }

    private final char prefix;
    private final int priority;
    private final boolean potentialDialecticInclusion, async;
    private final String desc;
    private final IModeGetter modeGetter;
    private final IStringsGetter stringsGetter;
    private final boolean limitedStringIndex;

    public PrefixInfo(char prefix, int priority, boolean potentialDialecticInclusion, String desc, IModeGetter modeGetter, IStringsGetter stringsGetter,
                      boolean limitedStringIndex) {
        this(prefix, priority, potentialDialecticInclusion, true, desc, modeGetter, stringsGetter, limitedStringIndex);
    }

    public PrefixInfo(char prefix, int priority, boolean potentialDialecticInclusion, boolean async, String desc, IModeGetter modeGetter, IStringsGetter stringsGetter,
                      boolean limitedStringIndex) {
        this.prefix = prefix;
        this.priority = priority;
        this.potentialDialecticInclusion = potentialDialecticInclusion;
        this.async = async;
        this.desc = desc;
        this.modeGetter = modeGetter;
        this.stringsGetter = stringsGetter;
        this.limitedStringIndex = limitedStringIndex;
    }

    public char getPrefix() {
        return prefix;
    }

    public int getPriority() {
        return priority;
    }

    public boolean hasPotentialDialecticInclusion() {
        return potentialDialecticInclusion;
    }

    public boolean isAsyncable() {
        return this.async;
    }

    public String getDesc() {
        return desc;
    }

    public Config.SearchMode getMode() {
        return modeGetter.getMode();
    }

    public ISearchIndexBuilder<IIngredientListElement<?>> createIndexBuilder(ISearchIndexBuilderFactory factory) {
        if (limitedStringIndex) {
            return new LimitedStringIndexBuilder<>(factory, desc);
        }
        return factory.create(desc);
    }

    public Collection<String> getStrings(IIngredientListElement<?> element) {
        if (!Config.getSearchStrippedDiacritics() || !this.potentialDialecticInclusion) {
            return this.stringsGetter.getStrings(element);
        }
        Collection<String> strings = this.stringsGetter.getStrings(element);
        Collection<String> newStrings = null;
        for (String string : strings) {
            for (int i = 0; i < string.length(); i++) {
                if (string.charAt(i) > 0x7F) {
                    String stripped = StringUtil.stripAccents(string);
                    if (!stripped.equals(string)) {
                        if (newStrings == null) {
                            newStrings = new ArrayList<>(strings);
                        }
                        newStrings.add(stripped);
                    }
                    break;
                }
            }
        }
        return newStrings == null ? strings : newStrings;
    }

    @Override
    public int compareTo(PrefixInfo o) {
        return Integer.compare(o.priority, this.priority);
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
