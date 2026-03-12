
package com.cogtrade.network;

import net.minecraft.util.Identifier;

public class DepotActionPacket {
    public static final Identifier ID = new Identifier("cogtrade", "depot_action");
    // Action types
    public static final int ACTION_ADD    = 0;
    public static final int ACTION_REMOVE = 1;
}