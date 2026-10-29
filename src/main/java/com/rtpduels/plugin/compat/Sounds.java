package com.rtpduels.plugin.compat;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Sound lookup by name. Sound names changed in 1.9 (ORB_PICKUP -> ENTITY_EXPERIENCE_ORB_PICKUP) and in
 * 1.21.3 Sound stopped being an enum, so values are read as plain static fields and any miss is silent.
 */
public final class Sounds {

    private static final Sound ORB_PICKUP = find("ENTITY_EXPERIENCE_ORB_PICKUP", "ORB_PICKUP");

    private Sounds() {}

    public static boolean isAvailable() {
        return ORB_PICKUP != null;
    }

    public static void challengePing(Player player) {
        if (ORB_PICKUP == null || player == null) return;
        try {
            player.playSound(player.getLocation(), ORB_PICKUP, 1.0F, 1.2F);
        } catch (Throwable ignored) {
            // a missing sound is never worth an error
        }
    }

    private static Sound find(String... names) {
        for (String name : names) {
            Object value = Reflect.staticField(Sound.class, name);
            if (value instanceof Sound) {
                return (Sound) value;
            }
        }
        return null;
    }
}
