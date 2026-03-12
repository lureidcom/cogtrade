package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class BalanceUpdatePacket {

    public static final Identifier ID = new Identifier("cogtrade", "balance_update");

    public static void send(ServerPlayerEntity player, double balance, double dailyEarned, double dailySpent) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(balance);
        buf.writeDouble(dailyEarned);
        buf.writeDouble(dailySpent);
        ServerPlayNetworking.send(player, ID, buf);
    }
}