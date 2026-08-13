
package top.mcocet.lightingFolia.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import top.mcocet.lightingFolia.LightingFolia;
import top.mcocet.lightingFolia.config.BridgeConfig;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Smart scheduler that automatically routes tasks to the appropriate scheduler
 * based on context and plugin compatibility.
 *
 * Modes:
 * - AUTO: Automatically choose based on plugin and context
 * - FOLIA: Always use Folia schedulers
 * - BUKKIT: Always use Bukkit scheduler (compatibility mode)
 */
public class SmartScheduler {

    private final LightingFolia plugin;
    private final BridgeConfig config;
    private final String mode;

    // Folia scheduler instances (initialized via reflection)
    private Object globalRegionScheduler;
    private Object asyncScheduler;
    private Object regionScheduler;
    private Method globalExecute;
    private Method globalRunDelayed;
    private Method globalRunAtFixedRate;
    private Method globalCancelTasks;
    private Method asyncRunNow;
    private Method asyncRunDelayed;
    private Method asyncRunAtFixedRate;
    private Method asyncCancelTasks;
    private Method regionExecute;
    private Method regionRunDelayed;
    private Method regionRunAtFixedRate;
    private Method scheduledTaskCancel;

    // Task ID management
    private static final int ID_START = 2_000_000;
    private final AtomicInteger nextId = new AtomicInteger(ID_START);
    private final ConcurrentHashMap<Integer, Object> foliaTasks = new ConcurrentHashMap<>();

    // Cached BukkitScheduler proxy
    private BukkitScheduler schedulerProxy;

    public SmartScheduler(LightingFolia plugin) {
        this.plugin = plugin;
        this.config = plugin.getBridgeConfig();
        this.mode = config.getSchedulerMode();

        if (plugin.isFolia()) {
            initFoliaSchedulers();
        }
    }

    private void initFoliaSchedulers() {
        try {
            Object server = Bukkit.getServer();
            Class<?> serverClass = server.getClass();

            globalRegionScheduler = serverClass.getMethod("getGlobalRegionScheduler").invoke(server);
            asyncScheduler = serverClass.getMethod("getAsyncScheduler").invoke(server);
            regionScheduler = serverClass.getMethod("getRegionScheduler").invoke(server);

            Class<?> pluginClass = Class.forName("org.bukkit.plugin.Plugin");
            Class<?> runnableClass = Runnable.class;
            Class<?> consumerClass = Consumer.class;
            Class<?> locationClass = Class.forName("org.bukkit.Location");

            // Global region scheduler methods
            Class<?> globalClass = globalRegionScheduler.getClass();
            globalExecute = globalClass.getMethod("execute", pluginClass, runnableClass);
            globalRunDelayed = globalClass.getMethod("runDelayed", pluginClass, consumerClass, long.class);
            globalRunAtFixedRate = globalClass.getMethod("runAtFixedRate", pluginClass, consumerClass, long.class, long.class);
            globalCancelTasks = globalClass.getMethod("cancelTasks", pluginClass);

            // Async scheduler methods
            Class<?> asyncClass = asyncScheduler.getClass();
            asyncRunNow = asyncClass.getMethod("runNow", pluginClass, consumerClass);
            asyncRunDelayed = asyncClass.getMethod("runDelayed", pluginClass, consumerClass, long.class, TimeUnit.class);
            asyncRunAtFixedRate = asyncClass.getMethod("runAtFixedRate", pluginClass, consumerClass, long.class, long.class, TimeUnit.class);
            asyncCancelTasks = asyncClass.getMethod("cancelTasks", pluginClass);

            // Region scheduler methods
            Class<?> regionClass = regionScheduler.getClass();
            regionExecute = regionClass.getMethod("execute", pluginClass, locationClass, runnableClass);
            regionRunDelayed = regionClass.getMethod("runDelayed", pluginClass, locationClass, consumerClass, long.class);
            regionRunAtFixedRate = regionClass.getMethod("runAtFixedRate", pluginClass, locationClass, consumerClass, long.class, long.class);

            Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
            scheduledTaskCancel = scheduledTaskClass.getMethod("cancel");

            plugin.getLogger().info("Folia schedulers initialized");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize Folia schedulers: " + e.getMessage(), e);
        }
    }

    /**
     * Get the appropriate scheduler for a plugin.
     * Returns a BukkitScheduler proxy that routes to Folia schedulers.
     */
    public BukkitScheduler getScheduler(Plugin targetPlugin) {
        if (!plugin.isFolia()) {
            return Bukkit.getScheduler();
        }

        if ("BUKKIT".equals(mode) || config.shouldUseBukkitScheduler(targetPlugin.getName())) {
            return Bukkit.getScheduler();
        }

        if (schedulerProxy == null) {
            schedulerProxy = new FoliaSchedulerProxy(this);
        }

        return schedulerProxy;
    }

    /**
     * Get the BukkitScheduler proxy for bytecode-patched plugins.
     * This is called by transformed classes via INVOKESTATIC.
     */
    public static BukkitScheduler getScheduler() {
        LightingFolia bridge = LightingFolia.getInstance();
        if (bridge == null || !bridge.isFolia()) {
            return Bukkit.getScheduler();
        }
        SmartScheduler scheduler = bridge.getSmartScheduler();
        if (scheduler == null) {
            return Bukkit.getScheduler();
        }
        if (scheduler.schedulerProxy == null) {
            scheduler.schedulerProxy = new FoliaSchedulerProxy(scheduler);
        }
        return scheduler.schedulerProxy;
    }

