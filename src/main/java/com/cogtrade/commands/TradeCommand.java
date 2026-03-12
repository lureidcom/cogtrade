package com.cogtrade.commands;

import com.cogtrade.trade.DirectTradeManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /trade <oyuncu>   — takas isteği gönder
 * /trade accept     — bekleyen isteği kabul et
 * /trade reject     — bekleyen isteği reddet
 * /trade cancel     — aktif takas seansını iptal et
 */
public class TradeCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(
                CommandManager.literal("trade")

                        // /trade accept
                        .then(CommandManager.literal("accept")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) { ctx.getSource().sendError(Text.literal("Sadece oyuncular.")); return 0; }

                                    String result = DirectTradeManager.acceptRequest(
                                            ctx.getSource().getServer(), player);
                                    switch (result) {
                                        case "no_request" ->
                                            player.sendMessage(Text.literal("§7Kabul edilecek bekleyen bir takas isteğin yok."), false);
                                        case "already_in_session" ->
                                            player.sendMessage(Text.literal("§cZaten aktif bir takas seansındasın."), false);
                                        case "initiator_offline" -> { /* message sent by manager */ }
                                        case "initiator_in_session" -> { /* message sent by manager */ }
                                        // "ok" → GUI opened
                                    }
                                    return result.equals("ok") ? 1 : 0;
                                }))

                        // /trade reject
                        .then(CommandManager.literal("reject")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) { ctx.getSource().sendError(Text.literal("Sadece oyuncular.")); return 0; }

                                    DirectTradeManager.rejectRequest(ctx.getSource().getServer(), player);
                                    return 1;
                                }))

                        // /trade cancel
                        .then(CommandManager.literal("cancel")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) { ctx.getSource().sendError(Text.literal("Sadece oyuncular.")); return 0; }

                                    com.cogtrade.trade.DirectTradeSession session =
                                            DirectTradeManager.getSessionForPlayer(player.getUuid());
                                    if (session == null) {
                                        player.sendMessage(Text.literal("§7Aktif bir takas seansın yok."), false);
                                        return 0;
                                    }
                                    DirectTradeManager.handleCancel(ctx.getSource().getServer(), player, session.getSessionId());
                                    return 1;
                                }))

                        // /trade <oyuncu>
                        .then(CommandManager.argument("oyuncu", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    ServerPlayerEntity sender = source.getPlayer();
                                    if (sender == null) { source.sendError(Text.literal("Sadece oyuncular.")); return 0; }

                                    String targetName = StringArgumentType.getString(ctx, "oyuncu");

                                    if (sender.getName().getString().equalsIgnoreCase(targetName)) {
                                        sender.sendMessage(Text.literal("§cKendine takas isteği gönderemezsin."), false);
                                        return 0;
                                    }

                                    ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
                                    if (target == null) {
                                        sender.sendMessage(Text.literal("§c'" + targetName + "' şu an çevrimiçi değil."), false);
                                        return 0;
                                    }

                                    String result = DirectTradeManager.sendRequest(source.getServer(), sender, target);
                                    switch (result) {
                                        case "self" ->
                                            sender.sendMessage(Text.literal("§cKendine takas isteği gönderemezsin."), false);
                                        case "already_in_session" ->
                                            sender.sendMessage(Text.literal("§cZaten aktif bir takas seansındasın."), false);
                                        case "target_in_session" ->
                                            sender.sendMessage(Text.literal("§c" + targetName + " zaten başka bir takasta."), false);
                                        case "request_pending" ->
                                            sender.sendMessage(Text.literal("§cZaten bekleyen bir isteğin var. Önce iptal etmek için §e/trade cancel§c kullan."), false);
                                        case "target_busy" ->
                                            sender.sendMessage(Text.literal("§c" + targetName + " şu an başka bir takas isteğini yanıtlıyor."), false);
                                        case "sent" ->
                                            sender.sendMessage(Text.literal("§e[CogTrade] §f" + targetName + "§7'ya takas isteği gönderildi. Yanıtı bekleniyor..."), false);
                                    }
                                    return result.equals("sent") ? 1 : 0;
                                }))
        );
    }
}
