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

                        // /pay <oyuncu> <miktar>  (not yok)
                        .executes(ctx -> execute(ctx.getSource(),
                                StringArgumentType.getString(ctx, "oyuncu"),
                                DoubleArgumentType.getDouble(ctx, "miktar"),
                                null))

                        // /pay <oyuncu> <miktar> <not...>  (opsiyonel not)
                        .then(CommandManager.argument("not", StringArgumentType.greedyString())
                            .executes(ctx -> execute(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "oyuncu"),
                                    DoubleArgumentType.getDouble(ctx, "miktar"),
                                    StringArgumentType.getString(ctx, "not"))))
                    )
                )
        );
    }

    private static int execute(ServerCommandSource source,
                                String targetName,
                                double amount,
                                String note) {

        ServerPlayerEntity sender = source.getPlayer();
        if (sender == null) {
            source.sendError(Text.literal("Bu komut sadece oyuncular tarafından kullanılabilir."));
            return 0;
        }

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

        // Transfer yap (note veritabanına kaydedilir)
        boolean success = PlayerEconomy.transfer(
                sender.getUuid(),
                target.getUuid(),
                amount,
                note
        );

        if (!success) {
            source.sendError(Text.literal("Yetersiz bakiye! Bakiyen: ⬡ " +
                    String.format("%.0f", PlayerEconomy.getBalance(sender.getUuid()))));
            return 0;
        }

        // Gönderene bildir
        String amtStr = String.format("%.0f", amount);
        source.sendFeedback(() ->
                Text.literal("⬡ " + amtStr + " coin " + targetName + " adlı oyuncuya gönderildi."),
                false
        );

        // Alıcıya bildir (not varsa göster)
        String receiverMsg = "⬡ " + sender.getName().getString()
                + " sana " + amtStr + " coin gönderdi!";
        if (note != null && !note.isBlank()) {
            receiverMsg += "  §7[Not: " + note + "]";
        }
        target.sendMessage(Text.literal(receiverMsg));

        // HUD güncelle — her iki tarafa da yeni bakiyeyi gönder
        com.cogtrade.network.BalanceUpdatePacket.send(
                sender,
                PlayerEconomy.getBalance(sender.getUuid()),
                PlayerEconomy.getDailyEarned(sender.getUuid()),
                PlayerEconomy.getDailySpent(sender.getUuid())
        );
        com.cogtrade.network.BalanceUpdatePacket.send(
                target,
                PlayerEconomy.getBalance(target.getUuid()),
                PlayerEconomy.getDailyEarned(target.getUuid()),
                PlayerEconomy.getDailySpent(target.getUuid())
        );

        return 1;
    }
}
