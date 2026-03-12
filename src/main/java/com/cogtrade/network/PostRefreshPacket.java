package com.cogtrade.network;

import net.minecraft.util.Identifier;

/**
 * Client → Server: Trade Post yönetim ekranı anlık güncelleme isteği.
 * Sunucu OpenTradePostManagePacket ile yanıt verir.
 */
public class PostRefreshPacket {
    public static final Identifier ID = new Identifier("cogtrade", "post_refresh");
}
