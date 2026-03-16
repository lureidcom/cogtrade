package com.cogtrade.network;

import net.minecraft.util.Identifier;

/**
 * Client → Server: Büyü Marketi satın alma isteği.
 * Paket içeriği: effectId (String), amplifier (int), durationTicks (int)
 */
public class BuyEffectPacket {
    public static final Identifier ID = new Identifier("cogtrade", "buy_effect");
}
