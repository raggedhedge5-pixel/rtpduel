package com.rtpduels.plugin.util;

import com.rtpduels.plugin.compat.Blocks;
import com.rtpduels.plugin.compat.Items;
import com.rtpduels.plugin.compat.Sounds;
import com.rtpduels.plugin.compat.Teleport;
import com.rtpduels.plugin.duel.DuelGuiHolder;
import com.rtpduels.plugin.sched.Sched;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Developer diagnostic - does nothing on a normal server. Only runs when the server is started with
 * -Drtpduels.selftest=true (the optional "Test on real servers" GitHub workflow does that). It exercises
 * everything that differs between Minecraft versions (items, inventory, scheduler, random spot finder)
 * without needing a player and logs one "SELFTEST RESULT: PASS/FAIL" line.
 */
public final class SelfTest {

    private SelfTest() {}

    public static boolean requested() {
        return Boolean.getBoolean("rtpduels.selftest");
    }

    /** Waits ~5 seconds of server ticks so the world is ready, then runs once. */
    public static void scheduleOnce(final Plugin plugin) {
        final AtomicInteger waited = new AtomicInteger();
        final Sched.Handle[] handle = new Sched.Handle[1];
        handle[0] = Sched.repeatGlobal(new Runnable() {
            @Override
            public void run() {
                if (waited.incrementAndGet() < 5) return;
                if (handle[0] != null) handle[0].cancel();
                try {
                    execute(plugin);
                } catch (Throwable ex) {
                    plugin.getLogger().info("SELFTEST RESULT: FAIL " + ex);
                }
            }
        }, 20L, 20L);
    }

    private static void say(Plugin plugin, String line) {
        plugin.getLogger().info("SELFTEST " + line);
    }

    private static void execute(final Plugin plugin) {
        final List<String> failures = Collections.synchronizedList(new ArrayList<String>());
        say(plugin, "server: " + Bukkit.getVersion() + " | api: " + Bukkit.getBukkitVersion());
        say(plugin, "folia=" + Sched.isFolia() + " asyncTeleport=" + Teleport.hasAsyncTeleport() + " sound=" + Sounds.isAvailable());

        // 1) items + inventory
        try {
            ItemStack head = Items.skull(Bukkit.getOfflinePlayer(UUID.randomUUID()), "&eTest", Collections.singletonList("&7Biome: &fTest"));
            ItemStack lime = Items.pane(true, "&a&lACCEPT");
            ItemStack red = Items.pane(false, "&c&lCANCEL");
            DuelGuiHolder holder = new DuelGuiHolder(UUID.randomUUID(), null);
            Inventory inv = Bukkit.createInventory(holder, 9, "Duel vs Test");
            holder.setInventory(inv);
            inv.setItem(3, head);
            inv.setItem(4, lime);
            inv.setItem(5, red);
            say(plugin, "items ok: head=" + head.getType() + " accept=" + lime.getType() + " cancel=" + red.getType());
            if (head.getType().name().equals("STONE") || lime.getType().name().equals("STONE")) {
                failures.add("items fell back to STONE");
            }
        } catch (Throwable ex) {
            failures.add("items/inventory: " + ex);
        }

        // 2) scheduler: a global task and a repeating timer that must fire twice
        final AtomicInteger ticks = new AtomicInteger();
        final Sched.Handle[] timer = new Sched.Handle[1];
        try {
            Sched.runGlobal(new Runnable() {
                @Override
                public void run() {
                    say(plugin, "scheduler runGlobal ok");
                }
            });
            timer[0] = Sched.repeatGlobal(new Runnable() {
                @Override
                public void run() {
                    if (ticks.incrementAndGet() == 2) {
                        say(plugin, "scheduler repeatGlobal ok");
                        if (timer[0] != null) timer[0].cancel();
                    }
                }
            }, 1L, 2L);
        } catch (Throwable ex) {
            failures.add("scheduler: " + ex);
        }

        // 3) random spot finder (async chunk load + region-thread read on Paper/Folia)
        World world = null;
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.getEnvironment() == World.Environment.NORMAL) {
                world = candidate;
                break;
            }
        }
        if (world == null) {
            failures.add("no overworld found");
            finish(plugin, failures);
            return;
        }
        try {
            SafeLocationFinder.find(world, 300, 20, new SafeLocationFinder.Callback() {
                @Override
                public void done(SafeLocationFinder.Result result) {
                    say(plugin, "finder ok: " + result.getLocation().getBlockX() + "," + result.getLocation().getBlockY() + ","
                            + result.getLocation().getBlockZ() + " biome=" + result.getBiome()
                            + (result.isFallback() ? " (fell back to spawn)" : "")
                            + " minHeight=" + Blocks.minHeight(result.getLocation().getWorld()));
                    if (result.getBiome().equals("Unknown")) {
                        failures.add("biome name unresolved");
                    }
                    finish(plugin, failures);
                }
            });
        } catch (Throwable ex) {
            failures.add("finder: " + ex);
            finish(plugin, failures);
        }
    }

    private static void finish(Plugin plugin, List<String> failures) {
        if (failures.isEmpty()) {
            say(plugin, "RESULT: PASS");
        } else {
            say(plugin, "RESULT: FAIL " + failures);
        }
    }
}
