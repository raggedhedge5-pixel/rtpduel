package com.rtpduels.plugin.compat;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the GUI items on any version. 1.13 renamed almost every item ("the flattening"), so
 * materials are looked up BY NAME: modern names first, then the pre-1.13 name + data value.
 */
public final class Items {

    private Items() {}

    /** Player head showing {@code owner}'s skin. */
    public static ItemStack skull(OfflinePlayer owner, String displayName, List<String> lore) {
        ItemStack item;
        Material modern = Material.getMaterial("PLAYER_HEAD");
        if (modern != null) {
            item = new ItemStack(modern);
        } else {
            Material legacy = Material.getMaterial("SKULL_ITEM");
            item = legacy != null ? new ItemStack(legacy, 1, (short) 3) : new ItemStack(Material.STONE);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Class<?> skullMeta = Reflect.classOrNull("org.bukkit.inventory.meta.SkullMeta");
        if (skullMeta != null && skullMeta.isInstance(meta)) {
            boolean ownerSet = false;
            Method setOwningPlayer = Reflect.method(skullMeta, "setOwningPlayer", OfflinePlayer.class);
            if (setOwningPlayer != null) {
                try {
                    setOwningPlayer.invoke(meta, owner);
                    ownerSet = true;
                } catch (Throwable ignored) {
                    // try the old method below
                }
            }
            if (!ownerSet) {
                Method setOwner = Reflect.method(skullMeta, "setOwner", String.class);
                if (setOwner != null) {
                    try {
                        setOwner.invoke(meta, owner.getName());
                    } catch (Throwable ignored) {
                        // head just won't show a skin - not worth failing the GUI
                    }
                }
            }
        }

        applyText(meta, displayName, lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Coloured glass pane button. {@code lime} true = green, false = red. */
    public static ItemStack pane(boolean lime, String displayName) {
        ItemStack item;
        Material modern = Material.getMaterial(lime ? "LIME_STAINED_GLASS_PANE" : "RED_STAINED_GLASS_PANE");
        if (modern != null) {
            item = new ItemStack(modern);
        } else {
            Material legacy = Material.getMaterial("STAINED_GLASS_PANE");
            item = legacy != null ? new ItemStack(legacy, 1, (short) (lime ? 5 : 14)) : new ItemStack(Material.STONE);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyText(meta, displayName, null);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void applyText(ItemMeta meta, String displayName, List<String> lore) {
        if (displayName != null) {
            meta.setDisplayName(Text.color("&r" + displayName));
        }
        if (lore != null && !lore.isEmpty()) {
            List<String> colored = new ArrayList<String>(lore.size());
            for (String line : lore) {
                colored.add(Text.color("&r" + line));
            }
            meta.setLore(colored);
        }
    }
}
