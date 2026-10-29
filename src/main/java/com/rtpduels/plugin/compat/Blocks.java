package com.rtpduels.plugin.compat;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Version-proof world/block helpers: biome display name, world floor height, and material
 * classification by NAME (water was split into WATER/STATIONARY_WATER before 1.13, kelp and
 * seagrass only exist from 1.13, and so on).
 */
public final class Blocks {

    private static final Method GET_MIN_HEIGHT = Reflect.method(World.class, "getMinHeight");

    private Blocks() {}

    /** Lowest build height of the world (0 before 1.17, -64 in a modern overworld). */
    public static int minHeight(World world) {
        Object value = Reflect.invokeQuietly(GET_MIN_HEIGHT, world);
        return value instanceof Integer ? ((Integer) value).intValue() : 0;
    }

    public static boolean isAir(Material material) {
        String name = material.name();
        return name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR");
    }

    /** True for anything a land-only landing must never be on: water in any form, lava, kelp, seagrass. */
    public static boolean isWaterOrLava(Material material) {
        String name = material.name();
        return name.equals("WATER") || name.equals("STATIONARY_WATER")
                || name.equals("LAVA") || name.equals("STATIONARY_LAVA")
                || name.equals("KELP") || name.equals("KELP_PLANT")
                || name.equals("SEAGRASS") || name.equals("TALL_SEAGRASS")
                || name.equals("BUBBLE_COLUMN");
    }

    /**
     * Nice biome name ("Dark Forest"). Biome is an enum on old servers and a registry-backed
     * interface from 1.21.3, so it is handled as a plain Object. Must be called on a thread that
     * owns the block (the region thread on Folia).
     */
    public static String biomeName(Block block) {
        try {
            Object biome = block.getBiome();
            if (biome == null) return "Unknown";

            String raw;
            if (biome instanceof Enum) {
                raw = ((Enum<?>) biome).name();
            } else {
                raw = keyOf(biome);
                if (raw == null) raw = biome.toString();
            }
            return prettify(raw);
        } catch (Throwable ex) {
            return "Unknown";
        }
    }

    /** "minecraft:dark_forest" style key via the Keyed interface (1.14+), path part only. */
    private static String keyOf(Object keyed) {
        Class<?> keyedClass = Reflect.classOrNull("org.bukkit.Keyed");
        if (keyedClass == null || !keyedClass.isInstance(keyed)) return null;
        Object key = Reflect.invokeQuietly(Reflect.method(keyedClass, "getKey"), keyed);
        if (key == null) return null;
        String full = key.toString();
        int colon = full.indexOf(':');
        return colon >= 0 ? full.substring(colon + 1) : full;
    }

    static String prettify(String raw) {
        String[] words = raw.toLowerCase(Locale.ROOT).replace('/', ' ').replace('_', ' ').trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.length() == 0 ? "Unknown" : sb.toString();
    }
}
