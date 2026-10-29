package com.rtpduels.plugin.compat;

import com.rtpduels.plugin.sched.Sched;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Teleports a player the right way for the server it is on.
 *
 * Folia forbids the normal synchronous teleport (the destination is usually in another region), and
 * Paper's teleportAsync also avoids a main-thread stall when the destination chunk has to be loaded.
 * Plain Spigot and old servers don't have it, so they get the ordinary teleport.
 */
public final class Teleport {

    private static final Method TELEPORT_ASYNC = Reflect.method(Entity.class, "teleportAsync", Location.class);

    private Teleport() {}

    public static boolean hasAsyncTeleport() {
        return TELEPORT_ASYNC != null;
    }

    /** Must be called on the player's own thread (the main thread outside Folia). {@code done} runs on the player's thread again. */
    public static void to(final Player player, final Location destination, final Consumer<Boolean> done) {
        if (TELEPORT_ASYNC != null) {
            try {
                Object future = TELEPORT_ASYNC.invoke(player, destination);
                if (future instanceof CompletableFuture) {
                    ((CompletableFuture<?>) future).whenComplete(new BiConsumer<Object, Throwable>() {
                        @Override
                        public void accept(final Object result, Throwable error) {
                            final boolean ok = error == null && !Boolean.FALSE.equals(result);
                            Sched.runOnEntity(player, new Runnable() {
                                @Override
                                public void run() {
                                    done.accept(Boolean.valueOf(ok));
                                }
                            });
                        }
                    });
                    return;
                }
            } catch (Throwable ex) {
                if (Sched.isFolia()) {
                    // no safe fallback on Folia - report failure rather than call a forbidden sync teleport
                    done.accept(Boolean.FALSE);
                    return;
                }
                // otherwise fall through to the plain teleport below
            }
        }

        if (Sched.isFolia()) {
            done.accept(Boolean.FALSE);
            return;
        }
        boolean ok = player.teleport(destination);
        done.accept(Boolean.valueOf(ok));
    }
}
