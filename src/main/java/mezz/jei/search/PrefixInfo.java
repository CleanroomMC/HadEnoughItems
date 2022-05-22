package mezz.jei.search;

import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.StringUtil;
import mezz.jei.util.Translator;

import java.util.*;
import java.util.function.Supplier;

public class PrefixInfo implements Comparable<PrefixInfo> {

    public static final PrefixInfo NO_PREFIX;

    private static final Char2ObjectMap<PrefixInfo> instances = new Char2ObjectArrayMap<>(6);

    static {
        NO_PREFIX = new PrefixInfo('\0', -1, true, "default", () -> Config.SearchMode.ENABLED, i -> Collections.singleton(Translator.toLowercaseWithLocale(i.getDisplayName())),
                GeneralizedSuffixTree::new);
        addPrefix(new PrefixInfo('#', 0, true, "tooltip", Config::getTooltipSearchMode, IIngredientListElement::getTooltipStrings, GeneralizedSuffixTree::new));
        addPrefix(new PrefixInfo('&', 1, false, "resource_id", Config::getResourceIdSearchMode, e -> Collections.singleton(e.getResourceId()), GeneralizedSuffixTree::new));
        addPrefix(new PrefixInfo('^', 2, true, "color", Config::getColorSearchMode, IIngredientListElement::getColorStrings, LimitedStringStorage::new));
        addPrefix(new PrefixInfo('$', 3, false, "oredict", Config::getOreDictSearchMode, IIngredientListElement::getOreDictStrings, LimitedStringStorage::new));
        addPrefix(new PrefixInfo('@', 4, false, "mod_name", Config::getModNameSearchMode, IIngredientListElement::getModNameStrings, LimitedStringStorage::new));
        addPrefix(new PrefixInfo('%', 5, true, "creative_tab", Config::getCreativeTabSearchMode, IIngredientListElement::getCreativeTabsStrings, LimitedStringStorage::new));
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
    private final boolean potentialDialecticInclusion;
    private final String desc;
    private final IModeGetter modeGetter;
    private final IStringsGetter stringsGetter;
    private final Supplier<ISearchStorage<IIngredientListElement<?>>> storage;

    public PrefixInfo(char prefix, int priority, boolean potentialDialecticInclusion, String desc, IModeGetter modeGetter, IStringsGetter stringsGetter,
                      Supplier<ISearchStorage<IIngredientListElement<?>>> storage) {
        this.prefix = prefix;
        this.priority = priority;
        this.potentialDialecticInclusion = potentialDialecticInclusion;
        this.desc = desc;
        this.modeGetter = modeGetter;
        this.stringsGetter = stringsGetter;
        this.storage = storage;
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

    public String getDesc() {
        return desc;
    }

    public Config.SearchMode getMode() {
        return modeGetter.getMode();
    }

    public ISearchStorage<IIngredientListElement<?>> createStorage() {
        return this.storage.get();
    }

    public Collection<String> getStrings(IIngredientListElement<?> element) {
        if (!Config.getSearchStrippedDiacritics() || !this.potentialDialecticInclusion) {
            return this.stringsGetter.getStrings(element);
        }
        Collection<String> strings = this.stringsGetter.getStrings(element);
        for (String string : strings) {
            boolean hasNonAscii = false;
            for (int i = 0; i < string.length(); i++) {
                if (string.charAt(i) > 0x7F) {
                    hasNonAscii = true;
                    break;
                }
            }
            if (hasNonAscii) {
                String stripped = StringUtil.stripAccents(string);
                if (!stripped.equals(string)) {
                    try {
                        strings.add(stripped);
                    } catch (UnsupportedOperationException e) { // If list is unmodifiable
                        strings = new ArrayList<>(strings);
                        strings.add(StringUtil.stripAccents(string));
                    }
                }
            }
        }
        return strings;
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
