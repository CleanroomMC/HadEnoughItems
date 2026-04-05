package mezz.jei.gui.overlay;

import java.util.List;

import mezz.jei.gui.ingredients.IIngredientListElement;

public interface IIngredientGridSource {
	List<IIngredientListElement> getIngredientList();

	/**
	 * Returns a list of display items for the grid, which may include
	 * CollapsedStack objects alongside IIngredientListElement objects
	 * when collapsible groups are enabled.
	 */
	default List<IIngredientListElement> getCollapsedIngredientList() {
		return getIngredientList();
	}

	/**
	 * Returns the total number of displayed ingredients (counting collapsed groups as 1 each).
	 */
	default int collapsedSize() {
		return size();
	}

	int size();

	void addListener(Listener listener);

	interface Listener {
		void onChange();
	}
}
