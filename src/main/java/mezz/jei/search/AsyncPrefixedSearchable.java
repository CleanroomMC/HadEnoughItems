package mezz.jei.search;

import mezz.jei.gui.ingredients.IIngredientListElement;
import mezz.jei.util.Log;
import mezz.jei.util.LoggedTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.NonNullList;
import org.apache.commons.lang3.concurrent.ConcurrentRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AsyncPrefixedSearchable extends PrefixedSearchable {

    private static ExecutorService service;

    public static void startService() {
        service = Executors.newSingleThreadExecutor();
    }

    public static void endService() {
        if (service == null) {
            return;
        }
        service.shutdown();
        try {
            if (!service.awaitTermination(90, TimeUnit.SECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
        service = null;
    }

    private boolean firstBuild = true;
    private List<IIngredientListElement> leftovers; // strictly written by service thread and read by main thread

    public AsyncPrefixedSearchable(ISearchStorage<IIngredientListElement<?>> searchStorage, PrefixInfo prefixInfo) {
        super(searchStorage, prefixInfo);
    }

    @Override
    public void submitAll(NonNullList<IIngredientListElement> ingredients) {
        if (service != null) {
            service.submit(() -> {
                if (firstBuild) {
                    start();
                    firstBuild = false;
                }
                for (IIngredientListElement ingredient : ingredients) {
                    try {
                        submit(ingredient);
                    } catch (ConcurrentRuntimeException e) {
                        Log.get().error(prefixInfo + " building failed on ingredient: " + ingredient.getDisplayName(), e);
                        if (leftovers == null) {
                            this.leftovers = new ArrayList<>();
                        }
                        this.leftovers.add(ingredient);
                    }
                }
                stop();
            });
        } else {
            super.submitAll(ingredients);
        }
    }

    @Override
    public void start() {
        this.timer = new LoggedTimer();
        this.timer.start("Asynchronously building [" + prefixInfo.getDesc() + "] search tree");
    }

    @Override
    public void stop() {
        if (this.timer != null) {
            super.stop();
        }
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread() && this.leftovers != null && !this.leftovers.isEmpty()) {
            Log.get().info("{} search tree had {} errors, moving onto the main thread to process these errors.", prefixInfo, this.leftovers.size());
            this.leftovers.forEach(this::submit);
            this.leftovers = null;
        }
    }

}
