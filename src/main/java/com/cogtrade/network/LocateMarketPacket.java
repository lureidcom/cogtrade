package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class LocateMarketPacket {

    public static final Identifier ID = new Identifier("cogtrade", "locate_market");

    /** pos == null → bulunamadı */
    public static void send(ServerPlayerEntity player, BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeInt(pos.getX());
            buf.writeInt(pos.getY());
            buf.writeInt(pos.getZ());
        }
        ServerPlayNetworking.send(player, ID, buf);
    }
}
