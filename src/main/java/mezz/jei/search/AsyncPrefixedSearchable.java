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

    @Override
    public void start() {
        this.service = Executors.newFixedThreadPool(1);
        this.timer = new LoggedTimer();
        this.timer.start("Building [" + prefixInfo.getDesc() + "] search tree in a separate thread");
    }

    @Override
    public void stop() {
        this.service.shutdownNow().forEach(Runnable::run);
        this.service = null;
        super.stop();
    }

}
