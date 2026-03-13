package com.cogtrade.network;

import net.minecraft.util.Identifier;

/**
 * C2S — Player wants to move an item between their inventory and offer slot.
 *
 * Payload: sessionId (String), offerSlot (int), invSlot (int), clickType (byte)
 *
 * offerSlot: 0–8   target offer grid index; -1 = auto-pick first empty
 * invSlot:   0–35  source inventory slot;   -1 = return offer slot to inventory
 * clickType: 0 = left click (full stack), 1 = right click (single item)
 */
public class TradeItemMovePacket {
    public static final Identifier ID = new Identifier("cogtrade", "trade_item_move");

    public static final byte CLICK_LEFT  = 0;  // Move entire stack
    public static final byte CLICK_RIGHT = 1;  // Move single item
}
