package com.rtpduels.plugin.commands;

import com.rtpduels.plugin.compat.Text;
import com.rtpduels.plugin.duel.DuelManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DuelCommand implements CommandExecutor, TabCompleter {

    private final DuelManager duelManager;

    public DuelCommand(DuelManager duelManager) {
        this.duelManager = duelManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Text.color("&cOnly players can duel."));
            return true;
        }
        Player player = (Player) sender;
        if (args.length != 1) {
            Text.send(player, "&cUsage: /duel <player|accept|decline>");
            return true;
        }

        String arg = args[0];
        if (arg.equalsIgnoreCase("accept")) {
            duelManager.openConfirmation(player);
            return true;
        }
        if (arg.equalsIgnoreCase("decline")) {
            duelManager.decline(player);
            return true;
        }

        Player target = Bukkit.getPlayerExact(arg);
        if (target == null || !target.isOnline()) {
            Text.send(player, "&c'" + arg + "' isn't online.");
            return true;
        }
        duelManager.challenge(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> matches = new ArrayList<String>();
        if (args.length != 1) return matches;

        List<String> options = new ArrayList<String>();
        options.add("accept");
        options.add("decline");
        for (Player online : Bukkit.getOnlinePlayers()) {
            options.add(online.getName());
        }

        String typedLower = args[0].toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(typedLower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
