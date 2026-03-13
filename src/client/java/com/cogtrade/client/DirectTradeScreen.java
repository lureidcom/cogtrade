package com.cogtrade.client;

import com.cogtrade.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Direct player-to-player trade screen.
 *
 * ┌─────────────────────────────────────────────────────────────┐
 * │  ◆ DOĞRUDAN TAKAS — PartnerAdı                 [x]         │  h=22
 * ├──────────────────────────┬──────────────────────────────────┤
 * │  SEN (yeşil panel)       │  PARTNER (sarı panel)           │
 * │  ┌──┬──┬──┐             │  ┌──┬──┬──┐                     │
 * │  │  │  │  │             │  │  │  │  │                     │
 * │  ├──┼──┼──┤  3×3 grid   │  ├──┼──┼──┤  3×3 grid          │
 * │  │  │  │  │             │  │  │  │  │                     │
 * │  ├──┼──┼──┤             │  ├──┼──┼──┤                     │
 * │  │  │  │  │             │  │  │  │  │                     │
 * │  └──┴──┴──┘             │  └──┴──┴──┘                     │
 * │  ⬡ Coin Teklifiniz:     │  ⬡ Partner Coin Teklifi:       │
 * │  [__coin input__________]│  0 ⬡                           │
 * │  ■ HAZIR / Bekliyor      │  ■ HAZIR / Bekliyor            │
 * │  [durum mesajı]          │                                │
 * │                [✓ HAZIR] [✗ İPTAL ET]                     │
 * ├─────────────────────────────────────────────────────────────┤
 * │  ENVANTERİNİZ                                              │
 * │  [inv 9×3 slots]                                          │
 * │  ─────────────────────                                    │
 * │  [hotbar 9 slots]                                         │
 * └─────────────────────────────────────────────────────────────┘
 *
 * Orta tık → manuel adet popup (quantity input — üstte float eder, otomatik focus)
 */
public class DirectTradeScreen extends Screen {

    // ── Panel boyutu ──────────────────────────────────────────────────────
    private static final int PANEL_W = 500;
    private static final int PANEL_H = 310;

    // ── Slot parametreleri ────────────────────────────────────────────────
    private static final int SLOT_SIZE  = 20;   // görsel boyut
    private static final int SLOT_PITCH = 22;   // merkez mesafesi
    private static final int COLS       = 3;
    private static final int ROWS       = 3;

    // ── Panel-relative layout koordinatları ──────────────────────────────
    // İki panel: sol (benim teklifim) ve sağ (partnerin teklifi)
    // Her panel ~220px geniş, aralarında 12px divider
    private static final int LEFT_PANEL_X  = 6;
    private static final int LEFT_PANEL_W  = 216;
    private static final int RIGHT_PANEL_X = 228;  // LEFT_PANEL_X + LEFT_PANEL_W + 6
    private static final int RIGHT_PANEL_W = 216;

    private static final int PANELS_Y     = 24;   // başlık altında
    private static final int PANELS_H     = 175;  // section header + grid + coin + ready

    private static final int SECTION_HDR_Y = PANELS_Y + 5;    // "Teklifiniz" yazısı
    private static final int GRID_Y        = PANELS_Y + 18;   // 3×3 grid başlangıcı
    private static final int GRID_H        = ROWS * SLOT_PITCH; // 66px

    private static final int COIN_LABEL_Y  = GRID_Y + GRID_H + 8;
    private static final int COIN_INPUT_Y  = COIN_LABEL_Y + 10;

    private static final int READY_DOT_Y   = COIN_INPUT_Y + 20;
    private static final int STATUS_MSG_Y  = READY_DOT_Y + 11;

    private static final int BTN_Y         = PANELS_Y + PANELS_H + 4;
    private static final int BTN_W         = 88;
    private static final int BTN_H         = 18;
    // butonlar ortada yer alacak
    private static final int READY_BTN_REL  = 94;   // panel-relative x
    private static final int CANCEL_BTN_REL = 190;  // panel-relative x (çok önemli: 94+88+8=190)

