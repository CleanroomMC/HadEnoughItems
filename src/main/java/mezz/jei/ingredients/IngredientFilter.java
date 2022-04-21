package mezz.jei.ingredients;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import mezz.jei.search.*;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.util.NonNullList;

import com.google.common.collect.ImmutableList;
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
	public static final Pattern QUOTE_PATTERN = Pattern.compile("\"");
	public static final Pattern FILTER_SPLIT_PATTERN = Pattern.compile("(-?\".*?(?:\"|$)|\\S+)");

	private final IngredientBlacklistInternal blacklist;

	private final IElementSearch elementSearch;
	// private final Set<String> modNamesForSorting = new ObjectOpenHashSet<>(); // TODO

	@Nullable
	private String filterCached;
	private List<IIngredientListElement> ingredientListCached = Collections.emptyList();
	private final List<IIngredientGridSource.Listener> listeners = new ArrayList<>();

	public IngredientFilter(IngredientBlacklistInternal blacklist) {
		this.blacklist = blacklist;
		this.elementSearch = Config.isUltraLowMemoryMode() ? new ElementSearchLowMem() : new ElementSearch();
	}

	public void logStatistics() {
		this.elementSearch.logStatistics();
	}

	public void trimToSize() {
		// NO-OP
	}

	public void addIngredients(NonNullList<IIngredientListElement> ingredients) {
		ingredients.sort(IngredientListElementComparator.INSTANCE);
		long modNameCount = ingredients.stream()
			.map(IIngredientListElement::getModNameForSorting)
			.distinct()
			.count();
		this.elementSearch.start();
		ProgressManager.ProgressBar progressBar = ProgressManager.push("Indexing ingredients from " + modNameCount + " mods", 1, false);
		progressBar.step("");
		this.elementSearch.addAll(ingredients);
		this.elementSearch.stop();
		this.filterCached = null;
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
		final List<IIngredientListElement<V>> matchingElements = new ArrayList<>();
		for (IIngredientListElement<?> searchElement : this.elementSearch.getSearchResults(new TokenInfo(Translator.toLowercaseWithLocale(element.getDisplayName()), PrefixInfo.NO_PREFIX))) {
			Object searchElementObject = searchElement.getIngredient();
			if (ingredientClass.isInstance(searchElementObject)) {
				V castSearchElementObject = ingredientClass.cast(searchElementObject);
				String searchElementUid = ingredientHelper.getUniqueId(castSearchElementObject);
				if (ingredientUid.equals(searchElementUid)) {
					matchingElements.add((IIngredientListElement<V>) searchElement);
				}
			}
		}
		return matchingElements;
	}

	public void modesChanged() {
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
		return getIngredientList(Config.getFilterText());
	}

	public List<IIngredientListElement> getIngredientList(String filterText) {
		filterText = Translator.toLowercaseWithLocale(filterText);
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
		return getFilteredIngredients(Config.getFilterText());
	}

	public ImmutableList<Object> getFilteredIngredients(String filterText) {
		List<IIngredientListElement> elements = getIngredientList(filterText);
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
		List<SearchToken> tokens = Arrays.stream(filters).map(SearchToken::parseSearchToken).filter(s -> !s.search.isEmpty()).collect(Collectors.toList());
		Stream<IIngredientListElement<?>> stream;
		if (tokens.isEmpty()) {
			stream = this.elementSearch.getAllIngredients().parallelStream();
		} else {
			stream = tokens.stream().map(token -> token.getSearchResults(this.elementSearch)).flatMap(Set::stream).distinct();
		}
		return stream.filter(IIngredientListElement::isVisible).collect(Collectors.toList()); // TODO: sort
	}

	/**
	 * Scans up and down the element list to find wildcard matches that touch the given element.
	 */
	public <T> List<IIngredientListElement<T>> getMatches(IIngredientListElement<T> ingredientListElement, Function<IIngredientListElement<?>, String> uidFunction) {
		List<IIngredientListElement<T>> initialSearchResult = findMatchingElements(ingredientListElement);
		if (initialSearchResult.isEmpty()) {
			return initialSearchResult;
		}
		String uid = uidFunction.apply(ingredientListElement);
		List<IIngredientListElement<T>> searchResult = new ArrayList<>();
		for (IIngredientListElement<T> searchedElement : initialSearchResult) {
			if (uid.equals(searchedElement.getIngredientHelper().getUniqueId(searchedElement.getIngredient()))) {
				searchResult.add(searchedElement);
			}
		}
		if (!searchResult.isEmpty()) {
			return searchResult;
		}
		IntSet matchingIndexes = new IntOpenHashSet();
		List<IIngredientListElement> ingredientList = this.getIngredientList("");
		int startingIndex = -1;
		for (IIngredientListElement<T> searchedElement : initialSearchResult) {
			int index = ingredientList.indexOf(searchedElement);
			startingIndex = Math.max(index, startingIndex);
			matchingIndexes.add(index);
			searchResult.add(searchedElement);
		}
		for (int i = startingIndex - 1; i >= 0 && !matchingIndexes.contains(i); i--) {
			IIngredientListElement<T> ingredient = ingredientList.get(i);
			String searchElementUid = uidFunction.apply(ingredient);
			if (uid.equals(searchElementUid)) {
				matchingIndexes.add(i);
				searchResult.add(ingredient);
			}
		}
		for (int i = startingIndex + 1; i < ingredientList.size() && !matchingIndexes.contains(i); i++) {
			IIngredientListElement<T> ingredient = ingredientList.get(i);
			String searchElementUid = uidFunction.apply(ingredient);
			if (uid.equals(searchElementUid)) {
				matchingIndexes.add(i);
				searchResult.add(ingredient);
			}
		}
		return searchResult;
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
