package top.mcocet.lightingFolia;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import top.mcocet.lightingFolia.config.BridgeConfig;
import top.mcocet.lightingFolia.scheduler.SmartScheduler;
import top.mcocet.lightingFolia.thread.EntityThreadHelper;
import top.mcocet.lightingFolia.thread.WorldThreadHelper;
import top.mcocet.lightingFolia.transform.BytecodePatcher;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * LightingFolia - Universal Folia compatibility bridge for Bukkit plugins.
 *
 * This plugin provides runtime compatibility for traditional Bukkit plugins
 * on Folia servers through bytecode patching and thread-safe wrappers.
 */
public final class LightingFolia extends JavaPlugin implements Listener {

    private static LightingFolia instance;
    private boolean isFolia;
    private BridgeConfig bridgeConfig;
    private SmartScheduler smartScheduler;
    private boolean patcherInitialized = false;

    @Override
    public void onEnable() {
        instance = this;

        // Detect Folia
        isFolia = detectFolia();

        // Save default config
        saveDefaultConfig();
        bridgeConfig = new BridgeConfig(this);

        if (!isFolia) {
            getLogger().info("LightingFolia is running in compatibility mode (Folia not detected)");
            return;
        }

        getLogger().info("LightingFolia v" + getDescription().getVersion());
        getLogger().info("Folia detected - Compatibility bridge active");

        // Initialize helpers
        EntityThreadHelper.init(this);
        WorldThreadHelper.init(this);

        // Initialize smart scheduler
        smartScheduler = new SmartScheduler(this);

        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);

        // Initialize bytecode patcher if enabled
        if (bridgeConfig.isBytecodePatchEnabled()) {
            initBytecodePatcher();
        }

        getLogger().info("LightingFolia enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (smartScheduler != null) {
            smartScheduler.shutdown();
        }
        getLogger().info("LightingFolia disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("lightingfolia")) {
            return false;
        }

        if (!sender.hasPermission("lightingfolia.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                reloadConfig();
                bridgeConfig.reload();
                sender.sendMessage("§LightingFolia configuration reloaded!");
                break;
            case "status":
                sendStatus(sender);
                break;
            case "patch":
                if (args.length > 1 && args[1].equalsIgnoreCase("apply")) {
                    applyPatches(sender);
                } else {
                    sender.sendMessage("§eUsage: /lightingfolia patch apply");
                }
                break;
            case "help":
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("lightingfolia")) {
            return null;
        }

        if (args.length == 1) {
            return Arrays.asList("reload", "status", "patch", "help");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("patch")) {
            return Arrays.asList("apply");
        }

        return null;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (!isFolia || !bridgeConfig.isBytecodePatchEnabled()) {
            return;
        }

        String pluginName = event.getPlugin().getName();

        // Skip excluded plugins
        if (bridgeConfig.isPluginExcluded(pluginName)) {
            return;
        }

        // Check if plugin needs patching
        if (bridgeConfig.shouldPatchPlugin(pluginName)) {
            getLogger().info("Detected plugin that may need patching: " + pluginName);

            // Retransform loaded classes from this plugin
            if (patcherInitialized) {
                try {
                    BytecodePatcher.retransformPluginClasses(event.getPlugin());
                } catch (Exception e) {
                    getLogger().log(Level.WARNING, "Failed to patch plugin " + pluginName + ": " + e.getMessage(), e);
                }
            }
        }
    }

    private void initBytecodePatcher() {
        try {
            BytecodePatcher.init(this);
            patcherInitialized = true;
            getLogger().info("Bytecode patcher initialized");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize bytecode patcher: " + e.getMessage(), e);
            getLogger().warning("Some plugins may not work correctly without bytecode patching");
        }
    }

    private void applyPatches(CommandSender sender) {
        if (!isFolia) {
            sender.sendMessage("§cNot running on Folia - no patches needed");
            return;
        }

        if (!patcherInitialized) {
            sender.sendMessage("§cBytecode patcher is not initialized");
            return;
        }

        sender.sendMessage("§eApplying patches to loaded plugins...");

        try {
            int patched = BytecodePatcher.retransformAllLoadedClasses();
            sender.sendMessage("§aSuccessfully patched " + patched + " classes");
        } catch (Exception e) {
            sender.sendMessage("§cFailed to apply patches: " + e.getMessage());
            getLogger().log(Level.SEVERE, "Patch application failed", e);
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage("§6=== LightingFolia Status ===");
        sender.sendMessage("§7Version: §f" + getDescription().getVersion());
        sender.sendMessage("§7Folia detected: §f" + isFolia);
        sender.sendMessage("§7Bytecode patcher: §f" + (patcherInitialized ? "Active" : "Inactive"));

        if (smartScheduler != null) {
            sender.sendMessage("§7Smart scheduler: §f" + smartScheduler.getMode());
        }

        sender.sendMessage("§7Patched plugins: §f" + BytecodePatcher.getPatchedPluginCount());
        sender.sendMessage("§7Patched classes: §f" + BytecodePatcher.getPatchedClassCount());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== LightingFolia Commands ===");
        sender.sendMessage("§e/lightingfolia reload §7- Reload configuration");
        sender.sendMessage("§e/lightingfolia status §7- Show plugin status");
        sender.sendMessage("§e/lightingfolia patch apply §7- Apply patches to loaded plugins");
        sender.sendMessage("§e/lightingfolia help §7- Show this help");
    }

    private boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static LightingFolia getInstance() {
        return instance;
    }

    public boolean isFolia() {
        return isFolia;
    }

    public BridgeConfig getBridgeConfig() {
        return bridgeConfig;
    }

    public SmartScheduler getSmartScheduler() {
        return smartScheduler;
    }

    public boolean isPatcherInitialized() {
        return patcherInitialized;
    }
}
