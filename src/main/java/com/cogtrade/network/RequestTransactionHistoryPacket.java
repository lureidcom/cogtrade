package com.cogtrade.network;

import net.minecraft.util.Identifier;

/** Client → Server: Oyuncunun bakiye geçmişini iste. */
public class RequestTransactionHistoryPacket {
    public static final Identifier ID = new Identifier("cogtrade", "request_tx_history");
}
