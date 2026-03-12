package com.cogtrade.network;

import net.minecraft.util.Identifier;

/**
 * C2S — Player cancels the active trade (via GUI button or /trade cancel).
 *
 * Payload: sessionId (String)
 */
public class TradeCancelPacket {
    public static final Identifier ID = new Identifier("cogtrade", "trade_cancel");
}
