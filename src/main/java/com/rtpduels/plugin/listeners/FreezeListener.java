package com.rtpduels.plugin.listeners;

import com.rtpduels.plugin.compat.Text;
import com.rtpduels.plugin.duel.FreezeManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/**
 * Enforces everything tied to being frozen during a duel countdown:
 *   - can't move (snapped back), can't deal or take damage
 *   - can't use ANY command (no exceptions) - players with quickduel.admin bypass this
 *   - nobody else can teleport in near a frozen duelist (blocks /tpa, warps, etc.
 *     landing within duel-teleport-protection-radius of either player) - this does
 *     NOT block the duelists' own scripted teleport at the countdown's midpoint,
 *     since they themselves are still marked frozen when that happens.
 */
public class FreezeListener implements Listener {

    private final Plugin plugin;
    private final FreezeManager freezeManager;

    public FreezeListener(Plugin plugin, FreezeManager freezeManager) {
        this.plugin = plugin;
        this.freezeManager = freezeManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!freezeManager.isFrozen(player.getUniqueId())) return;
        // Internal only: on Paper/Folia the duel's own teleport completes asynchronously, so don't snap
        // the player back to their old spot while that teleport is still in flight.
        if (freezeManager.isTeleporting(player.getUniqueId())) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return; // just looking around, not actually moving - fine
        }

        Location freezeLoc = freezeManager.getFreezeLocation(player.getUniqueId());
        if (freezeLoc != null) {
            event.setTo(freezeLoc);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && freezeManager.isFrozen(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Player && freezeManager.isFrozen(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("quickduel.admin")) return;
        if (!freezeManager.isFrozen(player.getUniqueId())) return;

        event.setCancelled(true);
        Text.send(player, "&cYou can't use commands right now!");
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        // The duelists' own countdown-driven teleport is exempt - they're frozen
        // themselves at that moment, this only blocks OTHER players teleporting in.
        if (freezeManager.isFrozen(player.getUniqueId())) return;

        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        double radius = plugin.getConfig().getDouble("duel-teleport-protection-radius", 15);
        double radiusSquared = radius * radius;

        for (Location frozenLoc : freezeManager.getAllFrozenLocations()) {
            if (frozenLoc.getWorld() == null || !frozenLoc.getWorld().equals(to.getWorld())) continue;
            if (frozenLoc.distanceSquared(to) <= radiusSquared) {
                event.setCancelled(true);
                Text.send(player, "&cYou can't teleport near a duel in progress!");
                return;
            }
        }
    }
}
