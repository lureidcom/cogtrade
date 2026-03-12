package com.cogtrade.network;

import net.minecraft.util.Identifier;

/**
 * C2S — Player toggles their ready/confirm state.
 *
 * Payload: sessionId (String)
 */
public class TradeSetReadyPacket {
    public static final Identifier ID = new Identifier("cogtrade", "trade_set_ready");
}
