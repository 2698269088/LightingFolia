package top.mcocet.lightingFolia.transform;

import net.bytebuddy.agent.ByteBuddyAgent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import top.mcocet.lightingFolia.LightingFolia;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * ByteBuddy-based bytecode patcher for LightingFolia.
 * Dynamically modifies plugin classes to be Folia-compatible.
 */
public class BytecodePatcher {

    private static boolean initialized = false;
    private static JavaPlugin plugin;
    private static Instrumentation instrumentation;
    private static BridgeClassTransformer transformer;
    private static final AtomicInteger patchedPlugins = new AtomicInteger(0);
    private static final AtomicInteger patchedClasses = new AtomicInteger(0);

    /**
     * Initialize the bytecode patcher.
     */
    public static void init(JavaPlugin pluginInstance) {
        if (initialized) {
            return;
        }

        plugin = pluginInstance;

        try {
            // Install ByteBuddy agent
            instrumentation = ByteBuddyAgent.install();
            
            // Create and register transformer
            transformer = new BridgeClassTransformer();
            instrumentation.addTransformer(transformer, true);
            
            initialized = true;
            plugin.getLogger().info("[LightingFolia] Bytecode patcher initialized");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[LightingFolia] Failed to initialize bytecode patcher: " + e.getMessage(), e);
            throw new RuntimeException("Failed to initialize bytecode patcher", e);
        }
    }

    /**
     * Retransform all loaded classes from plugins.
     */
    public static int retransformAllLoadedClasses() {
        if (!initialized) {
            throw new IllegalStateException("Bytecode patcher not initialized");
        }

        List<Class<?>> targetClasses = new ArrayList<>();
        
        for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
            String name = clazz.getName();
            // Target common plugin package patterns
            if (shouldTransformClass(name)) {
                targetClasses.add(clazz);
            }
        }

        return retransformClasses(targetClasses);
    }

    /**
     * Retransform classes from a specific plugin.
     */
    public static void retransformPluginClasses(Plugin targetPlugin) {
        if (!initialized) {
            return;
        }

        List<Class<?>> targetClasses = new ArrayList<>();
        ClassLoader pluginLoader = targetPlugin.getClass().getClassLoader();
        
        for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
            if (clazz.getClassLoader() == pluginLoader) {
                targetClasses.add(clazz);
            }
        }

        if (!targetClasses.isEmpty()) {
            plugin.getLogger().info("[LightingFolia] Patching " + targetClasses.size() + " classes from " + targetPlugin.getName());
            retransformClasses(targetClasses);
            patchedPlugins.incrementAndGet();
        }
    }

    private static int retransformClasses(List<Class<?>> classes) {
        int success = 0;
        int failed = 0;

        for (Class<?> clazz : classes) {
            try {
                instrumentation.retransformClasses(clazz);
                success++;
                patchedClasses.incrementAndGet();
            } catch (UnmodifiableClassException e) {
                failed++;
                // Class cannot be modified (e.g., native methods)
            } catch (Exception e) {
                failed++;
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING, "[LightingFolia] Failed to retransform " + clazz.getName(), e);
                }
            }
        }

        if (plugin != null && success > 0) {
            plugin.getLogger().info("[LightingFolia] Retransformed " + success + " classes (" + failed + " failed)");
        }

        return success;
    }

    private static boolean shouldTransformClass(String className) {
        // Skip Java/JDK classes
        if (className.startsWith("java.") || className.startsWith("javax.") || 
            className.startsWith("sun.") || className.startsWith("com.sun.")) {
            return false;
        }
        
        // Skip Bukkit/Paper/Folia classes
        if (className.startsWith("org.bukkit.") || className.startsWith("net.minecraft.") ||
            className.startsWith("io.papermc.") || className.startsWith("ca.spottedleaf.")) {
            return false;
        }
        
        // Skip LightingFolia itself
        if (className.startsWith("top.mcocet.lightingFolia.")) {
            return false;
        }
        
        // Target plugin classes (common patterns)
        // This is a heuristic - actual transformation is controlled by the transformer
        return true;
    }

    public static int getPatchedPluginCount() {
        return patchedPlugins.get();
    }

    public static int getPatchedClassCount() {
        return patchedClasses.get();
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
