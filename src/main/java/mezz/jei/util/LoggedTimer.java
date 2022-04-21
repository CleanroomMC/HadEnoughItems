package mezz.jei.util;

import com.google.common.base.Stopwatch;

public class LoggedTimer {

    private final Stopwatch stopWatch = Stopwatch.createUnstarted();
    private String message = "";

    public void start(String message) {
        this.message = message;
        Log.get().info("{}...", message);
        stopWatch.reset();
        stopWatch.start();
    }

    public void stop() {
        stopWatch.stop();
        Log.get().info("{} took {}", message, stopWatch);
    }

}
