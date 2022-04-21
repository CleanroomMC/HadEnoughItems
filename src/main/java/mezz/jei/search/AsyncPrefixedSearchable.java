package mezz.jei.search;

import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.LoggedTimer;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncPrefixedSearchable extends PrefixedSearchable {

    private LoggedTimer timer;
    private ExecutorService service;

    public AsyncPrefixedSearchable(ISearchStorage<IIngredientListElement<?>> searchStorage, PrefixInfo prefixInfo) {
        super(searchStorage, prefixInfo);
    }

    public void start() {
        this.timer = new LoggedTimer();
        this.timer.start("HEI " + prefixInfo.getDesc() + " thread");
        this.service = Executors.newFixedThreadPool(1);
    }

    @Override
    public void submit(IIngredientListElement<?> info) {
        if (this.service == null) {
            super.submit(info);
        } else {
            this.service.submit(() -> {
                if (prefixInfo.getMode() != Config.SearchMode.DISABLED) {
                    Collection<String> strings = prefixInfo.getStrings(info);
                    for (String string : strings) {
                        searchStorage.put(string, info);
                    }
                }
            });
        }
    }

    public void stop() {
        this.service.shutdownNow().forEach(Runnable::run);
        this.service = null;
        timer.stop();
        this.timer = null;
    }

}
