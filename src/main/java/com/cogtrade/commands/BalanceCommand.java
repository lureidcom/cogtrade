package com.cogtrade.commands;

import com.cogtrade.economy.PlayerEconomy;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class BalanceCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("balance")
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();
                            ServerPlayerEntity player = source.getPlayer();
                            if (player == null) {
                                source.sendError(Text.literal("Bu komut sadece oyuncular tarafından kullanılabilir."));
                                return 0;
                            }
                            double balance = PlayerEconomy.getBalance(player.getUuid());
                            source.sendFeedback(() ->
                                            Text.literal("⬡ Bakiyen: " + String.format("%.0f", balance)),
                                    false
                            );
                            return 1;
                        })
        );
    }
}