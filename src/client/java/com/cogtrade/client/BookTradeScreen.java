package com.cogtrade.client;

import com.cogtrade.CogTrade;
import com.cogtrade.client.gui.CogTradeGuiLayout;
import com.cogtrade.client.gui.NineSliceRenderer;
import com.cogtrade.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Book-themed direct player-to-player trade screen.
 *
 * Layout uses FIXED screen-pixel sizes for all interactive elements so that
 * items always render at their natural 16×16 pixels and slots have consistent
 * padding — regardless of the book's scale factor.
 *
 * Only the book background and page origin are computed with sc().
 *
 * LEFT PAGE:  My offer (3×3) + coin field + ready/cancel buttons + inventory
 * RIGHT PAGE: Partner offer (3×3) + partner coin + partner ready status
 */
public class BookTradeScreen extends Screen {

    // ── Texture ───────────────────────────────────────────────────────────
    private static final Identifier BOOK_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/book.png");

    // ── Renkler (kitap / parşömen teması) ────────────────────────────────
    private static final int C_LABEL     = 0xFF2C1A08;
    private static final int C_DIM       = 0xFF7A5B2A;
    private static final int C_GOLD      = 0xFF9B6200;
    private static final int C_GOLD_HI   = 0xFFFFD080;
    private static final int C_GREEN_HI  = 0xFF44CC44;
    private static final int C_RED_HI    = 0xFFCC4444;
    private static final int C_READY_OK  = 0xFF228822;
    private static final int C_WAIT      = 0xFF888877;
    private static final int C_SEP       = 0x66604000;

    // Slot renkleri
    private static final int C_SLOT_BG   = 0x99C8A870;  // parşömen içi (boş slot)
    private static final int C_SLOT_HOV  = 0xBBE8C890;  // hover
    private static final int C_SLOT_BRD  = 0xFF9B6200;  // benim slotum kenarı
    private static final int C_SLOT_LOCK = 0xFF7A5B2A;  // partner slotu kenarı
    private static final int C_INV_BG    = 0x77C8A870;
    private static final int C_INV_HOV   = 0xBBE8C890;
    private static final int C_INV_BRD   = 0xAA9B6200;

    // ── Sabit ekran-piksel boyutları (GUI scale'den bağımsız) ─────────────
    //
    // Minecraft her zaman item'ı 16×16 ekran pikselinde çizer.
    // Slot boyutunu sabit tutarak item her zaman sığar ve ortalanır.

    /** Teklif slotu boyutu (px). 16px item + 1px padding her kenar = 18px. */
    private static final int SLOT       = 18;
    /** Teklif slotları arası boşluk (px). */
    private static final int SLOT_GAP   = 2;
    /** Teklif slotu adımı. */
    private static final int SLOT_PITCH = SLOT + SLOT_GAP;  // 20

    /** Envanter slotu boyutu (px) — standart MC slot boyutu. */
    private static final int INV_SLOT   = 18;
    /** Envanter slotları arası boşluk. */
    private static final int INV_GAP    = 1;
    /** Envanter slotu adımı. */
    private static final int INV_PITCH  = INV_SLOT + INV_GAP;  // 19

    /** Item'ın slot içindeki ofset (ortalama için). */
    private static final int ITEM_OFF   = (SLOT - 16) / 2;        // 1
    private static final int INV_ITEM_OFF = (INV_SLOT - 16) / 2;  // 1

    /** Sayfa kenarından içerik başlangıcına padding. */
    private static final int PAD        = 10;

    // ── Sabit dikey pozisyonlar (sayfa Y'sinden ekran pikseli) ────────────

    private static final int HDR_Y      = 6;
    private static final int HINT_Y     = HDR_Y + 14;              // 20

    // Teklif grid'i: 3 satır × SLOT_PITCH px
    private static final int GRID_Y     = HINT_Y + 10;             // 30
    private static final int GRID_H     = 3 * SLOT_PITCH - SLOT_GAP; // 56

    private static final int COIN_LBL_Y = GRID_Y + GRID_H + 8;    // 94
    private static final int COIN_FLD_Y = COIN_LBL_Y + 10;        // 104
    private static final int COIN_FLD_H = 14;
    private static final int COIN_FLD_W = 120;

    private static final int READY_Y    = COIN_FLD_Y + COIN_FLD_H + 7;  // 125
    private static final int STATUS_Y   = READY_Y + 12;            // 137

