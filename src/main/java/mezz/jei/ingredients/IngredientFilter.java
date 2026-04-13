package mezz.jei.ingredients;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import mezz.jei.Internal;
import mezz.jei.ingredients.group.CollapsibleGroup;
import mezz.jei.ingredients.group.CollapsedGroupIngredient;
import mezz.jei.ingredients.group.CollapsibleGroupRegistry;
import mezz.jei.search.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.util.NonNullList;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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
	private List<IIngredientListElement> collapsedListCached = Collections.emptyList();
	@Nullable private String filterCached;
	/**
	 * Cached sorted list of all currently-visible ingredients — the result of a full
	 * suffix-tree traversal + sort. This does NOT change when the search-bar text changes,
	 * only when ingredients are added/removed or their visibility changes. Caching it here
	 * avoids the expensive {@code elementSearch.getAllIngredients()} traversal on every
	 * keystroke (once in {@link #getIngredientListUncached} for empty filter and once more
	 * in {@link #withGroupNameMatches} for every non-empty filter).
	 */
	@Nullable private List<IIngredientListElement<?>> allVisibleIngredientsCache = null;
	/**
	 * Precomputed mapping from each visible element (by identity) to the list of
	 * {@link CollapsedGroupIngredient} groups it belongs to. Built once from
	 * {@link #getAllVisibleIngredients()} and ALL registered groups; reused across
	 * keystrokes.  Invalidated alongside {@code allVisibleIngredientsCache} when
	 * ingredients or groups change.
	 */
	@Nullable private Multimap<IIngredientListElement<?>, CollapsibleGroup> groupMembershipCache = null;
	/**
	 * Reverse of {@link #groupMembershipCache}: maps each {@link CollapsibleGroup} to the
	 * visible elements that belong to it. Built directly during the sorted
	 * {@code allVisibleIngredientsCache} traversal to preserve sort order; used by
	 * {@link #withGroupNameMatches} to look up group members directly instead of scanning
	 * all visible ingredients.
	 */
	@Nullable private Multimap<CollapsibleGroup, IIngredientListElement<?>> groupToElementsCache = null;

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
		invalidateCache();
	}

	public <V> void addIngredient(IIngredientListElement<V> element) {
		updateHiddenState(element);
		this.elementSearch.add(element);
		invalidateCache();
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
		this.allVisibleIngredientsCache = null;
		this.groupMembershipCache = null;
		this.groupToElementsCache = null;
	}

	private void buildCache() {
		if (allVisibleIngredientsCache == null) {
			allVisibleIngredientsCache = this.elementSearch.getAllIngredients().stream()
					.filter(IIngredientListElement::isVisible)
					.sorted(IngredientListElementComparator.INSTANCE)
					.collect(Collectors.toList());
		}
		if (groupMembershipCache == null) {
			groupMembershipCache = HashMultimap.create();
			if (!Config.isCollapsibleGroupsEnabled()) {
				return;
			}
			CollapsibleGroupRegistry registry = Internal.getCollapsedGroupRegistry();
			Map<String, CollapsibleGroup> groups = registry.getAllGroups();
			if (groups.isEmpty()) {
				return;
			}
			Multimap<String, CollapsibleGroup> uids = HashMultimap.create();
			List<Map.Entry<String, CollapsibleGroup>> wildcardEntries = new ArrayList<>();
			for (CollapsibleGroup group : groups.values()) {
				for (String uid : group.getIngredient().getUids()) {
					if (uid.endsWith(":*")) {
						wildcardEntries.add(new AbstractMap.SimpleImmutableEntry<>(uid.substring(0, uid.length() - 2), group));
					} else {
						uids.put(uid, group);
					}
				}
			}
			// Build groupToElementsCache directly during the sorted traversal so its
			// per-group value order matches allVisibleIngredientsCache's sort order.
			// Multimaps.invertFrom over a HashMultimap would lose that order because
			// HashMultimap.entries() iterates keys in hash order, not insertion order.
			SetMultimap<CollapsibleGroup, IIngredientListElement<?>> gtoc = LinkedHashMultimap.create();
			for (IIngredientListElement element : allVisibleIngredientsCache) {
				String uid = element.getIngredientHelper().getUniqueId(element.getIngredient());
				Collection<CollapsibleGroup> uidGroups = uids.get(uid);
				if (!uidGroups.isEmpty()) {
					groupMembershipCache.putAll(element, uidGroups);
					for (CollapsibleGroup group : uidGroups) {
						gtoc.put(group, element);
					}
				}
				for (Map.Entry<String, CollapsibleGroup> entry : wildcardEntries) {
					String prefix = entry.getKey();
					if (uid.equals(prefix) || uid.startsWith(prefix + ":")) {
						groupMembershipCache.put(element, entry.getValue());
						gtoc.put(entry.getValue(), element);
					}
				}
			}
			groupToElementsCache = gtoc;

			for (CollapsibleGroup group : groups.values()) {
				Collection<IIngredientListElement<?>> groupElements = groupToElementsCache.get(group);
				group.getIngredient().setStableIngredients(groupElements.isEmpty() ? Collections.emptyList() : new ArrayList<>(groupElements));
			}
		}
	}

	/**
	 * Returns a cached, sorted list of every currently-visible ingredient.
	 * The cache is invalidated whenever {@link #invalidateCache()} is called (ingredient
	 * additions, visibility changes, mode changes), but NOT on search-text changes — the
	 * full ingredient set is independent of the search bar content.
	 */
	private List<IIngredientListElement<?>> getAllVisibleIngredients() {
		this.buildCache();
		return allVisibleIngredientsCache;
	}

	/**
	 * Returns a cached identity-map from each visible element to the list of
	 * {@link CollapsibleGroup} groups it matches.  Elements with no group match are
	 * absent from the map.  The cache is built once from the full visible-ingredient
	 * list and ALL registered groups (both built-in and custom), so the expensive
	 * matcher / UID computation happens only once — not on every keystroke.
	 * <p>
	 * {@link #collapse} then filters this by the currently-active (enabled) entries
	 * and uses O(1) map lookups per element instead of re-running matchers.
	 */
	private Multimap<IIngredientListElement<?>, CollapsibleGroup> getGroupMembership() {
		this.buildCache();
		return groupMembershipCache;
	}

	private Multimap<CollapsibleGroup, IIngredientListElement<?>> getGroupToElements() {
		this.buildCache();
		return groupToElementsCache;
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
		invalidateCache();
		updateHidden();

		// In Hide Ingredients Mode the user cannot Alt+Click to expand/collapse groups,
		// so expand all groups when entering edit mode and collapse them on exit.
		Internal.getCollapsedGroupRegistry().setExpandedOnAllGroups(event.isEditModeEnabled());
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
			invalidateCache();
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
	public List<IIngredientListElement> getCollapsedIngredientList() {
		getIngredientList(); // ensure cache is populated
		return collapsedListCached;
	}

	@Override
	public int collapsedSize() {
		List<IIngredientListElement> collapsed = getCollapsedIngredientList();
		int count = 0;
		for (IIngredientListElement obj : collapsed) {
			if (obj instanceof CollapsedGroupIngredient) {
				CollapsedGroupIngredient cs = (CollapsedGroupIngredient) obj;
				count += cs.isExpanded() ? cs.getFilterIngredients().size() : 1;
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
			return new ArrayList<>(getAllVisibleIngredients());
		}
		List<SearchToken> tokens = Arrays.stream(filterText.split("\\|"))
				.map(SearchToken::parseSearchToken)
				.filter(s -> !s.search.isEmpty())
				.collect(Collectors.toList());
		if (tokens.isEmpty()) {
			return new ArrayList<>(getAllVisibleIngredients());
		}
		return tokens.stream()
				.map(token -> token.getSearchResults(this.elementSearch))
				.flatMap(Set::stream)
				.filter(IIngredientListElement::isVisible)
				.sorted(IngredientListElementComparator.INSTANCE)
				.collect(Collectors.toList());
	}

	/**
	 * Augments a filtered ingredient list with any groups whose display name matches the
	 * search text, so that typing "wool" surfaces the entire Wool group even though none of
	 * the individual wool variants mention "wool" in their item name.
	 * <p>
	 * We deliberately only expand on group-name match, not on member match. Expanding on
	 * member match would cause e.g. searching "orange" to pull in the full 16-item Wool
	 * group when the user only wants the orange wool item.
	 */
	private List<IIngredientListElement<?>> withGroupNameMatches(List<IIngredientListElement<?>> baseList, String filterText) {
		Multimap<CollapsibleGroup, IIngredientListElement<?>> groupToElements = getGroupToElements();

		// Collect groups whose display name matches the search text.
		// We intentionally do NOT expand groups just because a member matched — that produced
		// "full group" results when the user typed e.g. "orange", expecting individual items.
		Set<CollapsibleGroup> groupsToExpand = new ObjectOpenHashSet<>();
		for (CollapsibleGroup group : Internal.getCollapsedGroupRegistry().getAllGroups().values()) {
			if (Translator.toLowercaseWithLocale(group.getIngredient().getDisplayName()).contains(filterText)) {
				groupsToExpand.add(group);
			}
		}

		if (groupsToExpand.isEmpty()) {
			return baseList;
		}

		Set<IIngredientListElement<?>> seen = new ObjectOpenHashSet<>(baseList);
		List<IIngredientListElement<?>> result = new ArrayList<>(baseList);
		for (CollapsibleGroup group : groupsToExpand) {
			for (IIngredientListElement<?> element : groupToElements.get(group)) {
				if (seen.add(element)) {
					result.add(element);
				}
			}
		}

		if (result.size() == baseList.size()) {
			return baseList;
		}
		result.sort(IngredientListElementComparator.INSTANCE);
		return result;
	}

	/**
	 * Converts a flat filtered ingredient list into a mixed list containing
	 * both individual IIngredientListElement objects and CollapsedStack groups.
	 * Each ingredient is assigned to the first matching CollapsedStack group (first match wins).
	 * If collapsible groups are disabled, returns the original list cast to List&lt;Object&gt;.
	 */
	private List<IIngredientListElement> collapse(List<IIngredientListElement> ingredientList) {
		if (!Config.isCollapsibleGroupsEnabled()) {
			return new ArrayList<>(ingredientList);
		}
		Map<String, CollapsibleGroup> allGroups = Internal.getCollapsedGroupRegistry().getAllGroups();
		if (allGroups.isEmpty()) {
			return new ArrayList<>(ingredientList);
		}

		// Collect enabled groups and clear their transient ingredient lists in one pass.
		Set<CollapsibleGroup> activeSet = new ObjectOpenHashSet<>();
		for (CollapsibleGroup group : allGroups.values()) {
			if (group.isEnabled()) {
				group.getIngredient().clearIngredients();
				activeSet.add(group);
			}
		}
		if (activeSet.isEmpty()) {
			return new ArrayList<>(ingredientList);
		}

		Multimap<IIngredientListElement<?>, CollapsibleGroup> membership = getGroupMembership();
		List<IIngredientListElement> result = new ArrayList<>(ingredientList.size());
		Set<CollapsedGroupIngredient> addedToResult = new ObjectOpenHashSet<>();

		for (IIngredientListElement<?> element : ingredientList) {
			Collection<CollapsibleGroup> groups = membership.get(element);
			boolean matched = false;
			for (CollapsibleGroup group : groups) {
				if (activeSet.contains(group)) {
					CollapsedGroupIngredient ingredient = group.getIngredient();
					if (addedToResult.add(ingredient)) {
						result.add(ingredient);
					}
					ingredient.addIngredient(element);
					matched = true;
				}
			}
			if (!matched) {
				result.add(element);
			}
		}

		// Remove empty groups and deduplicate size-1 groups that share the same single element.
		// The set tracks the first size-1 group element seen; later duplicates are dropped.
		Set<IIngredientListElement<?>> seenSingles = new ObjectOpenHashSet<>();
		result.removeIf(obj -> {
			if (!(obj instanceof CollapsedGroupIngredient)) {
				return false;
			}
			CollapsedGroupIngredient cg = (CollapsedGroupIngredient) obj;
			if (cg.isFilterEmpty()) {
				return true;
			}
			List<IIngredientListElement<?>> fi = cg.getFilterIngredients();
			if (fi.size() == 1) {
				return !seenSingles.add(fi.get(0));
			}
			return false;
		});
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

	public void notifyCollapsedStateChanged() {
		// Do NOT null filterCached here. Creates client lag spikes.
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
