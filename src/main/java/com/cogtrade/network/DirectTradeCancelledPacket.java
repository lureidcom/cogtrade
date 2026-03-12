package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * S2C — Tells the client to close the DirectTradeScreen.
 * Sent on abort, failure, or unexpected disconnect.
 */
public class DirectTradeCancelledPacket {

    public static final Identifier ID = new Identifier("cogtrade", "direct_trade_cancelled");

    public static void send(ServerPlayerEntity player, String reason) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(reason);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
