package com.cogtrade.network;

import net.minecraft.util.Identifier;

/**
 * C2S — Player wants to move an item between their inventory and offer slot.
 *
 * offerSlot: 0–8   target offer grid index; -1 = auto-pick first empty
 * invSlot:   0–35  source inventory slot;   -1 = return offer slot to inventory
 */
public class TradeItemMovePacket {
    public static final Identifier ID = new Identifier("cogtrade", "trade_item_move");
}
