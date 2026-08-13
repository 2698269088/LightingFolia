package top.mcocet.lightingFolia.transform;

import org.objectweb.asm.*;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Universal ASM ClassFileTransformer for LightingFolia.
 *
 * This transformer modifies plugin classes to be Folia-compatible by:
 * 1. Redirecting entity operations (addPotionEffect, setHealth, teleport, etc.)
 *    through EntityThreadHelper so they execute on the entity's region thread.
 * 2. Redirecting Bukkit.getScheduler() calls through SmartScheduler
 *    so plugins use Folia's region-aware schedulers.
 * 3. Redirecting world operations (setBlock, spawnEntity, etc.)
 *    through WorldThreadHelper.
 *
 * Unlike AEAddon's approach, this transformer is generic and can be configured
 * to target any plugin's classes.
 */
public class BridgeClassTransformer implements ClassFileTransformer {

    private static final Logger LOGGER = Logger.getLogger("LightingFolia-Transformer");

    // Statistics counters
    private static int transformCallCount = 0;
    private static int schedulerPatchCount = 0;
    private static int entityPatchCount = 0;
    private static int worldPatchCount = 0;
    private static int bukkitRunnablePatchCount = 0;

    // Configuration
    private static volatile boolean patchEntityOps = true;
    private static volatile boolean patchScheduler = true;
    private static volatile boolean patchWorldOps = true;
    private static volatile boolean patchPlayerState = true;

    // Plugin-specific class patterns (plugin name -> set of class name patterns)
    private static final Map<String, Set<String>> pluginClassPatterns = new ConcurrentHashMap<>();

    // Excluded class prefixes
    private static final Set<String> EXCLUDED_PREFIXES = new HashSet<>(Arrays.asList(
            "java.", "javax.", "sun.", "com.sun.",
            "org.bukkit.", "net.minecraft.",
            "io.papermc.", "ca.spottedleaf.",
            "top.mcocet.foliabridge.",
            "top.mcocet.lightingFolia.",
            "net.bytebuddy.", "org.objectweb.asm."
    ));

    private static final String ENTITY_HELPER = "top/mcocet/lightingFolia/thread/EntityThreadHelper";
    private static final String WORLD_HELPER = "top/mcocet/lightingFolia/thread/WorldThreadHelper";
    private static final String SCHEDULER_HELPER = "top/mcocet/lightingFolia/scheduler/SmartScheduler";
    private static final String BUKKIT_CLASS = "org/bukkit/Bukkit";
    private static final String BUKKIT_SCHEDULER_CLASS = "org/bukkit/scheduler/BukkitScheduler";

    public static void setConfig(boolean entityOps, boolean scheduler, boolean worldOps, boolean playerState) {
        patchEntityOps = entityOps;
        patchScheduler = scheduler;
        patchWorldOps = worldOps;
        patchPlayerState = playerState;
    }

    public static void addPluginPattern(String pluginName, String... classPatterns) {
        Set<String> patterns = pluginClassPatterns.computeIfAbsent(pluginName, k -> ConcurrentHashMap.newKeySet());
        patterns.addAll(Arrays.asList(classPatterns));
    }

