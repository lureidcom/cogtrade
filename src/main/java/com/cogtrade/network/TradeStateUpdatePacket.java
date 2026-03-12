package com.cogtrade.network;

import com.cogtrade.trade.DirectTradeSession;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * S2C — Incremental state sync sent to both players after any offer change.
 * Same payload structure as the offer-state portion of OpenDirectTradePacket.
 */
public class TradeStateUpdatePacket {

    public static final Identifier ID = new Identifier("cogtrade", "trade_state_update");

    public static void send(ServerPlayerEntity player, DirectTradeSession session, boolean isInitiator) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(session.getSessionId());
        OpenDirectTradePacket.writeOfferState(buf, session, player.getUuid(), isInitiator);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
