package com.rtpduels.plugin.duel;

import com.rtpduels.plugin.compat.Teleport;
import com.rtpduels.plugin.compat.Text;
import com.rtpduels.plugin.sched.Sched;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Runs the whole accept sequence as a 1-tick-per-second state machine:
 *   5,4,3,2,1 ("don't move") while frozen at the CURRENT location
 *   -> teleport both players to the pre-rolled destination (the same location whose biome was already
 *      shown in the confirmation GUI, not a fresh roll)
 *   -> 3,2,1,GO while frozen at the NEW location
 *   -> unfreeze, duel begins
 * Both players stay frozen (can't move, can't deal or take damage - see FreezeListener) for the entire
 * sequence, including the moment of teleport.
 *
 * The timer lives on the main/global thread; everything that touches a player is handed to that player's
 * own thread (a no-op hop outside Folia), because on Folia the two duelists can be in different regions.
 */
public class DuelCountdown implements Runnable {

    private final FreezeManager freezeManager;
    private final Player p1;
    private final Player p2;
    private final Location destination;
    private final double separation;

    private volatile Sched.Handle timer;
    private volatile boolean finished = false;
    private int tick = 0;

    public DuelCountdown(FreezeManager freezeManager, Player p1, Player p2, Location destination, double separation) {
        this.freezeManager = freezeManager;
        this.p1 = p1;
        this.p2 = p2;
        this.destination = destination;
        this.separation = separation;
    }

    public void start() {
        freezePlayer(p1);
        freezePlayer(p2);
        Sched.Handle handle = Sched.repeatGlobal(this, 0L, 20L);
        timer = handle;
        if (finished) {
            handle.cancel(); // finished before the handle was stored
        }
    }

    @Override
    public void run() {
        if (finished) {
            return;
        }
        if (!p1.isOnline() || !p2.isOnline()) {
            cleanupAndCancel();
            return;
        }

        switch (tick) {
            case 0:
                sendCountdown("5", "&7Don't move!");
                break;
            case 1:
                sendCountdown("4", "&7Don't move!");
                break;
            case 2:
                sendCountdown("3", "&7Don't move!");
                break;
            case 3:
                sendCountdown("2", "&7Don't move!");
                break;
            case 4:
                sendCountdown("1", "&7Don't move!");
                break;
            case 5:
                teleportBoth();
                sendCountdown("3", null);
                break;
            case 6:
                sendCountdown("2", null);
                break;
            case 7:
                sendCountdown("1", null);
                break;
            case 8:
                sendTitle(p1, "&aGO!");
                sendTitle(p2, "&aGO!");
                cleanupAndCancel();
                return;
            default:
                cleanupAndCancel();
                return;
        }

        tick++;
    }

    private void freezePlayer(final Player player) {
        Sched.runOnEntity(player, new Runnable() {
            @Override
            public void run() {
                freezeManager.freeze(player);
            }
        });
    }

    private void teleportBoth() {
        Location loc1 = destination.clone();
        // Explicitly reuse loc1's Y (not destination's) so there's no ambiguity -
        // both duelists land on exactly the same Y level, only offset in X.
        Location loc2 = new Location(destination.getWorld(), destination.getX() + separation, loc1.getY(), destination.getZ());

        // Face each other.
        loc1.setDirection(loc2.toVector().subtract(loc1.toVector()));
        loc2.setDirection(loc1.toVector().subtract(loc2.toVector()));

        teleportOne(p1, loc1);
        teleportOne(p2, loc2);
    }

    private void teleportOne(final Player player, final Location target) {
        Sched.runOnEntity(player, new Runnable() {
            @Override
            public void run() {
                freezeManager.beginTeleport(player.getUniqueId());
                Teleport.to(player, target, new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean ok) {
                        // Keep them frozen at the NEW spot - otherwise the move-lock would try to
                        // snap them back to where they were before the teleport.
                        if (ok.booleanValue()) {
                            freezeManager.updateFreezeLocation(player.getUniqueId(), player.getLocation());
                        }
                        freezeManager.endTeleport(player.getUniqueId());
                    }
                });
            }
        });
    }

    private void sendCountdown(String number, String subtitle) {
        showTitle(p1, "&e" + number, subtitle, 0, 18, 0);
        showTitle(p2, "&e" + number, subtitle, 0, 18, 0);
    }

    private void sendTitle(Player player, String title) {
        showTitle(player, title, null, 0, 20, 6);
    }

    private void showTitle(final Player player, final String title, final String subtitle,
                           final int fadeIn, final int stay, final int fadeOut) {
        Sched.runOnEntity(player, new Runnable() {
            @Override
            public void run() {
                Text.title(player, title, subtitle, fadeIn, stay, fadeOut);
            }
        });
    }

    private void cleanupAndCancel() {
        finished = true;
        freezeManager.unfreeze(p1.getUniqueId());
        freezeManager.unfreeze(p2.getUniqueId());
        Sched.Handle handle = timer;
        if (handle != null) {
            handle.cancel();
        }
    }
}
