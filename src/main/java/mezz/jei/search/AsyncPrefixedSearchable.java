package mezz.jei.search;

import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.LoggedTimer;
import net.minecraft.util.NonNullList;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncPrefixedSearchable extends PrefixedSearchable {

    private ExecutorService service;

    public AsyncPrefixedSearchable(ISearchStorage<IIngredientListElement<?>> searchStorage, PrefixInfo prefixInfo) {
        super(searchStorage, prefixInfo);
    }

    @Override
    public void submitAll(NonNullList<IIngredientListElement> ingredients) {
        start();
        this.service.submit(() -> {
            for (IIngredientListElement ingredient : ingredients) {
                submit(ingredient);
            }
            stop();
        });
    }

    @Override
    public void start() {
        this.service = Executors.newFixedThreadPool(1);
        this.timer = new LoggedTimer();
        this.timer.start("Building [" + prefixInfo.getDesc() + "] search tree in a separate thread");
    }

    @Override
    public void stop() {
        if (this.service != null) {
            this.service.shutdownNow().forEach(Runnable::run);
            this.service = null;
            if (this.timer != null) {
                super.stop();
            }
        }
    }

}