    /**
     * Execute a task on the global region thread.
     */
    public void runGlobalTask(Plugin targetPlugin, Runnable task) {
        if (!plugin.isFolia() || "BUKKIT".equals(mode)) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        try {
            globalExecute.invoke(globalRegionScheduler, targetPlugin, wrap(targetPlugin, task));
        } catch (Exception e) {
            logException(targetPlugin, "runGlobalTask", e);
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Execute a task on an entity's region thread.
     */
    public void runEntityTask(Plugin targetPlugin, Entity entity, Runnable task) {
        if (!plugin.isFolia() || "BUKKIT".equals(mode)) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        try {
            regionExecute.invoke(regionScheduler, targetPlugin, entity.getLocation(), wrap(targetPlugin, task));
        } catch (Exception e) {
            logException(targetPlugin, "runEntityTask", e);
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Execute a task at a location's region thread.
     */
    public void runLocationTask(Plugin targetPlugin, Location location, Runnable task) {
        if (!plugin.isFolia() || "BUKKIT".equals(mode)) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        try {
            regionExecute.invoke(regionScheduler, targetPlugin, location, wrap(targetPlugin, task));
        } catch (Exception e) {
            logException(targetPlugin, "runLocationTask", e);
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Execute an async task.
     */
    public BukkitTask runAsyncTask(Plugin targetPlugin, Runnable task) {
        if (!plugin.isFolia() || "BUKKIT".equals(mode)) {
            return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }

        try {
            Object foliaTask = asyncRunNow.invoke(asyncScheduler, targetPlugin, (Consumer<Object>) t -> wrap(targetPlugin, task).run());
            int id = nextId.getAndIncrement();
            foliaTasks.put(id, foliaTask);
            return new FoliaBukkitTask(targetPlugin, id, false, foliaTask);
        } catch (Exception e) {
            logException(targetPlugin, "runAsyncTask", e);
            return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Execute a delayed task on the global region thread.
     */
    public BukkitTask runGlobalTaskLater(Plugin targetPlugin, Runnable task, long delay) {
        if (!plugin.isFolia() || "BUKKIT".equals(mode)) {
            return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }

        try {
            Object foliaTask = globalRunDelayed.invoke(globalRegionScheduler, targetPlugin,
                    (Consumer<Object>) t -> wrap(targetPlugin, task).run(), Math.max(1L, delay));
            int id = nextId.getAndIncrement();
            foliaTasks.put(id, foliaTask);
            return new FoliaBukkitTask(targetPlugin, id, true, foliaTask);
        } catch (Exception e) {
            logException(targetPlugin, "runGlobalTaskLater", e);
            return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    /**
     * Execute a delayed async task.
     */
    public BukkitTask runAsyncTaskLater(Plugin targetPlugin, Runnable task, long delay) {
        if (!plugin.isFolia() || "BUKKIT".equals(mode)) {
            return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }

        try {
            Object foliaTask = asyncRunDelayed.invoke(asyncScheduler, targetPlugin,
                    (Consumer<Object>) t -> wrap(targetPlugin, task).run(),
                    Math.max(1L, delay) * 50L, TimeUnit.MILLISECONDS);
            int id = nextId.getAndIncrement();
            foliaTasks.put(id, foliaTask);
            return new FoliaBukkitTask(targetPlugin, id, false, foliaTask);
        } catch (Exception e) {
            logException(targetPlugin, "runAsyncTaskLater", e);
            return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
        }
    }

    /**
     * Execute a repeating task on the global region thread.
     */
    public BukkitTask runGlobalTaskTimer(Plugin targetPlugin, Runnable task, long delay, long period) {
        if (!plugin.isFolia() || "BUKKIT".equals(mode)) {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }

        try {
            Object foliaTask = globalRunAtFixedRate.invoke(globalRegionScheduler, targetPlugin,
                    (Consumer<Object>) t -> wrap(targetPlugin, task).run(),
                    Math.max(1L, delay), Math.max(1L, period));
            int id = nextId.getAndIncrement();
            foliaTasks.put(id, foliaTask);
            return new FoliaBukkitTask(targetPlugin, id, true, foliaTask);
        } catch (Exception e) {
            logException(targetPlugin, "runGlobalTaskTimer", e);
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        }
    }

    /**
     * Cancel all tasks for a plugin.
     */
    public void cancelTasks(Plugin targetPlugin) {
        if (!plugin.isFolia()) {
            Bukkit.getScheduler().cancelTasks(targetPlugin);
            return;
        }

        try {
            if (globalCancelTasks != null) globalCancelTasks.invoke(globalRegionScheduler, targetPlugin);
            if (asyncCancelTasks != null) asyncCancelTasks.invoke(asyncScheduler, targetPlugin);
            foliaTasks.clear();
        } catch (Exception e) {
            logException(targetPlugin, "cancelTasks", e);
        }

        Bukkit.getScheduler().cancelTasks(targetPlugin);
    }

    public void shutdown() {
        foliaTasks.clear();
    }

    public String getMode() {
        return mode;
    }

    private Runnable wrap(Plugin targetPlugin, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                targetPlugin.getLogger().log(Level.WARNING, "Task exception in LightingFolia wrapper", e);
            }
        };
    }

    private void logException(Plugin targetPlugin, String method, Exception e) {
        if (config.isVerboseDebug()) {
            plugin.getLogger().log(Level.WARNING, "[LightingFolia] " + method + " failed for " + targetPlugin.getName() + ": " + e.getMessage(), e);
        }
    }
}
