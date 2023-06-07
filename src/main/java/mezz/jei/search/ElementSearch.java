package mezz.jei.search;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.Log;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ElementSearch implements IElementSearch {

    private final Map<PrefixInfo, PrefixedSearchable> prefixedSearchables = new Reference2ObjectArrayMap<>();
    private final CombinedSearchables<IIngredientListElement<?>> combinedSearchables = new CombinedSearchables<>();

    private boolean loggedStatistics = false;

    public ElementSearch() {
        if (Config.isSearchTreeBuildingAsync()) {
            AsyncPrefixedSearchable.startService();
        }

        ISearchStorage<IIngredientListElement<?>> storage = PrefixInfo.NO_PREFIX.createStorage();
        PrefixedSearchable searchable = new PrefixedSearchable(storage, PrefixInfo.NO_PREFIX);
        this.prefixedSearchables.put(PrefixInfo.NO_PREFIX, searchable);
        this.combinedSearchables.addSearchable(searchable);

        for (PrefixInfo prefixInfo : PrefixInfo.all()) {
            storage = prefixInfo.createStorage();
            searchable = Config.isSearchTreeBuildingAsync() && prefixInfo.isAsyncable() ?
                    new AsyncPrefixedSearchable(storage, prefixInfo) :
                    new PrefixedSearchable(storage, prefixInfo);
            this.prefixedSearchables.put(prefixInfo, searchable);
            this.combinedSearchables.addSearchable(searchable);
        }
    }

    public void block() {
        if (Config.isSearchTreeBuildingAsync()) {
            AsyncPrefixedSearchable.endService();
            for (PrefixedSearchable prefixedSearchable : this.prefixedSearchables.values()) {
                prefixedSearchable.stop();
            }
        }
        if (!this.loggedStatistics && FMLLaunchHandler.isDeobfuscatedEnvironment()) {
            this.loggedStatistics = true;
            this.logStatistics();
        }
    }

    @Override
    public Set<IIngredientListElement<?>> getSearchResults(TokenInfo tokenInfo) {
        String token = tokenInfo.token;
        if (token.isEmpty()) {
            return Collections.emptySet();
        }
        Set<IIngredientListElement<?>> results = new ReferenceOpenHashSet<>();
        PrefixInfo prefixInfo = tokenInfo.prefixInfo;
        if (prefixInfo == PrefixInfo.NO_PREFIX) {
            combinedSearchables.getSearchResults(token, results);
            return results;
        }
        final ISearchable<IIngredientListElement<?>> searchable = this.prefixedSearchables.get(prefixInfo);
        if (searchable == null || searchable.getMode() == Config.SearchMode.DISABLED) {
            combinedSearchables.getSearchResults(token, results);
            return results;
        }
        searchable.getSearchResults(token, results);
        return results;
    }

    @Override
    public void add(IIngredientListElement<?> ingredient) {
        for (PrefixedSearchable prefixedSearchable : this.prefixedSearchables.values()) {
            prefixedSearchable.submit(ingredient);
        }
    }

    @Override
    public void addAll(NonNullList<IIngredientListElement> ingredients) {
        for (PrefixedSearchable prefixedSearchable : this.prefixedSearchables.values()) {
            prefixedSearchable.submitAll(ingredients);
        }
    }

    @Override
    public Set<IIngredientListElement<?>> getAllIngredients() {
        Set<IIngredientListElement<?>> results = new ReferenceOpenHashSet<>();
        this.prefixedSearchables.get(PrefixInfo.NO_PREFIX).getAllElements(results);
        return results;
    }

    @Override
    public void logStatistics() {
        for (Map.Entry<PrefixInfo, PrefixedSearchable> entry : this.prefixedSearchables.entrySet()) {
            PrefixInfo prefixInfo = entry.getKey();
            if (prefixInfo.getMode() != Config.SearchMode.DISABLED) {
                ISearchStorage<IIngredientListElement<?>> storage = entry.getValue().getSearchStorage();
                Log.get().info("ElementSearch {} Storage Stats: {}", prefixInfo, storage.statistics());
                try {
                    FileWriter fileWriter = new FileWriter("GeneralizedSuffixTree-" + prefixInfo + ".dot");
                    try (PrintWriter out = new PrintWriter(fileWriter)) {
                        storage.printTree(out, false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}