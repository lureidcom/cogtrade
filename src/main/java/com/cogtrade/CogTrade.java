package com.cogtrade;

import com.cogtrade.block.*;
import com.cogtrade.config.CogTradeConfig;
import com.cogtrade.database.DatabaseManager;
import com.cogtrade.economy.PlayerEconomy;
import com.cogtrade.market.PlayerShopManager;
import com.cogtrade.network.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CogTrade implements ModInitializer {

    public static final String MOD_ID = "cogtrade";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("CogTrade başlatılıyor...");

        CogTradeConfig.load();

        // ── MarketBlock ───────────────────────────────────────────────────
        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "market_block"), MarketBlock.INSTANCE);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "market_block"), MarketBlock.BLOCK_ITEM);
        MarketBlockEntity.TYPE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "market_block"),
                BlockEntityType.Builder.create(MarketBlockEntity::new, MarketBlock.INSTANCE).build(null)
        );

        // ── TradeDepotBlock ───────────────────────────────────────────────
        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "trade_depot"), TradeDepotBlock.INSTANCE);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "trade_depot"), TradeDepotBlock.BLOCK_ITEM);
        TradeDepotBlock.TYPE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "trade_depot"),
                BlockEntityType.Builder.create(TradeDepotBlockEntity::new, TradeDepotBlock.INSTANCE).build(null)
        );

        // ── TradePostBlock ────────────────────────────────────────────────
        Registry.register(Registries.BLOCK, new Identifier(MOD_ID, "trade_post"), TradePostBlock.INSTANCE);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "trade_post"), TradePostBlock.BLOCK_ITEM);
        TradePostBlock.TYPE = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier(MOD_ID, "trade_post"),
                BlockEntityType.Builder.create(TradePostBlockEntity::new, TradePostBlock.INSTANCE).build(null)
        );

        // ── Özel Creative Tab ─────────────────────────────────────────────
        RegistryKey<ItemGroup> groupKey =
                RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier(MOD_ID, "main"));
        Registry.register(Registries.ITEM_GROUP, groupKey.getValue(),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(TradeDepotBlock.BLOCK_ITEM))
                        .displayName(net.minecraft.text.Text.translatable("itemGroup.cogtrade.main"))
                        .entries((context, entries) -> {
                            entries.add(MarketBlock.BLOCK_ITEM);
                            entries.add(TradeDepotBlock.BLOCK_ITEM);
                            entries.add(TradePostBlock.BLOCK_ITEM);
                        })
                        .build());

        // ── Veritabanı ────────────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Integrated server: gameDir/saves/LevelName/  |  Dedicated server: gameDir/
            java.nio.file.Path gameDir = FabricLoader.getInstance().getGameDir();
            java.nio.file.Path savesDir = gameDir.resolve("saves");
            java.nio.file.Path dbPath;
            if (savesDir.toFile().exists()) {
                // Tek oyunculu / LAN
                dbPath = savesDir
                        .resolve(server.getSaveProperties().getLevelName())
                        .resolve("cogtrade");
            } else {
                // Dedicated sunucu
                dbPath = gameDir.resolve("cogtrade");
            }

            DatabaseManager.initialize(dbPath);

            if (DatabaseManager.getConnection() == null) {
                LOGGER.error("CogTrade: Veritabanı başlatılamadı — mod devre dışı kalıyor!");
                return;
            }

            com.cogtrade.market.MarketManager.seedDefaultItemsIfEmpty();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DatabaseManager.close());

        // ── Oyuncu katılımı ───────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;

            // ── Alpha uyarısı ─────────────────────────────────────────────
            server.execute(() -> {
                player.sendMessage(Text.literal(""));
                player.sendMessage(Text.literal(
                        "§6§l⚠ CogTrade — ALPHA SÜRÜMÜ §6§l⚠"));
                player.sendMessage(Text.literal(
                        "§eBu mod erken geliştirme aşamasındadır."));
                player.sendMessage(Text.literal(
                        "§e• Hatalar ve beklenmedik davranışlar yaşanabilir."));
                player.sendMessage(Text.literal(
                        "§e• Veriler silinebilir veya bozulabilir."));
                player.sendMessage(Text.literal(
                        "§e• Önemli dünyalarda kullanmadan önce §cyedek alın§e!"));
                player.sendMessage(Text.literal(
                        "§8Sorunları bildirmek için: modrinth.com/mod/cogtrade"));
                player.sendMessage(Text.literal(""));
            });

            if (DatabaseManager.getConnection() == null) {
                LOGGER.warn("JOIN: Veritabanı henüz hazır değil.");
                return;
            }
            PlayerEconomy.initPlayer(player.getUuid(), player.getName().getString());
            double balance = PlayerEconomy.getBalance(player.getUuid());
            BalanceUpdatePacket.send(player, balance,
                    PlayerEconomy.getDailyEarned(player.getUuid()),
                    PlayerEconomy.getDailySpent(player.getUuid()));
        });

        // ── Sadece sahibi kırabilir: TradeDepot ve TradePost ─────────────
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (state.getBlock() == TradeDepotBlock.INSTANCE) {
                if (blockEntity instanceof TradeDepotBlockEntity depot) {
                    String ownerUuid = depot.getOwnerUuid();
                    if (ownerUuid != null && !ownerUuid.equals(player.getUuid().toString())) {
                        player.sendMessage(Text.literal("§cBu Trade Depot sana ait değil!"));
                        return false;
                    }
                }
            }
            if (state.getBlock() == TradePostBlock.INSTANCE) {
                if (blockEntity instanceof TradePostBlockEntity post) {
                    String ownerUuid = post.getOwnerUuid();
                    if (ownerUuid != null && !ownerUuid.isBlank()
                            && !ownerUuid.equals(player.getUuid().toString())) {
                        player.sendMessage(Text.literal("§cBu Trade Post sana ait değil!"));
                        return false;
                    }
                }
            }
            return true;
        });

        // ── Sandık seçimi (Trade Depot item elinde tutarken) ──────────────
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            ItemStack heldItem = player.getStackInHand(hand);
            if (!heldItem.isOf(TradeDepotBlock.BLOCK_ITEM)) return ActionResult.PASS;

            net.minecraft.util.math.BlockPos pos = hitResult.getBlockPos();
            if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity)) return ActionResult.PASS;

            // Elinde Trade Depot item'ı varken sandığa tıkladı → seç/kaldır
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            String uuid = sp.getUuid().toString();
            String worldId = world.getRegistryKey().getValue().toString();

            // Geçici sandık listesini sunucu-taraflı Map'te tut
            List<PlayerShopManager.ChestPos> current = pendingChests.computeIfAbsent(uuid, k -> new ArrayList<>());

            // Çift sandık tespiti: tıklanan sandığın diğer yarısını bul
            net.minecraft.block.BlockState chestState = world.getBlockState(pos);
            List<net.minecraft.util.math.BlockPos> toToggle = new ArrayList<>();
            toToggle.add(pos);
            if (chestState.getBlock() instanceof net.minecraft.block.ChestBlock) {
                net.minecraft.block.enums.ChestType chestType =
                        chestState.get(net.minecraft.block.ChestBlock.CHEST_TYPE);
                if (chestType != net.minecraft.block.enums.ChestType.SINGLE) {
                    // getDirectionToAttached private olduğu için manuel hesaplıyoruz:
                    // LEFT  → facing.rotateYClockwise()
                    // RIGHT → facing.rotateYCounterclockwise()
                    net.minecraft.util.math.Direction facing =
                            chestState.get(net.minecraft.block.HorizontalFacingBlock.FACING);
                    net.minecraft.util.math.Direction dir =
                            (chestType == net.minecraft.block.enums.ChestType.LEFT)
                                    ? facing.rotateYClockwise()
                                    : facing.rotateYCounterclockwise();
                    net.minecraft.util.math.BlockPos otherPos = pos.offset(dir);
                    if (world.getBlockEntity(otherPos) instanceof ChestBlockEntity) {
                        toToggle.add(otherPos);
                    }
                }
            }

            // Tüm yarıları aynı anda ekle / kaldır
            boolean removed = current.removeIf(c ->
                    toToggle.stream().anyMatch(p -> c.x() == p.getX() && c.y() == p.getY() && c.z() == p.getZ()));
            if (!removed) {
                for (net.minecraft.util.math.BlockPos p : toToggle) {
                    current.add(PlayerShopManager.ChestPos.of(worldId, p));
                }
            }

            boolean added = !removed;
            String doubleNote = toToggle.size() > 1 ? " §8(çift sandık)" : "";
            ChestSelectedPacket.sendAll(sp, current, worldId);
            sp.sendMessage(Text.literal(added
                    ? "§a✓ Sandık eklendi" + doubleNote + " §7(" + current.size() + " sandık seçili). Trade Depot'u yerleştir."
                    : "§e✗ Sandık kaldırıldı" + doubleNote + " §7(" + current.size() + " sandık seçili)."));

            return ActionResult.SUCCESS; // sandık GUI'si açılmasın
        });

        // ── Komutlar ──────────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            com.cogtrade.commands.BalanceCommand.register(dispatcher);
            com.cogtrade.commands.PayCommand.register(dispatcher);
            com.cogtrade.commands.AdminCommand.register(dispatcher, registryAccess);
            com.cogtrade.commands.MarketCommand.register(dispatcher);
            com.cogtrade.commands.PlaceMarketCommand.register(dispatcher);
        });

        // ── Sunucu Market: satın alma ─────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.BuyItemPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    int itemId = buf.readInt();
                    int amount = buf.readInt();
                    server.execute(() -> {
                        com.cogtrade.market.MarketItem item = com.cogtrade.market.MarketManager.getItemById(itemId);
                        if (item == null || !item.isEnabled()) return;
                        if (item.getStock() < amount) { player.sendMessage(Text.literal("Yetersiz stok!")); return; }
                        double total = item.getPrice() * amount;
                        double balance = PlayerEconomy.getBalance(player.getUuid());
                        if (balance < total) { player.sendMessage(Text.literal("Yetersiz bakiye!")); return; }
                        PlayerEconomy.setBalance(player.getUuid(), balance - total);
                        PlayerEconomy.recordDailySpent(player.getUuid(), total);
                        com.cogtrade.market.MarketManager.updateStock(itemId, item.getStock() - amount);
                        com.cogtrade.market.MarketManager.recordTransaction(player.getUuid().toString(), item.getItemId(), amount, item.getPrice(), "BUY");
                        ItemStack stack = getItemStack(item.getItemId(), amount);
                        if (stack != null) player.getInventory().offerOrDrop(stack);
                        double newBalance = PlayerEconomy.getBalance(player.getUuid());
                        BalanceUpdatePacket.send(player, newBalance, PlayerEconomy.getDailyEarned(player.getUuid()), PlayerEconomy.getDailySpent(player.getUuid()));
                        BuySoundPacket.send(player);
                        player.sendMessage(Text.literal("⬡ " + item.getDisplayName() + " x" + amount + " satın aldın. (" + String.format("%.0f", total) + " coin)"));
                    });
                });

        // ── Sunucu Market: satış ─────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.SellItemPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    int marketItemId = buf.readInt();
                    int amount = buf.readInt();
                    server.execute(() -> {
                        com.cogtrade.market.MarketItem item = com.cogtrade.market.MarketManager.getItemById(marketItemId);
                        if (item == null || !item.isEnabled()) { player.sendMessage(Text.literal("§cItem bulunamadı!")); return; }
                        net.minecraft.util.Identifier mcId;
                        try { mcId = new net.minecraft.util.Identifier(item.getItemId()); } catch (Exception e) { player.sendMessage(Text.literal("§cGeçersiz item!")); return; }
                        net.minecraft.item.Item mcItem = Registries.ITEM.get(mcId);
                        if (mcItem == net.minecraft.item.Items.AIR) { player.sendMessage(Text.literal("§cGeçersiz item!")); return; }
                        int inInventory = 0;
                        for (int i = 0; i < player.getInventory().size(); i++) { net.minecraft.item.ItemStack s = player.getInventory().getStack(i); if (s.isOf(mcItem)) inInventory += s.getCount(); }
                        if (inInventory < amount || amount <= 0) { player.sendMessage(Text.literal("§cEnvanterinde yeterli item yok! (Envanter: " + inInventory + ")")); return; }
                        int canSell = item.getMaxStock() - item.getStock();
                        if (canSell <= 0) { player.sendMessage(Text.literal("§cMarket bu ürün için dolu!")); return; }
                        int actualAmount = Math.min(amount, canSell);
                        int toRemove = actualAmount;
                        for (int i = 0; i < player.getInventory().size() && toRemove > 0; i++) { net.minecraft.item.ItemStack s = player.getInventory().getStack(i); if (s.isOf(mcItem)) { int remove = Math.min(s.getCount(), toRemove); s.decrement(remove); toRemove -= remove; } }
                        player.getInventory().markDirty();
                        double sellPrice = item.getPrice() * CogTradeConfig.get().sellPriceMultiplier;
                        double totalEarned = sellPrice * actualAmount;
                        double balance = PlayerEconomy.getBalance(player.getUuid());
                        PlayerEconomy.setBalance(player.getUuid(), balance + totalEarned);
                        PlayerEconomy.recordDailyEarned(player.getUuid(), totalEarned);
                        com.cogtrade.market.MarketManager.updateStock(marketItemId, item.getStock() + actualAmount);
                        com.cogtrade.market.MarketManager.recordTransaction(player.getUuid().toString(), item.getItemId(), actualAmount, sellPrice, "SELL");
                        double newBalance = PlayerEconomy.getBalance(player.getUuid());
                        BalanceUpdatePacket.send(player, newBalance, PlayerEconomy.getDailyEarned(player.getUuid()), PlayerEconomy.getDailySpent(player.getUuid()));
                        player.sendMessage(Text.literal("§a⬡ " + item.getDisplayName() + " x" + actualAmount + " sattın. (+" + String.format("%.0f", totalEarned) + " coin)"));
                    });
                });

        // ── Depot: listing ekle/kaldır ────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(DepotActionPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    int action    = buf.readInt();
                    String itemId = buf.readString();
                    double price  = buf.readDouble();
                    String category = buf.readString();
                    server.execute(() -> {
                        String uuid = player.getUuid().toString();
                        if (action == DepotActionPacket.ACTION_ADD) {
                            net.minecraft.item.Item mcItem = Registries.ITEM.get(new Identifier(itemId));
                            String displayName = mcItem.getDefaultStack().getName().getString();
                            PlayerShopManager.addListing(uuid, itemId, displayName, category, price);
                            // Güncel depot'u gönder
                            OpenDepotPacket.send(player, uuid);
                        } else {
                            PlayerShopManager.removeListing(uuid, itemId);
                            OpenDepotPacket.send(player, uuid);
                        }
                    });
                });

        // ── Player Shop: satın alma ───────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(PlayerShopBuyPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    String ownerUuid = buf.readString();
                    String itemId    = buf.readString();
                    int    amount    = buf.readInt();
                    server.execute(() -> {
                        List<PlayerShopManager.ShopListing> listings = PlayerShopManager.getListings(ownerUuid);
                        PlayerShopManager.ShopListing listing = listings.stream()
                                .filter(l -> l.itemId().equals(itemId))
                                .findFirst().orElse(null);
                        if (listing == null) { player.sendMessage(Text.literal("§cÜrün bulunamadı.")); return; }

                        double total = listing.price() * amount;
                        double buyerBalance = PlayerEconomy.getBalance(player.getUuid());
                        if (buyerBalance < total) { player.sendMessage(Text.literal("§cYetersiz bakiye!")); return; }

                        // Sandıktan çek — satıcının depot worldünü kullan (buyer farklı boyutta olabilir)
                        List<PlayerShopManager.ChestPos> sellerChests = PlayerShopManager.getChests(ownerUuid);
                        net.minecraft.server.world.ServerWorld depotWorld = player.getServerWorld();
                        if (!sellerChests.isEmpty()) {
                            String worldKey = sellerChests.get(0).world();
                            for (net.minecraft.server.world.ServerWorld w : server.getWorlds()) {
                                if (w.getRegistryKey().getValue().toString().equals(worldKey)) {
                                    depotWorld = w;
                                    break;
                                }
                            }
                        }
                        boolean ok = PlayerShopManager.withdrawFromChests(ownerUuid, depotWorld, itemId, amount);
                        if (!ok) { player.sendMessage(Text.literal("§cSatıcının stoğu yetersiz!")); return; }

                        // Alıcıdan para al
                        PlayerEconomy.setBalance(player.getUuid(), buyerBalance - total);
                        PlayerEconomy.recordDailySpent(player.getUuid(), total);

                        // Satıcıya para ver
                        double sellerBalance = PlayerEconomy.getBalance(java.util.UUID.fromString(ownerUuid));
                        PlayerEconomy.setBalance(java.util.UUID.fromString(ownerUuid), sellerBalance + total);
                        PlayerEconomy.recordDailyEarned(java.util.UUID.fromString(ownerUuid), total);

                        // Alıcıya item ver
                        ItemStack stack = getItemStack(itemId, amount);
                        if (stack != null) player.getInventory().offerOrDrop(stack);

                        // HUD güncelle
                        double newBuyerBal = PlayerEconomy.getBalance(player.getUuid());
                        BalanceUpdatePacket.send(player, newBuyerBal,
                                PlayerEconomy.getDailyEarned(player.getUuid()),
                                PlayerEconomy.getDailySpent(player.getUuid()));

                        // Satıcı online ise HUD'unu güncelle
                        ServerPlayerEntity seller = server.getPlayerManager()
                                .getPlayer(java.util.UUID.fromString(ownerUuid));
                        if (seller != null) {
                            double newSellerBal = PlayerEconomy.getBalance(java.util.UUID.fromString(ownerUuid));
                            BalanceUpdatePacket.send(seller, newSellerBal,
                                    PlayerEconomy.getDailyEarned(java.util.UUID.fromString(ownerUuid)),
                                    PlayerEconomy.getDailySpent(java.util.UUID.fromString(ownerUuid)));
                            seller.sendMessage(Text.literal("§a⬡ " + player.getName().getString()
                                    + " pazarından " + listing.displayName() + " x" + amount
                                    + " satın aldı. (+" + String.format("%.0f", total) + " coin)"));
                        }

                        BuySoundPacket.send(player);
                        player.sendMessage(Text.literal("§a⬡ " + listing.displayName()
                                + " x" + amount + " satın aldın. (" + String.format("%.0f", total) + " coin)"));
                    });
                });

        // ── Locate paketi (tüm yüklü chunklar) ───────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.LocateRequestPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        net.minecraft.server.world.ServerWorld serverWorld = player.getServerWorld();
                        net.minecraft.util.math.BlockPos playerPos = player.getBlockPos();
                        int playerCX = playerPos.getX() >> 4;
                        int playerCZ = playerPos.getZ() >> 4;
                        int chunkRadius = 32;

                        net.minecraft.util.math.BlockPos nearest = null;
                        double nearestDistSq = Double.MAX_VALUE;

                        for (int cx = playerCX - chunkRadius; cx <= playerCX + chunkRadius; cx++) {
                            for (int cz = playerCZ - chunkRadius; cz <= playerCZ + chunkRadius; cz++) {
                                net.minecraft.world.chunk.WorldChunk chunk =
                                        serverWorld.getChunkManager().getWorldChunk(cx, cz);
                                if (chunk == null) continue;
                                for (Map.Entry<net.minecraft.util.math.BlockPos,
                                        net.minecraft.block.entity.BlockEntity> entry :
                                        chunk.getBlockEntities().entrySet()) {
                                    if (entry.getValue() instanceof MarketBlockEntity) {
                                        net.minecraft.util.math.BlockPos pos = entry.getKey();
                                        double dist = playerPos.getSquaredDistance(pos);
                                        if (dist < nearestDistSq) {
                                            nearestDistSq = dist;
                                            nearest = pos.toImmutable();
                                        }
                                    }
                                }
                            }
                        }

                        com.cogtrade.network.LocateMarketPacket.send(player, nearest);
                        if (nearest == null) {
                            player.sendMessage(Text.literal("§cYüklü chunklar içinde Market Bloğu bulunamadı."));
                        } else {
                            final net.minecraft.util.math.BlockPos found = nearest;
                            player.sendMessage(Text.literal("§a⬡ Market Bloğu bulundu: §e"
                                    + found.getX() + ", " + found.getY() + ", " + found.getZ()
                                    + " §7(60 sn outline)"));
                        }
                    });
                });

        // ── Depot yenile ──────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.DepotRefreshPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> OpenDepotPacket.send(player, player.getUuid().toString()));
                });

        // ── Tüm oyuncu mağaza ilanlarını gönder ───────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.RequestAllShopListingsPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() -> {
                        List<AllShopListingsPacket.Entry> entries =
                                PlayerShopManager.getAllListingsWithInfo(server);
                        AllShopListingsPacket.send(player, entries);
                    });
                });

        // ── Trade Post konumunu bul ve gönder ─────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.RequestLocateTradePostPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    String ownerUuid = buf.readString();
                    server.execute(() -> {
                        net.minecraft.util.math.BlockPos pos = PlayerShopManager.getPostPos(ownerUuid);
                        com.cogtrade.network.LocateTradePostPacket.send(player, pos);
                        if (pos == null) {
                            player.sendMessage(Text.literal("§cBu satıcının Trade Post'u bulunamadı."));
                        } else {
                            player.sendMessage(Text.literal("§b⬡ Trade Post bulundu: §e"
                                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                                    + " §7(60 sn outline)"));
                        }
                    });
                });

        // ── Trade Post: ilan ekle / kaldır (sahip) ────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.PostActionPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    int    action   = buf.readInt();
                    String itemId   = buf.readString();
                    double price    = buf.readDouble();
                    String category = buf.readString();
                    server.execute(() -> {
                        String uuid = player.getUuid().toString();
                        if (action == com.cogtrade.network.PostActionPacket.ACTION_ADD) {
                            net.minecraft.item.Item mcItem =
                                    Registries.ITEM.get(new Identifier(itemId));
                            String displayName = mcItem.getDefaultStack().getName().getString();
                            PlayerShopManager.addListing(uuid, itemId, displayName, category, price);
                        } else {
                            PlayerShopManager.removeListing(uuid, itemId);
                        }
                        // Güncel Trade Post yönetim verisini gönder
                        com.cogtrade.network.OpenTradePostManagePacket.send(player, uuid);
                    });
                });

        // ── Trade Post yönetim ekranını yenile (sahip) ────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.PostRefreshPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    server.execute(() ->
                            com.cogtrade.network.OpenTradePostManagePacket.send(
                                    player, player.getUuid().toString()));
                });

        // ── PlayerShop ekranını yenile (alıcı) ───────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.PlayerShopRefreshPacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    String ownerUuid = buf.readString();
                    server.execute(() -> {
                        List<PlayerShopManager.ShopListing> listings =
                                PlayerShopManager.getListings(ownerUuid);
                        if (listings.isEmpty()) return;

                        java.util.Map<String, Integer> available =
                                PlayerShopManager.scanChestContents(ownerUuid, player.getServerWorld());
                        final java.util.Map<String, Integer> avail = available;
                        listings = listings.stream()
                                .filter(l -> avail.getOrDefault(l.itemId(), 0) > 0)
                                .toList();

                        String ownerName = PlayerShopManager.getOwnerName(ownerUuid);
                        OpenPlayerShopPacket.send(player, ownerUuid, ownerName, listings,
                                available, com.cogtrade.config.CogTradeConfig.get().sellPriceMultiplier);
                    });
                });

        LOGGER.info("CogTrade hazır!");
    }

    // Oyuncu başına bekleyen sandık listesi (henüz depot yerleştirilmedi)
    public static final java.util.concurrent.ConcurrentHashMap<String, List<PlayerShopManager.ChestPos>>
            pendingChests = new java.util.concurrent.ConcurrentHashMap<>();

    private static ItemStack getItemStack(String itemId, int amount) {
        try {
            Identifier id = new Identifier(itemId);
            net.minecraft.item.Item item = Registries.ITEM.get(id);
            if (item == net.minecraft.item.Items.AIR) return null;
            return new ItemStack(item, amount);
        } catch (Exception e) { return null; }
    }
}