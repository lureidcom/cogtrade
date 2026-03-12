package com.cogtrade.network;

import com.cogtrade.market.PlayerShopManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Server → Client: Trade Post sahibine yönetim ekranını açar.
 * Depot'taki stok + aktif ilanlar gönderilir.
 */
public class OpenTradePostManagePacket {
    public static final Identifier ID = new Identifier("cogtrade", "open_trade_post_manage");

    public static void send(ServerPlayerEntity player, String ownerUuid) {
        Map<String, Integer> available = PlayerShopManager.scanChestContents(
                ownerUuid, player.getServerWorld());
        List<PlayerShopManager.ShopListing> listings = PlayerShopManager.getListings(ownerUuid);

        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(available.size());
        for (Map.Entry<String, Integer> entry : available.entrySet()) {
            buf.writeString(entry.getKey());
            buf.writeInt(entry.getValue());
        }

        buf.writeInt(listings.size());
        for (PlayerShopManager.ShopListing l : listings) {
            buf.writeInt(l.id());
            buf.writeString(l.itemId());
            buf.writeString(l.displayName());
            buf.writeString(l.category());
            buf.writeDouble(l.price());
        }

        ServerPlayNetworking.send(player, ID, buf);
    }
}
