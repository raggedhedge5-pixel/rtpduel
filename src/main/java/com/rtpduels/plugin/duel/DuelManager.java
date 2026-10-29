package com.rtpduels.plugin.duel;

import com.rtpduels.plugin.compat.Items;
import com.rtpduels.plugin.compat.Sounds;
import com.rtpduels.plugin.compat.Text;
import com.rtpduels.plugin.sched.Sched;
import com.rtpduels.plugin.util.PlaceholderBridge;
import com.rtpduels.plugin.util.SafeLocationFinder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending duel challenges (keyed by TARGET - a new request to the same target simply replaces
 * whatever was pending, which is what makes "/duel accept" always resolve to the most recent one) and
 * handles the whole challenge -> confirm -> countdown -> teleport flow.
 *
 * Thread rule (matters on Folia): anything that touches a player other than the one whose thread we
 * are on goes through Sched.runOnEntity, which is a plain direct call everywhere except Folia.
 */
public class DuelManager {

    private final Plugin plugin;
    private final FreezeManager freezeManager;
    private final Map<UUID, DuelRequest> pendingByTarget = new ConcurrentHashMap<UUID, DuelRequest>();
    /** Targets whose confirmation GUI is still being prepared (random spot being found) -> when that started. */
    private final Map<UUID, Long> preparing = new ConcurrentHashMap<UUID, Long>();

    public DuelManager(Plugin plugin, FreezeManager freezeManager) {
        this.plugin = plugin;
        this.freezeManager = freezeManager;
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public void challenge(final Player sender, final Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            Text.send(sender, "&cYou can't duel yourself.");
            return;
        }

        pendingByTarget.put(target.getUniqueId(), new DuelRequest(sender.getUniqueId(), sender.getName(), System.currentTimeMillis()));

        Text.send(sender, "&aDuel request sent to " + target.getName() + ".");

        final String senderName = sender.getName();
        Sched.runOnEntity(target, new Runnable() {
            @Override
            public void run() {
                target.sendMessage("");
                Text.send(target, "&e&l" + senderName + " has challenged you to a duel!");
                Text.sendDuelButtons(target);
                Text.send(target, "&8(or type /duel accept / /duel decline)");
                target.sendMessage("");
                Sounds.challengePing(target);
            }
        });
    }

    /** /duel accept - opens the confirmation GUI. Does NOT consume the pending request yet. */
    public void openConfirmation(final Player target) {
        final DuelRequest request = peekValidRequest(target);
        if (request == null) return;

        final Player sender = Bukkit.getPlayer(request.getSenderUuid());
        if (sender == null || !sender.isOnline()) {
            Text.send(target, "&c" + request.getSenderName() + " isn't online anymore.");
            return;
        }
        Long startedAt = preparing.get(target.getUniqueId());
        if (startedAt != null && System.currentTimeMillis() - startedAt.longValue() < 15000L) {
            return; // already finding a spot for this player - ignore the repeated click/command
        }
        preparing.put(target.getUniqueId(), Long.valueOf(System.currentTimeMillis()));

        double radius = config().getDouble("teleport-radius", 6000);
        int maxAttempts = config().getInt("max-location-attempts", 20);
        World duelWorld = resolveOverworld(target);

        // Rolled ONCE, right now - the biome shown in the GUI and the spot actually teleported to on
        // Accept are guaranteed to be this exact same roll.
        try {
            SafeLocationFinder.find(duelWorld, radius, maxAttempts, new SafeLocationFinder.Callback() {
                @Override
                public void done(final SafeLocationFinder.Result result) {
                    // may arrive on a region thread - open the GUI on the target's own thread
                    Sched.runOnEntity(target, new Runnable() {
                        @Override
                        public void run() {
                            preparing.remove(target.getUniqueId());
                            if (!target.isOnline()) return;
                            try {
                                openGui(target, sender, result);
                            } catch (RuntimeException ex) {
                                Text.send(target, "&cCouldn't open the duel screen - try /duel accept again.");
                                plugin.getLogger().warning("Could not open the duel GUI: " + ex);
                            }
                        }
                    });
                }
            });
        } catch (RuntimeException ex) {
            preparing.remove(target.getUniqueId());
            Text.send(target, "&cCouldn't find a spot for the duel - try again.");
            plugin.getLogger().warning("Could not roll a duel location: " + ex);
        }
    }

