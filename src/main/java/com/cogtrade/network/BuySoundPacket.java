package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Sunucu → İstemci: başarılı satın alımda ses çaldır. */
public class BuySoundPacket {
    public static final Identifier ID = new Identifier("cogtrade", "buy_sound");

    public static void send(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, ID, PacketByteBufs.empty());
    }
}
