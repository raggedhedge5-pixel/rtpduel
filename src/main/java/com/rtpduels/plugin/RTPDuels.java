package com.rtpduels.plugin;

import com.rtpduels.plugin.commands.DuelCommand;
import com.rtpduels.plugin.duel.DuelManager;
import com.rtpduels.plugin.duel.FreezeManager;
import com.rtpduels.plugin.listeners.DuelGuiListener;
import com.rtpduels.plugin.listeners.FreezeListener;
import com.rtpduels.plugin.sched.Sched;
import com.rtpduels.plugin.util.SelfTest;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RTP Duels - /duel a player, confirm in a GUI, then both land on the same random land-only spot.
 * One jar for Bukkit, Spigot, Paper and Folia (Minecraft 1.8 up to the newest release).
 */
public class RTPDuels extends JavaPlugin implements Listener {

    private DuelManager duelManager;
    private FreezeManager freezeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            Sched.init(this);
        } catch (Exception ex) {
            getLogger().severe("Could not set up the scheduler (" + ex + "). RTP Duels is disabling itself.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        freezeManager = new FreezeManager();
        duelManager = new DuelManager(this, freezeManager);

        DuelCommand duelCommand = new DuelCommand(duelManager);
        PluginCommand cmd = getCommand("duel");
        if (cmd != null) {
            cmd.setExecutor(duelCommand);
            cmd.setTabCompleter(duelCommand);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new FreezeListener(this, freezeManager), this);
        getServer().getPluginManager().registerEvents(new DuelGuiListener(duelManager), this);

        getLogger().info("RTP Duels enabled" + (Sched.isFolia() ? " (Folia mode)." : "."));

        if (SelfTest.requested()) {
            SelfTest.scheduleOnce(this); // developer diagnostic, only with -Drtpduels.selftest=true
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Drop any pending request TO this player - no point letting them accept/decline something after
        // logging out and back in with no context. (If they disconnect mid-countdown, DuelCountdown
        // notices within a second and cleans that up too.)
        duelManager.forget(event.getPlayer().getUniqueId());
    }
}
