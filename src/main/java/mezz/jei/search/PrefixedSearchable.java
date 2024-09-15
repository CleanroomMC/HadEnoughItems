package mezz.jei.search;

import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.util.LoggedTimer;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.ProgressManager;

import java.util.Collection;
import java.util.Objects;
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
        if (prefixInfo.getMode() == Config.SearchMode.DISABLED) {
            return;
        }
        Collection<String> strings = prefixInfo.getStrings(ingredient);
        for (String string : strings) {
            searchStorage.put(string, ingredient);
        }
    }

    @Override
    public void submitAll(NonNullList<IIngredientListElement> ingredients) {
        if (prefixInfo.getMode() == Config.SearchMode.DISABLED) {
            return;
        }
        if (IngredientFilter.firstBuild) {
            start();
            ProgressManager.ProgressBar progressBar = null;
            if (!IngredientFilter.rebuild) {
                long modNameCount = ingredients.stream()
                        .map(IIngredientListElement::getModNameForSorting)
                        .distinct()
                        .count();
                progressBar = ProgressManager.push("Indexing ingredients", (int) modNameCount);
            }
            String currentModName = null;
            for (IIngredientListElement ingredient : ingredients) {
                String modname = ingredient.getModNameForSorting();
                if (!Objects.equals(currentModName, modname)) {
                    currentModName = modname;
                    if (progressBar != null) {
                        progressBar.step(modname);
                    }
                }
                submit(ingredient);
            }
            if (progressBar != null) {
                ProgressManager.pop(progressBar);
            }
            stop();
        } else {
            ProgressManager.ProgressBar progressBar = ProgressManager.push("Adding ingredients at runtime", ingredients.size());
            for (IIngredientListElement ingredient : ingredients) {
                progressBar.step(ingredient.getDisplayName());
                submit(ingredient);
            }
            ProgressManager.pop(progressBar);
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
        if (this.timer != null) {
            this.timer.stop();
            this.timer = null;
        }
    }

}
