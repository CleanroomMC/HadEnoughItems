package mezz.jei.ingredients;

import mezz.jei.gui.ingredients.IIngredientListElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a collapsed group in a specific filter result.
 * Pairs a {@link CollapsibleEntry} definition with the actual matched ingredients
 * from the current filtered list.
 *
 * This is NOT an {@link IIngredientListElement} — it coexists with them in a mixed list.
 */
public class CollapsedStack {
	private final CollapsibleEntry entry;
	private final List<IIngredientListElement<?>> ingredients;

	public CollapsedStack(CollapsibleEntry entry) {
		this.entry = entry;
		this.ingredients = new ArrayList<>();
	}

	public CollapsibleEntry getEntry() {
		return entry;
	}

	public List<IIngredientListElement<?>> getIngredients() {
		return ingredients;
	}

	public void addIngredient(IIngredientListElement<?> element) {
		ingredients.add(element);
	}

	public int size() {
		return ingredients.size();
	}

	public boolean isEmpty() {
		return ingredients.isEmpty();
	}

	public String getDisplayName() {
		return entry.getDisplayName();
	}

	public boolean isExpanded() {
		return entry.isExpanded();
	}

	public void setExpanded(boolean expanded) {
		entry.setExpanded(expanded);
	}

	public void toggleExpanded() {
		entry.toggleExpanded();
	}
}
