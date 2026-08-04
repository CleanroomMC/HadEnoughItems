package mezz.jei.search;

import mezz.jei.config.Config;
import mezz.jei.api.search.ISearchIndex;
import mezz.jei.api.search.ISearchIndexBuilder;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.ingredients.IngredientFilter;
import mezz.jei.util.LoggedTimer;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.ProgressManager;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public class PrefixedSearchable implements ISearchable<IIngredientListElement<?>>, IBuildable {

    protected final ISearchIndexBuilder<IIngredientListElement<?>> searchIndexBuilder;
    protected final PrefixInfo prefixInfo;

    /**
     * Null until {@link #build()} bakes the builder's contents.
     * Submits that arrive afterwards go straight to the index, which handles them itself.
     */
    @Nullable
    protected volatile ISearchIndex<IIngredientListElement<?>> searchIndex;

    protected LoggedTimer timer;

    public PrefixedSearchable(ISearchIndexBuilder<IIngredientListElement<?>> searchIndexBuilder, PrefixInfo prefixInfo) {
        this.searchIndexBuilder = searchIndexBuilder;
        this.prefixInfo = prefixInfo;
    }

    @Nullable
    public ISearchIndex<IIngredientListElement<?>> getSearchIndex() {
        return searchIndex;
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
        ISearchIndex<IIngredientListElement<?>> index = this.searchIndex;
        for (String string : strings) {
            if (index == null) {
                searchIndexBuilder.put(string, ingredient);
            } else {
                index.put(string, ingredient);
            }
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
                if (!Config.skipShowingProgressBar()) {
                    progressBar = ProgressManager.push("Indexing ingredients", (int) modNameCount);
                }
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
        ISearchIndex<IIngredientListElement<?>> index = this.searchIndex;
        if (index != null) {
            index.getSearchResults(token, results);
        }
    }

    @Override
    public void getAllElements(Set<IIngredientListElement<?>> results) {
        ISearchIndex<IIngredientListElement<?>> index = this.searchIndex;
        if (index != null) {
            index.getAllElements(results);
        }
    }

    /**
     * Bakes everything submitted so far into the search index. Idempotent:
     * once built, later submits are handled by the index itself.
     */
    @Override
    public void build() {
        if (this.searchIndex == null) {
            this.searchIndex = this.searchIndexBuilder.build();
        }
    }

    @Override
    public void start() {
        this.timer = new LoggedTimer();
        this.timer.start("Building [" + prefixInfo.getDesc() + "] search index");
    }

    @Override
    public void stop() {
        build();
        if (this.timer != null) {
            this.timer.stop();
            this.timer = null;
        }
    }

}
