package com.cogtrade.network;

import net.minecraft.util.Identifier;

/**
 * Client → Server: Alıcı Trade Post ekranı anlık güncelleme isteği.
 * Payload: ownerUuid (String)
 * Sunucu OpenPlayerShopPacket ile yanıt verir.
 */
public class PlayerShopRefreshPacket {
    public static final Identifier ID = new Identifier("cogtrade", "player_shop_refresh");
}