    private void openGui(Player target, Player sender, SafeLocationFinder.Result result) {
        String rankLine = PlaceholderBridge.getRankTag(sender);
        List<String> lore = new ArrayList<String>();
        lore.add("&7Biome: &f" + result.getBiome());
        lore.add("&7Name: &f" + sender.getName());
        if (rankLine != null) {
            lore.add("&7Rank: &f" + rankLine);
        }

        DuelGuiHolder holder = new DuelGuiHolder(sender.getUniqueId(), result.getLocation());
        Inventory inv = Bukkit.createInventory(holder, 9, Text.color("&5Duel vs " + sender.getName()));
        holder.setInventory(inv);
        inv.setItem(3, Items.skull(sender, "&e" + sender.getName(), lore));
        inv.setItem(4, Items.pane(true, "&a&lACCEPT"));
        inv.setItem(5, Items.pane(false, "&c&lCANCEL"));

        target.openInventory(inv);
    }

    /** Called from the GUI's ACCEPT click - this is the point the pending request actually gets consumed. */
    public void confirmAccept(Player target, DuelGuiHolder holder) {
        DuelRequest request = pendingByTarget.get(target.getUniqueId());
        if (request == null || !request.getSenderUuid().equals(holder.getSenderUuid())) {
            Text.send(target, "&cThat duel request is no longer valid.");
            return;
        }

        final Player sender = Bukkit.getPlayer(holder.getSenderUuid());
        if (sender == null || !sender.isOnline()) {
            Text.send(target, "&cThey aren't online anymore.");
            return;
        }

        pendingByTarget.remove(target.getUniqueId());

        double separation = config().getDouble("spawn-separation", 3);
        final String message = "&a&lDuel accepted! &f" + sender.getName() + " vs " + target.getName();
        Text.send(target, message);
        Sched.runOnEntity(sender, new Runnable() {
            @Override
            public void run() {
                Text.send(sender, message);
            }
        });

        new DuelCountdown(freezeManager, target, sender, holder.getDestination(), separation).start();
    }

    /**
     * Called from the GUI's CANCEL click. This only stops THIS confirmation attempt - it does NOT decline
     * the underlying duel request, which stays pending exactly as before. Running /duel accept again
     * just reopens the GUI.
     */
    public void cancelConfirmation(Player target) {
        Text.send(target, "&7Confirmation closed - the duel request is still pending.");
    }

    /** /duel decline - this is the one that actually removes the request and notifies the sender. */
    public void decline(Player target) {
        DuelRequest request = pendingByTarget.remove(target.getUniqueId());
        if (request == null) {
            Text.send(target, "&cYou don't have any pending duel requests.");
            return;
        }

        Text.send(target, "&7Duel declined.");

        final Player sender = Bukkit.getPlayer(request.getSenderUuid());
        if (sender != null && sender.isOnline()) {
            final String targetName = target.getName();
            Sched.runOnEntity(sender, new Runnable() {
                @Override
                public void run() {
                    Text.send(sender, "&c" + targetName + " declined your duel request.");
                }
            });
        }
    }

    private DuelRequest peekValidRequest(Player target) {
        DuelRequest request = pendingByTarget.get(target.getUniqueId());
        if (request == null) {
            Text.send(target, "&cYou don't have any pending duel requests.");
            return null;
        }
        long timeoutSeconds = config().getLong("request-timeout-seconds", 60);
        if (request.isExpired(System.currentTimeMillis(), timeoutSeconds)) {
            pendingByTarget.remove(target.getUniqueId());
            Text.send(target, "&cThat duel request expired.");
            return null;
        }
        return request;
    }

    private World resolveOverworld(Player target) {
        String configuredName = config().getString("teleport-world", "world");
        World configured = Bukkit.getWorld(configuredName);
        if (configured != null) return configured;

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        return target.getWorld();
    }

    public void forget(UUID uuid) {
        pendingByTarget.remove(uuid);
        preparing.remove(uuid);
    }
}
