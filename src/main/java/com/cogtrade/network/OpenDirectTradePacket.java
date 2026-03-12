package com.cogtrade.network;

import com.cogtrade.trade.DirectTradeSession;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * S2C — Opens the DirectTradeScreen on the client for the first time.
 * Contains session metadata + initial offer state.
 */
public class OpenDirectTradePacket {

    public static final Identifier ID = new Identifier("cogtrade", "open_direct_trade");

    /**
     * @param isInitiator true when sending to the trade initiator, false for the target.
     *                    Determines which side is "mine" vs "theirs" in the GUI.
     */
    public static void send(ServerPlayerEntity player, DirectTradeSession session, boolean isInitiator) {
        PacketByteBuf buf = PacketByteBufs.create();

        buf.writeString(session.getSessionId());
        buf.writeBoolean(isInitiator);
        buf.writeString(session.getPartnerName(player.getUuid()));
        buf.writeString(session.getPartnerUuid(player.getUuid()).toString());

        // State (perspective of the recipient)
        writeOfferState(buf, session, player.getUuid(), isInitiator);

        ServerPlayNetworking.send(player, ID, buf);
    }

    /** Shared serialisation used by both Open and Update packets. */
    public static void writeOfferState(PacketByteBuf buf, DirectTradeSession session,
                                        java.util.UUID playerUuid, boolean isInitiator) {
        ItemStack[] myOffer      = session.getOfferFor(playerUuid);
        ItemStack[] partnerOffer = session.getOfferFor(session.getPartnerUuid(playerUuid));

        for (int i = 0; i < DirectTradeSession.OFFER_SLOTS; i++) {
            buf.writeItemStack(myOffer[i] != null ? myOffer[i] : ItemStack.EMPTY);
        }
        for (int i = 0; i < DirectTradeSession.OFFER_SLOTS; i++) {
            buf.writeItemStack(partnerOffer[i] != null ? partnerOffer[i] : ItemStack.EMPTY);
        }

        buf.writeDouble(session.getCoinsFor(playerUuid));
        buf.writeDouble(session.getCoinsFor(session.getPartnerUuid(playerUuid)));
        buf.writeBoolean(session.isReadyFor(playerUuid));
        buf.writeBoolean(session.isReadyFor(session.getPartnerUuid(playerUuid)));
    }
}
