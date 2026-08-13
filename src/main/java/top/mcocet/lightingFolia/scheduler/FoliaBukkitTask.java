package top.mcocet.lightingFolia.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * BukkitTask implementation for Folia scheduled tasks.
 */
public class FoliaBukkitTask implements BukkitTask {

    private final Plugin plugin;
    private final int taskId;
    private final boolean sync;
    private final Object foliaTask;
    private volatile boolean cancelled = false;

    public FoliaBukkitTask(Plugin plugin, int taskId, boolean sync, Object foliaTask) {
        this.plugin = plugin;
        this.taskId = taskId;
        this.sync = sync;
        this.foliaTask = foliaTask;
    }

    @Override
    public int getTaskId() {
        return taskId;
    }

    @Override
    public Plugin getOwner() {
        return plugin;
    }

    @Override
    public boolean isSync() {
        return sync;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        cancelled = true;
        if (foliaTask != null) {
            try {
                java.lang.reflect.Method cancelMethod = foliaTask.getClass().getMethod("cancel");
                cancelMethod.invoke(foliaTask);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
