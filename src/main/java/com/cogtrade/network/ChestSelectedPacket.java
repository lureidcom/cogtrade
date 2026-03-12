package com.cogtrade.network;

import com.cogtrade.market.PlayerShopManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.stream.Collectors;

public class ChestSelectedPacket {
    public static final Identifier ID = new Identifier("cogtrade", "chest_selected");

    /**
     * Server → Client: Seçili tüm sandık pozisyonlarını gönder.
     * Oynatıcının mevcut dünyasında olan sandıklar filtrelenir.
     */
    public static void sendAll(ServerPlayerEntity player,
                               List<PlayerShopManager.ChestPos> allChests,
                               String currentWorldId) {
        List<BlockPos> worldChests = allChests.stream()
                .filter(cp -> cp.world().equals(currentWorldId))
                .map(PlayerShopManager.ChestPos::toBlockPos)
                .collect(Collectors.toList());

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(worldChests.size());
        for (BlockPos pos : worldChests) {
            buf.writeBlockPos(pos);
        }
        ServerPlayNetworking.send(player, ID, buf);
    }

    /** Eski uyumluluk — tek pozisyon gönder (artık kullanılmıyor ama derleme kırılmasın) */
    @Deprecated
    public static void send(ServerPlayerEntity player, BlockPos pos, boolean added, int totalChests) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(1);
        buf.writeBlockPos(pos);
        ServerPlayNetworking.send(player, ID, buf);
    }
}
