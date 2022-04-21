package mezz.jei.search;

import mezz.jei.config.Config;
import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.LoggedTimer;
import net.minecraft.util.NonNullList;

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
    public void submit(IIngredientListElement<?> ingredient) {
        if (this.service == null) {
            super.submit(ingredient);
        } else {
            this.service.submit(() -> super.submit(ingredient));
        }
    }

    @Override
    public void submitAll(NonNullList<IIngredientListElement> ingredients) {
        if (this.service == null) {
            super.submitAll(ingredients);
        } else {
            this.service.submit(() -> super.submitAll(ingredients));
        }
    }

    public void stop() {
        this.service.shutdownNow().forEach(Runnable::run);
        this.service = null;
        timer.stop();
        this.timer = null;
    }

}
