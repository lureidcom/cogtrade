package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * S2C — Bekleyen takas teklifi temizlendi.
 * Teklif kabul, ret, zaman aşımı veya teklif sahibi çevrimdışı olunca gönderilir.
 */
public class TradeOfferClearedPacket {

    public static final Identifier ID = new Identifier("cogtrade", "trade_offer_cleared");

    public static void send(ServerPlayerEntity target) {
        ServerPlayNetworking.send(target, ID, PacketByteBufs.empty());
    }
}
