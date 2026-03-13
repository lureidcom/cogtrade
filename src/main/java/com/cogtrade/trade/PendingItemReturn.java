package com.cogtrade.trade;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Holds items that need to be returned to an offline player.
 * These items are recovered when the player logs back in.
 * This prevents item loss from unsafe world drops.
 */
public class PendingItemReturn {

    private final UUID ownerUuid;
    private final String ownerName;
    private final List<ItemStack> items;
    private final long timestamp;
    private final String reason;

    public PendingItemReturn(UUID ownerUuid, String ownerName, ItemStack[] itemArray, String reason) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.items = new ArrayList<>();
        for (ItemStack stack : itemArray) {
            if (!stack.isEmpty()) {
                this.items.add(stack.copy());
            }
        }
        this.timestamp = System.currentTimeMillis();
        this.reason = reason;
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public List<ItemStack> getItems() { return items; }
    public long getTimestamp() { return timestamp; }
    public String getReason() { return reason; }
    public boolean isEmpty() { return items.isEmpty(); }
}
