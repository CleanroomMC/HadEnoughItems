package mezz.jei.ingredients;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import mezz.jei.search.*;
import mezz.jei.search.IElementSearch;
import mezz.jei.search.MemoryHogElementSearch;
import mezz.jei.search.UltraLowMemoryElementSearch;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.util.NonNullList;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import mezz.jei.api.IIngredientFilter;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.config.Config;
import mezz.jei.config.EditModeToggleEvent;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.startup.PlayerJoinedWorldEvent;
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.Translator;

public class IngredientFilter implements IIngredientFilter, IIngredientGridSource {
	private static final Pattern QUOTE_PATTERN = Pattern.compile("\"");
	private static final Pattern FILTER_SPLIT_PATTERN = Pattern.compile("(-?\".*?(?:\"|$)|\\S+)");

	private final IngredientBlacklistInternal blacklist;

	private final IElementSearch elementSearch;
	private final Char2ObjectMap<PrefixInfo> prefixInfos = new Char2ObjectOpenHashMap<>();

	@Nullable
	private String filterCached;
	private List<IIngredientListElement> ingredientListCached = Collections.emptyList();
	private final List<IIngredientGridSource.Listener> listeners = new ArrayList<>();

	public IngredientFilter(IngredientBlacklistInternal blacklist) {
		this.blacklist = blacklist;
		this.elementSearch = Config.isUltraLowMemoryMode() ? new UltraLowMemoryElementSearch() : new MemoryHogElementSearch();
		this.prefixInfos.put('@', new PrefixInfo(Config::getModNameSearchMode, IIngredientListElement::getModNameStrings));
		this.prefixInfos.put('#', new PrefixInfo(Config::getTooltipSearchMode, IIngredientListElement::getTooltipStrings));
		this.prefixInfos.put('$', new PrefixInfo(Config::getOreDictSearchMode, IIngredientListElement::getOreDictStrings));
		this.prefixInfos.put('%', new PrefixInfo(Config::getCreativeTabSearchMode, IIngredientListElement::getCreativeTabsStrings));
		this.prefixInfos.put('^', new PrefixInfo(Config::getColorSearchMode, IIngredientListElement::getColorStrings));
		this.prefixInfos.put('&', new PrefixInfo(Config::getResourceIdSearchMode, element -> Collections.singleton(element.getResourceId())));
		for (PrefixInfo prefixInfo : this.prefixInfos.values()) {
			this.elementSearch.registerPrefix(prefixInfo);
		}
	}

	public void trimToSize() {
		/*
		searchTree.trimToSize();
		for (PrefixedSearchTree tree : prefixedSearchTrees.values()) {
			tree.getTree().trimToSize();
		}
		 */
	}

	public void addIngredients(NonNullList<IIngredientListElement> ingredients) {
		ingredients.sort(IngredientListElementComparator.INSTANCE);
		long modNameCount = ingredients.stream()
			.map(IIngredientListElement::getModNameForSorting)
			.distinct()
			.count();
		ProgressManager.ProgressBar progressBar = ProgressManager.push("Indexing ingredients from " + modNameCount + " mods", 0, true);
		for (IIngredientListElement<?> element : ingredients) {
			addIngredient(element);
		}
		ProgressManager.pop(progressBar);
	}

	public <V> void addIngredient(IIngredientListElement<V> element) {
		updateHiddenState(element);
		this.elementSearch.add(element);
		this.filterCached = null;
	}

	public void invalidateCache() {
		this.filterCached = null;
	}

