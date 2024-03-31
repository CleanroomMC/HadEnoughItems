package mezz.jei.ingredients;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import mezz.jei.search.*;
import net.minecraft.client.Minecraft;
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
import mezz.jei.util.ErrorUtil;
import mezz.jei.util.Translator;

public class IngredientFilter implements IIngredientFilter, IIngredientGridSource {
	public static final Pattern QUOTE_PATTERN = Pattern.compile("\"");
	public static final Pattern FILTER_SPLIT_PATTERN = Pattern.compile("(-?\".*?(?:\"|$)|\\S+)");

	public static boolean firstBuild = true;
	public static boolean rebuild = false;

	private final IngredientBlacklistInternal blacklist;
	private final List<IIngredientGridSource.Listener> listeners = new ArrayList<>();

	private IElementSearch elementSearch;
	private List<IIngredientListElement> ingredientListCached = Collections.emptyList();
	@Nullable private String filterCached;

	private boolean afterBlock = false;
	@Nullable private List<Runnable> delegatedActions;

	public IngredientFilter(IngredientBlacklistInternal blacklist, NonNullList<IIngredientListElement> ingredients) {
		this.blacklist = blacklist;
		this.elementSearch = Config.isUltraLowMemoryMode() ? new ElementSearchLowMem() : new ElementSearch();
		this.elementSearch.addAll(ingredients);
		firstBuild = false;
	}

	public void logStatistics() {
		this.elementSearch.logStatistics();
	}

	public void addIngredients(NonNullList<IIngredientListElement> ingredients) {
		ingredients.sort(IngredientListElementComparator.INSTANCE);
		this.elementSearch.addAll(ingredients);
		this.filterCached = null;
	}

	public <V> void addIngredient(IIngredientListElement<V> element) {
		updateHiddenState(element);
		this.elementSearch.add(element);
		this.filterCached = null;
	}

	public void delegateAfterBlock(Runnable runnable) {
		if (this.afterBlock) {
			runnable.run();
			invalidateCache();
		} else {
			if (this.delegatedActions == null) {
				this.delegatedActions = new ArrayList<>();
			}
			this.delegatedActions.add(runnable);
		}
	}

	public void block() {
		if (this.elementSearch instanceof ElementSearch) {
			((ElementSearch) this.elementSearch).block();
		}
		if (this.delegatedActions != null) {
			Minecraft.getMinecraft().addScheduledTask(() -> {
				invalidateCache();
				this.delegatedActions.forEach(Runnable::run);
				this.delegatedActions = null;
				this.afterBlock = true;
				updateHidden();
			});
		} else {
			Minecraft.getMinecraft().addScheduledTask(this::updateHidden);
		}
		invalidateCache();
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
		this.invalidateCache();
		if (Config.doesSearchTreeNeedReload()) {
			firstBuild = true;
			rebuild = true;
			afterBlock = false;
			NonNullList<IIngredientListElement> ingredients = NonNullList.from(null, this.elementSearch.getAllIngredients().toArray(new IIngredientListElement[0]));
			this.elementSearch = Config.isUltraLowMemoryMode() ? new ElementSearchLowMem() : new ElementSearch();
			ingredients.sort(IngredientListElementComparator.INSTANCE);
			this.elementSearch.addAll(ingredients);
			// make sure search tree finishes building before gameplay resumes
			if (this.elementSearch instanceof ElementSearch) {
				((ElementSearch) this.elementSearch).block();
			}
			firstBuild = false;
			rebuild = false;
		}
	}

	@SubscribeEvent
	public void onEditModeToggleEvent(EditModeToggleEvent event) {
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
		if (filterText.isEmpty()) {
			return this.elementSearch.getAllIngredients().stream()
					.filter(IIngredientListElement::isVisible)
					.sorted(IngredientListElementComparator.INSTANCE)
					.collect(Collectors.toList());
		}
		List<SearchToken> tokens = Arrays.stream(filterText.split("\\|"))
				.map(SearchToken::parseSearchToken)
				.filter(s -> !s.search.isEmpty())
				.collect(Collectors.toList());
		if (tokens.isEmpty()) {
			return this.elementSearch.getAllIngredients().stream()
					.filter(IIngredientListElement::isVisible)
					.sorted(IngredientListElementComparator.INSTANCE)
					.collect(Collectors.toList());
		}
		return tokens.stream()
				.map(token -> token.getSearchResults(this.elementSearch))
				.flatMap(Set::stream)
				.filter(IIngredientListElement::isVisible)
				.sorted(IngredientListElementComparator.INSTANCE)
				.collect(Collectors.toList());
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

	public IngredientBlacklistInternal getIngredientBlacklist() {
		return blacklist;
	}

}
