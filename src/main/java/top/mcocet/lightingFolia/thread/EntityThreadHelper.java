package top.mcocet.lightingFolia.thread;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe helper for entity operations on Folia.
 * Automatically dispatches entity operations to the correct region thread.
 */
public class EntityThreadHelper {

    private static final Logger LOGGER = Logger.getLogger("LightingFolia-Entity");

    private static boolean isFolia = false;
    private static boolean initialized = false;
    private static Object regionScheduler = null;
    private static java.lang.reflect.Method executeMethod = null;
    private static JavaPlugin plugin = null;

    // MethodHandle for fast thread checks
    private static MethodHandle isOwnedMethodHandle = null;
    private static boolean isOwnedMethodChecked = false;

    // Entity state caches for non-blocking reads
    private static final Map<UUID, Double> healthCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> maxHealthCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> deadCache = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> absorptionCache = new ConcurrentHashMap<>();

    public static void init(JavaPlugin pluginInstance) {
        if (initialized) return;
        plugin = pluginInstance;

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;

            regionScheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            executeMethod = regionScheduler.getClass().getMethod("execute",
                    org.bukkit.plugin.Plugin.class, org.bukkit.Location.class, Runnable.class);

            initIsOwnedMethodHandle();
            startCacheCleanupTask();

            LOGGER.info("[LightingFolia] EntityThreadHelper initialized");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            LOGGER.info("[LightingFolia] Not running on Folia, EntityThreadHelper disabled");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[LightingFolia] Failed to initialize EntityThreadHelper: " + e.getMessage(), e);
        }

