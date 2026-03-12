package com.cogtrade.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * S2C — Trade completed successfully.  Closes GUI and shows summary.
 */
public class TradeCompletePacket {

    public static final Identifier ID = new Identifier("cogtrade", "trade_complete");

    /**
     * @param partnerName    the other player's display name
     * @param myCoinsGiven   coins this player sent to the partner
     * @param myCoinsReceived coins this player received from the partner
     */
    public static void send(ServerPlayerEntity player, String partnerName,
                             double myCoinsGiven, double myCoinsReceived) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(partnerName);
        buf.writeDouble(myCoinsGiven);
        buf.writeDouble(myCoinsReceived);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