	public <V> List<IIngredientListElement<V>> findMatchingElements(IIngredientListElement<V> element) {
		final IIngredientHelper<V> ingredientHelper = element.getIngredientHelper();
		final V ingredient = element.getIngredient();
		final String ingredientUid = ingredientHelper.getUniqueId(ingredient);
		@SuppressWarnings("unchecked") final Class<? extends V> ingredientClass = (Class<? extends V>) ingredient.getClass();

		final IntSet matchingIndexes = this.elementSearch.getSearchResults(Translator.toLowercaseWithLocale(element.getDisplayName()), PrefixInfo.NO_PREFIX);
		if (matchingIndexes == null) {
			return Collections.emptyList();
		}
		final List<IIngredientListElement<V>> matchingElements = new ArrayList<>();
		final IntIterator iterator = matchingIndexes.iterator();
		while (iterator.hasNext()) {
			int index = iterator.nextInt();
			IIngredientListElement<?> matchingElement = this.elementSearch.get(index);
			Object matchingIngredient = matchingElement.getIngredient();
			if (ingredientClass.isInstance(matchingIngredient)) {
				V castMatchingIngredient = ingredientClass.cast(matchingIngredient);
				String matchingUid = ingredientHelper.getUniqueId(castMatchingIngredient);
				if (ingredientUid.equals(matchingUid)) {
					matchingElements.add((IIngredientListElement<V>) matchingElement);
				}
			}
		}
		return matchingElements;
	}

	public void modesChanged() {
		this.elementSearch.start();
		this.filterCached = null;
	}

	@SubscribeEvent
	public void onEditModeToggleEvent(EditModeToggleEvent event) {
		this.filterCached = null;
		updateHidden();
	}

	@SubscribeEvent
	public void onPlayerJoinedWorldEvent(PlayerJoinedWorldEvent event) {
		this.filterCached = null;
		updateHidden();
	}

	public void updateHidden() {
		for (IIngredientListElement<?> element : this.elementSearch.getAllIngredients()) {
			updateHiddenState(element);
		}
	}

	public <V> void updateHiddenState(IIngredientListElement<V> element) {
		V ingredient = element.getIngredient();
		IIngredientHelper<V> ingredientHelper = element.getIngredientHelper();
		boolean visible = !blacklist.isIngredientBlacklistedByApi(ingredient, ingredientHelper) &&
			ingredientHelper.isIngredientOnServer(ingredient) &&
			(Config.isEditModeEnabled() || !Config.isIngredientOnConfigBlacklist(ingredient, ingredientHelper));
		if (element.isVisible() != visible) {
			element.setVisible(visible);
			this.filterCached = null;
		}
	}

	@Override
	public List<IIngredientListElement> getIngredientList() {
		String filterText = Translator.toLowercaseWithLocale(Config.getFilterText());
		if (!filterText.equals(filterCached)) {
			List<IIngredientListElement<?>> ingredientList = getIngredientListUncached(filterText);
			ingredientList.sort(IngredientListElementComparator.INSTANCE);
			ingredientListCached = Collections.unmodifiableList(ingredientList);
			filterCached = filterText;
		}
		return ingredientListCached;
	}

	@Override
	public ImmutableList<Object> getFilteredIngredients() {
		List<IIngredientListElement> elements = getIngredientList();
		ImmutableList.Builder<Object> builder = ImmutableList.builder();
		for (IIngredientListElement element : elements) {
			Object ingredient = element.getIngredient();
			builder.add(ingredient);
		}
		return builder.build();
	}

	@Override
	public String getFilterText() {
		return Config.getFilterText();
	}

	@Override
	public void setFilterText(String filterText) {
		ErrorUtil.checkNotNull(filterText, "filterText");
		if (Config.setFilterText(filterText)) {
			notifyListenersOfChange();
		}
	}

	private List<IIngredientListElement<?>> getIngredientListUncached(String filterText) {
		String[] filters = filterText.split("\\|");

		IntSet matches = null;

		for (String filter : filters) {
			IntSet elements = getElements(filter);
			if (elements != null) {
				if (matches == null) {
					matches = elements;
				} else {
					matches.addAll(elements);
				}
			}
		}


		if (matches == null) {
			return this.elementSearch.getAllIngredients()
					.parallelStream()
					.filter(IIngredientListElement::isVisible)
					.collect(Collectors.toList());
		}

		List<IIngredientListElement<?>> matchingIngredients = new ArrayList<>();

		int[] matchesList = matches.toIntArray();
		Arrays.sort(matchesList);
		for (int match : matchesList) {
			IIngredientListElement<?> element = this.elementSearch.get(match);
			if (element.isVisible()) {
				matchingIngredients.add(element);
			}
		}

		return matchingIngredients;
	}

