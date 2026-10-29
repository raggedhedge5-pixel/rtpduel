package com.rtpduels.plugin.compat;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * All player-facing text. Uses only the classic String API plus the BungeeCord chat
 * components (clickable buttons) - those exist on every Spigot/Paper/Folia version.
 * Anything that could be missing falls back to plain text, so nothing here can crash a duel.
 */
public final class Text {

    public static final char SECTION = '§';

    private static final Method SEND_TITLE_5 = Reflect.method(Player.class, "sendTitle",
            String.class, String.class, int.class, int.class, int.class);
    private static final Method SEND_TITLE_2 = Reflect.method(Player.class, "sendTitle",
            String.class, String.class);

    private Text() {}

    /** '&' colour codes to section-sign codes. */
    public static String color(String input) {
        if (input == null) return "";
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(chars[i + 1]) > -1) {
                chars[i] = SECTION;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static void send(Player player, String message) {
        if (player != null) player.sendMessage(color(message));
    }

    /**
     * The "[Accept]  [Decline]" line with real click-to-run-command buttons. If the chat
     * component API misbehaves on some build, the player still gets a readable line telling
     * them to type the commands (which always work).
     */
    public static void sendDuelButtons(Player target) {
        try {
            ChatButtons.send(target);
        } catch (Throwable ex) {
            target.sendMessage(color("&a&l[Accept] &c&l[Decline] &7- type &f/duel accept &7or &f/duel decline"));
        }
    }

    /** Title + subtitle. Falls back to a chat line on servers without the title API. Times are in ticks. */
    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        String t = color(title);
        String s = color(subtitle == null ? "" : subtitle);
        if (SEND_TITLE_5 != null) {
            try {
                SEND_TITLE_5.invoke(player, t, s, Integer.valueOf(fadeIn), Integer.valueOf(stay), Integer.valueOf(fadeOut));
                return;
            } catch (Throwable ignored) {
                // fall through
            }
        }
        if (SEND_TITLE_2 != null) {
            try {
                SEND_TITLE_2.invoke(player, t, s);
                return;
            } catch (Throwable ignored) {
                // fall through
            }
        }
        player.sendMessage(s.isEmpty() ? t : t + " " + s);
    }
}
