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

	private final List<IIngredientGridSource.Listener> listeners = new ArrayList<>();
	private final List<Runnable> collapsedStateListeners = new ArrayList<>();

	private IngredientBlacklistInternal blacklist;
	private IElementSearch elementSearch;
	private List<IIngredientListElement> ingredientListCached = Collections.emptyList();
	private List<Object> collapsedListCached = Collections.emptyList();
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
		this.afterBlock = true;
		if (this.delegatedActions != null) {
			Minecraft.getMinecraft().addScheduledTask(() -> {
				invalidateCache();
				this.delegatedActions.forEach(Runnable::run);
				this.delegatedActions = null;
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
			this.afterBlock = false;
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
			this.afterBlock = true;
		}
	}

	@SubscribeEvent
	public void onEditModeToggleEvent(EditModeToggleEvent event) {
		this.filterCached = null;
		updateHidden();

		// In Hide Ingredients Mode the user cannot Alt+Click to expand/collapse groups,
		// so expand all groups when entering edit mode and collapse them on exit.
		boolean editMode = event.isEditModeEnabled();
		CollapsibleEntryRegistry registry = mezz.jei.Internal.getCollapsibleEntryRegistry();
		for (CollapsibleEntry entry : registry.getEntries()) {
			entry.setExpanded(editMode);
		}
		for (CollapsibleEntry entry : registry.getCustomEntries()) {
			entry.setExpanded(editMode);
		}
		this.collapsedListCached = Collections.emptyList();
		notifyCollapsedStateChanged();
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
			if (!filterText.isEmpty() && Config.isCollapsibleGroupsEnabled()) {
				ingredientList = withGroupNameMatches(ingredientList, filterText);
			}
			ingredientListCached = Collections.unmodifiableList(ingredientList);
			collapsedListCached = collapse(ingredientListCached);
			filterCached = filterText;
		}
		return ingredientListCached;
	}

	@Override
	public List<Object> getCollapsedIngredientList() {
		getIngredientList(); // ensure cache is populated
		return collapsedListCached;
	}

	@Override
	public int collapsedSize() {
		List<Object> collapsed = getCollapsedIngredientList();
		int count = 0;
		for (Object obj : collapsed) {
			if (obj instanceof CollapsedStack) {
				CollapsedStack cs = (CollapsedStack) obj;
				count += cs.isExpanded() ? cs.size() : 1;
			} else {
				count++;
			}
		}
		return count;
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
	 * Augments a filtered ingredient list with any ingredients that belong to a group whose
	 * display name contains the filter text, but that weren't returned by the normal token
	 * search. This lets users find groups by name in the main search bar.
	 */
	@SuppressWarnings("unchecked")
	private List<IIngredientListElement<?>> withGroupNameMatches(
			List<IIngredientListElement<?>> baseList, String filterText) {
		CollapsibleEntryRegistry registry = CollapsibleEntryRegistry.getInstance();
		List<CollapsibleEntry> matchingGroups = new ArrayList<>();
		for (CollapsibleEntry entry : registry.getEntries()) {
			if (Translator.toLowercaseWithLocale(entry.getDisplayName()).contains(filterText)) {
				matchingGroups.add(entry);
			}
		}
		for (CollapsibleEntry entry : registry.getCustomEntries()) {
			if (Translator.toLowercaseWithLocale(entry.getDisplayName()).contains(filterText)) {
				matchingGroups.add(entry);
			}
		}
		if (matchingGroups.isEmpty()) {
			return baseList;
		}
		// Use identity comparison so dedup works regardless of equals() implementation.
		Set<IIngredientListElement<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		seen.addAll(baseList);
		List<IIngredientListElement<?>> result = new ArrayList<>(baseList);
		for (IIngredientListElement<?> element : this.elementSearch.getAllIngredients()) {
			if (!element.isVisible() || seen.contains(element)) {
				continue;
			}
			for (CollapsibleEntry entry : matchingGroups) {
				if (entry.matches(element)) {
					result.add(element);
					seen.add(element);
					break;
				}
			}
		}
		result.sort(IngredientListElementComparator.INSTANCE);
		return result;
	}

	/**
	 * Converts a flat filtered ingredient list into a mixed list containing
	 * both individual IIngredientListElement objects and CollapsedStack groups.
	 * Each ingredient is assigned to the first matching CollapsibleEntry (first match wins).
	 * If collapsible groups are disabled, returns the original list cast to List&lt;Object&gt;.
	 */
	private List<Object> collapse(List<IIngredientListElement> ingredientList) {
		if (!Config.isCollapsibleGroupsEnabled()) {
			return new ArrayList<>(ingredientList);
		}
		CollapsibleEntryRegistry registry = CollapsibleEntryRegistry.getInstance();
		Collection<CollapsibleEntry> entries = registry.getEntries();
		List<CollapsibleEntry> customEntries = registry.getCustomEntries();
		if (entries.isEmpty() && customEntries.isEmpty()) {
			return new ArrayList<>(ingredientList);
		}

		// Build the list of active entries (not disabled)
		List<CollapsibleEntry> activeEntries = new ArrayList<>();
		for (CollapsibleEntry entry : entries) {
			if (registry.isGroupEnabled(entry.getId())) {
				activeEntries.add(entry);
			}
		}
		for (CollapsibleEntry entry : customEntries) {
			if (registry.isGroupEnabled(entry.getId())) {
				activeEntries.add(entry);
			}
		}
		if (activeEntries.isEmpty()) {
			return new ArrayList<>(ingredientList);
		}

		// Map from entry -> CollapsedStack (created on first match)
		Map<CollapsibleEntry, CollapsedStack> collapsedMap = new LinkedHashMap<>();
		List<Object> result = new ArrayList<>(ingredientList.size());

		for (IIngredientListElement<?> element : ingredientList) {
			boolean matched = false;
			for (CollapsibleEntry entry : activeEntries) {
				if (entry.matches(element)) {
					CollapsedStack collapsed = collapsedMap.get(entry);
					if (collapsed == null) {
						collapsed = new CollapsedStack(entry);
						collapsedMap.put(entry, collapsed);
						result.add(collapsed);
					}
					collapsed.addIngredient(element);
					matched = true;
				}
			}
			if (!matched) {
				result.add(element);
			}
		}

		// Remove empty collapsed stacks (shouldn't happen, but be safe)
		result.removeIf(obj -> obj instanceof CollapsedStack && ((CollapsedStack) obj).isEmpty());
		return result;
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

	public void addCollapsedStateListener(Runnable listener) {
		collapsedStateListeners.add(listener);
	}

	/**
	 * Called when a group is expanded or collapsed. Invalidates the cached collapsed list
	 * and notifies only collapsed-state listeners (preserves the current page position).
	 */
	public void notifyCollapsedStateChanged() {
		this.filterCached = null;
		for (Runnable listener : collapsedStateListeners) {
			listener.run();
		}
	}

	public void replaceBlacklist(IngredientBlacklistInternal blacklist) {
		this.blacklist = blacklist;
	}

	public void notifyListenersOfChange() {
		for (IIngredientGridSource.Listener listener : listeners) {
			listener.onChange();
		}
	}

}
