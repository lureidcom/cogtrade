package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ChestSelectedPacket {
    public static final Identifier ID = new Identifier("cogtrade", "chest_selected");

    /** Server → Client: sandık eklendi/çıkarıldı bildirimi */
    public static void send(ServerPlayerEntity player, BlockPos pos, boolean added, int totalChests) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeBoolean(added);
        buf.writeInt(totalChests);
        ServerPlayNetworking.send(player, ID, buf);
    }
}