package top.mcocet.lightingFolia.thread;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe helper for world operations on Folia.
 * Automatically dispatches world operations to the correct region thread.
 */
public class WorldThreadHelper {

    private static final Logger LOGGER = Logger.getLogger("LightingFolia-World");

    private static boolean isFolia = false;
    private static boolean initialized = false;
    private static Object regionScheduler = null;
    private static Method executeMethod = null;
    private static JavaPlugin plugin = null;

    public static void init(JavaPlugin pluginInstance) {
        if (initialized) return;
        plugin = pluginInstance;

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;

            regionScheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            executeMethod = regionScheduler.getClass().getMethod("execute",
                    org.bukkit.plugin.Plugin.class, org.bukkit.Location.class, Runnable.class);

            LOGGER.info("[LightingFolia] WorldThreadHelper initialized");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            LOGGER.info("[LightingFolia] Not running on Folia, WorldThreadHelper disabled");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[LightingFolia] Failed to initialize WorldThreadHelper: " + e.getMessage(), e);
        }

        initialized = true;
    }

    /**
     * Execute a task on the region thread for a specific location.
     */
    public static void executeOnLocationThread(Location location, Runnable task) {
        if (!isFolia || regionScheduler == null || executeMethod == null) {
            task.run();
            return;
        }

        try {
            executeMethod.invoke(regionScheduler, plugin, location, task);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[LightingFolia] Failed to schedule location task: " + e.getMessage(), e);
            task.run();
        }
    }

    /**
     * Spawn an entity at a location, ensuring thread safety.
     */
    public static Entity spawnEntity(Location location, EntityType type) {
        if (!isFolia) {
            return location.getWorld().spawnEntity(location, type);
        }

        // For spawning, we need to be on the correct thread
        final Entity[] result = new Entity[1];
        executeOnLocationThread(location, () -> {
            result[0] = location.getWorld().spawnEntity(location, type);
        });
        return result[0];
    }

    /**
     * Set a block type at a location, ensuring thread safety.
     */
    public static void setBlockType(Location location, org.bukkit.Material material) {
        if (!isFolia) {
            location.getBlock().setType(material);
            return;
        }

        executeOnLocationThread(location, () -> location.getBlock().setType(material));
    }

    /**
     * Set block data at a location, ensuring thread safety.
     */
    public static void setBlockData(Location location, org.bukkit.block.data.BlockData data) {
        if (!isFolia) {
            location.getBlock().setBlockData(data);
            return;
        }

        executeOnLocationThread(location, () -> location.getBlock().setBlockData(data));
    }

    /**
     * Break a block naturally at a location, ensuring thread safety.
     */
    public static void breakNaturally(Location location) {
        if (!isFolia) {
            location.getBlock().breakNaturally();
            return;
        }

        executeOnLocationThread(location, () -> location.getBlock().breakNaturally());
    }

    /**
     * Create an explosion at a location, ensuring thread safety.
     */
    public static void createExplosion(Location location, float power) {
        if (!isFolia) {
            location.getWorld().createExplosion(location, power);
            return;
        }

        executeOnLocationThread(location, () -> location.getWorld().createExplosion(location, power));
    }

    /**
     * Create an explosion at a location with fire and block breaking, ensuring thread safety.
     */
    public static void createExplosion(Location location, float power, boolean setFire, boolean breakBlocks) {
        if (!isFolia) {
            location.getWorld().createExplosion(location, power, setFire, breakBlocks);
            return;
        }

        executeOnLocationThread(location, () -> location.getWorld().createExplosion(location, power, setFire, breakBlocks));
    }

    /**
     * Drop an item naturally at a location, ensuring thread safety.
     */
    public static org.bukkit.entity.Item dropItemNaturally(Location location, org.bukkit.inventory.ItemStack item) {
        if (!isFolia) {
            return location.getWorld().dropItemNaturally(location, item);
        }

        final org.bukkit.entity.Item[] result = new org.bukkit.entity.Item[1];
        executeOnLocationThread(location, () -> {
            result[0] = location.getWorld().dropItemNaturally(location, item);
        });
        return result[0];
    }

    /**
     * Strike lightning at a location, ensuring thread safety.
     */
    public static void strikeLightning(Location location) {
        if (!isFolia) {
            location.getWorld().strikeLightning(location);
            return;
        }

        executeOnLocationThread(location, () -> location.getWorld().strikeLightning(location));
    }

    /**
     * Strike lightning effect at a location, ensuring thread safety.
     */
    public static void strikeLightningEffect(Location location) {
        if (!isFolia) {
            location.getWorld().strikeLightningEffect(location);
            return;
        }

        executeOnLocationThread(location, () -> location.getWorld().strikeLightningEffect(location));
    }

    /**
     * Play a sound at a location, ensuring thread safety.
     */
    public static void playSound(Location location, org.bukkit.Sound sound, float volume, float pitch) {
        if (!isFolia) {
            location.getWorld().playSound(location, sound, volume, pitch);
            return;
        }

        executeOnLocationThread(location, () -> location.getWorld().playSound(location, sound, volume, pitch));
    }

    /**
     * Spawn a particle at a location, ensuring thread safety.
     */
    public static void spawnParticle(Location location, org.bukkit.Particle particle, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if (!isFolia) {
            location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            return;
        }

        executeOnLocationThread(location, () -> location.getWorld().spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra));
    }

    public static boolean isFolia() {
        return isFolia;
    }

    // ==================== World-parameter overloads for bytecode patching ====================

    public static void setBlockData(World world, Location location, org.bukkit.block.data.BlockData data) {
        setBlockData(location, data);
    }

    public static Entity spawnEntity(World world, Location location, EntityType type) {
        return spawnEntity(location, type);
    }

    public static org.bukkit.entity.Item dropItem(World world, Location location, org.bukkit.inventory.ItemStack item) {
        if (!isFolia) {
            return world.dropItem(location, item);
        }
        final org.bukkit.entity.Item[] result = new org.bukkit.entity.Item[1];
        executeOnLocationThread(location, () -> result[0] = world.dropItem(location, item));
        return result[0];
    }

    public static org.bukkit.entity.LightningStrike strikeLightning(World world, Location location) {
        if (!isFolia) {
            return world.strikeLightning(location);
        }
        final org.bukkit.entity.LightningStrike[] result = new org.bukkit.entity.LightningStrike[1];
        executeOnLocationThread(location, () -> result[0] = world.strikeLightning(location));
        return result[0];
    }
}
