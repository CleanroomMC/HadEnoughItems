package mezz.jei.gui.ghost;

import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.input.IShowsRecipeFocuses;

public interface IGhostIngredientDragSource extends IShowsRecipeFocuses {
	IIngredientListElement getElementUnderMouse();
}
