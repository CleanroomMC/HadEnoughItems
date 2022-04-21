package mezz.jei.search;

import mezz.jei.gui.ingredients.IIngredientListElement;
import net.minecraft.util.NonNullList;

public interface IBuildable {

    void start();

    void stop();

    void submit(IIngredientListElement<?> ingredient);

    void submitAll(NonNullList<IIngredientListElement> ingredients);

}