    private static final int BTN_Y      = STATUS_Y + 12;           // 149
    private static final int BTN_H      = 18;
    private static final int BTN_W      = 88;
    private static final int BTN_GAP    = 6;

    private static final int SEP_Y      = BTN_Y + BTN_H + 8;      // 175
    private static final int INV_HDR_Y  = SEP_Y + 5;              // 180
    private static final int INV_MAIN_Y = INV_HDR_Y + 10;         // 190

    // 3 satır ana envanter: 3 × INV_PITCH = 57px
    private static final int HBAR_SEP_Y = INV_MAIN_Y + 3 * INV_PITCH;  // 247
    private static final int HBAR_Y     = HBAR_SEP_Y + 3;         // 250

    // ── Static screen reference ───────────────────────────────────────────
    public static BookTradeScreen currentScreen = null;

    // ── Trade state ───────────────────────────────────────────────────────
    private String sessionId;
    private final String partnerName;

    private final ItemStack[] myOffer      = new ItemStack[9];
    private final ItemStack[] partnerOffer = new ItemStack[9];
    private double  myCoins;
    private double  partnerCoins;
    private boolean myReady;
    private boolean partnerReady;

    // ── Coin custom text input ─────────────────────────────────────────────
    private String  coinText       = "";
    private int     coinCursor     = 0;
    private int     coinViewStart  = 0;
    private boolean coinActive     = false;
    private int     coinDirtyTicks = 0;
    private double  lastSentCoins  = 0;

    // ── Quantity popup (middle-click on offer slot) ───────────────────────
    private int    qSlot   = -1;
    private String qText   = "";
    private int    qCursor = 0;
    private int    qPopupX, qPopupY;

    // ── Misc ──────────────────────────────────────────────────────────────
    private boolean   closedByServer = false;
    private ItemStack tooltipStack   = ItemStack.EMPTY;

    // ── Layout (recomputed on init/resize) ───────────────────────────────
    private double scale;
    private int    guiX, guiY;

    // ── Cached page origins (screen px) ──────────────────────────────────
    private int lpX, lpY, lpW, lpH;   // left page
    private int rpX, rpY, rpW, rpH;   // right page

    // ── Constructor ───────────────────────────────────────────────────────

    private BookTradeScreen(String sessionId, String partnerName,
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
        this.coinText     = myCoins > 0 ? String.format("%.0f", myCoins) : "";
    }

    // ── Static API ────────────────────────────────────────────────────────

    public static void open(MinecraftClient client, String sessionId, String partnerName,
                            ItemStack[] myOffer, ItemStack[] partnerOffer,
                            double myCoins, double partnerCoins,
                            boolean myReady, boolean partnerReady) {
        BookTradeScreen screen = new BookTradeScreen(
                sessionId, partnerName, myOffer, partnerOffer,
                myCoins, partnerCoins, myReady, partnerReady);
        currentScreen = screen;
        client.setScreen(screen);
    }

    public static void updateState(ItemStack[] myOffer, ItemStack[] partnerOffer,
                                   double myCoins, double partnerCoins,
                                   boolean myReady, boolean partnerReady) {
        BookTradeScreen s = currentScreen;
        if (s == null) return;
        for (int i = 0; i < 9; i++) {
            s.myOffer[i]      = myOffer[i]      != null ? myOffer[i].copy()      : ItemStack.EMPTY;
            s.partnerOffer[i] = partnerOffer[i] != null ? partnerOffer[i].copy() : ItemStack.EMPTY;
        }
        s.partnerCoins = partnerCoins;
        s.myReady      = myReady;
        s.partnerReady = partnerReady;
        if (Math.abs(myCoins - s.myCoins) > 0.5) {
            s.myCoins       = myCoins;
            s.coinText      = myCoins > 0 ? String.format("%.0f", myCoins) : "";
            s.coinCursor    = s.coinText.length();
            s.coinViewStart = 0;
            s.lastSentCoins = myCoins;
        }
    }

