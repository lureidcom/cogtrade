package com.cogtrade.commands;

import com.cogtrade.config.CogTradeConfig;
import com.cogtrade.economy.PlayerEconomy;
import com.cogtrade.network.BalanceUpdatePacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class AdminCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess) {
        dispatcher.register(
            CommandManager.literal("ctadmin")
                .requires(source -> source.hasPermissionLevel(2))

                // ── market ──────────────────────────────────────────────────
                .then(CommandManager.literal("market")

                    // /ctadmin market add <item_id> <category> <price> <stock>
                    .then(CommandManager.literal("add")
                        .then(CommandManager.argument("item_id", ItemStackArgumentType.itemStack(registryAccess))
                            .then(CommandManager.argument("category", StringArgumentType.word())
                                .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(0.01))
                                    .then(CommandManager.argument("stock", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            net.minecraft.item.Item mcItem =
                                                    ItemStackArgumentType.getItemStackArgument(ctx, "item_id").getItem();
                                            if (mcItem == net.minecraft.item.Items.AIR) {
                                                ctx.getSource().sendError(Text.literal("Geçersiz item (air)!"));
                                                return 0;
                                            }
                                            String itemId = mcItem.toString();
                                            String name   = mcItem.getDefaultStack().getName().getString();
                                            if (name.isBlank()) {
                                                ctx.getSource().sendError(Text.literal("Item adı alınamadı: " + itemId));
                                                return 0;
                                            }
                                            String cat   = StringArgumentType.getString(ctx, "category");
                                            double price = DoubleArgumentType.getDouble(ctx, "price");
                                            int    stock = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "stock");
                                            boolean ok   = com.cogtrade.market.MarketManager.addItem(
                                                    itemId, name, cat, price, stock, 9999);
                                            if (ok)
                                                ctx.getSource().sendFeedback(() -> Text.literal("✓ " + name + " (" + itemId + ") pazara eklendi."), false);
                                            else
                                                ctx.getSource().sendError(Text.literal("Ürün eklenemedi."));
                                            return ok ? 1 : 0;
                                        })
                                    )
                                )
                            )
                        )
                    )

                    .then(CommandManager.literal("price")
                        .then(CommandManager.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .then(CommandManager.argument("fiyat", DoubleArgumentType.doubleArg(0.01))
                                .executes(ctx -> {
                                    int id = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id");
                                    double price = DoubleArgumentType.getDouble(ctx, "fiyat");
                                    boolean ok = com.cogtrade.market.MarketManager.updatePrice(id, price);
                                    if (ok) {
                                        ctx.getSource().sendFeedback(
                                                () -> Text.literal("✓ ID " + id + " fiyat → " + String.format("%.2f", price)),
                                                false);
                                        return 1;
                                    }
                                    ctx.getSource().sendError(Text.literal("Fiyat güncellenemedi."));
                                    return 0;
                                })
                            )
                        )
                    )

                    // /ctadmin market stock <id> <miktar>
                    .then(CommandManager.literal("stock")
                        .then(CommandManager.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .then(CommandManager.argument("miktar", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int id    = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id");
                                    int stock = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "miktar");
                                    boolean ok = com.cogtrade.market.MarketManager.updateStock(id, stock);
                                    if (ok) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("✓ ID " + id + " stok → " + stock), false);
                                        return 1;
                                    }
                                    ctx.getSource().sendError(Text.literal("Stok güncellenemedi."));
                                    return 0;
                                })
                            )
                        )
                    )

                    .then(CommandManager.literal("edit")
                        .then(CommandManager.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .then(CommandManager.argument("fiyat", DoubleArgumentType.doubleArg(0.01))
                                .then(CommandManager.argument("stok", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                    .executes(ctx -> {
                                        int id = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id");
                                        double price = DoubleArgumentType.getDouble(ctx, "fiyat");
                                        int stock = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "stok");
                                        boolean ok = com.cogtrade.market.MarketManager.updateItem(id, price, stock);
                                        if (ok) {
                                            ctx.getSource().sendFeedback(
                                                    () -> Text.literal("✓ ID " + id
                                                            + " güncellendi → fiyat: "
                                                            + String.format("%.2f", price)
                                                            + ", stok: " + stock),
                                                    false);
                                            return 1;
                                        }
                                        ctx.getSource().sendError(Text.literal("Ürün güncellenemedi."));
                                        return 0;
                                    })
                                )
                            )
                        )
                    )

                    // /ctadmin market remove <id>
                    .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                int id = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id");
                                boolean ok = com.cogtrade.market.MarketManager.removeItem(id);
                                if (ok) {
                                    ctx.getSource().sendFeedback(() -> Text.literal("✓ ID " + id + " pazardan kaldırıldı."), false);
                                    return 1;
                                }
                                ctx.getSource().sendError(Text.literal("Ürün kaldırılamadı."));
                                return 0;
                            })
                        )
                    )
                )

                // ── balance ─────────────────────────────────────────────────
                .then(CommandManager.literal("balance")

                    // /ctadmin balance add <oyuncu> <miktar>
                    .then(CommandManager.literal("add")
                        .then(CommandManager.argument("oyuncu", EntityArgumentType.player())
                            .then(CommandManager.argument("miktar", DoubleArgumentType.doubleArg(0.01))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "oyuncu");
                                    double amount  = DoubleArgumentType.getDouble(ctx, "miktar");
                                    double current = PlayerEconomy.getBalance(player.getUuid());
                                    PlayerEconomy.setBalance(player.getUuid(), current + amount);
                                    PlayerEconomy.recordDailyEarned(player.getUuid(), amount);
                                    double newBal = PlayerEconomy.getBalance(player.getUuid());
                                    BalanceUpdatePacket.send(player, newBal,
                                            PlayerEconomy.getDailyEarned(player.getUuid()),
                                            PlayerEconomy.getDailySpent(player.getUuid()));
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("⬡ +" + String.format("%.0f", amount)
                                                    + " → " + player.getName().getString()
                                                    + " (bakiye: " + String.format("%.0f", newBal) + ")"), false);
                                    return 1;
                                })
                            )
                        )
                    )

                    // /ctadmin balance remove <oyuncu> <miktar>
                    .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("oyuncu", EntityArgumentType.player())
                            .then(CommandManager.argument("miktar", DoubleArgumentType.doubleArg(0.01))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "oyuncu");
                                    double amount  = DoubleArgumentType.getDouble(ctx, "miktar");
                                    double current = PlayerEconomy.getBalance(player.getUuid());
                                    if (current < amount) {
                                        ctx.getSource().sendError(Text.literal(
                                                player.getName().getString() + " bakiyesi yetersiz!"));
                                        return 0;
                                    }
                                    PlayerEconomy.setBalance(player.getUuid(), current - amount);
                                    PlayerEconomy.recordDailySpent(player.getUuid(), amount);
                                    double newBal = PlayerEconomy.getBalance(player.getUuid());
                                    BalanceUpdatePacket.send(player, newBal,
                                            PlayerEconomy.getDailyEarned(player.getUuid()),
                                            PlayerEconomy.getDailySpent(player.getUuid()));
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("⬡ -" + String.format("%.0f", amount)
                                                    + " → " + player.getName().getString()
                                                    + " (bakiye: " + String.format("%.0f", newBal) + ")"), false);
                                    return 1;
                                })
                            )
                        )
                    )

                    // /ctadmin balance set <oyuncu> <miktar>
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("oyuncu", EntityArgumentType.player())
                            .then(CommandManager.argument("miktar", DoubleArgumentType.doubleArg(0))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "oyuncu");
                                    double amount = DoubleArgumentType.getDouble(ctx, "miktar");
                                    PlayerEconomy.setBalance(player.getUuid(), amount);
                                    BalanceUpdatePacket.send(player, amount,
                                            PlayerEconomy.getDailyEarned(player.getUuid()),
                                            PlayerEconomy.getDailySpent(player.getUuid()));
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("⬡ " + player.getName().getString()
                                                    + " bakiyesi " + String.format("%.0f", amount) + " yapıldı."), false);
                                    return 1;
                                })
                            )
                        )
                    )

                    // /ctadmin balance check <oyuncu>
                    .then(CommandManager.literal("check")
                        .then(CommandManager.argument("oyuncu", EntityArgumentType.player())
                            .executes(ctx -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "oyuncu");
                                double balance = PlayerEconomy.getBalance(target.getUuid());
                                ctx.getSource().sendFeedback(() ->
                                        Text.literal(target.getName().getString()
                                                + " bakiyesi: ⬡ " + String.format("%.0f", balance)), false);
                                return 1;
                            })
                        )
                    )
                )

                // ── config ──────────────────────────────────────────────────
                .then(CommandManager.literal("config")
                    // /ctadmin config  (mevcut değerleri göster)
                    .executes(ctx -> {
                        CogTradeConfig cfg = CogTradeConfig.get();
                        ctx.getSource().sendFeedback(() ->
                                Text.literal("§eCogTrade Config:\n§7startingBalance §f= §a" + cfg.startingBalance),
                                false);
                        return 1;
                    })
                    // /ctadmin config set starting_balance <deger>
                    .then(CommandManager.literal("set")
                        .then(CommandManager.literal("starting_balance")
                            .then(CommandManager.argument("deger", DoubleArgumentType.doubleArg(0))
                                .executes(ctx -> {
                                    double val = DoubleArgumentType.getDouble(ctx, "deger");
                                    CogTradeConfig.get().startingBalance = val;
                                    CogTradeConfig.save();
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("§aStartingBalance → " + val + " §7(config dosyasına kaydedildi)"),
                                            false);
                                    return 1;
                                })
                            )
                        )
                    )
                )
        );
    }
}
