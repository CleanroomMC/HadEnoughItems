package mezz.jei.ingredients.group;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.render.CollapsedGroupRenderer;
import mezz.jei.util.Translator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Represents a collapsible ingredient group
 * Both the group definition (id, display name, matcher, expanded state)
 * and the runtime list of matched ingredients from the current filter.
 * <p>
 * Registered as an {@link IIngredientType} so that addons which introspect grid items
 * always find a valid type. Recipe lookups are delegated to the first ingredient via
 * {@link mezz.jei.api.ingredients.IIngredientHelper#translateFocus}.
 * <p>
 */
public class CollapsedGroupIngredient implements IIngredientListElement<CollapsedGroupIngredient> {

	// Registered as IIngredientType for addon compatibility — addons expect every grid item to have a type
	public static final IIngredientType<CollapsedGroupIngredient> TYPE = () -> CollapsedGroupIngredient.class;

	public static final int BACKGROUND_COLOR_SMOKE = 0x33555555; // subtle smoke background
	public static final int BORDER_COLOR_SMOKE = 0xCC888888; // medium smoke border

	public enum GroupSource {
		DEFAULT,
		MOD,
		CUSTOM
	}

	private final String id;
	private final String langKey;
	/** Identifies who registered this group. */
	private final GroupSource source;
	private final List<IIngredientListElement<?>> filterElements;
	private final Set<String> uids;
	/** Pre-extracted from {@code uids}: the prefix before {@code :*} for each wildcard entry. */
	private final List<String> wildcardPrefixes;
	private final int backgroundColor;
	private final int borderColor;

	private List<IIngredientListElement<?>> elements;
	/** Matches against the raw ingredient object (any type). */
	private boolean expanded;
	private boolean visible = true;

	public CollapsedGroupIngredient(String id, String langKey, int backgroundColor, int borderColor, Set<String> uids, GroupSource source) {
		this.id = id;
		this.langKey = langKey;
		this.uids = uids;
		this.source = source;
		this.expanded = false;
		this.elements = new ArrayList<>(uids.size());
		this.filterElements = new ArrayList<>(uids.size());
		this.backgroundColor = backgroundColor;
		this.borderColor = borderColor;
		// Pre-extract wildcard prefixes so matches() doesn't scan all UIDs on every call
		List<String> wc = null;
		for (String uid : uids) {
			if (uid.endsWith(":*")) {
				if (wc == null) {
					wc = new ArrayList<>();
				}
				wc.add(uid.substring(0, uid.length() - 2));
			}
		}
		this.wildcardPrefixes = wc != null ? Collections.unmodifiableList(wc) : Collections.emptyList();
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return Translator.translateToLocal(langKey);
	}

	public GroupSource getSource() {
		return source;
	}

	public int getBackgroundColor() {
		return backgroundColor;
	}

	public int getBorderColor() {
		return borderColor;
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public void toggleExpanded() {
		this.setExpanded(!this.expanded);
	}

	public boolean matches(IIngredientListElement element) {
		String uid = element.getIngredientHelper().getUniqueId(element.getIngredient());
		return matchesUid(uid);
	}

	/**
	 * Matches against a pre-computed UID string.
	 * Prefer this overload when checking many elements to avoid recomputing the UID per group.
	 */
	public boolean matchesUid(String uid) {
		if (this.uids.contains(uid)) {
			return true;
		}
		for (String prefix : this.wildcardPrefixes) {
			if (uid.startsWith(prefix) &&
				(uid.length() == prefix.length() || uid.charAt(prefix.length()) == ':')) {
				return true;
			}
		}
		return false;
	}

	// --- Runtime ingredient list (transient per filter cycle) ---

	public Set<String> getUids() {
		return uids;
	}

	public List<IIngredientListElement<?>> getIngredients() {
		return elements;
	}

	public List<IIngredientListElement<?>> getFilterIngredients() {
		return filterElements;
	}

	public void setStableIngredients(List<IIngredientListElement<?>> stableIngredients) {
		this.elements = stableIngredients;
	}

	public void addIngredient(IIngredientListElement<?> element) {
		filterElements.add(element);
	}

	public void clearIngredients() {
		filterElements.clear();
	}

	/**
	 * Returns the ingredient list that should be displayed in the current context.
	 * When a search filter is active ({@code filterElements} is non-empty), returns
	 * only the matched subset so the count badge and icons reflect the search results.
	 * Falls back to the full stable list when no filter is applied (e.g. bookmarks).
	 */
	public List<IIngredientListElement<?>> getDisplayIngredients() {
		return filterElements.isEmpty() ? elements : filterElements;
	}

	public int size() {
		return getDisplayIngredients().size();
	}

	public boolean isEmpty() {
		return elements.isEmpty();
	}

	public boolean isFilterEmpty() {
		return filterElements.isEmpty();
	}

	@Override
	public CollapsedGroupIngredient getIngredient() {
		return this;
	}

	@Override
	public int getOrderIndex() {
		return elements.isEmpty() ? 0 : elements.get(0).getOrderIndex();
	}

	@SuppressWarnings("unchecked")
	@Override
	public IIngredientHelper<CollapsedGroupIngredient> getIngredientHelper() {
		return CollapsedGroupIngredientHelper.INSTANCE;
	}

	@SuppressWarnings("unchecked")
	@Override
	public IIngredientRenderer<CollapsedGroupIngredient> getIngredientRenderer() {
		return CollapsedGroupRenderer.INSTANCE;
	}

	@Override
	public String getModNameForSorting() {
		return elements.isEmpty() ? "" : elements.get(0).getModNameForSorting();
	}

	@Override
	public Set<String> getModNameStrings() {
		return elements.isEmpty() ? Collections.emptySet() : elements.get(0).getModNameStrings();
	}

	@Override
	public List<String> getTooltipStrings() {
		return elements.isEmpty() ? Collections.emptyList() : elements.get(0).getTooltipStrings();
	}

	@Override
	public Collection<String> getOreDictStrings() {
		return elements.isEmpty() ? Collections.emptyList() : elements.get(0).getOreDictStrings();
	}

	@Override
	public Collection<String> getCreativeTabsStrings() {
		return elements.isEmpty() ? Collections.emptyList() : elements.get(0).getCreativeTabsStrings();
	}

	@Override
	public Collection<String> getColorStrings() {
		return elements.isEmpty() ? Collections.emptyList() : elements.get(0).getColorStrings();
	}

	@Override
	public String getResourceId() {
		return "collapsedstack:" + id;
	}

	@Override
	public boolean isVisible() {
		return visible;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	@Override
	public int getGroupIndex() {
		return 0;
	}

	@Override
	public boolean startsNewRow() {
		return false;
	}

}
