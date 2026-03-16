package com.cogtrade.client.gui;

import com.cogtrade.CogTrade;
import com.cogtrade.client.ClientEconomyData;
import com.cogtrade.client.ClientTransactionData;
import com.cogtrade.client.TransactionEntry;
import com.cogtrade.market.EffectMarketEntry;
import com.cogtrade.market.EffectMarketRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class CogTradeBookScreen extends Screen {

    // ── Textures ──────────────────────────────────────────────────────────

    private static final Identifier BOOK_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/book.png");
    private static final Identifier TAB_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/tab.png");
    private static final Identifier TAB_ACTIVE_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/tab-active.png");

    // ── Renkler ───────────────────────────────────────────────────────────

    private static final int COLOR_MENU_NORMAL = 0x2C1A08;
    private static final int COLOR_MENU_HOVER  = 0x9B6200;
    private static final int COLOR_BACK        = 0x7A5B2A;
    private static final int COLOR_BACK_HOVER  = 0x2C1A08;
    private static final int COLOR_SUBTITLE    = 0x5A3F10;
    private static final int COLOR_LABEL       = 0x3D2200;
    private static final int COLOR_DIM         = 0x7A5B2A;

    // ── Metin ölçekleri ───────────────────────────────────────────────────

    private static final float MENU_SCALE       = 2.5f;
    private static final float MENU_LINE_RATIO  = 1.9f;
    private static final float BACK_SCALE       = 1.4f;

    /** Geri butonu + alt başlık için ayrılan dikey alan (ekran pikseli). */
    private static final int BACK_AREA_H   = 50;
    /** Bakiye listesindeki her satırın yüksekliği (ekran pikseli). */
    private static final int TX_ITEM_H     = 15;
    /** Bakiye listesinin üstündeki başlık+özet alanı yüksekliği. */
    private static final int TX_HEADER_H   = 32;

    // ── Navigasyon state ──────────────────────────────────────────────────

    private CogTradeMainTab activeTab    = CogTradeMainTab.STORE;
    private CogTradeSubTab  activeSubTab = null;
    private boolean         inSubPage    = false;

    // ── Ödeme sayfası state ───────────────────────────────────────────────

    private String         selectedPlayer     = null;
    private int            playerScrollOffset = 0;
    private List<String>   allPlayers         = new ArrayList<>();
    private List<String>   filteredPlayers    = new ArrayList<>();

    // Ödeme sayfası özel metin giriş state'i
    private String searchQuery     = "";
    private String amountText      = "";
    private String noteText        = "";
    private int    payActiveField  = 0;    // 0=yok, 1=arama, 2=miktar, 3=not
    private int    searchCursor    = 0;
    private int    amountCursor    = 0;
    private int    noteCursor      = 0;
    private int    searchViewStart = 0;
    private int    amountViewStart = 0;
    private int    noteViewStart   = 0;

    // ── Bakiye sayfası state ──────────────────────────────────────────────
    private int              txScrollOffset = 0;
    private TransactionEntry selectedTx     = null;

    // ── Büyü Marketi state ────────────────────────────────────────────────
    private int              effectScrollOffset  = 0;
    private EffectMarketEntry selectedEffect      = null;
    private int              selectedAmplifier    = 0;
    private int              selectedDurationIdx  = 1; // varsayılan: 5 dk

    // ── Layout ────────────────────────────────────────────────────────────

    private int    guiX, guiY;
    private double scale;

    // ─────────────────────────────────────────────────────────────────────

    public CogTradeBookScreen() {
        super(Text.translatable("gui.cogtrade.book"));
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();

        this.scale = CogTradeGuiLayout.getScale(this.width, this.height);
        int guiW  = sc(CogTradeGuiLayout.BOOK_W);
        int guiH  = sc(CogTradeGuiLayout.BOOK_H);
        this.guiX = (this.width  - guiW) / 2;
        this.guiY = (this.height - guiH) / 2;

        // Alt öğesi olmayan sekmeler direkt içerik modunda başlar
        if (!inSubPage) {
            inSubPage = CogTradeSubTab.getSubTabsFor(activeTab).isEmpty();
        }

        // BAKIYE dışındaki sayfalarda işlem state'ini sıfırla
        if (activeSubTab != CogTradeSubTab.BAKIYE) {
            txScrollOffset = 0;
            selectedTx     = null;
        }
        // BUYU_MARKETI dışındaki sayfalarda efekt state'ini sıfırla
        if (activeSubTab != CogTradeSubTab.BUYU_MARKETI) {
            effectScrollOffset = 0;
            selectedEffect     = null;
            selectedAmplifier  = 0;
            selectedDurationIdx = 1;
        }

        // Ödeme sayfası — oyuncu listesini yükle
        if (inSubPage && activeSubTab == CogTradeSubTab.ODEME_YAP) {
            searchQuery = ""; searchCursor = 0; searchViewStart = 0;
            amountText  = ""; amountCursor = 0; amountViewStart = 0;
            noteText    = ""; noteCursor   = 0; noteViewStart   = 0;
            payActiveField = 0;
            selectedPlayer = null;
            playerScrollOffset = 0;
            allPlayers      = getOnlinePlayers();
            filteredPlayers = new ArrayList<>(allPlayers);
        }
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);

        draw(ctx, BOOK_TEX, 0, 0, CogTradeGuiLayout.BOOK_W, CogTradeGuiLayout.BOOK_H);

        for (CogTradeMainTab tab : CogTradeMainTab.values()) {
            draw(ctx, tab == activeTab ? TAB_ACTIVE_TEX : TAB_TEX,
                    tab.tabX, tab.tabY, tab.tabW, tab.tabH);
            draw(ctx, tab.iconTexture,
                    tab.iconX, tab.iconY, tab.iconW, tab.iconH);
        }

        if (inSubPage) {
            renderBackButton(ctx, mouseX, mouseY);
            if (activeSubTab == CogTradeSubTab.ODEME_YAP) {
                renderPaymentSearchField(ctx, mouseX, mouseY);
                renderPlayerList(ctx, mouseX, mouseY);
                renderPaymentRight(ctx, mouseX, mouseY);
            } else if (activeSubTab == CogTradeSubTab.BAKIYE) {
                renderBakiyeList(ctx, mouseX, mouseY);
                renderBakiyeDetail(ctx);
            } else if (activeSubTab == CogTradeSubTab.BUYU_MARKETI) {
                renderEffectList(ctx, mouseX, mouseY);
                renderEffectDetail(ctx, mouseX, mouseY);
            }
        } else {
            renderBigMenu(ctx, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta); // widget'ları çizer
    }

    // ── Oyuncu listesi (sol sayfa) ────────────────────────────────────────

    private void renderPlayerList(DrawContext ctx, int mouseX, int mouseY) {
        int lpX    = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int listY  = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + BACK_AREA_H + 24; // back alan + arama kutusu
        int lpW    = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        int lpH    = sc(CogTradeGuiLayout.LEFT_PAGE_H) - BACK_AREA_H - 24;
        int itemH  = 14;
        int visible = lpH / itemH;

        int maxScroll = Math.max(0, filteredPlayers.size() - visible);
        playerScrollOffset = Math.min(playerScrollOffset, maxScroll);

        int y = listY;
        for (int i = playerScrollOffset; i < Math.min(filteredPlayers.size(), playerScrollOffset + visible); i++) {
            String name      = filteredPlayers.get(i);
            boolean selected = name.equals(selectedPlayer);
            boolean hovered  = !selected
                    && mouseX >= lpX && mouseX < lpX + lpW - 5
                    && mouseY >= y   && mouseY < y + itemH;

            if (selected) ctx.fill(lpX, y, lpX + lpW - 5, y + itemH, 0x44AA6600);
            else if (hovered) ctx.fill(lpX, y, lpX + lpW - 5, y + itemH, 0x22AA6600);

            ctx.drawText(textRenderer,
                    Text.literal((selected ? "▶ " : "  ") + name),
                    lpX + 4, y + 3,
                    selected ? COLOR_LABEL : COLOR_DIM, false);
            y += itemH;
        }

        // Boş durum mesajı
        if (filteredPlayers.isEmpty()) {
            String msg = allPlayers.isEmpty() ? "Çevrimiçi oyuncu yok" : "Sonuç bulunamadı";
            ctx.drawText(textRenderer, Text.literal(msg), lpX + 6, listY + 8, COLOR_DIM, false);
        }

        // Kaydırma çubuğu
        if (filteredPlayers.size() > visible && visible > 0) {
            int sbX    = lpX + lpW - 4;
            int thumbH = Math.max(8, lpH * visible / filteredPlayers.size());
            int thumbY = maxScroll > 0
                    ? listY + (lpH - thumbH) * playerScrollOffset / maxScroll
                    : listY;
            ctx.fill(sbX, listY, sbX + 3, listY + lpH, 0x22000000);
            ctx.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xBB9B6200);
        }
    }

    // ── Ödeme sayfası — sol sayfa arama alanı ────────────────────────────

    private void renderPaymentSearchField(DrawContext ctx, int mouseX, int mouseY) {
        int lpX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int lpY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
        int lpW = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        renderTextField(ctx, searchQuery, searchCursor, searchViewStart,
                "Oyuncu ara...", lpX, lpY + BACK_AREA_H, lpW, 18, payActiveField == 1);
    }

    // ── Ödeme sayfası — sağ sayfa form alanları ───────────────────────────

    private void renderPaymentRight(DrawContext ctx, int mouseX, int mouseY) {
        int rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
        int rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
        int rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W);

        // ── Alıcı kutusu ─────────────────────────────────────────────────
        boolean hasPlayer = (selectedPlayer != null);
        int boxY = rpY + 2;
        int boxH = 20;
        ctx.fill(rpX, boxY, rpX + rpW, boxY + boxH,
                hasPlayer ? 0x55AA6600 : 0x22604000);
        int boxBc = hasPlayer ? 0xBBAA6600 : 0x66604000;
        ctx.fill(rpX,         boxY,         rpX + rpW, boxY + 1,     boxBc);
        ctx.fill(rpX,         boxY + boxH-1, rpX + rpW, boxY + boxH,  boxBc);
        ctx.fill(rpX,         boxY,         rpX + 1,   boxY + boxH,  boxBc);
        ctx.fill(rpX + rpW-1, boxY,         rpX + rpW, boxY + boxH,  boxBc);
        String playerDisp = hasPlayer ? "\u2714 " + selectedPlayer : "\u2190 Sol sayfadan oyuncu se\u00e7in";
        ctx.drawText(textRenderer, Text.literal(playerDisp),
                rpX + 5, boxY + (boxH - textRenderer.fontHeight) / 2,
                hasPlayer ? COLOR_LABEL : COLOR_DIM, false);

        // ── Ayırıcı ──────────────────────────────────────────────────────
        ctx.fill(rpX, rpY + 27, rpX + rpW, rpY + 28, 0x44000000);

        // ── Miktar ───────────────────────────────────────────────────────
        ctx.drawText(textRenderer, Text.literal("Miktar:"), rpX, rpY + 33, COLOR_LABEL, false);
        int amtFY = rpY + 44;
        int amtFW = 120;
        renderTextField(ctx, amountText, amountCursor, amountViewStart,
                "Rakam girin", rpX, amtFY, amtFW, 18, payActiveField == 2);
        ctx.drawText(textRenderer, Text.literal("coin"),
                rpX + amtFW + 5, amtFY + (18 - textRenderer.fontHeight) / 2, COLOR_DIM, false);

        // ── Hızlı miktar butonları ────────────────────────────────────────
        int[] amounts = {100, 200, 500, 1000};
        int qY = rpY + 68, qBtnH = 16, qGap = 4;
        int qBtnW = (rpW - qGap * (amounts.length - 1)) / amounts.length;
        for (int i = 0; i < amounts.length; i++) {
            int qx = rpX + i * (qBtnW + qGap);
            boolean qh = mouseX >= qx && mouseX < qx + qBtnW
                      && mouseY >= qY && mouseY < qY + qBtnH;
            ctx.fill(qx, qY, qx + qBtnW, qY + qBtnH, qh ? 0x99604000 : 0x44604000);
            ctx.fill(qx,          qY,          qx + qBtnW, qY + 1,         0x66AA6600);
            ctx.fill(qx,          qY + qBtnH-1, qx + qBtnW, qY + qBtnH,   0x66AA6600);
            ctx.fill(qx,          qY,          qx + 1,     qY + qBtnH,    0x66AA6600);
            ctx.fill(qx + qBtnW-1, qY,          qx + qBtnW, qY + qBtnH,   0x66AA6600);
            String lbl = String.valueOf(amounts[i]);
            ctx.drawText(textRenderer, Text.literal(lbl),
                    qx + (qBtnW - textRenderer.getWidth(lbl)) / 2,
                    qY + (qBtnH - textRenderer.fontHeight) / 2,
                    COLOR_LABEL, false);
        }

        // ── Ayırıcı ──────────────────────────────────────────────────────
        ctx.fill(rpX, rpY + 90, rpX + rpW, rpY + 91, 0x44000000);

        // ── Not ──────────────────────────────────────────────────────────
        ctx.drawText(textRenderer, Text.literal("Not (opsiyonel):"),
                rpX, rpY + 97, COLOR_LABEL, false);
        String ctr = noteText.length() + "/100";
        ctx.drawText(textRenderer, Text.literal(ctr),
                rpX + rpW - textRenderer.getWidth(ctr), rpY + 97,
                noteText.length() > 90 ? 0xCC3300 : COLOR_DIM, false);
        int noteFY = rpY + 109;
        renderTextField(ctx, noteText, noteCursor, noteViewStart,
                "Not (opsiyonel, maks. 100 karakter)", rpX, noteFY, rpW, 18, payActiveField == 3);

        // ── Ayırıcı ──────────────────────────────────────────────────────
        ctx.fill(rpX, rpY + 133, rpX + rpW, rpY + 134, 0x44000000);

        // ── Para Gönder butonu ────────────────────────────────────────────
        int sbW = 110, sbH = 22;
        int sbX = rpX + (rpW - sbW) / 2;
        int sbY = rpY + 140;
        boolean canSend = hasPlayer && !amountText.isBlank();
        boolean sbHov   = canSend && mouseX >= sbX && mouseX < sbX + sbW
                       && mouseY >= sbY && mouseY < sbY + sbH;
        int sbBg   = !canSend ? 0x44604000 : sbHov ? 0xFF604000 : 0xFF9B6200;
        int sbBord = !canSend ? 0x44AA6600 : 0xFFFFD080;
        ctx.fill(sbX, sbY, sbX + sbW, sbY + sbH, sbBg);
        ctx.fill(sbX,         sbY,         sbX + sbW, sbY + 1,     sbBord);
        ctx.fill(sbX,         sbY + sbH-1, sbX + sbW, sbY + sbH,   sbBord);
        ctx.fill(sbX,         sbY,         sbX + 1,   sbY + sbH,   sbBord);
        ctx.fill(sbX + sbW-1, sbY,         sbX + sbW, sbY + sbH,   sbBord);
        String sbLbl = "Para G\u00f6nder";
        ctx.drawText(textRenderer, Text.literal(sbLbl),
                sbX + (sbW - textRenderer.getWidth(sbLbl)) / 2,
                sbY + (sbH - textRenderer.fontHeight) / 2,
                canSend ? 0xFFFFFFFF : 0x88FFFFFF, false);
    }

    // ── Özel metin alanı çizici ───────────────────────────────────────────

    private void renderTextField(DrawContext ctx, String text, int cursor, int viewStart,
                                 String placeholder, int x, int y, int w, int h, boolean active) {
        ctx.fill(x, y, x + w, y + h, active ? 0xFFF5EAB8 : 0xFFEAD9A0);
        int bc = active ? 0xFF9B6200 : 0x66604000;
        ctx.fill(x,       y,       x + w,   y + 1,   bc);
        ctx.fill(x,       y + h-1, x + w,   y + h,   bc);
        ctx.fill(x,       y,       x + 1,   y + h,   bc);
        ctx.fill(x + w-1, y,       x + w,   y + h,   bc);

        int pad = 4;
        int ty  = y + (h - textRenderer.fontHeight) / 2;
        int tx  = x + pad;

        ctx.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        if (text.isEmpty() && !active) {
            ctx.drawText(textRenderer, Text.literal(placeholder), tx, ty, 0x88604000, false);
        } else if (text.isEmpty()) {
            // Boş + aktif: yalnızca imleç
            if (isCursorVisible())
                ctx.fill(tx, ty, tx + 1, ty + textRenderer.fontHeight, 0xFF604000);
        } else {
            int vs = Math.max(0, Math.min(viewStart, text.length()));
            String displayed = text.substring(vs);
            ctx.drawText(textRenderer, Text.literal(displayed), tx, ty, COLOR_LABEL, false);
            if (active && isCursorVisible()) {
                int cp = Math.max(0, cursor - vs);
                if (cp <= displayed.length()) {
                    int cx = tx + textRenderer.getWidth(displayed.substring(0, cp));
                    ctx.fill(cx, ty, cx + 1, ty + textRenderer.fontHeight, 0xFF604000);
                }
            }
        }
        ctx.disableScissor();
    }

    private boolean isCursorVisible() {
        return (System.currentTimeMillis() / 530) % 2 == 0;
    }

    // İmleç için görünür alan (viewStart) hesaplama
    private int adjustViewStart(String text, int cursor, int viewStart, int innerW) {
        if (cursor < viewStart) return cursor;
        while (viewStart < cursor && viewStart < text.length()) {
            String vis = text.substring(viewStart, cursor);
            if (textRenderer.getWidth(vis) <= innerW - 2) break;
            viewStart++;
        }
        return Math.max(0, viewStart);
    }

    // Tıklama piksel konumundan karakter indeksine dönüştürme
    private int charIndexAtPixel(String text, int viewStart, int pixelOffset) {
        if (pixelOffset <= 0 || text.isEmpty()) return viewStart;
        int vs = Math.max(0, Math.min(viewStart, text.length()));
        String displayed = text.substring(vs);
        int idx = 0;
        while (idx < displayed.length()) {
            if (textRenderer.getWidth(displayed.substring(0, idx + 1)) > pixelOffset) break;
            idx++;
        }
        return vs + idx;
    }

    // Oyuncu listesini filtrele
    private void updateFilteredPlayers() {
        String lq = searchQuery.toLowerCase(Locale.ROOT);
        filteredPlayers = allPlayers.stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).contains(lq))
                .collect(Collectors.toList());
        playerScrollOffset = 0;
        selectedPlayer = null;
    }

    // ── Bakiye listesi (sol sayfa) ────────────────────────────────────────

    private void renderBakiyeList(DrawContext ctx, int mouseX, int mouseY) {
        int lpX  = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int lpY  = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
        int lpW  = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        int lpH  = sc(CogTradeGuiLayout.LEFT_PAGE_H);

        int contentY = lpY + BACK_AREA_H;
        int listY    = contentY + TX_HEADER_H;
        int listH    = lpH - BACK_AREA_H - TX_HEADER_H;
        int visible  = listH / TX_ITEM_H;

        // Başlık
        ctx.drawText(textRenderer, Text.literal("İşlem Geçmişi"),
                lpX, contentY, COLOR_LABEL, false);

        // Bakiye özeti
        if (ClientEconomyData.isInitialized()) {
            String summary = String.format("Bakiye: %.0f coin   +%.0f / -%.0f bugün",
                    ClientEconomyData.getBalance(),
                    ClientEconomyData.getDailyEarned(),
                    ClientEconomyData.getDailySpent());
            ctx.drawText(textRenderer, Text.literal(summary),
                    lpX, contentY + 13, COLOR_DIM, false);
        }

        // Ayırıcı çizgi
        ctx.fill(lpX, contentY + 26, lpX + lpW, contentY + 27, 0x44000000);

        // Yükleniyor durumu
        if (!ClientTransactionData.isLoaded()) {
            ctx.drawText(textRenderer, Text.literal("Yükleniyor..."),
                    lpX + 4, listY + 8, COLOR_DIM, false);
            return;
        }

        List<TransactionEntry> entries = ClientTransactionData.get();

        if (entries.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal("Henüz işlem kaydı yok."),
                    lpX + 4, listY + 8, COLOR_DIM, false);
            return;
        }

        int maxScroll = Math.max(0, entries.size() - visible);
        txScrollOffset = Math.min(txScrollOffset, maxScroll);

        int y = listY;
        for (int i = txScrollOffset; i < Math.min(entries.size(), txScrollOffset + visible); i++) {
            TransactionEntry tx = entries.get(i);
            boolean selected = selectedTx != null && selectedTx.id == tx.id;
            boolean hovered  = !selected
                    && mouseX >= lpX && mouseX < lpX + lpW - 4
                    && mouseY >= y   && mouseY < y + TX_ITEM_H;

            if (selected) ctx.fill(lpX, y, lpX + lpW - 4, y + TX_ITEM_H, 0x44AA6600);
            else if (hovered) ctx.fill(lpX, y, lpX + lpW - 4, y + TX_ITEM_H, 0x22AA6600);

            // Tarih (dd/MM)
            LocalDateTime dt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(tx.timestamp), ZoneId.systemDefault());
            String dateStr = String.format("%02d/%02d", dt.getDayOfMonth(), dt.getMonthValue());
            ctx.drawText(textRenderer, Text.literal(dateStr),
                    lpX + 2, y + 3, COLOR_DIM, false);

            // Tür etiketi + rengi
            String typeLabel;
            int    typeColor;
            if ("TRANSFER_IN".equals(tx.txType))   { typeLabel = "GEL"; typeColor = 0x44CC44; }
            else if ("TRANSFER_OUT".equals(tx.txType)) { typeLabel = "GİT"; typeColor = 0xCC4444; }
            else if ("MARKET_BUY".equals(tx.txType))   { typeLabel = "ALM"; typeColor = 0xCC4444; }
            else if ("MARKET_SELL".equals(tx.txType))  { typeLabel = "SAT"; typeColor = 0x44CC44; }
            else if ("EFFECT_BUY".equals(tx.txType))   { typeLabel = "BYU"; typeColor = 0x8844FF; }
            else                                        { typeLabel = "TKS"; typeColor = 0xCC9933; }
            ctx.drawText(textRenderer, Text.literal(typeLabel),
                    lpX + 38, y + 3, typeColor, false);

            // Karşı taraf adı (sığdır)
            String party  = tx.party;
            int    maxPW  = lpW - 150;
            while (textRenderer.getWidth(party) > maxPW && party.length() > 2)
                party = party.substring(0, party.length() - 1);
            if (!party.equals(tx.party)) party += "..";
            ctx.drawText(textRenderer, Text.literal(party),
                    lpX + 60, y + 3, selected ? COLOR_LABEL : COLOR_DIM, false);

            // Miktar (sağa hizalı)
            int    amtColor = tx.amount > 0 ? 0x44CC44 : (tx.amount < 0 ? 0xCC4444 : COLOR_DIM);
            String amtStr   = (tx.amount > 0 ? "+" : "") + String.format("%.0f", tx.amount) + " \u29c6";
            int    amtX     = lpX + lpW - 5 - textRenderer.getWidth(amtStr);
            ctx.drawText(textRenderer, Text.literal(amtStr), amtX, y + 3, amtColor, false);

            y += TX_ITEM_H;
        }

        // Scroll çubuğu
        if (entries.size() > visible && visible > 0) {
            int sbX    = lpX + lpW - 4;
            int thumbH = Math.max(8, listH * visible / entries.size());
            int thumbY = maxScroll > 0
                    ? listY + (listH - thumbH) * txScrollOffset / maxScroll
                    : listY;
            ctx.fill(sbX, listY, sbX + 3, listY + listH, 0x22000000);
            ctx.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xBB9B6200);
        }
    }

    // ── Bakiye işlem detayı (sağ sayfa) ──────────────────────────────────

    private void renderBakiyeDetail(DrawContext ctx) {
        int rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
        int rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
        int rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W);
        int rpH = sc(CogTradeGuiLayout.RIGHT_PAGE_H);

        if (selectedTx == null) {
            String hint = "\u2190 Bir i\u015flem se\u00e7in";
            int hintX   = rpX + (rpW - textRenderer.getWidth(hint)) / 2;
            int hintY   = rpY + rpH / 2 - textRenderer.fontHeight / 2;
            ctx.drawText(textRenderer, Text.literal(hint), hintX, hintY, COLOR_DIM, false);
            return;
        }

        TransactionEntry tx = selectedTx;

        // İşlem türü başlığı
        String typeName;
        int    typeColor;
        if      ("TRANSFER_IN".equals(tx.txType))  { typeName = "Para Alındı";                   typeColor = 0x44CC44; }
        else if ("TRANSFER_OUT".equals(tx.txType)) { typeName = "Para Gönderildi";               typeColor = 0xCC4444; }
        else if ("MARKET_BUY".equals(tx.txType))   { typeName = "Market \u2014 Sat\u0131n Alma"; typeColor = 0xCC4444; }
        else if ("MARKET_SELL".equals(tx.txType))  { typeName = "Market \u2014 Sat\u0131\u015f"; typeColor = 0x44CC44; }
        else if ("EFFECT_BUY".equals(tx.txType))   { typeName = "B\u00fcy\u00fc Sat\u0131n Al\u0131nd\u0131"; typeColor = 0x8844FF; }
        else                                        { typeName = "Do\u011frudan Takas";            typeColor = 0xCC9933; }

        ctx.drawText(textRenderer, Text.literal(typeName), rpX, rpY + 8, typeColor, false);

        // Tarih + saat
        LocalDateTime dt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(tx.timestamp), ZoneId.systemDefault());
        String dateStr = String.format("%02d/%02d/%04d  %02d:%02d",
                dt.getDayOfMonth(), dt.getMonthValue(), dt.getYear(),
                dt.getHour(), dt.getMinute());
        ctx.drawText(textRenderer, Text.literal("Tarih:   " + dateStr),
                rpX, rpY + 24, COLOR_LABEL, false);

        // Miktar
        int    amtColor = tx.amount > 0 ? 0x44CC44 : (tx.amount < 0 ? 0xCC4444 : COLOR_DIM);
        String amtLabel = (tx.amount > 0 ? "+" : "") + String.format("%.0f", tx.amount) + " coin";
        ctx.drawText(textRenderer, Text.literal("Miktar:  " + amtLabel),
                rpX, rpY + 38, amtColor, false);

        // Karşı taraf
        String partyLabel;
        if      ("TRANSFER_IN".equals(tx.txType))                    partyLabel = "G\u00f6nderen: ";
        else if ("TRANSFER_OUT".equals(tx.txType))                   partyLabel = "Al\u0131c\u0131:     ";
        else if ("MARKET_BUY".equals(tx.txType) || "MARKET_SELL".equals(tx.txType)) partyLabel = "\u00dcr\u00fcn:      ";
        else if ("EFFECT_BUY".equals(tx.txType))                     partyLabel = "B\u00fcy\u00fc:      ";
        else                                                          partyLabel = "Partner:   ";
        ctx.drawText(textRenderer, Text.literal(partyLabel + tx.party),
                rpX, rpY + 52, COLOR_LABEL, false);

        int nextY = rpY + 66;

        // Not
        if (!tx.note.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal("Not:       " + tx.note),
                    rpX, nextY, COLOR_DIM, false);
            nextY += 16;
        }

        // Ayırıcı
        ctx.fill(rpX, nextY, rpX + rpW, nextY + 1, 0x33000000);
        nextY += 6;

        // Detay satırları
        if (!tx.detail.isEmpty()) {
            for (String line : tx.detail.split("\n")) {
                ctx.drawText(textRenderer, Text.literal(line), rpX, nextY, COLOR_DIM, false);
                nextY += 13;
            }
        }
    }

    // ── Büyü Marketi — sol sayfa (efekt listesi) ─────────────────────────

    private void renderEffectList(DrawContext ctx, int mouseX, int mouseY) {
        List<EffectMarketEntry> effects = EffectMarketRegistry.EFFECTS;
        int lpX      = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int lpY      = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
        int lpW      = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        int lpH      = sc(CogTradeGuiLayout.LEFT_PAGE_H);
        int contentY = lpY + BACK_AREA_H;
        int availH   = lpH - BACK_AREA_H;
        int rowH     = 22;
        int visRows  = availH / rowH;

        int maxScroll      = Math.max(0, effects.size() - visRows);
        effectScrollOffset = Math.min(effectScrollOffset, maxScroll);

        int ey = contentY;
        for (int i = effectScrollOffset; i < Math.min(effects.size(), effectScrollOffset + visRows); i++) {
            EffectMarketEntry e = effects.get(i);
            boolean sel  = e == selectedEffect;
            boolean rHov = mouseX >= lpX && mouseX < lpX + lpW - 6
                        && mouseY >= ey  && mouseY < ey + rowH;

            if (sel)       ctx.fill(lpX, ey, lpX + lpW - 6, ey + rowH, 0x44AA6600);
            else if (rHov) ctx.fill(lpX, ey, lpX + lpW - 6, ey + rowH, 0x22AA6600);

            if (sel) ctx.drawText(textRenderer, Text.literal("\u25b6"), lpX + 2, ey + 6, COLOR_LABEL, false);
            ctx.drawText(textRenderer, Text.literal(e.displayName()),
                    lpX + 14, ey + 6, sel ? COLOR_LABEL : COLOR_MENU_NORMAL, false);

            long minPrice = EffectMarketRegistry.calcPrice(e, 0, 0);
            String ph = minPrice + " \u2B21+";
            ctx.drawText(textRenderer, Text.literal(ph),
                    lpX + lpW - 6 - textRenderer.getWidth(ph) - 4, ey + 6, COLOR_DIM, false);

            ctx.fill(lpX, ey + rowH - 1, lpX + lpW - 6, ey + rowH, 0x22000000);
            ey += rowH;
        }

        if (effects.size() > visRows && visRows > 0) {
            int sbX    = lpX + lpW - 5;
            int thumbH = Math.max(12, availH * visRows / effects.size());
            int thumbY = maxScroll > 0
                    ? contentY + (availH - thumbH) * effectScrollOffset / maxScroll
                    : contentY;
            ctx.fill(sbX, contentY, sbX + 4, contentY + availH, 0x22000000);
            ctx.fill(sbX, thumbY,   sbX + 4, thumbY + thumbH,   0xBB9B6200);
        }
    }

    // ── Büyü Marketi — sağ sayfa (efekt detayı) ──────────────────────────

    private void renderEffectDetail(DrawContext ctx, int mouseX, int mouseY) {
        int rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
        int rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
        int rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W);
        int rpH = sc(CogTradeGuiLayout.RIGHT_PAGE_H);

        if (selectedEffect == null) {
            ctx.drawText(textRenderer, Text.literal("\u2190 Bir b\u00fcy\u00fc se\u00e7in"),
                    rpX + 8, rpY + rpH / 2 - 4, COLOR_DIM, false);
            return;
        }

        EffectMarketEntry e = selectedEffect;
        int dy = rpY + BACK_AREA_H;

        // Efekt adı (büyük)
        drawText(ctx, e.displayName(), rpX, dy, COLOR_LABEL, 1.6f);
        dy += (int)(textRenderer.fontHeight * 1.6f) + 8;

        // Açıklama
        ctx.drawText(textRenderer, Text.literal(e.description()), rpX, dy, COLOR_DIM, false);
        dy += textRenderer.fontHeight + 12;

        // Ayırıcı
        ctx.fill(rpX, dy, rpX + rpW, dy + 1, 0x44000000);
        dy += 9;

        // Seviye seçici: I'den (maxAmplifier+1)'e kadar dinamik butonlar
        if (e.maxAmplifier() >= 1) {
            ctx.drawText(textRenderer, Text.literal("Seviye:"), rpX, dy, COLOR_SUBTITLE, false);
            int numLevels = e.maxAmplifier() + 1;
            int aH   = 18;
            int aGap = 4;
            int aW   = (rpW - aGap * (numLevels - 1)) / numLevels;
            String[] levelLabels = {"I", "II", "III", "IV", "V"};
            for (int a = 0; a < numLevels; a++) {
                int ax     = rpX + a * (aW + aGap);
                boolean as = selectedAmplifier == a;
                boolean ah = mouseX >= ax && mouseX < ax + aW && mouseY >= dy && mouseY < dy + aH;
                int bg     = as ? 0xFF9B6200 : ah ? 0x99604000 : 0x44604000;
                ctx.fill(ax, dy, ax + aW, dy + aH, bg);
                int border = as ? 0xFFFFD080 : 0x66AA6600;
                ctx.fill(ax,        dy,       ax + aW, dy + 1,    border);
                ctx.fill(ax,        dy+aH-1,  ax + aW, dy + aH,   border);
                ctx.fill(ax,        dy,       ax + 1,  dy + aH,   border);
                ctx.fill(ax + aW-1, dy,       ax + aW, dy + aH,   border);
                String lbl = levelLabels[Math.min(a, levelLabels.length - 1)];
                ctx.drawText(textRenderer, Text.literal(lbl),
                        ax + (aW - textRenderer.getWidth(lbl)) / 2,
                        dy + (aH - textRenderer.fontHeight) / 2,
                        as ? 0xFFFFFFFF : COLOR_LABEL, false);
            }
            dy += aH + 10;
        }

        // Süre seçin
        ctx.drawText(textRenderer, Text.literal("S\u00fcre se\u00e7in:"), rpX, dy, COLOR_SUBTITLE, false);
        dy += textRenderer.fontHeight + 6;

        // 5 süre butonu: ilk 3 + son 2 (alt satırda ortalı)
        int dBtnW = (rpW - 2 * 6) / 3;
        int dBtnH = 26;
        int dGap  = 6;
        for (int d = 0; d < EffectMarketRegistry.DURATION_LABELS.length; d++) {
            int col = d < 3 ? d : d - 3;
            int row = d < 3 ? 0 : 1;
            int rowStartX = (d >= 3) ? rpX + (rpW - 2 * dBtnW - dGap) / 2 : rpX;
            int bx   = rowStartX + col * (dBtnW + dGap);
            int by   = dy + row * (dBtnH + dGap);
            boolean ds = selectedDurationIdx == d;
            boolean dh = mouseX >= bx && mouseX < bx + dBtnW
                      && mouseY >= by && mouseY < by + dBtnH;

            int bg     = ds ? 0xFF9B6200 : dh ? 0x99604000 : 0x44604000;
            ctx.fill(bx, by, bx + dBtnW, by + dBtnH, bg);
            int border = ds ? 0xFFFFD080 : 0x66AA6600;
            ctx.fill(bx,          by,         bx + dBtnW, by + 1,        border);
            ctx.fill(bx,          by+dBtnH-1, bx + dBtnW, by + dBtnH,    border);
            ctx.fill(bx,          by,         bx + 1,     by + dBtnH,    border);
            ctx.fill(bx + dBtnW-1,by,         bx + dBtnW, by + dBtnH,    border);

            String lbl  = EffectMarketRegistry.DURATION_LABELS[d];
            long   price = EffectMarketRegistry.calcPrice(e, selectedAmplifier, d);
            String pric = price + " \u2B21";
            ctx.drawText(textRenderer, Text.literal(lbl),
                    bx + (dBtnW - textRenderer.getWidth(lbl)) / 2, by + 5,
                    ds ? 0xFFFFFFFF : COLOR_LABEL, false);
            ctx.drawText(textRenderer, Text.literal(pric),
                    bx + (dBtnW - textRenderer.getWidth(pric)) / 2, by + 15,
                    ds ? 0xFFFFD080 : COLOR_DIM, false);
        }
        dy += 2 * (dBtnH + dGap) + 4;

        // Toplam fiyat
        long totalPrice = EffectMarketRegistry.calcPrice(e, selectedAmplifier, selectedDurationIdx);
        String total = "Toplam: " + totalPrice + " \u2B21";
        ctx.drawText(textRenderer, Text.literal(total), rpX, dy, COLOR_LABEL, false);
        dy += textRenderer.fontHeight + 10;

        // Satın Al butonu
        int buyW = 100, buyH = 22;
        int buyX = rpX + (rpW - buyW) / 2;
        boolean buyHov = mouseX >= buyX && mouseX < buyX + buyW
                      && mouseY >= dy   && mouseY < dy + buyH;
        ctx.fill(buyX, dy, buyX + buyW, dy + buyH, buyHov ? 0xFF604000 : 0xFF9B6200);
        ctx.fill(buyX,          dy,         buyX + buyW, dy + 1,       0xFFFFD080);
        ctx.fill(buyX,          dy + buyH-1,buyX + buyW, dy + buyH,    0xFFFFD080);
        ctx.fill(buyX,          dy,         buyX + 1,    dy + buyH,    0xFFFFD080);
        ctx.fill(buyX + buyW-1, dy,         buyX + buyW, dy + buyH,    0xFFFFD080);
        String buyLbl = "Sat\u0131n Al";
        ctx.drawText(textRenderer, Text.literal(buyLbl),
                buyX + (buyW - textRenderer.getWidth(buyLbl)) / 2, dy + 7,
                0xFFFFFFFF, false);
    }

    // ── Input ──────────────────────────────────────────────────────────────

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (inSubPage && activeSubTab == CogTradeSubTab.ODEME_YAP) {
            if (payActiveField == 1) {
                if (searchQuery.length() < 32) {
                    searchQuery = searchQuery.substring(0, searchCursor) + chr + searchQuery.substring(searchCursor);
                    searchCursor++;
                    int innerW = sc(CogTradeGuiLayout.LEFT_PAGE_W) - 8;
                    searchViewStart = adjustViewStart(searchQuery, searchCursor, searchViewStart, innerW);
                    updateFilteredPlayers();
                }
                return true;
            } else if (payActiveField == 2) {
                if (Character.isDigit(chr) && amountText.length() < 12) {
                    amountText = amountText.substring(0, amountCursor) + chr + amountText.substring(amountCursor);
                    amountCursor++;
                    amountViewStart = adjustViewStart(amountText, amountCursor, amountViewStart, 112);
                }
                return true;
            } else if (payActiveField == 3) {
                if (noteText.length() < 100) {
                    noteText = noteText.substring(0, noteCursor) + chr + noteText.substring(noteCursor);
                    noteCursor++;
                    int innerW = sc(CogTradeGuiLayout.RIGHT_PAGE_W) - 8;
                    noteViewStart = adjustViewStart(noteText, noteCursor, noteViewStart, innerW);
                }
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inSubPage && activeSubTab == CogTradeSubTab.ODEME_YAP && payActiveField != 0) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                payActiveField = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                payActiveField = (payActiveField % 3) + 1; // 1→2→3→1
                return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                    && payActiveField != 1) {
                sendPayment();
                return true;
            }
            if (payActiveField == 1) {
                int innerW = sc(CogTradeGuiLayout.LEFT_PAGE_W) - 8;
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE && searchCursor > 0) {
                    searchQuery = searchQuery.substring(0, searchCursor - 1) + searchQuery.substring(searchCursor);
                    searchCursor--;
                    if (searchCursor < searchViewStart) searchViewStart = searchCursor;
                    updateFilteredPlayers();
                } else if (keyCode == GLFW.GLFW_KEY_DELETE && searchCursor < searchQuery.length()) {
                    searchQuery = searchQuery.substring(0, searchCursor) + searchQuery.substring(searchCursor + 1);
                    updateFilteredPlayers();
                } else if (keyCode == GLFW.GLFW_KEY_LEFT && searchCursor > 0) {
                    searchCursor--;
                    if (searchCursor < searchViewStart) searchViewStart = searchCursor;
                } else if (keyCode == GLFW.GLFW_KEY_RIGHT && searchCursor < searchQuery.length()) {
                    searchCursor++;
                    searchViewStart = adjustViewStart(searchQuery, searchCursor, searchViewStart, innerW);
                } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                    searchCursor = 0; searchViewStart = 0;
                } else if (keyCode == GLFW.GLFW_KEY_END) {
                    searchCursor = searchQuery.length();
                    searchViewStart = adjustViewStart(searchQuery, searchCursor, searchViewStart, innerW);
                }
            } else if (payActiveField == 2) {
                int innerW = 112;
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE && amountCursor > 0) {
                    amountText = amountText.substring(0, amountCursor - 1) + amountText.substring(amountCursor);
                    amountCursor--;
                    if (amountCursor < amountViewStart) amountViewStart = amountCursor;
                } else if (keyCode == GLFW.GLFW_KEY_DELETE && amountCursor < amountText.length()) {
                    amountText = amountText.substring(0, amountCursor) + amountText.substring(amountCursor + 1);
                } else if (keyCode == GLFW.GLFW_KEY_LEFT && amountCursor > 0) {
                    amountCursor--;
                    if (amountCursor < amountViewStart) amountViewStart = amountCursor;
                } else if (keyCode == GLFW.GLFW_KEY_RIGHT && amountCursor < amountText.length()) {
                    amountCursor++;
                    amountViewStart = adjustViewStart(amountText, amountCursor, amountViewStart, innerW);
                } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                    amountCursor = 0; amountViewStart = 0;
                } else if (keyCode == GLFW.GLFW_KEY_END) {
                    amountCursor = amountText.length();
                    amountViewStart = adjustViewStart(amountText, amountCursor, amountViewStart, innerW);
                }
            } else if (payActiveField == 3) {
                int innerW = sc(CogTradeGuiLayout.RIGHT_PAGE_W) - 8;
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE && noteCursor > 0) {
                    noteText = noteText.substring(0, noteCursor - 1) + noteText.substring(noteCursor);
                    noteCursor--;
                    if (noteCursor < noteViewStart) noteViewStart = noteCursor;
                } else if (keyCode == GLFW.GLFW_KEY_DELETE && noteCursor < noteText.length()) {
                    noteText = noteText.substring(0, noteCursor) + noteText.substring(noteCursor + 1);
                } else if (keyCode == GLFW.GLFW_KEY_LEFT && noteCursor > 0) {
                    noteCursor--;
                    if (noteCursor < noteViewStart) noteViewStart = noteCursor;
                } else if (keyCode == GLFW.GLFW_KEY_RIGHT && noteCursor < noteText.length()) {
                    noteCursor++;
                    noteViewStart = adjustViewStart(noteText, noteCursor, noteViewStart, innerW);
                } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                    noteCursor = 0; noteViewStart = 0;
                } else if (keyCode == GLFW.GLFW_KEY_END) {
                    noteCursor = noteText.length();
                    noteViewStart = adjustViewStart(noteText, noteCursor, noteViewStart, innerW);
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // 1. Üst sekmeler
        for (CogTradeMainTab tab : CogTradeMainTab.values()) {
            if (tabHit(tab, mouseX, mouseY)) {
                setActiveTab(tab);
                return true;
            }
        }

        // 2. Geri butonu
        if (inSubPage && isBackHovered(mouseX, mouseY)) {
            goBack();
            return true;
        }

        // 3. Ödeme sayfası — tıklama işlemleri
        if (inSubPage && activeSubTab == CogTradeSubTab.ODEME_YAP) {
            int lpX  = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int lpY  = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
            int lpW  = sc(CogTradeGuiLayout.LEFT_PAGE_W);
            int lpH  = sc(CogTradeGuiLayout.LEFT_PAGE_H);
            int rpX  = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
            int rpY  = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
            int rpW  = sc(CogTradeGuiLayout.RIGHT_PAGE_W);

            // Sol: arama alanı
            int sfY = lpY + BACK_AREA_H;
            if (mouseX >= lpX && mouseX < lpX + lpW && mouseY >= sfY && mouseY < sfY + 18) {
                payActiveField = 1;
                int clickRel = (int)mouseX - lpX - 4;
                searchCursor = charIndexAtPixel(searchQuery, searchViewStart, clickRel);
                return true;
            }

            // Sol: oyuncu listesi
            int listY = lpY + BACK_AREA_H + 24;
            int listH = lpH - BACK_AREA_H - 24;
            int itemH = 14;
            if (mouseX >= lpX && mouseX < lpX + lpW - 5
                    && mouseY >= listY && mouseY < listY + listH) {
                int index = playerScrollOffset + (int)((mouseY - listY) / itemH);
                if (index >= 0 && index < filteredPlayers.size()) {
                    selectedPlayer = filteredPlayers.get(index);
                }
                payActiveField = 0;
                return true;
            }

            // Sağ: miktar alanı
            int amtFY = rpY + 44;
            if (mouseX >= rpX && mouseX < rpX + 120 && mouseY >= amtFY && mouseY < amtFY + 18) {
                payActiveField = 2;
                int clickRel = (int)mouseX - rpX - 4;
                amountCursor = charIndexAtPixel(amountText, amountViewStart, clickRel);
                return true;
            }

            // Sağ: hızlı miktar butonları
            int[] amounts = {100, 200, 500, 1000};
            int qY = rpY + 68, qBtnH = 16, qGap = 4;
            int qBtnW = (rpW - qGap * (amounts.length - 1)) / amounts.length;
            for (int i = 0; i < amounts.length; i++) {
                int qx = rpX + i * (qBtnW + qGap);
                if (mouseX >= qx && mouseX < qx + qBtnW && mouseY >= qY && mouseY < qY + qBtnH) {
                    amountText = String.valueOf(amounts[i]);
                    amountCursor = amountText.length();
                    amountViewStart = 0;
                    payActiveField = 2;
                    return true;
                }
            }

            // Sağ: not alanı
            int noteFY = rpY + 109;
            if (mouseX >= rpX && mouseX < rpX + rpW && mouseY >= noteFY && mouseY < noteFY + 18) {
                payActiveField = 3;
                int clickRel = (int)mouseX - rpX - 4;
                noteCursor = charIndexAtPixel(noteText, noteViewStart, clickRel);
                return true;
            }

            // Sağ: Para Gönder butonu
            int sbW = 110, sbH = 22;
            int sbX = rpX + (rpW - sbW) / 2;
            int sbY = rpY + 140;
            if (mouseX >= sbX && mouseX < sbX + sbW && mouseY >= sbY && mouseY < sbY + sbH) {
                sendPayment();
                return true;
            }

            // Başka bir yere tıklandı: odağı kaldır
            payActiveField = 0;
        }

        // 3.5. Bakiye sayfası — işlem listesi tıklaması
        if (inSubPage && activeSubTab == CogTradeSubTab.BAKIYE) {
            int lpX   = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int lpY   = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
            int lpH   = sc(CogTradeGuiLayout.LEFT_PAGE_H);
            int lpW   = sc(CogTradeGuiLayout.LEFT_PAGE_W) - 4;
            int listY = lpY + BACK_AREA_H + TX_HEADER_H;
            int listH = lpH - BACK_AREA_H - TX_HEADER_H;

            if (mouseX >= lpX && mouseX < lpX + lpW
                    && mouseY >= listY && mouseY < listY + listH) {
                List<TransactionEntry> entries = ClientTransactionData.get();
                int index = txScrollOffset + (int)((mouseY - listY) / TX_ITEM_H);
                if (index >= 0 && index < entries.size()) {
                    TransactionEntry clicked = entries.get(index);
                    selectedTx = (selectedTx != null && selectedTx.id == clicked.id)
                            ? null : clicked;
                    return true;
                }
            }
        }

        // 4. Büyü Marketi — efekt seçimi, seviye/süre seçimi, satın al
        if (inSubPage && activeSubTab == CogTradeSubTab.BUYU_MARKETI) {
            List<EffectMarketEntry> effects = EffectMarketRegistry.EFFECTS;
            int lpX      = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int lpY      = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
            int lpW      = sc(CogTradeGuiLayout.LEFT_PAGE_W);
            int lpH      = sc(CogTradeGuiLayout.LEFT_PAGE_H);
            int rpX      = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
            int rpY      = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
            int rpW      = sc(CogTradeGuiLayout.RIGHT_PAGE_W);
            int contentY = lpY + BACK_AREA_H;
            int rowH     = 22;
            int visRows  = (lpH - BACK_AREA_H) / rowH;

            // Sol: efekt satırına tıklama
            int ey = contentY;
            for (int i = effectScrollOffset; i < Math.min(effects.size(), effectScrollOffset + visRows); i++) {
                if (mouseX >= lpX && mouseX < lpX + lpW - 6
                        && mouseY >= ey && mouseY < ey + rowH) {
                    EffectMarketEntry clicked = effects.get(i);
                    if (clicked == selectedEffect) {
                        selectedEffect = null;
                    } else {
                        selectedEffect      = clicked;
                        selectedAmplifier   = 0;
                        selectedDurationIdx = 1;
                    }
                    return true;
                }
                ey += rowH;
            }

            // Sağ: seviye, süre, satın al (seçili efekt varsa)
            if (selectedEffect != null) {
                EffectMarketEntry e = selectedEffect;
                int dy = rpY + BACK_AREA_H;
                dy += (int)(textRenderer.fontHeight * 1.6f) + 8;  // ad
                dy += textRenderer.fontHeight + 12;                 // açıklama
                dy += 9;                                            // ayırıcı

                // Seviye butonları (dinamik, I-V arası)
                if (e.maxAmplifier() >= 1) {
                    int numLevels = e.maxAmplifier() + 1;
                    int aH   = 18;
                    int aGap = 4;
                    int aW   = (rpW - aGap * (numLevels - 1)) / numLevels;
                    for (int a = 0; a < numLevels; a++) {
                        int ax = rpX + a * (aW + aGap);
                        if (mouseX >= ax && mouseX < ax + aW && mouseY >= dy && mouseY < dy + aH) {
                            selectedAmplifier = a;
                            return true;
                        }
                    }
                    dy += aH + 10;
                }

                dy += textRenderer.fontHeight + 6; // "Süre seçin:"
                int dBtnW = (rpW - 2 * 6) / 3;
                int dBtnH = 26;
                int dGap  = 6;
                for (int d = 0; d < EffectMarketRegistry.DURATION_LABELS.length; d++) {
                    int col = d < 3 ? d : d - 3;
                    int row = d < 3 ? 0 : 1;
                    int rowStartX = (d >= 3) ? rpX + (rpW - 2 * dBtnW - dGap) / 2 : rpX;
                    int bx = rowStartX + col * (dBtnW + dGap);
                    int by = dy + row * (dBtnH + dGap);
                    if (mouseX >= bx && mouseX < bx + dBtnW && mouseY >= by && mouseY < by + dBtnH) {
                        selectedDurationIdx = d;
                        return true;
                    }
                }
                int buyDy = dy + 2 * (dBtnH + dGap) + 4 + textRenderer.fontHeight + 10;
                int buyW  = 100, buyH = 22;
                int buyX  = rpX + (rpW - buyW) / 2;
                if (mouseX >= buyX && mouseX < buyX + buyW
                        && mouseY >= buyDy && mouseY < buyDy + buyH) {
                    purchaseEffect();
                    return true;
                }
            }
        }

        // 5. Büyük menü tıklaması
        if (!inSubPage) {
            List<CogTradeSubTab> subs = CogTradeSubTab.getSubTabsFor(activeTab);
            if (!subs.isEmpty()) {
                MenuGeometry geo = computeMenuGeometry(subs);
                for (int i = 0; i < subs.size(); i++) {
                    int iy = geo.startY + i * geo.lineH;
                    if (mouseX >= geo.szX && mouseX < geo.szX + geo.szW
                            && mouseY >= iy && mouseY < iy + geo.lineH) {
                        setSubPage(subs.get(i));
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (inSubPage && activeSubTab == CogTradeSubTab.ODEME_YAP) {
            int lpX   = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int listY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + BACK_AREA_H + 24;
            int lpW   = sc(CogTradeGuiLayout.LEFT_PAGE_W);
            int lpH   = sc(CogTradeGuiLayout.LEFT_PAGE_H) - BACK_AREA_H - 24;
            if (mouseX >= lpX && mouseX < lpX + lpW
                    && mouseY >= listY && mouseY < listY + lpH) {
                int visible   = lpH / 14;
                int maxScroll = Math.max(0, filteredPlayers.size() - visible);
                playerScrollOffset = Math.max(0, Math.min(
                        playerScrollOffset - (int) Math.signum(amount), maxScroll));
                return true;
            }
        }
        if (inSubPage && activeSubTab == CogTradeSubTab.BAKIYE) {
            int lpX   = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int lpY   = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
            int lpW   = sc(CogTradeGuiLayout.LEFT_PAGE_W);
            int lpH   = sc(CogTradeGuiLayout.LEFT_PAGE_H);
            int listY = lpY + BACK_AREA_H + TX_HEADER_H;
            int listH = lpH - BACK_AREA_H - TX_HEADER_H;
            if (mouseX >= lpX && mouseX < lpX + lpW
                    && mouseY >= listY && mouseY < listY + listH) {
                int visible   = listH / TX_ITEM_H;
                int maxScroll = Math.max(0, ClientTransactionData.get().size() - visible);
                txScrollOffset = Math.max(0, Math.min(
                        txScrollOffset - (int) Math.signum(amount), maxScroll));
                return true;
            }
        }
        if (inSubPage && activeSubTab == CogTradeSubTab.BUYU_MARKETI) {
            int lpX    = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int cY     = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + BACK_AREA_H;
            int lpW    = sc(CogTradeGuiLayout.LEFT_PAGE_W);
            int availH = sc(CogTradeGuiLayout.LEFT_PAGE_H) - BACK_AREA_H;
            if (mouseX >= lpX && mouseX < lpX + lpW && mouseY >= cY && mouseY < cY + availH) {
                int vis = availH / 22;
                effectScrollOffset = Math.max(0, Math.min(
                        effectScrollOffset - (int) Math.signum(amount),
                        Math.max(0, EffectMarketRegistry.EFFECTS.size() - vis)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    // ── Durum yönetimi ────────────────────────────────────────────────────

    private void setActiveTab(CogTradeMainTab tab) {
        this.activeTab    = tab;
        this.activeSubTab = null;
        this.inSubPage    = CogTradeSubTab.getSubTabsFor(tab).isEmpty();
        this.init(this.client, this.width, this.height);
    }

    private void setSubPage(CogTradeSubTab sub) {
        this.activeSubTab = sub;
        this.inSubPage    = true;
        if (sub == CogTradeSubTab.BAKIYE) {
            ClientTransactionData.reset(); // eskiyi temizle, yükleniyor göster
            ClientPlayNetworking.send(
                    com.cogtrade.network.RequestTransactionHistoryPacket.ID,
                    PacketByteBufs.empty());
        }
        this.init(this.client, this.width, this.height);
    }

    private void goBack() {
        this.activeSubTab = null;
        this.inSubPage    = false;
        this.init(this.client, this.width, this.height);
    }

    // ── Para gönderme ─────────────────────────────────────────────────────

    private void sendPayment() {
        if (selectedPlayer == null || selectedPlayer.isEmpty()) return;
        if (amountText.isBlank()) return;

        long amount;
        try {
            amount = Long.parseLong(amountText.trim());
            if (amount <= 0) return;
        } catch (NumberFormatException e) {
            return;
        }

        String note = noteText.trim();
        StringBuilder cmd = new StringBuilder("pay ")
                .append(selectedPlayer).append(" ").append(amount);
        if (!note.isEmpty()) cmd.append(" ").append(note);

        assert client != null && client.player != null;
        client.player.networkHandler.sendCommand(cmd.toString());

        // Action bar bildirim
        client.player.sendMessage(
                Text.literal("\u00a7a" + selectedPlayer + " adl\u0131 oyuncuya \u00a7e" + amount + " coin \u00a7ag\u00f6nderildi!"),
                true);

        close();
    }

    // ── Büyü satın alma ───────────────────────────────────────────────────

    private void purchaseEffect() {
        if (selectedEffect == null) return;
        net.minecraft.network.PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(selectedEffect.effectId());
        buf.writeInt(selectedAmplifier);
        buf.writeInt(EffectMarketRegistry.DURATION_TICKS[selectedDurationIdx]);
        ClientPlayNetworking.send(com.cogtrade.network.BuyEffectPacket.ID, buf);
        assert client != null && client.player != null;
        client.player.sendMessage(
                Text.literal("\u00a7a" + selectedEffect.displayName() + " b\u00fcy\u00fcs\u00fc sat\u0131n al\u0131n\u0131yor..."),
                true);
        close();
    }

    // ── Oyuncu listesi yükleme ────────────────────────────────────────────

    private List<String> getOnlinePlayers() {
        if (client == null || client.getNetworkHandler() == null) return List.of();
        String self = client.player != null ? client.player.getGameProfile().getName() : "";
        return client.getNetworkHandler().getPlayerList().stream()
                .map(PlayerListEntry::getProfile)
                .map(com.mojang.authlib.GameProfile::getName)
                .filter(n -> !n.equals(self))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    // ── Büyük menü ────────────────────────────────────────────────────────

    private void renderBigMenu(DrawContext ctx, int mouseX, int mouseY) {
        List<CogTradeSubTab> subs = CogTradeSubTab.getSubTabsFor(activeTab);
        if (subs.isEmpty()) return;

        MenuGeometry geo = computeMenuGeometry(subs);
        for (int i = 0; i < subs.size(); i++) {
            String text  = subs.get(i).label.toUpperCase(Locale.forLanguageTag("tr"));
            int    textW = (int)(textRenderer.getWidth(text) * MENU_SCALE);
            int    itemX = Math.max(geo.szX, geo.szX + (geo.szW - textW) / 2);
            int    itemY = geo.startY + i * geo.lineH;
            boolean hov  = mouseX >= geo.szX && mouseX < geo.szX + geo.szW
                        && mouseY >= itemY    && mouseY < itemY + geo.lineH;
            drawText(ctx, text, itemX, itemY,
                    hov ? COLOR_MENU_HOVER : COLOR_MENU_NORMAL, MENU_SCALE);
        }
    }

    // ── Geri butonu ───────────────────────────────────────────────────────

    private void renderBackButton(DrawContext ctx, int mouseX, int mouseY) {
        int btnX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X) + 10;
        int btnY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + 12;
        boolean hov = isBackHovered(mouseX, mouseY);
        drawText(ctx, "\u2190 Geri", btnX, btnY,
                hov ? COLOR_BACK_HOVER : COLOR_BACK, BACK_SCALE);
        if (activeSubTab != null) {
            int h = (int)(textRenderer.fontHeight * BACK_SCALE);
            drawText(ctx, activeSubTab.label, btnX, btnY + h + 5, COLOR_SUBTITLE, BACK_SCALE);
        }
    }

    // ── Geometri yardımcısı ───────────────────────────────────────────────

    private MenuGeometry computeMenuGeometry(List<CogTradeSubTab> subs) {
        int szX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int szY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
        int szW = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        int szH = sc(CogTradeGuiLayout.LEFT_PAGE_H);
        int fH  = (int)(textRenderer.fontHeight * MENU_SCALE);
        int lH  = (int)(fH * MENU_LINE_RATIO);
        int tot = (subs.size() - 1) * lH + fH;
        return new MenuGeometry(szX, szY, szW, szH, lH, szY + Math.max(0, (szH - tot) / 2));
    }

    private static final class MenuGeometry {
        final int szX, szY, szW, szH, lineH, startY;
        MenuGeometry(int szX, int szY, int szW, int szH, int lineH, int startY) {
            this.szX=szX; this.szY=szY; this.szW=szW; this.szH=szH;
            this.lineH=lineH; this.startY=startY;
        }
    }

    // ── Düşük seviye yardımcılar ──────────────────────────────────────────

    private boolean isBackHovered(double mx, double my) {
        int bx = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X) + 10;
        int by = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + 12;
        int bw = (int)(textRenderer.getWidth("\u2190 Geri") * BACK_SCALE) + 8;
        int bh = (int)(textRenderer.fontHeight * BACK_SCALE) + 4;
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private int sc(int v) { return (int) Math.round(v * scale); }

    private void draw(DrawContext ctx, Identifier tex,
                      int bx, int by, int nw, int nh) {
        NineSliceRenderer.drawFullTexture(ctx, tex,
                guiX + sc(bx), guiY + sc(by), sc(nw), sc(nh), nw, nh);
    }

    private void drawText(DrawContext ctx, String text,
                          int sx, int sy, int color, float s) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(sx, sy, 0);
        m.scale(s, s, 1f);
        ctx.drawText(textRenderer, Text.literal(text), 0, 0, color, false);
        m.pop();
    }

    private boolean tabHit(CogTradeMainTab tab, double mx, double my) {
        int x = guiX + sc(tab.tabX), y = guiY + sc(tab.tabY);
        int w = sc(tab.tabW),        h = sc(tab.tabH);
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override public boolean shouldPause() { return false; }

}
