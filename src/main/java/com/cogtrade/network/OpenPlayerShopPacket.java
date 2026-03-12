
package com.cogtrade.network;

import com.cogtrade.market.PlayerShopManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

public class OpenPlayerShopPacket {
    public static final Identifier ID = new Identifier("cogtrade", "open_player_shop");

    public static void send(ServerPlayerEntity player, String ownerUuid, String ownerName,
                            List<PlayerShopManager.ShopListing> listings,
                            Map<String, Integer> available,
                            double sellMultiplier) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(ownerUuid);
        buf.writeString(ownerName);
        buf.writeDouble(sellMultiplier);
        buf.writeInt(listings.size());
        for (PlayerShopManager.ShopListing l : listings) {
            buf.writeInt(l.id());
            buf.writeString(l.itemId());
            buf.writeString(l.displayName());
            buf.writeString(l.category());
            buf.writeDouble(l.price());
            buf.writeInt(available.getOrDefault(l.itemId(), 0)); // stock count
        }
        ServerPlayNetworking.send(player, ID, buf);
    }
}
