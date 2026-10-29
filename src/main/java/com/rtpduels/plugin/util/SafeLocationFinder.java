package com.rtpduels.plugin.util;

import com.rtpduels.plugin.compat.Blocks;
import com.rtpduels.plugin.compat.Reflect;
import com.rtpduels.plugin.sched.Sched;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Picks a random LAND spot within a radius of world spawn (never water, lava or the void) and reports
 * its biome. Same rules as the original, but built to work everywhere:
 *
 * - Paper / Folia: the chunk is loaded asynchronously ({@code getChunkAtAsync}) and then read on the
 *   thread that owns it, so a far-away random spot never freezes the server and never touches a region
 *   from the wrong Folia thread.
 * - Spigot and old servers: no async chunk API, so it reads on the main thread like before (a brief
 *   pause while a brand-new chunk generates is expected and fine for an occasional duel).
 *
 * The callback may arrive on any server thread - callers must hop to the thread they need.
 */
public final class SafeLocationFinder {

    private static final Method GET_CHUNK_ASYNC = Reflect.method(World.class, "getChunkAtAsync", int.class, int.class);
    private static final int MAX_ATTEMPTS_CAP = 200;

    private SafeLocationFinder() {}

    /** The chosen standing spot (feet position, block centre) and a display name for its biome. */
    public static final class Result {
        private final Location location;
        private final String biome;
        private final boolean fallback;

        public Result(Location location, String biome, boolean fallback) {
            this.location = location;
            this.biome = biome;
            this.fallback = fallback;
        }

        /** True when every random roll was rejected and world spawn was used instead. */
        public boolean isFallback() {
            return fallback;
        }

        public Location getLocation() {
            return location;
        }

        public String getBiome() {
            return biome;
        }
    }

    public interface Callback {
        void done(Result result);
    }

    public static void find(World world, double radius, int maxAttempts, Callback callback) {
        int attempts = Math.max(1, Math.min(maxAttempts, MAX_ATTEMPTS_CAP));
        attempt(world, world.getSpawnLocation(), Math.max(1.0D, radius), 0, attempts, callback);
    }

    private static void attempt(final World world, final Location center, final double radius,
                                final int attemptNumber, final int maxAttempts, final Callback callback) {
        if (attemptNumber >= maxAttempts) {
            fallbackToSpawn(world, center, callback);
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(0, Math.PI * 2);
        // sqrt() keeps the distribution uniform over the AREA of the circle, not biased toward the centre.
        double distance = Math.sqrt(random.nextDouble()) * radius;

        final int blockX = (int) Math.floor(center.getX() + distance * Math.cos(angle));
        final int blockZ = (int) Math.floor(center.getZ() + distance * Math.sin(angle));

        withChunk(world, blockX >> 4, blockZ >> 4, new Runnable() {
            @Override
            public void run() {
                Result result = null;
                try {
                    result = evaluate(world, blockX, blockZ);
                } catch (Throwable ignored) {
                    // unreadable spot (chunk not ready / not owned) - treat like any other bad roll
                }
                if (result != null) {
                    callback.done(result);
                } else {
                    attempt(world, center, radius, attemptNumber + 1, maxAttempts, callback);
                }
            }
        });
    }

    /** Reads the column at x/z. Returns null if it is unsafe (void, water, lava). Must run on the owning thread. */
    private static Result evaluate(World world, int x, int z) {
        int minHeight = Blocks.minHeight(world);
        int y = Math.min(world.getHighestBlockYAt(x, z), world.getMaxHeight() - 1);

        // Old servers report the first AIR block above the ground, new ones the top block itself.
        // Walking down over air makes both mean "the block we would stand on".
        while (y > minHeight && Blocks.isAir(world.getBlockAt(x, y, z).getType())) {
            y--;
        }

        if (y <= minHeight + 1) {
            return null; // basically the void
        }

        Material ground = world.getBlockAt(x, y, z).getType();
        if (Blocks.isWaterOrLava(ground)) {
            return null; // land only - never water, never lava
        }

        Block feet = world.getBlockAt(x, y + 1, z);
        Location location = new Location(world, x + 0.5D, y + 1, z + 0.5D);
        return new Result(location, Blocks.biomeName(feet), false);
    }

    /** Gave up finding a random safe spot - world spawn is always a reasonable fallback. */
    private static void fallbackToSpawn(final World world, final Location center, final Callback callback) {
        final Location spawn = center.clone();
        withChunk(world, spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4, new Runnable() {
            @Override
            public void run() {
                String biome;
                try {
                    biome = Blocks.biomeName(spawn.getBlock());
                } catch (Throwable ex) {
                    biome = "Unknown";
                }
                callback.done(new Result(spawn, biome, true));
            }
        });
    }

    /** Runs {@code task} once the chunk is loaded, on the thread that owns it. */
    private static void withChunk(final World world, final int chunkX, final int chunkZ, final Runnable task) {
        if (GET_CHUNK_ASYNC != null) {
            try {
                Object future = GET_CHUNK_ASYNC.invoke(world, Integer.valueOf(chunkX), Integer.valueOf(chunkZ));
                if (future instanceof CompletableFuture) {
                    ((CompletableFuture<?>) future).whenComplete(new BiConsumer<Object, Throwable>() {
                        @Override
                        public void accept(Object chunk, Throwable error) {
                            Sched.runAtChunk(world, chunkX, chunkZ, task);
                        }
                    });
                    return;
                }
            } catch (Throwable ignored) {
                // fall back to the direct read below
            }
        }
        Sched.runAtChunk(world, chunkX, chunkZ, task);
    }
}
