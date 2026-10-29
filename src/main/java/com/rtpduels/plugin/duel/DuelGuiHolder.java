package com.rtpduels.plugin.duel;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker for the confirmation GUI, carrying the sender's UUID and the exact destination that was
 * already rolled when the GUI opened - so the biome shown in the head's hover text and the location
 * actually teleported to on Accept are guaranteed to be the same roll, not two different random spots.
 */
public class DuelGuiHolder implements InventoryHolder {

    private final UUID senderUuid;
    private final Location destination;
    private Inventory inventory;
    private boolean resolved = false;

    public DuelGuiHolder(UUID senderUuid, Location destination) {
        this.senderUuid = senderUuid;
        this.destination = destination;
    }

    public UUID getSenderUuid() {
        return senderUuid;
    }

    public Location getDestination() {
        return destination;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void markResolved() {
        this.resolved = true;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
