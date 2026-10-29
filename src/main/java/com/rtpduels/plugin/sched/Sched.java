package com.rtpduels.plugin.sched;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * One scheduling API that works everywhere.
 *
 * - Spigot / Paper / Bukkit forks: the normal Bukkit scheduler (everything runs on the main thread).
 * - Folia: the global / region / entity schedulers, reached by reflection so the same jar also loads
 *   on servers (and old Java/Minecraft versions) that have never heard of Folia. On Folia there is no
 *   main thread - touching a player must happen on that player's thread, touching blocks on the
 *   thread of the region that owns them - so every call below says which one it wants.
 */
public final class Sched {

    /** Something that can be cancelled (a repeating timer). */
    public interface Handle {
        void cancel();
    }

    private static final String PKG = "io.papermc.paper.threadedregions.scheduler.";

    private static Plugin plugin;
    private static boolean folia;

    // Folia reflection handles (null when not on Folia)
    private static Object globalScheduler;
    private static Object regionScheduler;
    private static Method globalRun;
    private static Method globalRunAtFixedRate;
    private static Method regionRun;
    private static Method entityGetScheduler;
    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method taskCancel;

    private Sched() {}

    public static boolean isFolia() {
        return folia;
    }

    /** Call once from onEnable. Throws if Folia is detected but its scheduler API can't be reached. */
    public static void init(Plugin owner) throws Exception {
        plugin = owner;
        folia = isClass("io.papermc.paper.threadedregions.RegionizedServer");
        if (!folia) return;

        Class<?> globalType = Class.forName(PKG + "GlobalRegionScheduler");
        Class<?> regionType = Class.forName(PKG + "RegionScheduler");
        Class<?> entityType = Class.forName(PKG + "EntityScheduler");
        Class<?> taskType = Class.forName(PKG + "ScheduledTask");

        globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
        regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);

        globalRun = globalType.getMethod("run", Plugin.class, Consumer.class);
        globalRunAtFixedRate = globalType.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
        regionRun = regionType.getMethod("run", Plugin.class, World.class, int.class, int.class, Consumer.class);
        entityGetScheduler = Entity.class.getMethod("getScheduler");
        entityRun = entityType.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
        entityRunDelayed = entityType.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
        taskCancel = taskType.getMethod("cancel");
    }

    /** Run on the main thread (Bukkit) or the global region thread (Folia), next tick. */
    public static void runGlobal(final Runnable task) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        invoke(globalRun, globalScheduler, plugin, asConsumer(task));
    }

    /** Repeating timer on the main/global thread. Folia requires delay and period >= 1. */
    public static Handle repeatGlobal(final Runnable task, long delayTicks, long periodTicks) {
        final long delay = Math.max(1L, delayTicks);
        final long period = Math.max(1L, periodTicks);
        if (!folia) {
            // plain Bukkit accepts a 0-tick start, exactly like the original runTaskTimer(plugin, 0L, 20L)
            final BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(0L, delayTicks), period);
            return new Handle() {
                @Override
                public void cancel() {
                    bukkitTask.cancel();
                }
            };
        }
        final Object scheduled = invoke(globalRunAtFixedRate, globalScheduler, plugin, asConsumer(task), Long.valueOf(delay), Long.valueOf(period));
        return foliaHandle(scheduled);
    }

    /** Run something that touches {@code entity} (a player) on the thread that owns it. */
    public static void runOnEntity(Entity entity, final Runnable task) {
        if (!folia) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
            return;
        }
        Object scheduler = invoke(entityGetScheduler, entity);
        // third argument = "retired" callback, null is allowed (task is simply dropped if the player left)
        invoke(entityRun, scheduler, plugin, asConsumer(task), null);
    }

    /** Same as runOnEntity but after a delay in ticks (minimum 1). */
    public static void runOnEntityLater(Entity entity, final Runnable task, long delayTicks) {
        final long delay = Math.max(1L, delayTicks);
        if (!folia) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
            return;
        }
        Object scheduler = invoke(entityGetScheduler, entity);
        invoke(entityRunDelayed, scheduler, plugin, asConsumer(task), null, Long.valueOf(delay));
    }

    /** Run something that reads/writes blocks of one chunk on the thread of the region that owns it. */
    public static void runAtChunk(World world, int chunkX, int chunkZ, final Runnable task) {
        if (!folia) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                Bukkit.getScheduler().runTask(plugin, task);
            }
            return;
        }
        invoke(regionRun, regionScheduler, plugin, world, Integer.valueOf(chunkX), Integer.valueOf(chunkZ), asConsumer(task));
    }

    private static Consumer<Object> asConsumer(final Runnable task) {
        return new Consumer<Object>() {
            @Override
            public void accept(Object ignoredScheduledTask) {
                task.run();
            }
        };
    }

    private static Handle foliaHandle(final Object scheduledTask) {
        return new Handle() {
            @Override
            public void cancel() {
                if (scheduledTask != null) {
                    invoke(taskCancel, scheduledTask);
                }
            }
        };
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException(cause);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean isClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }
}
