package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import com.cogtrade.market.MarketItem;

import java.util.List;

public class OpenMarketPacket {

    public static final Identifier ID = new Identifier("cogtrade", "open_market");

    /**
     * @param canBuy             true → MarketBlock'tan açıldı (alım + satım aktif)
     *                           false → /market komutundan açıldı (sadece listeleme)
     * @param sellPriceMultiplier sunucu config'inden gelen satış fiyat çarpanı
     */
    public static void send(ServerPlayerEntity player, List<MarketItem> items,
                            boolean canBuy, double sellPriceMultiplier) {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeInt(items.size());
        for (MarketItem item : items) {
            buf.writeInt(item.getId());
            buf.writeString(item.getItemId());
            buf.writeString(item.getDisplayName());
            buf.writeString(item.getCategory());
            buf.writeDouble(item.getPrice());
            buf.writeInt(item.getStock());
        }
        buf.writeBoolean(canBuy);
        buf.writeDouble(sellPriceMultiplier);

        ServerPlayNetworking.send(player, ID, buf);
    }
}
