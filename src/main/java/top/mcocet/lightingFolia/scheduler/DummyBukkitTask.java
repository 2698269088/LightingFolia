package top.mcocet.lightingFolia.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Dummy BukkitTask for immediate execution tasks.
 */
public class DummyBukkitTask implements BukkitTask {

    private final Plugin plugin;
    private final int taskId;
    private final boolean sync;
    private volatile boolean cancelled = false;

    public DummyBukkitTask(Plugin plugin, int taskId, boolean sync) {
        this.plugin = plugin;
        this.taskId = taskId;
        this.sync = sync;
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
    }
}
