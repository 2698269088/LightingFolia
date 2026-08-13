package top.mcocet.lightingFolia.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * BukkitScheduler proxy that routes calls to Folia schedulers.
 * This allows plugins to use Bukkit.getScheduler() API while
 * transparently using Folia's region-aware schedulers.
 */
public class FoliaSchedulerProxy implements BukkitScheduler {

    private final SmartScheduler smartScheduler;
    private final BukkitScheduler bukkitScheduler;

    public FoliaSchedulerProxy(SmartScheduler smartScheduler) {
        this.smartScheduler = smartScheduler;
        this.bukkitScheduler = org.bukkit.Bukkit.getScheduler();
    }

    @Override
    public BukkitTask runTask(Plugin plugin, Runnable task) {
        smartScheduler.runGlobalTask(plugin, task);
        return new DummyBukkitTask(plugin, -1, true);
    }

    @Override
    public void runTask(Plugin plugin, Consumer<? super BukkitTask> task) {
        bukkitScheduler.runTask(plugin, task);
    }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) {
        return smartScheduler.runAsyncTask(plugin, task);
    }

    @Override
    public void runTaskAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task) {
        bukkitScheduler.runTaskAsynchronously(plugin, task);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        return smartScheduler.runGlobalTaskLater(plugin, task, delay);
    }

    @Override
    public void runTaskLater(Plugin plugin, Consumer<? super BukkitTask> task, long delay) {
        bukkitScheduler.runTaskLater(plugin, task, delay);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) {
        return smartScheduler.runAsyncTaskLater(plugin, task, delay);
    }

    @Override
    public void runTaskLaterAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task, long delay) {
        bukkitScheduler.runTaskLaterAsynchronously(plugin, task, delay);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        return smartScheduler.runGlobalTaskTimer(plugin, task, delay, period);
    }

    @Override
    public void runTaskTimer(Plugin plugin, Consumer<? super BukkitTask> task, long delay, long period) {
        bukkitScheduler.runTaskTimer(plugin, task, delay, period);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        return smartScheduler.runGlobalTaskTimer(plugin, task, delay, period);
    }

    @Override
    public void runTaskTimerAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task, long delay, long period) {
        bukkitScheduler.runTaskTimerAsynchronously(plugin, task, delay, period);
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task) {
        BukkitTask t = runTaskLater(plugin, task, 1);
        return t != null ? t.getTaskId() : -1;
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delay) {
        BukkitTask t = runTaskLater(plugin, task, delay);
        return t != null ? t.getTaskId() : -1;
    }

    @Override
    public int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        BukkitTask t = runTaskTimer(plugin, task, delay, period);
        return t != null ? t.getTaskId() : -1;
    }

    @Override
    public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task) {
        BukkitTask t = runTaskLaterAsynchronously(plugin, task, 1);
        return t != null ? t.getTaskId() : -1;
    }

    @Override
    public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task, long delay) {
        BukkitTask t = runTaskLaterAsynchronously(plugin, task, delay);
        return t != null ? t.getTaskId() : -1;
    }

    @Override
    public int scheduleAsyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        throw new UnsupportedOperationException("scheduleAsyncRepeatingTask is not supported on Folia");
    }

    @Override
    public <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> task) {
        return bukkitScheduler.callSyncMethod(plugin, task);
    }

    @Override
    public void cancelTask(int taskId) {
        bukkitScheduler.cancelTask(taskId);
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        smartScheduler.cancelTasks(plugin);
    }

    @Override
    public boolean isCurrentlyRunning(int taskId) {
        return bukkitScheduler.isCurrentlyRunning(taskId);
    }

    @Override
    public boolean isQueued(int taskId) {
        return bukkitScheduler.isQueued(taskId);
    }

    @Override
    public List<BukkitWorker> getActiveWorkers() {
        return bukkitScheduler.getActiveWorkers();
    }

    @Override
    public List<BukkitTask> getPendingTasks() {
        return bukkitScheduler.getPendingTasks();
    }

    @Override
    public Executor getMainThreadExecutor(Plugin plugin) {
        return bukkitScheduler.getMainThreadExecutor(plugin);
    }

    @Override
    public BukkitTask runTask(Plugin plugin, org.bukkit.scheduler.BukkitRunnable task) {
        return runTask(plugin, (Runnable) task);
    }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, org.bukkit.scheduler.BukkitRunnable task) {
        return runTaskAsynchronously(plugin, (Runnable) task);
    }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay) {
        return runTaskLater(plugin, (Runnable) task, delay);
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay) {
        return runTaskLaterAsynchronously(plugin, (Runnable) task, delay);
    }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay, long period) {
        return runTaskTimer(plugin, (Runnable) task, delay, period);
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay, long period) {
        return runTaskTimerAsynchronously(plugin, (Runnable) task, delay, period);
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, org.bukkit.scheduler.BukkitRunnable task) {
        BukkitTask t = runTaskLater(plugin, (Runnable) task, 1);
        return t != null ? t.getTaskId() : -1;
    }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay) {
        BukkitTask t = runTaskLater(plugin, (Runnable) task, delay);
        return t != null ? t.getTaskId() : -1;
    }

    @Override
    public int scheduleSyncRepeatingTask(Plugin plugin, org.bukkit.scheduler.BukkitRunnable task, long delay, long period) {
        BukkitTask t = runTaskTimer(plugin, (Runnable) task, delay, period);
        return t != null ? t.getTaskId() : -1;
    }
}
