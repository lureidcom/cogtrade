package com.cogtrade.network;

import net.minecraft.util.Identifier;

/**
 * Client → Server: Trade Post sahibinin ilan eklemesi / kaldırması.
 * (Trade Post yönetim ekranından gönderilir, Depot değil.)
 */
public class PostActionPacket {
    public static final Identifier ID = new Identifier("cogtrade", "post_action");

    public static final int ACTION_ADD    = 1;
    public static final int ACTION_REMOVE = 2;
}
