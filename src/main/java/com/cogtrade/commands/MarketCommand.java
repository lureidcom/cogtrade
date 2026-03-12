package com.cogtrade.commands;

import com.cogtrade.block.MarketBlock;
import com.cogtrade.config.CogTradeConfig;
import com.cogtrade.market.MarketManager;
import com.cogtrade.network.LocateMarketPacket;
import com.cogtrade.network.OpenMarketPacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class MarketCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("market")

                // /market  — marketi aç
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) return 0;
                    var items = MarketManager.getAllItems();
                    if (items.isEmpty()) {
                        context.getSource().sendFeedback(
                                () -> Text.literal("Pazarda henüz ürün yok."), false);
                        return 0;
                    }
                    OpenMarketPacket.send(player, items, false, CogTradeConfig.get().sellPriceMultiplier);
                    return 1;
                })

                // /market locate  — 50 blok yarıçapında en yakın MarketBlock
                .then(CommandManager.literal("locate")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;

                        BlockPos center = player.getBlockPos();
                        int radius = 50;
                        BlockPos nearest = null;
                        double nearestDistSq = Double.MAX_VALUE;

                        for (int dx = -radius; dx <= radius; dx++) {
                            for (int dy = -radius; dy <= radius; dy++) {
                                for (int dz = -radius; dz <= radius; dz++) {
                                    BlockPos pos = center.add(dx, dy, dz);
                                    if (player.getWorld().getBlockState(pos).getBlock()
                                            == MarketBlock.INSTANCE) {
                                        double dist = center.getSquaredDistance(pos);
                                        if (dist < nearestDistSq) {
                                            nearestDistSq = dist;
                                            nearest = pos.toImmutable();
                                        }
                                    }
                                }
                            }
                        }

                        if (nearest == null) {
                            context.getSource().sendFeedback(
                                    () -> Text.literal("§c50 blok içinde Market Bloğu bulunamadı."), false);
                            return 0;
                        }

                        final BlockPos found = nearest;
                        LocateMarketPacket.send(player, found);
                        context.getSource().sendFeedback(() -> Text.literal(
                                "§a⬡ Market Bloğu bulundu: §e"
                                + found.getX() + ", " + found.getY() + ", " + found.getZ()
                                + " §7(30 sn outline)"), false);
                        return 1;
                    })
                )

                // /market history  — son 10 işlem
                .then(CommandManager.literal("history")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;

                        List<MarketManager.HistoryEntry> history =
                                MarketManager.getPlayerHistory(player.getUuid(), 10);

                        if (history.isEmpty()) {
                            context.getSource().sendFeedback(
                                    () -> Text.literal("§7Henüz market işlemi yok."), false);
                            return 1;
                        }

                        context.getSource().sendFeedback(
                                () -> Text.literal("§e── Son 10 Market İşlemi ──"), false);
                        for (int i = 0; i < history.size(); i++) {
                            final String line = (i + 1) + ". " + history.get(i).format();
                            context.getSource().sendFeedback(() -> Text.literal(line), false);
                        }
                        return 1;
                    })
                )
        );
    }
}
