package com.rtpduels.plugin.duel;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are frozen (duel countdown in progress) and where to snap them back to if they
 * try to move. Thread-safe: on Folia the listeners run on different region threads at the same time.
 */
public class FreezeManager {

    private final Map<UUID, Location> frozen = new ConcurrentHashMap<UUID, Location>();
    /** Players whose own duel teleport is in flight - the move-lock and teleport-lock must let it through. */
    private final Set<UUID> teleporting = ConcurrentHashMap.newKeySet();

    /** Call on the player's own thread (reads their location). */
    public void freeze(Player player) {
        frozen.put(player.getUniqueId(), player.getLocation());
    }

    public void updateFreezeLocation(UUID uuid, Location location) {
        if (frozen.containsKey(uuid)) {
            frozen.put(uuid, location);
        }
    }

    public void unfreeze(UUID uuid) {
        frozen.remove(uuid);
        teleporting.remove(uuid);
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.containsKey(uuid);
    }

    public Location getFreezeLocation(UUID uuid) {
        return frozen.get(uuid);
    }

    /** Current locations of every frozen player - used to block third parties from teleporting in on top of an active duel. */
    public Collection<Location> getAllFrozenLocations() {
        return frozen.values();
    }

    public void beginTeleport(UUID uuid) {
        teleporting.add(uuid);
    }

    public void endTeleport(UUID uuid) {
        teleporting.remove(uuid);
    }

    public boolean isTeleporting(UUID uuid) {
        return teleporting.contains(uuid);
    }
}
