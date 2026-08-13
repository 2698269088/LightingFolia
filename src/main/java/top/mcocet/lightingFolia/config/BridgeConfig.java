package top.mcocet.lightingFolia.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

/**
 * Configuration manager for LightingFolia.
 * Handles all plugin configuration settings.
 */
public class BridgeConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    
    // Cached values
    private boolean enabled;
    private boolean bytecodePatchEnabled;
    private boolean autoDispatchEntity;
    private boolean autoDispatchWorld;
    private boolean cacheEntityState;
    private long cacheCleanupInterval;
    private String schedulerMode;
    private boolean allowBukkitFallback;
    private boolean verboseDebug;
    private boolean logThreadFailures;
    private boolean logSchedulerRedirects;
    private boolean logEntityDispatches;
    private Set<String> excludedPlugins;
    private Set<String> forceBukkitScheduler;
    private Set<String> forceFoliaScheduler;
    private Map<String, PluginPatchConfig> pluginConfigs;

    public BridgeConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadValues();
    }

    private void loadValues() {
        // Main settings
        enabled = config.getBoolean("enabled", true);
        
        // Bytecode patch settings
        ConfigurationSection bytecodeSection = config.getConfigurationSection("bytecode-patch");
        if (bytecodeSection != null) {
            bytecodePatchEnabled = bytecodeSection.getBoolean("enabled", true);
            
            // Plugin-specific configs
            pluginConfigs = new HashMap<>();
            ConfigurationSection pluginsSection = bytecodeSection.getConfigurationSection("plugins");
            if (pluginsSection != null) {
                for (String pluginName : pluginsSection.getKeys(false)) {
                    ConfigurationSection pluginSection = pluginsSection.getConfigurationSection(pluginName);
                    if (pluginSection != null) {
                        pluginConfigs.put(pluginName, new PluginPatchConfig(
                            pluginSection.getBoolean("enabled", true),
                            pluginSection.getBoolean("entity-operations", true),
                            pluginSection.getBoolean("scheduler-redirect", true),
                            pluginSection.getBoolean("world-operations", true)
                        ));
                    }
                }
            }
        } else {
            bytecodePatchEnabled = true;
            pluginConfigs = new HashMap<>();
        }
        
        // Thread safety settings
        ConfigurationSection threadSection = config.getConfigurationSection("thread-safety");
        if (threadSection != null) {
            autoDispatchEntity = threadSection.getBoolean("auto-dispatch-entity", true);
            autoDispatchWorld = threadSection.getBoolean("auto-dispatch-world", true);
            cacheEntityState = threadSection.getBoolean("cache-entity-state", true);
            cacheCleanupInterval = threadSection.getLong("cache-cleanup-interval", 6000);
        } else {
            autoDispatchEntity = true;
            autoDispatchWorld = true;
            cacheEntityState = true;
            cacheCleanupInterval = 6000;
        }
        
        // Scheduler settings
        ConfigurationSection schedulerSection = config.getConfigurationSection("scheduler");
        if (schedulerSection != null) {
            schedulerMode = schedulerSection.getString("mode", "AUTO").toUpperCase();
            allowBukkitFallback = schedulerSection.getBoolean("allow-bukkit-fallback", true);
            forceBukkitScheduler = new HashSet<>(schedulerSection.getStringList("force-bukkit-scheduler"));
            forceFoliaScheduler = new HashSet<>(schedulerSection.getStringList("force-folia-scheduler"));
        } else {
            schedulerMode = "AUTO";
            allowBukkitFallback = true;
            forceBukkitScheduler = new HashSet<>();
            forceFoliaScheduler = new HashSet<>();
        }
        
        // Debug settings
        ConfigurationSection debugSection = config.getConfigurationSection("debug");
        if (debugSection != null) {
            verboseDebug = debugSection.getBoolean("verbose", false);
            logThreadFailures = debugSection.getBoolean("log-thread-failures", true);
            logSchedulerRedirects = debugSection.getBoolean("log-scheduler-redirects", false);
            logEntityDispatches = debugSection.getBoolean("log-entity-dispatches", false);
        } else {
            verboseDebug = false;
            logThreadFailures = true;
            logSchedulerRedirects = false;
            logEntityDispatches = false;
        }
        
        // Compatibility settings
        ConfigurationSection compatSection = config.getConfigurationSection("compatibility");
        if (compatSection != null) {
            excludedPlugins = new HashSet<>(compatSection.getStringList("excluded-plugins"));
        } else {
            excludedPlugins = new HashSet<>(Arrays.asList("LightingFolia", "AEAddon"));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isBytecodePatchEnabled() {
        return bytecodePatchEnabled;
    }

    public boolean isAutoDispatchEntity() {
        return autoDispatchEntity;
    }

    public boolean isAutoDispatchWorld() {
        return autoDispatchWorld;
    }

    public boolean isCacheEntityState() {
        return cacheEntityState;
    }

    public long getCacheCleanupInterval() {
        return cacheCleanupInterval;
    }

    public String getSchedulerMode() {
        return schedulerMode;
    }

    public boolean isAllowBukkitFallback() {
        return allowBukkitFallback;
    }

    public boolean isVerboseDebug() {
        return verboseDebug;
    }

    public boolean isLogThreadFailures() {
        return logThreadFailures;
    }

    public boolean isLogSchedulerRedirects() {
        return logSchedulerRedirects;
    }

    public boolean isLogEntityDispatches() {
        return logEntityDispatches;
    }

    public boolean isPluginExcluded(String pluginName) {
        return excludedPlugins.contains(pluginName);
    }

    public boolean shouldUseBukkitScheduler(String pluginName) {
        if (forceBukkitScheduler.contains(pluginName)) {
            return true;
        }
        if (forceFoliaScheduler.contains(pluginName)) {
            return false;
        }
        // Check if plugin declares folia-supported
        return !isPluginFoliaSupported(pluginName);
    }

    public boolean shouldPatchPlugin(String pluginName) {
        if (isPluginExcluded(pluginName)) {
            return false;
        }
        
        PluginPatchConfig patchConfig = pluginConfigs.get(pluginName);
        if (patchConfig != null) {
            return patchConfig.isEnabled();
        }
        
        return true; // Default to patching
    }

    public PluginPatchConfig getPluginPatchConfig(String pluginName) {
        return pluginConfigs.getOrDefault(pluginName, new PluginPatchConfig(true, true, true, true));
    }

    private boolean isPluginFoliaSupported(String pluginName) {
        org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            return false;
        }
        
        try {
            // Check plugin.yml for folia-supported flag
            java.io.InputStream pluginYaml = plugin.getClass().getResourceAsStream("/plugin.yml");
            if (pluginYaml != null) {
                java.util.Scanner scanner = new java.util.Scanner(pluginYaml);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.contains("folia-supported: true")) {
                        scanner.close();
                        return true;
                    }
                }
                scanner.close();
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return false;
    }

    /**
     * Configuration for per-plugin patching.
     */
    public static class PluginPatchConfig {
        private final boolean enabled;
        private final boolean entityOperations;
        private final boolean schedulerRedirect;
        private final boolean worldOperations;

        public PluginPatchConfig(boolean enabled, boolean entityOperations, boolean schedulerRedirect, boolean worldOperations) {
            this.enabled = enabled;
            this.entityOperations = entityOperations;
            this.schedulerRedirect = schedulerRedirect;
            this.worldOperations = worldOperations;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public boolean isEntityOperations() {
            return entityOperations;
        }

        public boolean isSchedulerRedirect() {
            return schedulerRedirect;
        }

        public boolean isWorldOperations() {
            return worldOperations;
        }
    }
}