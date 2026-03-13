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
	default List<Object> getCollapsedIngredientList() {
		//noinspection unchecked,rawtypes
		return (List) getIngredientList();
	}

	/**
	 * Returns the total number of display items (counting collapsed groups as 1 each).
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
