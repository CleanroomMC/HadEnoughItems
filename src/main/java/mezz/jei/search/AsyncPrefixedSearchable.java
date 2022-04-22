package mezz.jei.search;

import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.Log;
import mezz.jei.util.LoggedTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.NonNullList;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncPrefixedSearchable extends PrefixedSearchable {

    private ExecutorService service;

    private List<IIngredientListElement> leftovers; // strictly written by service thread and read by main thread

    public AsyncPrefixedSearchable(ISearchStorage<IIngredientListElement<?>> searchStorage, PrefixInfo prefixInfo) {
        super(searchStorage, prefixInfo);
    }

    @Override
    public void submitAll(NonNullList<IIngredientListElement> ingredients) {
        start();
        this.service.submit(() -> {
            for (IIngredientListElement ingredient : ingredients) {
                try {
                    submit(ingredient);
                } catch (Throwable t) {
                    Log.get().debug(prefixInfo.toString() + " building failed on ingredient: " + ingredient.getDisplayName(), t);
                    if (leftovers == null) {
                        this.leftovers = new ArrayList<>();
                    }
                    this.leftovers.add(ingredient);
                }
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
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread() && this.leftovers != null && !this.leftovers.isEmpty()) {
            Log.get().info("{} search tree had {} errors, moving onto the main thread to process these errors.", prefixInfo, this.leftovers.size());
            this.leftovers.forEach(this::submit);
            this.leftovers = null;
        }
    }

}