    public static String getDebugStats() {
        return String.format(
                "transforms=%d, scheduler=%d, entity=%d, world=%d, bukkitRunnable=%d",
                transformCallCount, schedulerPatchCount, entityPatchCount, worldPatchCount, bukkitRunnablePatchCount
        );
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        if (className == null) {
            return null;
        }

        // Skip excluded classes
        if (shouldSkipClass(className)) {
            return null;
        }

        transformCallCount++;

        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new SafeClassWriter(reader, loader,
                    ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor = new BridgeClassVisitor(writer, className);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);

            byte[] result = writer.toByteArray();
            if (result != classfileBuffer) {
                LOGGER.fine("[LightingFolia] Transformed class: " + className);
            }
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[LightingFolia] Failed to transform class " + className + ": " + e.getMessage(), e);
            return null;
        }
    }

    private static boolean shouldSkipClass(String className) {
        for (String prefix : EXCLUDED_PREFIXES) {
            if (className.startsWith(prefix.replace('.', '/'))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Safe ClassWriter that handles missing classes gracefully.
     */
    private static class SafeClassWriter extends ClassWriter {
        private final ClassLoader classLoader;

        public SafeClassWriter(ClassReader reader, ClassLoader classLoader, int flags) {
            super(reader, flags);
            this.classLoader = classLoader;
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (TypeNotPresentException e) {
                return "java/lang/Object";
            }
        }
    }

    /**
     * ClassVisitor that applies all configured patches to methods.
     */
    private static class BridgeClassVisitor extends ClassVisitor {
        private final String className;

        public BridgeClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new BridgeMethodVisitor(mv);
        }
    }

    /**
     * MethodVisitor that patches individual method instructions.
     */
    private static class BridgeMethodVisitor extends MethodVisitor {

        public BridgeMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // 1. Patch Bukkit.getScheduler() -> SmartScheduler.getScheduler()
            if (patchScheduler
                    && opcode == Opcodes.INVOKESTATIC
                    && owner.equals(BUKKIT_CLASS)
                    && name.equals("getScheduler")
                    && descriptor.equals("()L" + BUKKIT_SCHEDULER_CLASS + ";")) {
                schedulerPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, SCHEDULER_HELPER, "getScheduler",
                        "()L" + BUKKIT_SCHEDULER_CLASS + ";", false);
                return;
            }

            // 2. Patch BukkitRunnable scheduler calls
            if (patchScheduler) {
                if (tryPatchBukkitRunnableCall(opcode, owner, name, descriptor)) {
                    return;
                }
            }

            // 3. Patch entity operations
            if (patchEntityOps) {
                if (tryPatchEntityCall(opcode, owner, name, descriptor)) {
                    return;
                }
            }

            // 4. Patch world operations
            if (patchWorldOps) {
                if (tryPatchWorldCall(opcode, owner, name, descriptor)) {
                    return;
                }
            }

            // 5. Patch player state operations
            if (patchPlayerState) {
                if (tryPatchPlayerStateCall(opcode, owner, name, descriptor)) {
                    return;
                }
            }

            mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        private boolean tryPatchBukkitRunnableCall(int opcode, String owner, String name, String descriptor) {
            if (opcode != Opcodes.INVOKEVIRTUAL) {
                return false;
            }

            if (!isBukkitRunnableSchedulerName(name)) {
                return false;
            }

            String expectedDescriptor;
            String helperDescriptor;
            switch (name) {
                case "runTask":
                    expectedDescriptor = "(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;";
                    helperDescriptor = "(Lorg/bukkit/scheduler/BukkitRunnable;Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;";
                    break;
                case "runTaskLater":
                    expectedDescriptor = "(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;";
                    helperDescriptor = "(Lorg/bukkit/scheduler/BukkitRunnable;Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;";
                    break;
                case "runTaskTimer":
                    expectedDescriptor = "(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;";
                    helperDescriptor = "(Lorg/bukkit/scheduler/BukkitRunnable;Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;";
                    break;
                case "runTaskAsynchronously":
                    expectedDescriptor = "(Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;";
                    helperDescriptor = "(Lorg/bukkit/scheduler/BukkitRunnable;Lorg/bukkit/plugin/Plugin;)Lorg/bukkit/scheduler/BukkitTask;";
                    break;
                case "runTaskLaterAsynchronously":
                    expectedDescriptor = "(Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;";
                    helperDescriptor = "(Lorg/bukkit/scheduler/BukkitRunnable;Lorg/bukkit/plugin/Plugin;J)Lorg/bukkit/scheduler/BukkitTask;";
                    break;
                case "runTaskTimerAsynchronously":
                    expectedDescriptor = "(Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;";
                    helperDescriptor = "(Lorg/bukkit/scheduler/BukkitRunnable;Lorg/bukkit/plugin/Plugin;JJ)Lorg/bukkit/scheduler/BukkitTask;";
                    break;
                default:
                    return false;
            }

            if (!descriptor.equals(expectedDescriptor)) {
                return false;
            }

            bukkitRunnablePatchCount++;
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SCHEDULER_HELPER, name, helperDescriptor, false);
            return true;
        }

        private static boolean isBukkitRunnableSchedulerName(String name) {
            return "runTask".equals(name)
                    || "runTaskLater".equals(name)
                    || "runTaskTimer".equals(name)
                    || "runTaskAsynchronously".equals(name)
                    || "runTaskLaterAsynchronously".equals(name)
                    || "runTaskTimerAsynchronously".equals(name);
        }

        private boolean tryPatchEntityCall(int opcode, String owner, String name, String descriptor) {
            // LivingEntity.addPotionEffect(PotionEffect)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/LivingEntity")
                    && name.equals("addPotionEffect")
                    && descriptor.equals("(Lorg/bukkit/potion/PotionEffect;)Z")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "addPotionEffect",
                        "(Lorg/bukkit/entity/LivingEntity;Lorg/bukkit/potion/PotionEffect;)Z", false);
                return true;
            }

            // LivingEntity.removePotionEffect(PotionEffectType)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/LivingEntity")
                    && name.equals("removePotionEffect")
                    && descriptor.equals("(Lorg/bukkit/potion/PotionEffectType;)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "removePotionEffect",
                        "(Lorg/bukkit/entity/LivingEntity;Lorg/bukkit/potion/PotionEffectType;)V", false);
                return true;
            }

            // Entity.teleport(Location)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/Entity")
                    && name.equals("teleport")
                    && descriptor.equals("(Lorg/bukkit/Location;)Z")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "teleport",
                        "(Lorg/bukkit/entity/Entity;Lorg/bukkit/Location;)V", false);
                return true;
            }

            // Entity.setVelocity(Vector)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/Entity")
                    && name.equals("setVelocity")
                    && descriptor.equals("(Lorg/bukkit/util/Vector;)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "setVelocity",
                        "(Lorg/bukkit/entity/Entity;Lorg/bukkit/util/Vector;)V", false);
                return true;
            }

            // Entity.remove()
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/Entity")
                    && name.equals("remove")
                    && descriptor.equals("()V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "removeEntity",
                        "(Lorg/bukkit/entity/Entity;)V", false);
                return true;
            }

            // LivingEntity.setHealth(double)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && (owner.equals("org/bukkit/entity/LivingEntity") || owner.equals("org/bukkit/entity/Damageable"))
                    && name.equals("setHealth")
                    && descriptor.equals("(D)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "setHealth",
                        "(Lorg/bukkit/entity/LivingEntity;D)V", false);
                return true;
            }

            // LivingEntity.damage(double)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/LivingEntity")
                    && name.equals("damage")
                    && descriptor.equals("(D)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "damage",
                        "(Lorg/bukkit/entity/LivingEntity;D)V", false);
                return true;
            }

            // LivingEntity.setFireTicks(int)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/LivingEntity")
                    && name.equals("setFireTicks")
                    && descriptor.equals("(I)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "setFireTicks",
                        "(Lorg/bukkit/entity/LivingEntity;I)V", false);
                return true;
            }

            // LivingEntity.setAI(boolean)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/LivingEntity")
                    && name.equals("setAI")
                    && descriptor.equals("(Z)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "setAI",
                        "(Lorg/bukkit/entity/LivingEntity;Z)V", false);
                return true;
            }

            // Entity.setInvulnerable(boolean)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/Entity")
                    && name.equals("setInvulnerable")
                    && descriptor.equals("(Z)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "setInvulnerable",
                        "(Lorg/bukkit/entity/Entity;Z)V", false);
                return true;
            }

            return false;
        }

        private boolean tryPatchWorldCall(int opcode, String owner, String name, String descriptor) {
            // World.setBlockData(Location, BlockData)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/World")
                    && name.equals("setBlockData")
                    && descriptor.equals("(Lorg/bukkit/Location;Lorg/bukkit/block/data/BlockData;)V")) {
                worldPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "setBlockData",
                        "(Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/block/data/BlockData;)V", false);
                return true;
            }

            // World.spawnEntity(Location, EntityType)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/World")
                    && name.equals("spawnEntity")
                    && descriptor.equals("(Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;")) {
                worldPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "spawnEntity",
                        "(Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/entity/EntityType;)Lorg/bukkit/entity/Entity;", false);
                return true;
            }

            // World.dropItem(Location, ItemStack)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/World")
                    && name.equals("dropItem")
                    && descriptor.equals("(Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;")) {
                worldPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "dropItem",
                        "(Lorg/bukkit/World;Lorg/bukkit/Location;Lorg/bukkit/inventory/ItemStack;)Lorg/bukkit/entity/Item;", false);
                return true;
            }

            // World.strikeLightning(Location)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/World")
                    && name.equals("strikeLightning")
                    && descriptor.equals("(Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;")) {
                worldPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, WORLD_HELPER, "strikeLightning",
                        "(Lorg/bukkit/World;Lorg/bukkit/Location;)Lorg/bukkit/entity/LightningStrike;", false);
                return true;
            }

            return false;
        }

        private boolean tryPatchPlayerStateCall(int opcode, String owner, String name, String descriptor) {
            // Player.setWalkSpeed(float)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/Player")
                    && name.equals("setWalkSpeed")
                    && descriptor.equals("(F)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "setWalkSpeed",
                        "(Lorg/bukkit/entity/Player;F)V", false);
                return true;
            }

            // Player.setFlySpeed(float)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/Player")
                    && name.equals("setFlySpeed")
                    && descriptor.equals("(F)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "setFlySpeed",
                        "(Lorg/bukkit/entity/Player;F)V", false);
                return true;
            }

            // Player.setFlying(boolean)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/Player")
                    && name.equals("setFlying")
                    && descriptor.equals("(Z)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "setFlying",
                        "(Lorg/bukkit/entity/Player;Z)V", false);
                return true;
            }

            // Player.setAllowFlight(boolean)
            if (opcode == Opcodes.INVOKEINTERFACE
                    && owner.equals("org/bukkit/entity/Player")
                    && name.equals("setAllowFlight")
                    && descriptor.equals("(Z)V")) {
                entityPatchCount++;
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, ENTITY_HELPER, "setAllowFlight",
                        "(Lorg/bukkit/entity/Player;Z)V", false);
                return true;
            }

            return false;
        }
    }
}