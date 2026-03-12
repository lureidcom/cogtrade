
package com.cogtrade.client;

import com.cogtrade.CogTrade;
import com.cogtrade.block.MarketBlockEntity;
import com.cogtrade.network.BalanceUpdatePacket;
import com.cogtrade.network.BuySoundPacket;
import com.cogtrade.network.LocateMarketPacket;
import com.cogtrade.network.OpenMarketPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CogTradeClient implements ClientModInitializer {

    private static KeyBinding marketKey;

    @Override
    public void onInitializeClient() {
        CogTrade.LOGGER.info("CogTrade client başlatılıyor...");

        // ── Keybinding: O tuşu → /market komutu ──────────────────────────
        marketKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cogtrade.market",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.cogtrade"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (marketKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.getNetworkHandler().sendChatCommand("market");
                }
            }
        });

        // ── Bakiye güncelleme paketi ──────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(BalanceUpdatePacket.ID,
                (client, handler, buf, responseSender) -> {
                    double balance     = buf.readDouble();
                    double dailyEarned = buf.readDouble();
                    double dailySpent  = buf.readDouble();
                    client.execute(() -> ClientEconomyData.update(balance, dailyEarned, dailySpent));
                });

        // ── Sunucu Market GUI aç ──────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(OpenMarketPacket.ID,
                (client, handler, buf, responseSender) -> {
                    int count = buf.readInt();
                    List<ClientMarketItem> items = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        items.add(new ClientMarketItem(
                                buf.readInt(),
                                buf.readString(),
                                buf.readString(),
                                buf.readString(),
                                buf.readDouble(),
                                buf.readInt()
                        ));
                    }
                    boolean canBuy            = buf.readBoolean();
                    double sellPriceMultiplier = buf.readDouble();
                    client.execute(() -> client.setScreen(new MarketScreen(items, canBuy, sellPriceMultiplier)));
                });

        // ── Market locate paketi ──────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(LocateMarketPacket.ID,
                (client, handler, buf, responseSender) -> {
                    boolean found = buf.readBoolean();
                    if (found) {
                        int x = buf.readInt();
                        int y = buf.readInt();
                        int z = buf.readInt();
                        client.execute(() -> MarketLocateRenderer.setTarget(
                                new net.minecraft.util.math.BlockPos(x, y, z)));
                    } else {
                        client.execute(() -> MarketLocateRenderer.setTarget(null));
                    }
                });

        // ── Satın alma başarı sesi ────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(BuySoundPacket.ID,
                (client, handler, buf, responseSender) -> client.execute(() -> {
                    if (client.player != null) {
                        client.player.playSound(
                                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                                SoundCategory.MASTER,
                                1.0f, 1.2f);
                    }
                }));

        // ── Depot GUI aç ──────────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.OpenDepotPacket.ID,
                (client, handler, buf, responseSender) -> {
                    int availCount = buf.readInt();
                    java.util.Map<String, Integer> available = new java.util.LinkedHashMap<>();
                    for (int i = 0; i < availCount; i++) {
                        available.put(buf.readString(), buf.readInt());
                    }
                    int listCount = buf.readInt();
                    java.util.List<com.cogtrade.market.PlayerShopManager.ShopListing> listings = new java.util.ArrayList<>();
                    for (int i = 0; i < listCount; i++) {
                        listings.add(new com.cogtrade.market.PlayerShopManager.ShopListing(
                                buf.readInt(), "", buf.readString(), buf.readString(),
                                buf.readString(), buf.readDouble(), true));
                    }
                    client.execute(() -> client.setScreen(new DepotScreen(available, listings)));
                });

        // ── Player Shop GUI aç ────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.OpenPlayerShopPacket.ID,
                (client, handler, buf, responseSender) -> {
                    String ownerUuid = buf.readString();
                    String ownerName = buf.readString();
                    double sellMult  = buf.readDouble();
                    int count = buf.readInt();
                    java.util.List<ClientMarketItem> items = new java.util.ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        int    id       = buf.readInt();
                        String itemId   = buf.readString();
                        String dispName = buf.readString();
                        String cat      = buf.readString();
                        double price    = buf.readDouble();
                        int    stock    = buf.readInt();   // gerçek stok miktarı
                        items.add(new ClientMarketItem(id, itemId, dispName, cat, price, stock));
                    }
                    client.execute(() -> client.setScreen(
                            new PlayerShopScreen(ownerUuid, ownerName, items, sellMult)));
                });

        // ── Sandık seçim bildirimi ────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(
                com.cogtrade.network.ChestSelectedPacket.ID,
                (client, handler, buf, responseSender) -> {
                    net.minecraft.util.math.BlockPos pos = buf.readBlockPos();
                    boolean added = buf.readBoolean();
                    int total = buf.readInt();
                    // Bildirim isteğe bağlı
                });

        // ── Bağlantı kesilince sıfırla ────────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientEconomyData.reset());

        CogTradeHud.register();
        MarketLocateRenderer.register();

        // ── Block Entity Renderer'ları CLIENT_STARTED'da kaydet ───────────
        // TYPE'lar CogTrade.onInitialize()'da atandığından burada null olabilir,
        // CLIENT_STARTED'da kaydetmek null pointer'ı önler.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (MarketBlockEntity.TYPE != null) {
                BlockEntityRendererFactories.register(
                        MarketBlockEntity.TYPE,
                        MarketBlockEntityRenderer::new);
            }
            if (com.cogtrade.block.TradePostBlock.TYPE != null) {
                BlockEntityRendererFactories.register(
                        com.cogtrade.block.TradePostBlock.TYPE,
                        TradePostBlockEntityRenderer::new);
            }
            if (com.cogtrade.block.TradeDepotBlock.TYPE != null) {
                BlockEntityRendererFactories.register(
                        com.cogtrade.block.TradeDepotBlock.TYPE,
                        TradeDepotBlockEntityRenderer::new);
            }
        });

        CogTrade.LOGGER.info("CogTrade client hazır!");
    }
}