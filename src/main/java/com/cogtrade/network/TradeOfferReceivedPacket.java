package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * S2C — Gelen takas teklifi bildirimi.
 * Sunucu, bir oyuncu takas isteği aldığında bu paketi hedefe gönderir.
 */
public class TradeOfferReceivedPacket {

    public static final Identifier ID = new Identifier("cogtrade", "trade_offer_received");

    public static void send(ServerPlayerEntity target, String initiatorName) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(initiatorName);
        ServerPlayNetworking.send(target, ID, buf);
    }
}
