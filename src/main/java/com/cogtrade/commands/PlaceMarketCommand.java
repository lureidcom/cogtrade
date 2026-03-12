
package com.cogtrade.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlaceMarketCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("placemarket")
                        // Herkes görebilir, yetki kontrolü execute içinde yapılır
                        .executes(ctx -> {
                            ServerCommandSource source = ctx.getSource();

                            boolean isSingleplayer = source.getServer().isSingleplayer();
                            boolean isOp = source.hasPermissionLevel(2);

                            if (!isSingleplayer && !isOp) {
                                source.sendError(Text.literal("§cBu komutu kullanmak için yetkiniz yok."));
                                return 0;
                            }

                            ServerPlayerEntity player = source.getPlayer();
                            if (player == null) {
                                source.sendError(Text.literal("Bu komut sadece oyuncular tarafından kullanılabilir."));
                                return 0;
                            }

                            BlockPos pos = player.getBlockPos();
                            World world = player.getWorld();

                            // Ayakta durduğu blok doluysa bir üste koy
                            if (!world.getBlockState(pos).isAir()) {
                                pos = pos.up();
                            }

                            world.setBlockState(pos, com.cogtrade.block.MarketBlock.INSTANCE.getDefaultState());

                            final BlockPos finalPos = pos;
                            source.sendFeedback(() ->
                                            Text.literal("§a✓ Market Bloğu yerleştirildi: §e"
                                                    + finalPos.getX() + ", " + finalPos.getY() + ", " + finalPos.getZ()),
                                    true);
                            return 1;
                        })
        );
    }
}