package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

public class AllShopListingsPacket {

    public static final Identifier ID = new Identifier("cogtrade", "all_shop_listings");

    public record Entry(
            String ownerUuid,
            String ownerName,
            String itemId,
            String displayName,
            String category,
            double price,
            int stock
    ) {}

    public static void send(ServerPlayerEntity player, List<Entry> entries) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entries.size());
        for (Entry e : entries) {
            buf.writeString(e.ownerUuid());
            buf.writeString(e.ownerName());
            buf.writeString(e.itemId());
            buf.writeString(e.displayName());
            buf.writeString(e.category());
            buf.writeDouble(e.price());
            buf.writeInt(e.stock());
        }
        ServerPlayNetworking.send(player, ID, buf);
    }
}