    public static void closeFromServer(MinecraftClient client, String message) {
        BookTradeScreen s = currentScreen;
        if (s != null) s.closedByServer = true;
        client.setScreen(null);
        currentScreen = null;
        if (client.player != null && !message.isEmpty())
            client.player.sendMessage(Text.literal(message), false);
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────

    @Override
    protected void init() {
        this.scale = CogTradeGuiLayout.getScale(this.width, this.height);
        int guiW   = sc(CogTradeGuiLayout.BOOK_W);
        int guiH   = sc(CogTradeGuiLayout.BOOK_H);
        this.guiX  = (this.width  - guiW) / 2;
        this.guiY  = (this.height - guiH) / 2;

        // Cache page origins + dimensions (in screen pixels)
        this.lpX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X)  + PAD;
        this.lpY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
        this.lpW = sc(CogTradeGuiLayout.LEFT_PAGE_W) - 2 * PAD;
        this.lpH = sc(CogTradeGuiLayout.LEFT_PAGE_H);

        this.rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X) + PAD;
        this.rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
        this.rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W) - 2 * PAD;
        this.rpH = sc(CogTradeGuiLayout.RIGHT_PAGE_H);
    }

    @Override
    public void tick() {
        super.tick();
        if (coinDirtyTicks > 0 && --coinDirtyTicks == 0) sendCoinUpdate();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);

        // Book background
        NineSliceRenderer.drawFullTexture(ctx, BOOK_TEX,
                guiX, guiY,
                sc(CogTradeGuiLayout.BOOK_W), sc(CogTradeGuiLayout.BOOK_H),
                CogTradeGuiLayout.BOOK_W, CogTradeGuiLayout.BOOK_H);

        drawLeftPage(ctx, mouseX, mouseY);
        drawRightPage(ctx, mouseX, mouseY);

        super.render(ctx, mouseX, mouseY, delta);

        if (qSlot >= 0) drawQuantityPopup(ctx);

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

    // ── Left page ─────────────────────────────────────────────────────────

    private void drawLeftPage(DrawContext ctx, int mouseX, int mouseY) {
        // ─ Başlık + ipucu ────────────────────────────────────────────────
        drawScaled(ctx, "TEKLİFİNİZ", lpX, lpY + HDR_Y, C_LABEL, 1.3f);
        ctx.drawText(textRenderer, Text.literal("Sol: tümü  Sağ: 1  Orta: adet"),
                lpX, lpY + HINT_Y, C_DIM, false);

        // ─ 3×3 teklif grid'i (benim) ─────────────────────────────────────
        drawOfferGrid(ctx, mouseX, mouseY, lpX, lpY + GRID_Y, true);

        // ─ Coin alanı ────────────────────────────────────────────────────
        ctx.drawText(textRenderer, Text.literal("⬡ Coin Teklifiniz:"),
                lpX, lpY + COIN_LBL_Y, C_GOLD, false);
        drawTextField(ctx,
                lpX, lpY + COIN_FLD_Y, COIN_FLD_W, COIN_FLD_H,
                coinText, coinCursor, coinViewStart, coinActive, "0");

        // ─ Hazır durumu ───────────────────────────────────────────────────
        drawReadyDot(ctx, lpX, lpY + READY_Y, myReady);
        ctx.drawText(textRenderer, Text.literal(myReady ? "HAZIR" : "Bekliyor..."),
                lpX + 10, lpY + READY_Y, myReady ? C_GREEN_HI : C_WAIT, false);

        // ─ Durum mesajı ───────────────────────────────────────────────────
        String statusMsg;
        int    statusColor;
        if      (myReady && partnerReady) { statusMsg = "✓ Her iki taraf hazır!";            statusColor = C_GREEN_HI; }
        else if (myReady)                 { statusMsg = "Partner onayını bekliyor...";        statusColor = C_DIM;      }
        else if (partnerReady)            { statusMsg = "Partner hazır — siz de onaylayın!";  statusColor = C_GOLD;     }
        else                              { statusMsg = "Teklif düzenleyin, HAZIR'a basın";   statusColor = C_DIM;      }
        ctx.drawText(textRenderer, Text.literal(statusMsg), lpX, lpY + STATUS_Y, statusColor, false);

        // ─ HAZIR + İPTAL butonları ────────────────────────────────────────
        int totalBW  = BTN_W * 2 + BTN_GAP;
        int btnStartX = lpX + (lpW - totalBW) / 2;
        int btnY     = lpY + BTN_Y;

        boolean hazirHov = !myReady
                && mouseX >= btnStartX && mouseX < btnStartX + BTN_W
                && mouseY >= btnY && mouseY < btnY + BTN_H;
        drawButton(ctx, btnStartX, btnY, BTN_W, BTN_H,
                myReady ? 0xFF228822 : hazirHov ? 0xFF2E8C2E : 0xFF1A661A,
                0xFF55CC55, "✓ HAZIR");

        int cancelX = btnStartX + BTN_W + BTN_GAP;
        boolean cancelHov = mouseX >= cancelX && mouseX < cancelX + BTN_W
                && mouseY >= btnY && mouseY < btnY + BTN_H;
        drawButton(ctx, cancelX, btnY, BTN_W, BTN_H,
                cancelHov ? 0xFF8C2E2E : 0xFF661A1A,
                0xFFCC4444, "✗ İPTAL");

        // ─ Envanter ayırıcı + başlık ──────────────────────────────────────
        ctx.fill(lpX, lpY + SEP_Y, lpX + lpW, lpY + SEP_Y + 1, C_SEP);
        ctx.drawText(textRenderer, Text.literal("ENVANTERİNİZ"),
                lpX, lpY + INV_HDR_Y, C_DIM, false);

        // ─ Envanter slotları ──────────────────────────────────────────────
        drawInventory(ctx, mouseX, mouseY);
    }

    // ── Right page ────────────────────────────────────────────────────────

    private void drawRightPage(DrawContext ctx, int mouseX, int mouseY) {
        // ─ Başlık: partner adı + skin kafası ─────────────────────────────
        int headSz = 11;
        drawPlayerHead(ctx, partnerName, rpX, rpY + HDR_Y - 1, headSz);
        String pLabel = partnerName.length() > 13
                ? partnerName.substring(0, 12) + "." : partnerName;
        drawScaled(ctx, pLabel.toUpperCase() + " TEKLİFİ",
                rpX + headSz + 3, rpY + HDR_Y, C_LABEL, 1.3f);
        ctx.drawText(textRenderer, Text.literal("Salt okunur"),
                rpX, rpY + HINT_Y, C_DIM, false);

        // ─ 3×3 teklif grid'i (partner) ───────────────────────────────────
        drawOfferGrid(ctx, mouseX, mouseY, rpX, rpY + GRID_Y, false);

        // ─ Partner coin ───────────────────────────────────────────────────
        ctx.drawText(textRenderer, Text.literal("⬡ Partner Coin:"),
                rpX, rpY + COIN_LBL_Y, C_GOLD, false);
        String pCoin = partnerCoins > 0 ? formatCoins(partnerCoins) + " ⬡" : "0 ⬡";
        ctx.drawText(textRenderer, Text.literal(pCoin),
                rpX, rpY + COIN_FLD_Y + (COIN_FLD_H - textRenderer.fontHeight) / 2,
                partnerCoins > 0 ? C_GREEN_HI : C_WAIT, false);

        // ─ Partner hazır durumu ───────────────────────────────────────────
        drawReadyDot(ctx, rpX, rpY + READY_Y, partnerReady);
        ctx.drawText(textRenderer, Text.literal(partnerReady ? "HAZIR" : "Bekliyor..."),
                rpX + 10, rpY + READY_Y, partnerReady ? C_GREEN_HI : C_WAIT, false);

        ctx.drawText(textRenderer,
                Text.literal(partnerReady ? "Partner takası onayladı!" : "Partner onay bekliyor..."),
                rpX, rpY + STATUS_Y, partnerReady ? C_GREEN_HI : C_DIM, false);
    }

    // ── Offer grid ────────────────────────────────────────────────────────

    private void drawOfferGrid(DrawContext ctx, int mouseX, int mouseY,
                               int startX, int startY, boolean isMine) {
        ItemStack[] offer = isMine ? myOffer : partnerOffer;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slot = row * 3 + col;
                int sx   = startX + col * SLOT_PITCH;
                int sy   = startY + row * SLOT_PITCH;
                boolean hov = isMine
                        && mouseX >= sx && mouseX < sx + SLOT
                        && mouseY >= sy && mouseY < sy + SLOT;

                // Dış kenarlık
                ctx.fill(sx - 1, sy - 1, sx + SLOT + 1, sy + SLOT + 1,
                        isMine ? C_SLOT_BRD : C_SLOT_LOCK);
                // Slot arka planı
                ctx.fill(sx, sy, sx + SLOT, sy + SLOT,
                        hov ? C_SLOT_HOV : C_SLOT_BG);

                // Kilitli görünüm (partner slotu)
                if (!isMine) {
                    ctx.fill(sx, sy, sx + 3, sy + 1, 0xFF888877);
                    ctx.fill(sx, sy, sx + 1, sy + 3, 0xFF888877);
                }

                // Aktif slot vurgusu (quantity editörü)
                if (isMine && slot == qSlot) {
                    ctx.fill(sx - 1, sy - 1, sx + SLOT + 1, sy,          0xFFFFFF00);
                    ctx.fill(sx - 1, sy + SLOT, sx + SLOT + 1, sy + SLOT + 1, 0xFFFFFF00);
                    ctx.fill(sx - 1, sy - 1, sx, sy + SLOT + 1,          0xFFFFFF00);
                    ctx.fill(sx + SLOT, sy - 1, sx + SLOT + 1, sy + SLOT + 1, 0xFFFFFF00);
                }

                // Item — merkezi konumda (1px padding her tarafta)
                ItemStack stack = offer[slot];
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx + ITEM_OFF, sy + ITEM_OFF);
                    ctx.drawItemInSlot(textRenderer, stack, sx + ITEM_OFF, sy + ITEM_OFF);
                    if (hov) tooltipStack = stack;
                }
            }
        }
    }

    // ── Envanter ──────────────────────────────────────────────────────────

    private void drawInventory(DrawContext ctx, int mouseX, int mouseY) {
        if (client == null || client.player == null) return;
        var inv  = client.player.getInventory();

        // 9 sütunu sayfa genişliğine sığdır
        // INV_PITCH * 9 = 171px — genellikle sayfa genişliğinden (>200px) az
        int invW = 9 * INV_PITCH - INV_GAP;
        int invX = lpX + (lpW - invW) / 2;   // ortalanmış

        // Ana envanter (slot 9–35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = invX + col * INV_PITCH;
                int sy = lpY + INV_MAIN_Y + row * INV_PITCH;
                drawInvSlot(ctx, mouseX, mouseY, sx, sy, inv.getStack(9 + row * 9 + col));
            }
        }

        // Hotbar ayırıcı
        ctx.fill(invX, lpY + HBAR_SEP_Y, invX + invW, lpY + HBAR_SEP_Y + 1, C_SEP);

        // Hotbar (slot 0–8)
        for (int col = 0; col < 9; col++) {
            int sx = invX + col * INV_PITCH;
            int sy = lpY + HBAR_Y;
            drawInvSlot(ctx, mouseX, mouseY, sx, sy, inv.getStack(col));
        }
    }

    private void drawInvSlot(DrawContext ctx, int mouseX, int mouseY,
                             int sx, int sy, ItemStack stack) {
        boolean hov = mouseX >= sx && mouseX < sx + INV_SLOT
                && mouseY >= sy && mouseY < sy + INV_SLOT;
        ctx.fill(sx - 1, sy - 1, sx + INV_SLOT + 1, sy + INV_SLOT + 1,
                hov ? 0xBBAA6600 : C_INV_BRD);
        ctx.fill(sx, sy, sx + INV_SLOT, sy + INV_SLOT,
                hov ? C_INV_HOV : C_INV_BG);
        if (!stack.isEmpty()) {
            // Item 16×16 — (INV_SLOT-16)/2 = 1px padding her tarafta
            ctx.drawItem(stack, sx + INV_ITEM_OFF, sy + INV_ITEM_OFF);
            ctx.drawItemInSlot(textRenderer, stack, sx + INV_ITEM_OFF, sy + INV_ITEM_OFF);
            if (hov) tooltipStack = stack;
        }
    }

    // ── Quantity popup ────────────────────────────────────────────────────

    private void drawQuantityPopup(DrawContext ctx) {
        int pw = 148, ph = 48;
        int x  = Math.min(qPopupX, this.width  - pw - 4);
        int y  = Math.max(qPopupY, 4);

        ctx.fill(x - 1, y - 1, x + pw + 1, y + ph + 1, 0xFFAA8800);
        ctx.fill(x, y, x + pw, y + ph, 0xFFF5EAB8);
        ctx.drawText(textRenderer, Text.literal("Adet (Enter=uygula  Esc=iptal)"),
                x + 4, y + 4, C_LABEL, false);
        drawTextField(ctx, x + 4, y + 16, pw - 8, 14, qText, qCursor, 0, true, "1");
        ItemStack cur = (qSlot >= 0 && qSlot < 9) ? myOffer[qSlot] : ItemStack.EMPTY;
        if (!cur.isEmpty()) {
            ctx.drawText(textRenderer,
                    Text.literal("Max: " + cur.getMaxCount() + "  Şu an: " + cur.getCount()),
                    x + 4, y + 34, C_DIM, false);
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sessionId == null) return false;
        if (button != 0 && button != 1 && button != 2) return false;
        int mx = (int) mouseX, my = (int) mouseY;

        if (qSlot >= 0) { closeQuantityPopup(); }

        byte clickType = (button == 1)
                ? TradeItemMovePacket.CLICK_RIGHT
                : TradeItemMovePacket.CLICK_LEFT;

        // ─ Coin alanı ─────────────────────────────────────────────────────
        if (mx >= lpX && mx < lpX + COIN_FLD_W
                && my >= lpY + COIN_FLD_Y && my < lpY + COIN_FLD_Y + COIN_FLD_H) {
            coinActive = true; return true;
        }

        // ─ HAZIR butonu ───────────────────────────────────────────────────
        int totalBW   = BTN_W * 2 + BTN_GAP;
        int btnStartX = lpX + (lpW - totalBW) / 2;
        int btnY      = lpY + BTN_Y;
        if (!myReady && mx >= btnStartX && mx < btnStartX + BTN_W
                && my >= btnY && my < btnY + BTN_H) {
            coinActive = false; sendReadyPacket(); return true;
        }

        // ─ İPTAL butonu ───────────────────────────────────────────────────
        int cancelX = btnStartX + BTN_W + BTN_GAP;
        if (mx >= cancelX && mx < cancelX + BTN_W
                && my >= btnY && my < btnY + BTN_H) {
            sendCancelPacket(); return true;
        }

        coinActive = false;

        // ─ Benim teklif slotlarım ─────────────────────────────────────────
        int mySlot = offerSlotAt(lpX, lpY + GRID_Y, mx, my);
        if (mySlot >= 0) {
            if (button == 2) { openQuantityPopup(mySlot, mx, my); }
            else if (!myOffer[mySlot].isEmpty()) { sendItemMove(mySlot, -1, clickType); }
            return true;
        }

        // ─ Partner teklif slotları (salt okunur) ──────────────────────────
        if (offerSlotAt(rpX, rpY + GRID_Y, mx, my) >= 0) return true;

        // ─ Envanter slotları ──────────────────────────────────────────────
        int invSlot = invSlotAt(mx, my);
        if (invSlot >= 0) {
            if (client != null && client.player != null
                    && !client.player.getInventory().getStack(invSlot).isEmpty()) {
                sendItemMove(-1, invSlot, clickType);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Keyboard ──────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Quantity popup
        if (qSlot >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyQuantityChange(); closeQuantityPopup(); return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { closeQuantityPopup(); return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && qCursor > 0) {
                qText = qText.substring(0, qCursor - 1) + qText.substring(qCursor);
                qCursor--;
            } else if (keyCode == GLFW.GLFW_KEY_DELETE && qCursor < qText.length()) {
                qText = qText.substring(0, qCursor) + qText.substring(qCursor + 1);
            } else if (keyCode == GLFW.GLFW_KEY_LEFT  && qCursor > 0) { qCursor--; }
            else if   (keyCode == GLFW.GLFW_KEY_RIGHT && qCursor < qText.length()) { qCursor++; }
            return true;
        }

        // Coin alanı
        if (coinActive) {
            coinCursor = Math.max(0, Math.min(coinCursor, coinText.length())); // güvenli clamp
            if (keyCode == GLFW.GLFW_KEY_ESCAPE)  { coinActive = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                sendCoinUpdate(); coinActive = false; return true;
            }
            int innerW = COIN_FLD_W - 6;
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && coinCursor > 0) {
                coinText = coinText.substring(0, coinCursor - 1) + coinText.substring(coinCursor);
                coinCursor--;
                if (coinCursor < coinViewStart) coinViewStart = coinCursor;
                coinDirtyTicks = 5;
            } else if (keyCode == GLFW.GLFW_KEY_DELETE && coinCursor < coinText.length()) {
                coinText = coinText.substring(0, coinCursor) + coinText.substring(coinCursor + 1);
                coinDirtyTicks = 5;
            } else if (keyCode == GLFW.GLFW_KEY_LEFT && coinCursor > 0) {
                coinCursor--;
                if (coinCursor < coinViewStart) coinViewStart = coinCursor;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT && coinCursor < coinText.length()) {
                coinCursor++;
                coinViewStart = adjustViewStart(coinText, coinCursor, coinViewStart, innerW);
            } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                coinCursor = 0; coinViewStart = 0;
            } else if (keyCode == GLFW.GLFW_KEY_END) {
                coinCursor = coinText.length();
                coinViewStart = adjustViewStart(coinText, coinCursor, coinViewStart, innerW);
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { sendCancelPacket(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (qSlot >= 0) {
            qCursor = Math.max(0, Math.min(qCursor, qText.length())); // güvenli clamp
            if (Character.isDigit(chr) && qText.length() < 3) {
                qText = qText.substring(0, qCursor) + chr + qText.substring(qCursor);
                qCursor++;
            }
            return true;
        }
        if (coinActive && Character.isDigit(chr) && coinText.length() < 12) {
            coinCursor = Math.max(0, Math.min(coinCursor, coinText.length())); // güvenli clamp
            coinText = coinText.substring(0, coinCursor) + chr + coinText.substring(coinCursor);
            coinCursor++;
            int innerW = COIN_FLD_W - 6;
            coinViewStart = adjustViewStart(coinText, coinCursor, coinViewStart, innerW);
            coinDirtyTicks = 5;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    // ── Hit testing ───────────────────────────────────────────────────────

    /** 3×3 teklif grid'inde slot indeksini (0-8) döndürür, yoksa -1. */
    private int offerSlotAt(int startX, int startY, int mx, int my) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = startX + col * SLOT_PITCH;
                int sy = startY + row * SLOT_PITCH;
                if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT)
                    return row * 3 + col;
            }
        }
        return -1;
    }

    /** Envanter slot indeksini (0-35) döndürür, yoksa -1. */
    private int invSlotAt(int mx, int my) {
        int invW = 9 * INV_PITCH - INV_GAP;
        int invX = lpX + (lpW - invW) / 2;

        // Ana envanter (9–35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = invX + col * INV_PITCH;
                int sy = lpY + INV_MAIN_Y + row * INV_PITCH;
                if (mx >= sx && mx < sx + INV_SLOT && my >= sy && my < sy + INV_SLOT)
                    return 9 + row * 9 + col;
            }
        }
        // Hotbar (0–8)
        for (int col = 0; col < 9; col++) {
            int sx = invX + col * INV_PITCH;
            int sy = lpY + HBAR_Y;
            if (mx >= sx && mx < sx + INV_SLOT && my >= sy && my < sy + INV_SLOT)
                return col;
        }
        return -1;
    }

    // ── Quantity popup ────────────────────────────────────────────────────

    private void openQuantityPopup(int slot, int clickX, int clickY) {
        if (myOffer[slot].isEmpty()) return;
        qSlot   = slot;
        qText   = String.valueOf(myOffer[slot].getCount());
        qCursor = qText.length();
        qPopupX = clickX - 10;
        qPopupY = Math.max(clickY - 56, 4);
    }

    private void closeQuantityPopup() { qSlot = -1; qText = ""; qCursor = 0; }

    private void applyQuantityChange() {
        if (qSlot < 0 || myOffer[qSlot].isEmpty()) return;
        int desired;
        try { desired = Integer.parseInt(qText.trim()); }
        catch (NumberFormatException e) { return; }
        desired = Math.max(0, Math.min(desired, myOffer[qSlot].getMaxCount()));
        int current = myOffer[qSlot].getCount();
        if (desired == current) return;

        if (desired < current) {
            for (int i = 0; i < current - desired; i++)
                sendItemMove(qSlot, -1, TradeItemMovePacket.CLICK_RIGHT);
        } else {
            if (client == null || client.player == null) return;
            int toAdd = desired - current;
            for (int inv = 0; inv < 36 && toAdd > 0; inv++) {
                ItemStack s = client.player.getInventory().getStack(inv);
                if (!s.isEmpty() && ItemStack.canCombine(s, myOffer[qSlot])) {
                    int take = Math.min(toAdd, s.getCount());
                    for (int i = 0; i < take; i++)
                        sendItemMove(qSlot, inv, TradeItemMovePacket.CLICK_RIGHT);
                    toAdd -= take;
                }
            }
        }
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
        if (sessionId == null) return;
        double amount;
        try {
            String t = coinText.trim();
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

    // ── Çizim yardımcıları ────────────────────────────────────────────────

    private void drawTextField(DrawContext ctx, int x, int y, int w, int h,
                               String text, int cursor, int viewStart,
                               boolean active, String placeholder) {
        ctx.fill(x, y, x + w, y + h, active ? 0xFFF5EAB8 : 0xFFEAD9A0);
        int bc = active ? 0xFF9B6200 : 0x66604000;
        ctx.fill(x,       y,       x + w, y + 1,   bc);
        ctx.fill(x,       y + h-1, x + w, y + h,   bc);
        ctx.fill(x,       y,       x + 1, y + h,   bc);
        ctx.fill(x + w-1, y,       x + w, y + h,   bc);

        int pad = 3;
        int ty  = y + (h - textRenderer.fontHeight) / 2;
        int tx  = x + pad;

        ctx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        if (text.isEmpty()) {
            if (!active)
                ctx.drawText(textRenderer, Text.literal(placeholder), tx, ty, 0x88604000, false);
            else if (cursorVisible())
                ctx.fill(tx, ty, tx + 1, ty + textRenderer.fontHeight, 0xFF604000);
        } else {
            int vs   = Math.max(0, Math.min(viewStart, text.length()));
            String d = text.substring(vs);
            ctx.drawText(textRenderer, Text.literal(d), tx, ty, C_LABEL, false);
            if (active && cursorVisible()) {
                int cp = Math.max(0, cursor - vs);
                if (cp <= d.length()) {
                    int cx = tx + textRenderer.getWidth(d.substring(0, cp));
                    ctx.fill(cx, ty, cx + 1, ty + textRenderer.fontHeight, 0xFF604000);
                }
            }
        }
        ctx.disableScissor();
    }

    private void drawButton(DrawContext ctx, int x, int y, int w, int h,
                            int bg, int border, String label) {
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.fill(x,       y,       x + w, y + 1,   border);
        ctx.fill(x,       y + h-1, x + w, y + h,   border);
        ctx.fill(x,       y,       x + 1, y + h,   border);
        ctx.fill(x + w-1, y,       x + w, y + h,   border);
        ctx.drawText(textRenderer, Text.literal(label),
                x + (w - textRenderer.getWidth(label)) / 2,
                y + (h - textRenderer.fontHeight) / 2,
                0xFFFFFFFF, false);
    }

    private void drawReadyDot(DrawContext ctx, int x, int y, boolean ready) {
        ctx.fill(x + 1, y + 2, x + 7, y + 8, ready ? C_GREEN_HI : C_WAIT);
    }

    private void drawScaled(DrawContext ctx, String text, int x, int y, int color, float s) {
        var m = ctx.getMatrices();
        m.push(); m.translate(x, y, 0); m.scale(s, s, 1f);
        ctx.drawText(textRenderer, Text.literal(text), 0, 0, color, false);
        m.pop();
    }

    /**
     * Oyuncunun skin kafasını çizer (yüz katmanı + şapka katmanı).
     * Chat Heads modundaki gibi: UV(8,8)→(16,16) + UV(40,8)→(48,16) bölgeleri.
     */
    private void drawPlayerHead(DrawContext ctx, String playerName, int x, int y, int size) {
        if (size <= 0 || client == null || client.getNetworkHandler() == null) return;
        for (var entry : client.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().getName().equalsIgnoreCase(playerName)) {
                Identifier skin = entry.getSkinTexture();
                ctx.drawTexture(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);  // yüz
                ctx.drawTexture(skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64); // şapka
                return;
            }
        }
        // Fallback
        ctx.fill(x, y, x + size, y + size, 0xFF887766);
        if (!playerName.isEmpty() && size >= 8) {
            String init = playerName.substring(0, 1).toUpperCase();
            ctx.drawText(textRenderer, init,
                    x + (size - textRenderer.getWidth(init)) / 2,
                    y + (size - textRenderer.fontHeight) / 2,
                    0xFFFFFFFF, false);
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────

    private boolean cursorVisible() {
        return (System.currentTimeMillis() / 530) % 2 == 0;
    }

    private int adjustViewStart(String text, int cursor, int viewStart, int innerW) {
        if (cursor < viewStart) return cursor;
        while (viewStart < cursor && viewStart < text.length()) {
            if (textRenderer.getWidth(text.substring(viewStart, cursor)) <= innerW - 2) break;
            viewStart++;
        }
        return Math.max(0, viewStart);
    }

    private int sc(int v) { return (int) Math.round(v * scale); }

    private static String formatCoins(double amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000)     return String.format("%.1fK", amount / 1_000);
        return String.format("%.0f", amount);
    }
}