    // Envanter bölümü
    private static final int INV_SEP_Y     = BTN_Y + BTN_H + 6;   // separator çizgisi
    private static final int INV_HDR_Y     = INV_SEP_Y + 5;
    private static final int INV_MAIN_Y    = INV_HDR_Y + 10;      // 3 satır main inv
    private static final int HOTBAR_SEP_Y  = INV_MAIN_Y + 3 * 18 + 3;
    private static final int HOTBAR_Y      = HOTBAR_SEP_Y + 3;

    // Envanter x: 9 slot × 18px = 162px → panel 500px → pad = (500-162)/2 = 169
    private static final int INV_X         = (PANEL_W - 9 * 18) / 2;

    // ── Renkler ───────────────────────────────────────────────────────────
    private static final int C_BG          = 0xFF1A1A1A;
    private static final int C_PANEL_DARK  = 0xFF111111;
    private static final int C_LEFT_PANEL  = 0xFF1E2A1E;  // koyu yeşil tint
    private static final int C_RIGHT_PANEL = 0xFF2A2A18;  // koyu sarı tint
    private static final int C_LEFT_BORDER = 0xFF3A6B3A;
    private static final int C_RIGHT_BORDER= 0xFF7A7A28;
    private static final int C_TITLE_BAR   = 0xFF0D0D0D;
    private static final int C_SLOT        = 0xFF333333;
    private static final int C_SLOT_HOVER  = 0xFF555555;
    private static final int C_SLOT_MINE   = 0xFF2E3D2E;  // hafif yeşil tint - benim slotum
    private static final int C_SLOT_THEIR  = 0xFF2A2A1A;  // hafif sarı tint - onların slotu
    private static final int C_INV_SLOT    = 0xFF303030;
    private static final int C_INV_HOVER   = 0xFF484848;
    private static final int C_READY_CLR   = 0xFF55FF55;
    private static final int C_WAIT_CLR    = 0xFF888888;
    private static final int C_GOLD        = 0xFFFFD54F;
    private static final int C_GREEN_TXT   = 0xFF88FF88;
    private static final int C_YELLOW_TXT  = 0xFFFFEE66;
    private static final int C_RED_TXT     = 0xFFFF6666;
    private static final int C_SUBTEXT     = 0xFF888888;
    private static final int C_TEXT        = 0xFFDDDDDD;
    private static final int C_SEPARATOR   = 0xFF333333;
    private static final int C_BTN_BG      = 0xFF252525;

    // ── Static screen reference ───────────────────────────────────────────
    public static DirectTradeScreen currentScreen = null;

    // ── State ─────────────────────────────────────────────────────────────
    private String sessionId;
    private final String partnerName;

    private final ItemStack[] myOffer      = new ItemStack[9];
    private final ItemStack[] partnerOffer = new ItemStack[9];
    private double myCoins;
    private double partnerCoins;
    private boolean myReady;
    private boolean partnerReady;

    // Coin input
    private TextFieldWidget coinInput;
    private boolean updatingCoinInput = false;
    private int     coinDirtyTicks    = 0;
    private double  lastSentCoins     = 0;

    // Quantity editor (floating popup — orta tık ile açılır)
    /** Açık quantity editörünün hedef offer slotu. -1 = kapalı. */
    private int qSlot = -1;
    private TextFieldWidget quantityInput;
    /** Quantity popup'ın ekrandaki mutlak koordinatları (init'te hesaplanmaz, tıklamada) */
    private int qPopupX, qPopupY;

    private boolean closedByServer = false;

    // Tooltip için
    private ItemStack tooltipStack = ItemStack.EMPTY;

    // Panel origin (her render'da hesaplanır)
    private int panelX, panelY;

    // ── Constructor ───────────────────────────────────────────────────────

    private DirectTradeScreen(String sessionId, String partnerName,
                              ItemStack[] myOffer, ItemStack[] partnerOffer,
                              double myCoins, double partnerCoins,
                              boolean myReady, boolean partnerReady) {
        super(Text.literal("Doğrudan Takas"));
        this.sessionId   = sessionId;
        this.partnerName = partnerName;
        for (int i = 0; i < 9; i++) {
            this.myOffer[i]      = myOffer[i]      != null ? myOffer[i].copy()      : ItemStack.EMPTY;
            this.partnerOffer[i] = partnerOffer[i] != null ? partnerOffer[i].copy() : ItemStack.EMPTY;
        }
        this.myCoins      = myCoins;
        this.partnerCoins = partnerCoins;
        this.myReady      = myReady;
        this.partnerReady = partnerReady;
    }

