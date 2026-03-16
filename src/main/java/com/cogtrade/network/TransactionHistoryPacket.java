package com.cogtrade.network;

import com.cogtrade.client.TransactionEntry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

/** Server → Client: Bakiye geçmişi listesi. */
public class TransactionHistoryPacket {

    public static final Identifier ID = new Identifier("cogtrade", "tx_history");

    public static void send(ServerPlayerEntity player, List<TransactionEntry> entries) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entries.size());
        for (TransactionEntry e : entries) {
            buf.writeInt(e.id);
            buf.writeString(e.txType);
            buf.writeDouble(e.amount);
            buf.writeString(e.party);
            buf.writeLong(e.timestamp);
            buf.writeString(e.note);
            buf.writeString(e.detail);
        }
        ServerPlayNetworking.send(player, ID, buf);
    }
}
