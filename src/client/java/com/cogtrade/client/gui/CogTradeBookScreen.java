package com.cogtrade.client.gui;

import com.cogtrade.CogTrade;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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

    // ── Navigasyon state ──────────────────────────────────────────────────

    private CogTradeMainTab activeTab    = CogTradeMainTab.STORE;
    private CogTradeSubTab  activeSubTab = null;
    private boolean         inSubPage    = false;

    // ── Ödeme sayfası state ───────────────────────────────────────────────

    private String         selectedPlayer     = null;
    private int            playerScrollOffset = 0;
    private List<String>   allPlayers         = new ArrayList<>();
    private List<String>   filteredPlayers    = new ArrayList<>();

    // Widget referansları (sadece ODEME_YAP sayfasındayken geçerli)
    private TextFieldWidget searchBox   = null;
    private TextFieldWidget amountField = null;
    private TextFieldWidget noteField   = null;

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

        // Ödeme sayfası widget'larını oluştur
        searchBox = null; amountField = null; noteField = null;
        if (inSubPage && activeSubTab == CogTradeSubTab.ODEME_YAP) {
            initPaymentWidgets();
        }
    }

    // ── Ödeme sayfası widget kurulumu ────────────────────────────────────

    private void initPaymentWidgets() {
        int lpX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int lpY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
        int lpW = sc(CogTradeGuiLayout.LEFT_PAGE_W);

        int rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
        int rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
        int rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W);

        // Sol: arama kutusu
        searchBox = new TextFieldWidget(textRenderer, lpX, lpY, lpW, 18, Text.empty());
        searchBox.setMaxLength(32);
        searchBox.setPlaceholder(Text.literal("Oyuncu ara..."));
        searchBox.setChangedListener(q -> {
            String lq = q.toLowerCase(Locale.ROOT);
            filteredPlayers = allPlayers.stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).contains(lq))
                    .collect(Collectors.toList());
            playerScrollOffset = 0;
        });
        addDrawableChild(searchBox);

        // Oyuncu listesini yükle
        allPlayers = getOnlinePlayers();
        filteredPlayers = new ArrayList<>(allPlayers);
        playerScrollOffset = 0;
        selectedPlayer = null;

        // Sağ: miktar alanı
        amountField = new TextFieldWidget(textRenderer, rpX, rpY + 55, 120, 18, Text.empty());
        amountField.setMaxLength(12);
        amountField.setPlaceholder(Text.literal("Miktar"));
        addDrawableChild(amountField);

        // Sağ: hızlı miktar butonları (100 / 200 / 500 / 1000)
        int[] amounts = {100, 200, 500, 1000};
        int qw = (rpW - 18) / 4;
        for (int i = 0; i < amounts.length; i++) {
            final int amt = amounts[i];
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(String.valueOf(amt)),
                    btn -> amountField.setText(String.valueOf(amt))
            ).dimensions(rpX + i * (qw + 6), rpY + 85, qw, 18).build());
        }

        // Sağ: not alanı
        noteField = new TextFieldWidget(textRenderer, rpX, rpY + 140, rpW, 18, Text.empty());
        noteField.setMaxLength(100);
        noteField.setPlaceholder(Text.literal("Not (opsiyonel, maks. 100 karakter)"));
        addDrawableChild(noteField);

        // Sağ: gönder butonu
        int sbW = 110;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Para Gönder"),
                btn -> sendPayment()
        ).dimensions(rpX + (rpW - sbW) / 2, rpY + 178, sbW, 20).build());
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
                renderPlayerList(ctx, mouseX, mouseY);
                renderPaymentLabels(ctx);
            }
        } else {
            renderBigMenu(ctx, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta); // widget'ları çizer
    }

    // ── Oyuncu listesi (sol sayfa) ────────────────────────────────────────

    private void renderPlayerList(DrawContext ctx, int mouseX, int mouseY) {
        int lpX    = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int listY  = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + 24; // arama kutusunun altı
        int lpW    = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        int lpH    = sc(CogTradeGuiLayout.LEFT_PAGE_H) - 24;
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

    // ── Ödeme formu etiketleri (sağ sayfa) ───────────────────────────────

    private void renderPaymentLabels(DrawContext ctx) {
        int rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
        int rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
        int rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W);

        // Alıcı
        if (selectedPlayer != null) {
            ctx.drawText(textRenderer,
                    Text.literal("Alıcı: " + selectedPlayer),
                    rpX, rpY + 8, COLOR_LABEL, false);
        } else {
            ctx.drawText(textRenderer,
                    Text.literal("← Oyuncu seçin"),
                    rpX, rpY + 8, COLOR_DIM, false);
        }

        // Miktar etiketi + "coin" suffix
        ctx.drawText(textRenderer, Text.literal("Miktar:"), rpX, rpY + 40, COLOR_LABEL, false);
        ctx.drawText(textRenderer, Text.literal("coin"),
                rpX + 127, rpY + 58, COLOR_DIM, false);

        // Not etiketi + karakter sayacı
        ctx.drawText(textRenderer, Text.literal("Not (opsiyonel):"), rpX, rpY + 125, COLOR_LABEL, false);
        if (noteField != null) {
            int len     = noteField.getText().length();
            String ctr  = len + "/100";
            int ctrX    = rpX + rpW - textRenderer.getWidth(ctr);
            ctx.drawText(textRenderer, Text.literal(ctr),
                    ctrX, rpY + 125, len > 90 ? 0xAA2200 : COLOR_DIM, false);
        }
    }

    // ── Input ──────────────────────────────────────────────────────────────

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

        // 3. Ödeme sayfası — oyuncu listesi tıklaması
        if (inSubPage && activeSubTab == CogTradeSubTab.ODEME_YAP) {
            int lpX   = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int listY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + 24;
            int lpW   = sc(CogTradeGuiLayout.LEFT_PAGE_W) - 5;
            int lpH   = sc(CogTradeGuiLayout.LEFT_PAGE_H) - 24;
            int itemH = 14;

            if (mouseX >= lpX && mouseX < lpX + lpW
                    && mouseY >= listY && mouseY < listY + lpH) {
                int index = playerScrollOffset + (int)((mouseY - listY) / itemH);
                if (index >= 0 && index < filteredPlayers.size()) {
                    selectedPlayer = filteredPlayers.get(index);
                    return true;
                }
            }
        }

        // 4. Büyük menü tıklaması
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
            int listY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + 24;
            int lpW   = sc(CogTradeGuiLayout.LEFT_PAGE_W);
            int lpH   = sc(CogTradeGuiLayout.LEFT_PAGE_H) - 24;
            if (mouseX >= lpX && mouseX < lpX + lpW
                    && mouseY >= listY && mouseY < listY + lpH) {
                int visible   = lpH / 14;
                int maxScroll = Math.max(0, filteredPlayers.size() - visible);
                playerScrollOffset = Math.max(0, Math.min(
                        playerScrollOffset - (int) Math.signum(amount), maxScroll));
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
        if (amountField == null || amountField.getText().isBlank()) return;

        long amount;
        try {
            amount = Long.parseLong(amountField.getText().trim());
            if (amount <= 0) return;
        } catch (NumberFormatException e) {
            return;
        }

        String note = noteField != null ? noteField.getText().trim() : "";
        StringBuilder cmd = new StringBuilder("pay ")
                .append(selectedPlayer).append(" ").append(amount);
        if (!note.isEmpty()) cmd.append(" ").append(note);

        assert client != null && client.player != null;
        client.player.networkHandler.sendCommand(cmd.toString());

        // Action bar bildirim
        client.player.sendMessage(
                Text.literal("§a" + selectedPlayer + " adlı oyuncuya §e" + amount + " coin §agönderildi!"),
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