    // ── Static factory / update methods ──────────────────────────────────

    public static void open(MinecraftClient client, String sessionId, String partnerName,
                            ItemStack[] myOffer, ItemStack[] partnerOffer,
                            double myCoins, double partnerCoins,
                            boolean myReady, boolean partnerReady) {
        DirectTradeScreen screen = new DirectTradeScreen(
                sessionId, partnerName, myOffer, partnerOffer,
                myCoins, partnerCoins, myReady, partnerReady);
        currentScreen = screen;
        client.setScreen(screen);
    }

    public static void updateState(ItemStack[] myOffer, ItemStack[] partnerOffer,
                                   double myCoins, double partnerCoins,
                                   boolean myReady, boolean partnerReady) {
        DirectTradeScreen s = currentScreen;
        if (s == null) return;
        for (int i = 0; i < 9; i++) {
            s.myOffer[i]      = myOffer[i]      != null ? myOffer[i].copy()      : ItemStack.EMPTY;
            s.partnerOffer[i] = partnerOffer[i] != null ? partnerOffer[i].copy() : ItemStack.EMPTY;
        }
        s.partnerCoins = partnerCoins;
        s.myReady      = myReady;
        s.partnerReady = partnerReady;
        // Coin input'u sadece sunucu farklı bir değere klampe ettiyse güncelle
        if (Math.abs(myCoins - s.myCoins) > 0.5) {
            s.myCoins = myCoins;
            if (s.coinInput != null) {
                s.updatingCoinInput = true;
                s.coinInput.setText(String.format("%.0f", myCoins));
                s.lastSentCoins      = myCoins;
                s.updatingCoinInput  = false;
            }
        }
    }

    public static void closeFromServer(MinecraftClient client, String message) {
        DirectTradeScreen s = currentScreen;
        if (s != null) s.closedByServer = true;
        client.setScreen(null);
        currentScreen = null;
        if (client.player != null && !message.isEmpty())
            client.player.sendMessage(Text.literal(message), false);
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────

    @Override
    protected void init() {
        int px = (this.width  - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        // Coin input — sol panelde, coin label'ın altında
        int coinX = px + LEFT_PANEL_X + 6;
        int coinY = py + COIN_INPUT_Y;
        coinInput = new TextFieldWidget(textRenderer, coinX, coinY, 100, 14, Text.empty());
        coinInput.setMaxLength(12);
        coinInput.setText(String.format("%.0f", myCoins));
        coinInput.setPlaceholder(Text.literal("0"));
        coinInput.setChangedListener(text -> { if (!updatingCoinInput) coinDirtyTicks = 5; });
        addDrawableChild(coinInput);

        // Quantity input (floating popup — başta gizli)
        quantityInput = new TextFieldWidget(textRenderer, px + 10, py + 10, 80, 14, Text.empty());
        quantityInput.setMaxLength(3);
        quantityInput.setVisible(false);
        addDrawableChild(quantityInput);

        // HAZIR butonu
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("✓ HAZIR"),
                        btn -> sendReadyPacket())
                .dimensions(px + READY_BTN_REL, py + BTN_Y, BTN_W, BTN_H)
                .build());

        // İPTAL butonu
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("✗ İPTAL ET"),
                        btn -> sendCancelPacket())
                .dimensions(px + CANCEL_BTN_REL, py + BTN_Y, BTN_W, BTN_H)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (coinInput != null) coinInput.tick();
        if (quantityInput != null) quantityInput.tick();
        if (coinDirtyTicks > 0 && --coinDirtyTicks == 0) sendCoinUpdate();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        panelX = (this.width  - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        drawMainPanel(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);   // widget'lar
        drawQuantityPopup(ctx);                      // popup — widget'ların üstünde

        if (!tooltipStack.isEmpty()) {
            ctx.drawItemTooltip(textRenderer, tooltipStack, mouseX, mouseY);
            tooltipStack = ItemStack.EMPTY;
        }
    }