        initialized = true;
    }

    private static void initIsOwnedMethodHandle() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodType methodType = MethodType.methodType(boolean.class);
            isOwnedMethodHandle = lookup.findVirtual(
                Class.forName("org.bukkit.entity.Entity"),
                "isOwnedByCurrentRegion",
                methodType
            );
        } catch (Exception e) {
            isOwnedMethodHandle = null;
        }
        isOwnedMethodChecked = true;
    }

    private static void startCacheCleanupTask() {
        if (!isFolia || plugin == null) return;
        try {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
                cleanupOfflinePlayerCaches();
            }, 6000L, 6000L);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[LightingFolia] Failed to start cache cleanup task", e);
        }
    }

    private static void cleanupOfflinePlayerCaches() {
        Set<UUID> onlinePlayers = new java.util.HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.add(player.getUniqueId());
        }
        
        healthCache.keySet().removeIf(uuid -> !onlinePlayers.contains(uuid));
        maxHealthCache.keySet().removeIf(uuid -> !onlinePlayers.contains(uuid));
        deadCache.keySet().removeIf(uuid -> !onlinePlayers.contains(uuid));
        absorptionCache.keySet().removeIf(uuid -> !onlinePlayers.contains(uuid));
    }

    public static void clearPlayerCache(UUID uuid) {
        healthCache.remove(uuid);
        maxHealthCache.remove(uuid);
        deadCache.remove(uuid);
        absorptionCache.remove(uuid);
    }

    // ==================== Core Execution ====================

    public static void executeOnEntityThread(Entity entity, Runnable task) {
        if (!isFolia || regionScheduler == null || executeMethod == null) {
            task.run();
            return;
        }

        if (isOnEntityThread(entity)) {
            task.run();
            return;
        }

        try {
            executeMethod.invoke(regionScheduler, plugin, entity.getLocation(), task);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[LightingFolia] Failed to schedule entity task: " + e.getMessage(), e);
            task.run();
        }
    }

    // ==================== Potion Effects ====================

    public static boolean addPotionEffect(LivingEntity entity, PotionEffect effect) {
        if (!isFolia || isOnEntityThread(entity)) {
            return entity.addPotionEffect(effect);
        }
        executeOnEntityThread(entity, () -> entity.addPotionEffect(effect));
        return true;
    }

    public static void removePotionEffect(LivingEntity entity, PotionEffectType type) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.removePotionEffect(type);
            return;
        }
        executeOnEntityThread(entity, () -> entity.removePotionEffect(type));
    }

    // ==================== Movement & Teleport ====================

    public static void teleport(Entity entity, Location location) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.teleport(location);
            return;
        }
        executeOnEntityThread(entity, () -> entity.teleport(location));
    }

    public static void setVelocity(Entity entity, Vector velocity) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.setVelocity(velocity);
            return;
        }
        executeOnEntityThread(entity, () -> entity.setVelocity(velocity));
    }

    public static void removeEntity(Entity entity) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.remove();
            return;
        }
        executeOnEntityThread(entity, () -> entity.remove());
    }

    // ==================== Player Movement ====================

    public static void setWalkSpeed(Player player, float speed) {
        if (!isFolia || isOnEntityThread(player)) {
            player.setWalkSpeed(speed);
            return;
        }
        executeOnEntityThread(player, () -> player.setWalkSpeed(speed));
    }

    public static void setFlySpeed(Player player, float speed) {
        if (!isFolia || isOnEntityThread(player)) {
            player.setFlySpeed(speed);
            return;
        }
        executeOnEntityThread(player, () -> player.setFlySpeed(speed));
    }

    public static void setFlying(Player player, boolean flying) {
        if (!isFolia || isOnEntityThread(player)) {
            player.setFlying(flying);
            return;
        }
        executeOnEntityThread(player, () -> player.setFlying(flying));
    }

    public static void setAllowFlight(Player player, boolean allow) {
        if (!isFolia || isOnEntityThread(player)) {
            player.setAllowFlight(allow);
            return;
        }
        executeOnEntityThread(player, () -> player.setAllowFlight(allow));
    }

    // ==================== Health & Damage ====================

    public static void setHealth(LivingEntity entity, double health) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.setHealth(health);
            return;
        }
        executeOnEntityThread(entity, () -> entity.setHealth(health));
    }

    public static void setMaxHealth(LivingEntity entity, double maxHealth) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.setMaxHealth(maxHealth);
            return;
        }
        executeOnEntityThread(entity, () -> entity.setMaxHealth(maxHealth));
    }

    public static void damage(LivingEntity entity, double amount) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.damage(amount);
            return;
        }
        executeOnEntityThread(entity, () -> entity.damage(amount));
    }

    public static void damage(LivingEntity entity, double amount, Entity source) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.damage(amount, source);
            return;
        }
        executeOnEntityThread(entity, () -> entity.damage(amount, source));
    }

    public static void setFireTicks(LivingEntity entity, int ticks) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.setFireTicks(ticks);
            return;
        }
        executeOnEntityThread(entity, () -> entity.setFireTicks(ticks));
    }

    public static void setFreezeTicks(LivingEntity entity, int ticks) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.setFreezeTicks(ticks);
            return;
        }
        executeOnEntityThread(entity, () -> entity.setFreezeTicks(ticks));
    }

    // ==================== Entity State ====================

    public static void setAI(LivingEntity entity, boolean ai) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.setAI(ai);
            return;
        }
        executeOnEntityThread(entity, () -> entity.setAI(ai));
    }

    public static void setInvulnerable(Entity entity, boolean invulnerable) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.setInvulnerable(invulnerable);
            return;
        }
        executeOnEntityThread(entity, () -> entity.setInvulnerable(invulnerable));
    }

    public static void setGlowing(Entity entity, boolean glowing) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.setGlowing(glowing);
            return;
        }
        executeOnEntityThread(entity, () -> entity.setGlowing(glowing));
    }

    public static void setGravity(Entity entity, boolean gravity) {
        if (!isFolia || isOnEntityThread(entity)) {
            entity.setGravity(gravity);
            return;
        }
        executeOnEntityThread(entity, () -> entity.setGravity(gravity));
    }

    // ==================== Health Getters (cached) ====================

    public static double getHealth(LivingEntity entity) {
        if (!isFolia || isOnEntityThread(entity)) {
            double health = entity.getHealth();
            healthCache.put(entity.getUniqueId(), health);
            return health;
        }
        Double cached = healthCache.get(entity.getUniqueId());
        executeOnEntityThread(entity, () -> healthCache.put(entity.getUniqueId(), entity.getHealth()));
        return cached != null ? cached : entity.getHealth();
    }

    public static double getMaxHealth(LivingEntity entity) {
        if (!isFolia || isOnEntityThread(entity)) {
            double maxHealth = entity.getMaxHealth();
            maxHealthCache.put(entity.getUniqueId(), maxHealth);
            return maxHealth;
        }
        Double cached = maxHealthCache.get(entity.getUniqueId());
        executeOnEntityThread(entity, () -> maxHealthCache.put(entity.getUniqueId(), entity.getMaxHealth()));
        return cached != null ? cached : entity.getMaxHealth();
    }

    public static boolean isDead(LivingEntity entity) {
        if (!isFolia || isOnEntityThread(entity)) {
            boolean dead = entity.isDead();
            deadCache.put(entity.getUniqueId(), dead);
            return dead;
        }
        Boolean cached = deadCache.get(entity.getUniqueId());
        executeOnEntityThread(entity, () -> deadCache.put(entity.getUniqueId(), entity.isDead()));
        return cached != null ? cached : entity.isDead();
    }

    public static double getAbsorptionAmount(Player player) {
        if (!isFolia || isOnEntityThread(player)) {
            double absorption = player.getAbsorptionAmount();
            absorptionCache.put(player.getUniqueId(), absorption);
            return absorption;
        }
        Double cached = absorptionCache.get(player.getUniqueId());
        executeOnEntityThread(player, () -> absorptionCache.put(player.getUniqueId(), player.getAbsorptionAmount()));
        return cached != null ? cached : player.getAbsorptionAmount();
    }

    // ==================== Thread Check ====================

    private static boolean isOnEntityThread(Entity entity) {
        if (!isOwnedMethodChecked) {
            initIsOwnedMethodHandle();
        }
        if (isOwnedMethodHandle == null) {
            return false;
        }
        try {
            return (boolean) isOwnedMethodHandle.invoke(entity);
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isFolia() {
        return isFolia;
    }
}
