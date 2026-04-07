package mezz.jei.ingredients.group;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.render.CollapsedGroupRenderer;

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

	public enum GroupSource {
		DEFAULT,
		MOD,
		CUSTOM
	}

	private final String id;
	private final String langKey;
	/** Identifies who registered this group. */
	private final GroupSource source;
	private final List<Object> ingredients;
	private final List<IIngredientListElement<?>> elements;
	private final Set<String> uids;
	/** Matches against the raw ingredient object (any type). */
	private boolean expanded;
	private boolean visible = true;

	public CollapsedGroupIngredient(String id, String langKey, List<Object> ingredients, Set<String> uids) {
		this(id, langKey, ingredients, uids, GroupSource.DEFAULT);
	}

	public CollapsedGroupIngredient(String id, String langKey, List<Object> ingredients, Set<String> uids, GroupSource source) {
		this.id = id;
		this.langKey = langKey;
		this.ingredients = ingredients;
		this.uids = uids;
		this.source = source;
		this.expanded = false;
		this.elements = new ArrayList<>(ingredients.size());
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return langKey;
	}

	public GroupSource getSource() {
		return source;
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public void toggleExpanded() {
		this.expanded = !this.expanded;
	}

	public boolean matches(IIngredientListElement element) {
		String uid = element.getIngredientHelper().getUniqueId(element.getIngredient());
		if (this.uids.contains(uid)) {
			return true;
		}
		for (String stored : this.uids) {
			if (stored.endsWith(":*")) {
				String prefix = stored.substring(0, stored.length() - 2);
				if (uid.equals(prefix) || uid.startsWith(prefix + ":")) {
					return true;
				}
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

	public void addIngredient(IIngredientListElement<?> element) {
		elements.add(element);
	}

	public void clearIngredients() {
		elements.clear();
	}

	public int size() {
		return elements.size();
	}

	public boolean isEmpty() {
		return elements.isEmpty();
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