    @Override
    public void removed() {
        if (!closedByServer && sessionId != null
                && client != null && client.getNetworkHandler() != null) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(sessionId);
            ClientPlayNetworking.send(TradeCancelPacket.ID, buf);
        }
        sessionId = null;
        if (currentScreen == this) currentScreen = null;
    }

    @Override public boolean shouldPause() { return false; }

    // ── Ana panel çizimi ──────────────────────────────────────────────────

    private void drawMainPanel(DrawContext ctx, int mouseX, int mouseY) {
        int px = panelX, py = panelY;

        // ─ Dış çerçeve / gölge efekti ───────────────────────────────────
        ctx.fill(px - 2, py - 2, px + PANEL_W + 2, py + PANEL_H + 2, 0xFF0A0A0A);
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, C_BG);

        // ─ Title bar ─────────────────────────────────────────────────────
        ctx.fill(px, py, px + PANEL_W, py + 22, C_TITLE_BAR);
        // Altın üst çizgi
        ctx.fill(px, py, px + PANEL_W, py + 1, 0xFFAA8800);
        String title = "◆ DOĞRUDAN TAKAS — " + partnerName;
        int tw = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, "§6" + title, px + (PANEL_W - tw) / 2, py + 7, C_GOLD, true);

        // ─ Sol panel (benim teklifim) ─────────────────────────────────────
        drawSection(ctx,
                px + LEFT_PANEL_X,  py + PANELS_Y,
                LEFT_PANEL_W,       PANELS_H,
                C_LEFT_PANEL, C_LEFT_BORDER);

        // Başlık: "SEN"
        ctx.fill(px + LEFT_PANEL_X, py + PANELS_Y,
                px + LEFT_PANEL_X + LEFT_PANEL_W, py + PANELS_Y + 16, 0xFF1A2A1A);
        ctx.drawText(textRenderer, "§aTEKLİFİNİZ",
                px + LEFT_PANEL_X + 6, py + SECTION_HDR_Y, C_GREEN_TXT, true);
        ctx.drawText(textRenderer, "§8Sol:tümü  Sağ:1  Orta:adet",
                px + LEFT_PANEL_X + 6, py + SECTION_HDR_Y + 9, 0xFF555555, false);

        // ─ Sağ panel (partner teklifi) ────────────────────────────────────
        drawSection(ctx,
                px + RIGHT_PANEL_X, py + PANELS_Y,
                RIGHT_PANEL_W,      PANELS_H,
                C_RIGHT_PANEL, C_RIGHT_BORDER);

        // Başlık: "PARTNER"
        ctx.fill(px + RIGHT_PANEL_X, py + PANELS_Y,
                px + RIGHT_PANEL_X + RIGHT_PANEL_W, py + PANELS_Y + 16, 0xFF2A2A10);
        String pLabel = partnerName.length() > 12
                ? partnerName.substring(0, 11) + "." : partnerName;
        ctx.drawText(textRenderer, "§e" + pLabel.toUpperCase() + " TEKLİFİ",
                px + RIGHT_PANEL_X + 6, py + SECTION_HDR_Y, C_YELLOW_TXT, true);
        ctx.drawText(textRenderer, "§8Salt okunur",
                px + RIGHT_PANEL_X + 6, py + SECTION_HDR_Y + 9, 0xFF555555, false);

        // ─ Offer grid'leri ────────────────────────────────────────────────
        drawOfferGrid(ctx, mouseX, mouseY, true);   // benim
        drawOfferGrid(ctx, mouseX, mouseY, false);  // partner

        // ─ Coin alanları ──────────────────────────────────────────────────
        // Sol: label + input widget (zaten addDrawableChild ile eklendi, sadece label çiz)
        ctx.drawText(textRenderer, "§6⬡ Coin Teklifiniz:",
                px + LEFT_PANEL_X + 6, py + COIN_LABEL_Y, C_GOLD, false);
        // coinInput widget zaten render ediliyor

        // Sağ: label + değer
        ctx.drawText(textRenderer, "§6⬡ Partner Coin:",
                px + RIGHT_PANEL_X + 6, py + COIN_LABEL_Y, C_GOLD, false);
        String pCoin = partnerCoins > 0
                ? "§a" + formatCoins(partnerCoins) + " ⬡"
                : "§80 ⬡";
        ctx.drawText(textRenderer, pCoin,
                px + RIGHT_PANEL_X + 6, py + COIN_INPUT_Y + 2, C_TEXT, false);

        // ─ Ready status ───────────────────────────────────────────────────
        drawReadyDot(ctx, px + LEFT_PANEL_X + 6,  py + READY_DOT_Y, myReady);
        ctx.drawText(textRenderer, myReady ? "§aHAZIR" : "§8Bekliyor...",
                px + LEFT_PANEL_X + 16, py + READY_DOT_Y, myReady ? C_READY_CLR : C_WAIT_CLR, false);

        drawReadyDot(ctx, px + RIGHT_PANEL_X + 6, py + READY_DOT_Y, partnerReady);
        ctx.drawText(textRenderer, partnerReady ? "§aHAZIR" : "§8Bekliyor...",
                px + RIGHT_PANEL_X + 16, py + READY_DOT_Y, partnerReady ? C_READY_CLR : C_WAIT_CLR, false);

        // ─ Durum mesajı (sol panelin altında) ────────────────────────────
        String statusMsg;
        int statusColor;
        if (myReady && partnerReady) {
            statusMsg   = "§a✓ Her iki taraf hazır — takas tamamlanıyor!";
            statusColor = C_READY_CLR;
        } else if (myReady) {
            statusMsg   = "§7Partner onayını bekliyor...";
            statusColor = C_SUBTEXT;
        } else if (partnerReady) {
            statusMsg   = "§ePartner hazır — siz de onaylayın!";
            statusColor = C_YELLOW_TXT;
        } else {
            statusMsg   = "§8Teklif düzenleyin ve ✓ HAZIR'a basın";
            statusColor = C_SUBTEXT;
        }
        ctx.drawText(textRenderer, statusMsg, px + LEFT_PANEL_X + 6, py + STATUS_MSG_Y, statusColor, false);

        // ─ Divider + envanter ────────────────────────────────────────────
        ctx.fill(px + 6, py + INV_SEP_Y, px + PANEL_W - 6, py + INV_SEP_Y + 1, C_SEPARATOR);
        ctx.drawText(textRenderer, "§8ENVANTERİNİZ",
                px + INV_X, py + INV_HDR_Y, C_SUBTEXT, false);

        drawInventory(ctx, mouseX, mouseY);
    }

    /** Kenarlıklı bölüm kutusu çizer. */
    private void drawSection(DrawContext ctx, int x, int y, int w, int h, int bg, int border) {
        // Dış border
        ctx.fill(x, y, x + w, y + h, border);
        // İç alan
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
    }

    /** 6×6 hazır/bekleme noktası. */
    private void drawReadyDot(DrawContext ctx, int x, int y, boolean ready) {
        int color = ready ? C_READY_CLR : C_WAIT_CLR;
        ctx.fill(x, y + 2, x + 6, y + 8, color);
    }

    // ── Offer grid çizimi ─────────────────────────────────────────────────

    private void drawOfferGrid(DrawContext ctx, int mouseX, int mouseY, boolean isMine) {
        int px = panelX, py = panelY;
        int panelRelX = isMine ? LEFT_PANEL_X : RIGHT_PANEL_X;
        // Grid: panelin sol kenarından 8px içeri, GRID_Y'den başla
        int gx = px + panelRelX + 8;
        int gy = py + GRID_Y;
        ItemStack[] offer = isMine ? myOffer : partnerOffer;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slot = row * COLS + col;
                int sx   = gx + col * SLOT_PITCH;
                int sy   = gy + row * SLOT_PITCH;

                boolean hovered = mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + SLOT_SIZE;

                // Slot arka planı
                int baseBg = isMine ? C_SLOT_MINE : C_SLOT_THEIR;
                ctx.fill(sx - 1, sy - 1, sx + SLOT_SIZE + 1, sy + SLOT_SIZE + 1,
                        isMine ? 0xFF3A6B3A : 0xFF7A7A28); // border
                ctx.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE,
                        hovered && isMine ? C_SLOT_HOVER : baseBg);

                // Salt okunur overlay: partner slotuna çapraz çizgi
                if (!isMine) {
                    // Hafif kilitli görünüm: köşe işareti
                    ctx.fill(sx, sy, sx + 3, sy + 1, 0xFF555555);
                    ctx.fill(sx, sy, sx + 1, sy + 3, 0xFF555555);
                }

                ItemStack stack = offer[slot];
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx + 2, sy + 2);
                    ctx.drawItemInSlot(textRenderer, stack, sx + 2, sy + 2);
                    if (hovered) tooltipStack = stack;
                }

                // Quantity editor'de aktif slot ise vurgu
                if (isMine && slot == qSlot) {
                    ctx.fill(sx - 1, sy - 1, sx + SLOT_SIZE + 1, sy - 0, 0xFFFFFF00);
                    ctx.fill(sx - 1, sy + SLOT_SIZE, sx + SLOT_SIZE + 1, sy + SLOT_SIZE + 1, 0xFFFFFF00);
                    ctx.fill(sx - 1, sy - 1, sx, sy + SLOT_SIZE + 1, 0xFFFFFF00);
                    ctx.fill(sx + SLOT_SIZE, sy - 1, sx + SLOT_SIZE + 1, sy + SLOT_SIZE + 1, 0xFFFFFF00);
                }
            }
        }
    }

    // ── Quantity popup çizimi ─────────────────────────────────────────────

    private void drawQuantityPopup(DrawContext ctx) {
        if (qSlot < 0 || quantityInput == null || !quantityInput.isVisible()) return;

        int x = qPopupX;
        int y = qPopupY;
        int w = 130;
        int h = 42;

        // Popup arka planı
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF666600);  // sarı border
        ctx.fill(x, y, x + w, y + h, 0xFF1E1E10);

        // Başlık
        ctx.drawText(textRenderer, "§eAdet Seçin (Enter = uygula)",
                x + 4, y + 4, C_YELLOW_TXT, false);

        // TextFieldWidget zaten render ediliyor (super.render içinde)
        // Mevcut item bilgisi
        ItemStack cur = myOffer[qSlot];
        if (!cur.isEmpty()) {
            String info = "§8Max: " + cur.getMaxCount() + "  Şu an: " + cur.getCount();
            ctx.drawText(textRenderer, info, x + 4, y + 30, C_SUBTEXT, false);
        }
    }

    // ── Envanter çizimi ───────────────────────────────────────────────────

    private void drawInventory(DrawContext ctx, int mouseX, int mouseY) {
        if (client == null || client.player == null) return;
        int px = panelX, py = panelY;
        var inv = client.player.getInventory();

        // 3 satır main inv (slot 9–35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = px + INV_X + col * 18;
                int sy = py + INV_MAIN_Y + row * 18;
                int vs = 9 + row * 9 + col;
                drawInvSlot(ctx, mouseX, mouseY, sx, sy, inv.getStack(vs));
            }
        }

        // Hotbar separator
        ctx.fill(px + INV_X, py + HOTBAR_SEP_Y,
                px + INV_X + 9 * 18, py + HOTBAR_SEP_Y + 1, C_SEPARATOR);

        // Hotbar (slot 0–8)
        for (int col = 0; col < 9; col++) {
            int sx = px + INV_X + col * 18;
            int sy = py + HOTBAR_Y;
            drawInvSlot(ctx, mouseX, mouseY, sx, sy, inv.getStack(col));
        }
    }

    private void drawInvSlot(DrawContext ctx, int mouseX, int mouseY,
                             int sx, int sy, ItemStack stack) {
        boolean hov = mouseX >= sx && mouseX < sx + 16
                && mouseY >= sy && mouseY < sy + 16;
        ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17,
                hov ? 0xFF4A4A4A : 0xFF2A2A2A); // border
        ctx.fill(sx, sy, sx + 16, sy + 16,
                hov ? C_INV_HOVER : C_INV_SLOT);
        if (!stack.isEmpty()) {
            ctx.drawItem(stack, sx, sy);
            ctx.drawItemInSlot(textRenderer, stack, sx, sy);
            if (hov) tooltipStack = stack;
        }
    }

    // ── Mouse tıklama ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Önce widget'ların tıklama kontrolü (TextField, Button vs.)
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button != 0 && button != 1 && button != 2) return false;
        if (sessionId == null) return false;

        int mx = (int) mouseX, my = (int) mouseY;

        // Quantity popup açıkken — popup dışı tıklamada kapat
        if (qSlot >= 0) {
            // Enter ile uygula zaten keyPressed'ta. Dışarı tıklayınca sadece kapat.
            closeQuantityEditor();
            // Devam et: tıklama yine de işlensin
        }

        byte clickType = (button == 1)
                ? TradeItemMovePacket.CLICK_RIGHT
                : TradeItemMovePacket.CLICK_LEFT;

        // ── Benim offer slotuma tıklama ───────────────────────────────────
        int mySlot = getOfferSlotAt(true, mx, my);
        if (mySlot >= 0) {
            if (button == 2) {
                // Orta tık → quantity editörü aç
                openQuantityEditor(mySlot, mx, my);
            } else if (!myOffer[mySlot].isEmpty()) {
                // Sol/Sağ tık → slottan geri al
                sendItemMove(mySlot, -1, clickType);
            }
            return true;
        }

        // ── Partner offer slotuna tıklama → yoksay ───────────────────────
        if (getOfferSlotAt(false, mx, my) >= 0) return true;

        // ── Envanter slotuna tıklama → teklife ekle ───────────────────────
        int invSlot = getInvSlotAt(mx, my);
        if (invSlot >= 0) {
            if (client != null && client.player != null) {
                ItemStack inInv = client.player.getInventory().getStack(invSlot);
                if (!inInv.isEmpty()) sendItemMove(-1, invSlot, clickType);
            }
            return true;
        }

        return false;
    }

    // ── Klavye ────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Quantity editörü açıkken Enter → uygula, Escape → iptal
        if (qSlot >= 0 && quantityInput != null && quantityInput.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyQuantityChange();
                closeQuantityEditor();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeQuantityEditor();
                return true;
            }
            // Diğer tuşları quantity input'a ver
            return quantityInput.keyPressed(keyCode, scanCode, modifiers);
        }

        // Coin input odakta değilse ve ESC basıldıysa → kapat
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            sendCancelPacket();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (qSlot >= 0 && quantityInput != null && quantityInput.isVisible()) {
            return quantityInput.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    // ── Quantity editor yardımcıları ──────────────────────────────────────

    /** Belirtilen offer slotu için quantity editörünü açar ve odaklanır. */
    private void openQuantityEditor(int slot, int clickX, int clickY) {
        if (myOffer[slot].isEmpty()) return;
        qSlot = slot;

        int pw = 130, ph = 42;
        qPopupX = Math.min(clickX - 10, this.width  - pw - 4);
        qPopupY = Math.max(clickY - ph - 8, 4);

        if (quantityInput != null) {
            remove(quantityInput);   // removeWidget → remove() in 1.20.1
            quantityInput = new TextFieldWidget(
                    textRenderer,
                    qPopupX + 4, qPopupY + 14,
                    pw - 8, 14,
                    Text.empty());
            quantityInput.setMaxLength(3);
            quantityInput.setText(String.valueOf(myOffer[slot].getCount()));
            quantityInput.setVisible(true);
            quantityInput.setSelectionEnd(quantityInput.getText().length());
            quantityInput.setSelectionStart(0);
            addDrawableChild(quantityInput);
            this.setFocused(quantityInput);
            quantityInput.setFocused(true);
        }
    }

    private void closeQuantityEditor() {
        qSlot = -1;
        if (quantityInput != null) {
            quantityInput.setVisible(false);
            quantityInput.setFocused(false);
        }
        // Ana ekrana focus'u geri ver
        this.setFocused(null);
    }

    /** Mevcut quantity input değerini uygular (Enter'da çağrılır). */
    private void applyQuantityChange() {
        if (qSlot < 0 || quantityInput == null) return;
        ItemStack current = myOffer[qSlot];
        if (current.isEmpty()) return;

        int desired;
        try {
            desired = Integer.parseInt(quantityInput.getText().trim());
        } catch (NumberFormatException e) {
            return;
        }
        desired = Math.max(0, Math.min(desired, current.getMaxCount()));

        int currentCount = current.getCount();
        if (desired == currentCount) return;

        if (desired < currentCount) {
            // Azalt: sağ tık paketleriyle birer birer kaldır
            int toRemove = currentCount - desired;
            for (int i = 0; i < toRemove; i++)
                sendItemMove(qSlot, -1, TradeItemMovePacket.CLICK_RIGHT);
        } else {
            // Artır: envanterden ekle
            if (client == null || client.player == null) return;
            int toAdd = desired - currentCount;
            for (int invSlot = 0; invSlot < 36 && toAdd > 0; invSlot++) {
                ItemStack inv = client.player.getInventory().getStack(invSlot);
                if (!inv.isEmpty() && ItemStack.canCombine(inv, current)) {
                    int take = Math.min(toAdd, inv.getCount());
                    for (int i = 0; i < take; i++)
                        sendItemMove(qSlot, invSlot, TradeItemMovePacket.CLICK_RIGHT);
                    toAdd -= take;
                }
            }
        }
    }

    // ── Hit-test yardımcıları ─────────────────────────────────────────────

    private int getOfferSlotAt(boolean isMine, int mx, int my) {
        int panelRelX = isMine ? LEFT_PANEL_X : RIGHT_PANEL_X;
        int gx = panelX + panelRelX + 8;
        int gy = panelY + GRID_Y;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int sx = gx + col * SLOT_PITCH;
                int sy = gy + row * SLOT_PITCH;
                if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE)
                    return row * COLS + col;
            }
        }
        return -1;
    }

    private int getInvSlotAt(int mx, int my) {
        int invX = panelX + INV_X;
        // Main inv (slot 9–35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = invX + col * 18;
                int sy = panelY + INV_MAIN_Y + row * 18;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16)
                    return 9 + row * 9 + col;
            }
        }
        // Hotbar (slot 0–8)
        for (int col = 0; col < 9; col++) {
            int sx = invX + col * 18;
            int sy = panelY + HOTBAR_Y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16)
                return col;
        }
        return -1;
    }

    // ── Paket göndericiler ────────────────────────────────────────────────

    private void sendItemMove(int offerSlot, int invSlot, byte clickType) {
        if (sessionId == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sessionId);
        buf.writeInt(offerSlot);
        buf.writeInt(invSlot);
        buf.writeByte(clickType);
        ClientPlayNetworking.send(TradeItemMovePacket.ID, buf);
    }

    private void sendCoinUpdate() {
        if (sessionId == null || coinInput == null) return;
        double amount;
        try {
            String t = coinInput.getText().trim();
            amount = t.isEmpty() ? 0.0 : Double.parseDouble(t);
            if (amount < 0) amount = 0;
        } catch (NumberFormatException e) { amount = 0; }

        if (amount == lastSentCoins) return;
        lastSentCoins = amount;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sessionId);
        buf.writeDouble(amount);
        ClientPlayNetworking.send(TradeCoinOfferPacket.ID, buf);
    }

    private void sendReadyPacket() {
        if (sessionId == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sessionId);
        ClientPlayNetworking.send(TradeSetReadyPacket.ID, buf);
    }

    private void sendCancelPacket() {
        if (sessionId == null) return;
        closedByServer = true;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sessionId);
        ClientPlayNetworking.send(TradeCancelPacket.ID, buf);
        sessionId = null;
        if (client != null) client.setScreen(null);
    }

    // ── Yardımcılar ──────────────────────────────────────────────────────

    private static String formatCoins(double amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000)     return String.format("%.1fK", amount / 1_000);
        return String.format("%.0f", amount);
    }
}