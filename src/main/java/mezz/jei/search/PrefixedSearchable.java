package mezz.jei.search;

import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.LoggedTimer;
import net.minecraft.util.NonNullList;

import java.util.Collection;
import java.util.Set;

public class PrefixedSearchable implements ISearchable<IIngredientListElement<?>>, IBuildable {

    protected final ISearchStorage<IIngredientListElement<?>> searchStorage;
    protected final PrefixInfo prefixInfo;

    protected LoggedTimer timer;

    public PrefixedSearchable(ISearchStorage<IIngredientListElement<?>> searchStorage, PrefixInfo prefixInfo) {
        this.searchStorage = searchStorage;
        this.prefixInfo = prefixInfo;
    }

    public ISearchStorage<IIngredientListElement<?>> getSearchStorage() {
        return searchStorage;
    }

    public Collection<String> getStrings(IIngredientListElement<?> element) {
        return prefixInfo.getStrings(element);
    }

    @Override
    public Config.SearchMode getMode() {
        return prefixInfo.getMode();
    }

    @Override
    public void submit(IIngredientListElement<?> ingredient) {
        Collection<String> strings = prefixInfo.getStrings(ingredient);
        for (String string : strings) {
            searchStorage.put(string, ingredient);
        }
    }

    @Override
    public void submitAll(NonNullList<IIngredientListElement> ingredients) {
        for (IIngredientListElement ingredient : ingredients) {
            submit(ingredient);
        }
    }

    @Override
    public void getSearchResults(String token, Set<IIngredientListElement<?>> results) {
        searchStorage.getSearchResults(token, results);
    }

    @Override
    public void getAllElements(Set<IIngredientListElement<?>> results) {
        searchStorage.getAllElements(results);
    }

    @Override
    public void start() {
        this.timer = new LoggedTimer();
        this.timer.start("Building [" + prefixInfo.getDesc() + "] search tree");
    }

    @Override
    public void stop() {
        this.timer.stop();
        this.timer = null;
    }

}
