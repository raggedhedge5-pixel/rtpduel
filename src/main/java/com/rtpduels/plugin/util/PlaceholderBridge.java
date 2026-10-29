package com.rtpduels.plugin.util;

import com.rtpduels.plugin.compat.Reflect;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Reads TimeSMP's rank via PlaceholderAPI, if both are installed - RTP Duels has no direct dependency
 * on either. It just asks PlaceholderAPI to resolve %timesmp_rank_tag% for a player (by reflection, so
 * no PlaceholderAPI jar is needed to build or run). Fails open: returns null if anything is missing or
 * the placeholder isn't registered.
 */
public final class PlaceholderBridge {

    private static final String PLACEHOLDER = "%timesmp_rank_tag%";
    private static Method setPlaceholders;
    private static boolean lookedUp;
    private static boolean warned;

    private PlaceholderBridge() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    /** The player's TimeSMP rank tag (e.g. "#7"), or null if unavailable for any reason. */
    public static String getRankTag(Player player) {
        if (!isAvailable()) return null;
        try {
            Method method = resolveMethod();
            if (method == null) return null;
            Object resolved = method.invoke(null, player, PLACEHOLDER);
            if (!(resolved instanceof String)) return null;
            String text = (String) resolved;
            if (text.isEmpty() || text.equals(PLACEHOLDER)) return null; // unresolved = not registered
            return text;
        } catch (Throwable ex) {
            if (!warned) {
                warned = true;
                Bukkit.getLogger().log(Level.WARNING, "[RTPDuels] Rank placeholder lookup failed - skipping rank display", ex);
            }
            return null;
        }
    }

    private static synchronized Method resolveMethod() {
        if (!lookedUp) {
            lookedUp = true;
            Class<?> api = Reflect.classOrNull("me.clip.placeholderapi.PlaceholderAPI");
            if (api != null) {
                setPlaceholders = Reflect.method(api, "setPlaceholders", Player.class, String.class);
            }
        }
        return setPlaceholders;
    }
}