	/**
	 * Scans up and down the element list to find wildcard matches that touch the given element.
	 */
	public <T> List<IIngredientListElement<T>> getMatches(IIngredientListElement<T> ingredientListElement, Function<IIngredientListElement<?>, String> uidFunction) {
		final String uid = uidFunction.apply(ingredientListElement);
		List<IIngredientListElement<T>> matchingElements = findMatchingElements(ingredientListElement);
		IntSet matchingIndexes = new IntOpenHashSet(50);
		IntSet startingIndexes = new IntOpenHashSet(matchingElements.size());
		for (IIngredientListElement<?> matchingElement : matchingElements) {
			int index = this.elementSearch.indexOf(matchingElement);
			startingIndexes.add(index);
			matchingIndexes.add(index);
		}

		IntIterator iterator = startingIndexes.iterator();
		while (iterator.hasNext()) {
			int startingIndex = iterator.nextInt();
			for (int i = startingIndex - 1; i >= 0 && !matchingIndexes.contains(i); i--) {
				IIngredientListElement<?> element = this.elementSearch.get(i);
				String elementWildcardId = uidFunction.apply(element);
				if (uid.equals(elementWildcardId)) {
					matchingIndexes.add(i);
					matchingElements.add((IIngredientListElement<T>) element);
				} else {
					break;
				}
			}
			for (int i = startingIndex + 1; i < this.elementSearch.size() && !matchingIndexes.contains(i); i++) {
				IIngredientListElement<?> element = this.elementSearch.get(i);
				String elementWildcardId = uidFunction.apply(element);
				if (uid.equals(elementWildcardId)) {
					matchingIndexes.add(i);
					matchingElements.add((IIngredientListElement<T>) element);
				} else {
					break;
				}
			}
		}
		return matchingElements;
	}

	@Nullable
	private IntSet getElements(String filterText) {
		Matcher filterMatcher = FILTER_SPLIT_PATTERN.matcher(filterText);

		IntSet matches = null;
		IntSet removeMatches = null;
		while (filterMatcher.find()) {
			String token = filterMatcher.group(1);
			final boolean remove = token.startsWith("-");
			if (remove) {
				token = token.substring(1);
			}
			token = QUOTE_PATTERN.matcher(token).replaceAll("");

			IntSet searchResults = getSearchResults(token);
			if (searchResults != null) {
				if (remove) {
					if (removeMatches == null) {
						removeMatches = searchResults;
					} else {
						removeMatches.addAll(searchResults);
					}
				} else {
					if (matches == null) {
						matches = searchResults;
					} else {
						matches = intersection(matches, searchResults);
					}
					if (matches.isEmpty()) {
						break;
					}
				}
			}
		}

		if (matches != null && removeMatches != null) {
			matches.removeAll(removeMatches);
		}

		return matches;
	}

	/**
	 * Gets the appropriate search tree for the given token, based on if the token has a prefix.
	 */
	@Nullable
	private IntSet getSearchResults(String token) {
		if (token.isEmpty()) {
			return null;
		}
		final char firstChar = token.charAt(0);
		final PrefixInfo prefixInfo = this.prefixInfos.get(firstChar);
		if (prefixInfo != null && prefixInfo.getMode() != Config.SearchMode.DISABLED) {
			token = token.substring(1);
			if (token.isEmpty()) {
				return null;
			}
			return this.elementSearch.getSearchResults(token, prefixInfo);
		} else {
			return this.elementSearch.getSearchResults(token, PrefixInfo.NO_PREFIX);
		}
	}

	/**
	 * Efficiently get the elements contained in both sets.
	 * Note that this implementation will alter the original sets.
	 */
	private static IntSet intersection(IntSet set1, IntSet set2) {
		if (set1.size() > set2.size()) {
			set2.retainAll(set1);
			return set2;
		} else {
			set1.retainAll(set2);
			return set1;
		}
	}

	@Override
	public int size() {
		return getIngredientList().size();
	}

	@Override
	public void addListener(IIngredientGridSource.Listener listener) {
		listeners.add(listener);
	}

	private void notifyListenersOfChange() {
		for (IIngredientGridSource.Listener listener : listeners) {
			listener.onChange();
		}
	}
}
