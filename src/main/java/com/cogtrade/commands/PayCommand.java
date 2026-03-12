package com.cogtrade.commands;

import com.cogtrade.economy.PlayerEconomy;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class PayCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("pay")
                        .then(CommandManager.argument("oyuncu", StringArgumentType.word())
                                .then(CommandManager.argument("miktar", DoubleArgumentType.doubleArg(0.01))
                                        .executes(context -> {
                                            ServerCommandSource source = context.getSource();
                                            ServerPlayerEntity sender = source.getPlayer();
                                            if (sender == null) {
                                                source.sendError(Text.literal("Bu komut sadece oyuncular tarafından kullanılabilir."));
                                                return 0;
                                            }

                                            String targetName = StringArgumentType.getString(context, "oyuncu");
                                            double amount = DoubleArgumentType.getDouble(context, "miktar");

                                            // Kendine ödeme yapılamaz
                                            if (sender.getName().getString().equalsIgnoreCase(targetName)) {
                                                source.sendError(Text.literal("Kendine para gönderemezsin."));
                                                return 0;
                                            }

                                            // Hedef oyuncuyu bul
                                            ServerPlayerEntity target = source.getServer()
                                                    .getPlayerManager()
                                                    .getPlayer(targetName);

                                            if (target == null) {
                                                source.sendError(Text.literal("'" + targetName + "' adlı oyuncu şu an çevrimiçi değil."));
                                                return 0;
                                            }

                                            // Transfer yap
                                            boolean success = PlayerEconomy.transfer(
                                                    sender.getUuid(),
                                                    target.getUuid(),
                                                    amount
                                            );

                                            if (!success) {
                                                source.sendError(Text.literal("Yetersiz bakiye! Bakiyen: ⬡ " +
                                                        String.format("%.0f", PlayerEconomy.getBalance(sender.getUuid()))));
                                                return 0;
                                            }

                                            // Gönderene bildir
                                            source.sendFeedback(() ->
                                                            Text.literal("⬡ " + String.format("%.0f", amount) +
                                                                    " coin " + targetName + " adlı oyuncuya gönderildi."),
                                                    false
                                            );

                                            // Alıcıya bildir
                                            target.sendMessage(
                                                    Text.literal("⬡ " + sender.getName().getString() +
                                                            " sana " + String.format("%.0f", amount) + " coin gönderdi!")
                                            );

                                            // HUD güncelle — her iki tarafa da yeni bakiyeyi gönder
                                            com.cogtrade.network.BalanceUpdatePacket.send(
                                                    sender,
                                                    PlayerEconomy.getBalance(sender.getUuid()),
                                                    PlayerEconomy.getDailySpent(sender.getUuid()) > 0 ? PlayerEconomy.getDailyEarned(sender.getUuid()) : 0,
                                                    PlayerEconomy.getDailySpent(sender.getUuid())
                                            );
                                            com.cogtrade.network.BalanceUpdatePacket.send(
                                                    target,
                                                    PlayerEconomy.getBalance(target.getUuid()),
                                                    PlayerEconomy.getDailyEarned(target.getUuid()),
                                                    PlayerEconomy.getDailySpent(target.getUuid())
                                            );

                                            return 1;
                                        })))
        );
    }
}