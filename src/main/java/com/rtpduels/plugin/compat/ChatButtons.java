package com.rtpduels.plugin.compat;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * The clickable "[Accept]  [Decline]" line, built with the BungeeCord chat components that ship with every
 * Spigot/Paper/Folia version. Kept in its own class so that, if a server somehow lacks those classes, only this
 * class fails to load - Text catches that and falls back to plain text.
 */
final class ChatButtons {

    private ChatButtons() {}

    static void send(Player target) {
        TextComponent accept = new TextComponent("[Accept]");
        accept.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        accept.setBold(Boolean.TRUE);
        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept"));
        accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[]{new TextComponent("Click to accept the duel")}));

        TextComponent decline = new TextComponent("[Decline]");
        decline.setColor(net.md_5.bungee.api.ChatColor.RED);
        decline.setBold(Boolean.TRUE);
        decline.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel decline"));
        decline.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new BaseComponent[]{new TextComponent("Click to decline the duel")}));

        TextComponent gap = new TextComponent("  ");

        target.spigot().sendMessage(new BaseComponent[]{accept, gap, decline});
    }
}
