package com.cogtrade.network;

import net.minecraft.util.Identifier;

/**
 * C2S — Player sets their coin offer amount.
 *
 * Payload: sessionId (String), amount (double)
 */
public class TradeCoinOfferPacket {
    public static final Identifier ID = new Identifier("cogtrade", "trade_coin_offer");
}
