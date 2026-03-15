package com.cogtrade.client.gui;

import com.cogtrade.CogTrade;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

/**
 * CogTrade ana kitap GUI ekranı.
 *
 * <h3>Navigasyon akışı</h3>
 * <pre>
 *  ┌─ Üst sekme çubuğu ─────────────────────────────────────────┐
 *  │  [tab1▣] [tab2] [tab3]              [tab4] [tab5]          │
 *  ├─ Sol sayfa ──────────────┬─ Sağ sayfa ─────────────────────┤
 *  │                          │                                  │
 *  │  MENÜ MODU               │  (boş veya dekoratif)           │
 *  │  ───────                 │                                  │
 *  │     PAZAR        ← büyük │                                  │
 *  │     İHALELER     ← bold  │                                  │
 *  │     İLANLARIM    ← metin │                                  │
 *  │     TAKASLAR             │                                  │
 *  │     ÖDEME YAP            │                                  │
 *  │                          │                                  │
 *  ├──────────────────────────┤──────────────────────────────────┤
 *  │  İÇERİK MODU             │                                  │
 *  │  ─────────               │  (seçilen sayfanın içeriği)     │
 *  │  ← Geri                  │                                  │
 *  │  Pazar                   │                                  │
 *  └──────────────────────────┴──────────────────────────────────┘
 * </pre>
 *
 * <p>LANDS ve TELEPORT'un alt öğesi yoktur; bu sekmeler seçilince
 * direkt içerik moduna geçilir (menü adımı atlanır).</p>
 */
@Environment(EnvType.CLIENT)
public class CogTradeBookScreen extends Screen {

    // ── Textures ──────────────────────────────────────────────────────────

    private static final Identifier BOOK_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/book.png");
    private static final Identifier TAB_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/tab.png");
    private static final Identifier TAB_ACTIVE_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/tab-active.png");

    // ── Renkler (0xRRGGBB) ────────────────────────────────────────────────

    /** Büyük menüde normal öğe rengi: koyu kahve (krem sayfada okunaklı). */
    private static final int COLOR_MENU_NORMAL = 0x2C1A08;
    /** Büyük menüde fare üzerindeyken: amber/altın. */
    private static final int COLOR_MENU_HOVER  = 0x9B6200;
    /** Geri butonu normal. */
    private static final int COLOR_BACK        = 0x7A5B2A;
    /** Geri butonu hover. */
    private static final int COLOR_BACK_HOVER  = 0x2C1A08;
    /** Geçerli sayfa adı (geri butonunun altındaki küçük başlık). */
    private static final int COLOR_SUBTITLE    = 0x5A3F10;

    // ── Metin ölçekleri ───────────────────────────────────────────────────

    /** Büyük menü öğeleri için metin büyütme katsayısı. */
    private static final float MENU_SCALE = 2.5f;
    /** Öğeler arası boşluk katsayısı (font yüksekliğinin katı). */
    private static final float MENU_LINE_RATIO = 1.9f;
    /** Geri butonu metin katsayısı. */
    private static final float BACK_SCALE = 1.4f;

    // ── Durum (State) ─────────────────────────────────────────────────────

    /** Şu anda aktif olan ana sekme. */
    private CogTradeMainTab activeTab    = CogTradeMainTab.STORE;
    /**
     * Seçili alt sayfa. {@code null} iken menü gösterilir;
     * {@code null} olmayan değerde ilgili içerik sayfası gösterilir.
     */
    private CogTradeSubTab  activeSubTab = null;
    /**
     * {@code true} → içerik modundayız (alt sayfa açık veya alt öğesi
     * olmayan sekme seçili).
     * {@code false} → menü modundayız (kullanıcı alt öğe seçmedi).
     */
    private boolean inSubPage = false;

    // ── Layout (init'te hesaplanır) ────────────────────────────────────────

    private int    guiX, guiY;
    private double scale;

    // ──────────────────────────────────────────────────────────────────────

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
        this.inSubPage = CogTradeSubTab.getSubTabsFor(activeTab).isEmpty();
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        // 1. Kitap arkaplanı
        draw(ctx, BOOK_TEX, 0, 0, CogTradeGuiLayout.BOOK_W, CogTradeGuiLayout.BOOK_H);

        // 2. Üst sekme çubuğu (arkaplan + ikon)
        for (CogTradeMainTab tab : CogTradeMainTab.values()) {
            draw(ctx,
                    tab == activeTab ? TAB_ACTIVE_TEX : TAB_TEX,
                    tab.tabX, tab.tabY, tab.tabW, tab.tabH);
            draw(ctx, tab.iconTexture,
                    tab.iconX, tab.iconY, tab.iconW, tab.iconH);
        }

        // 3. Sol sayfa içeriği
        if (inSubPage) {
            renderBackButton(ctx, mouseX, mouseY);
            // TODO: ilgili içerik sayfasını sağ sayfaya render et
        } else {
            renderBigMenu(ctx, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ── Büyük menü ────────────────────────────────────────────────────────

    /**
     * Sol sayfa safe zone'una, alt öğeleri dikey+yatay ortalanmış,
     * büyük ve kalın görünümde çizer.
     * Fare üzerindeyken öğe rengi değişir.
     */
    private void renderBigMenu(DrawContext ctx, int mouseX, int mouseY) {
        List<CogTradeSubTab> subs = CogTradeSubTab.getSubTabsFor(activeTab);
        if (subs.isEmpty()) return; // LANDS / TELEPORT — bu moda giremez

        MenuGeometry geo = computeMenuGeometry(subs);

        for (int i = 0; i < subs.size(); i++) {
            String text  = subs.get(i).label.toUpperCase(Locale.forLanguageTag("tr"));
            int    textW = (int)(this.textRenderer.getWidth(text) * MENU_SCALE);
            int    itemX = Math.max(geo.szX, geo.szX + (geo.szW - textW) / 2);
            int    itemY = geo.startY + i * geo.lineH;

            boolean hovered = mouseX >= geo.szX && mouseX < geo.szX + geo.szW
                           && mouseY >= itemY    && mouseY < itemY + geo.lineH;

            drawText(ctx, text, itemX, itemY,
                    hovered ? COLOR_MENU_HOVER : COLOR_MENU_NORMAL,
                    MENU_SCALE);
        }
    }

    // ── Geri butonu ───────────────────────────────────────────────────────

    /**
     * Sol sayfa safe zone'unun sol üstüne "← Geri" butonunu,
     * altına da aktif alt sayfanın adını çizer.
     */
    private void renderBackButton(DrawContext ctx, int mouseX, int mouseY) {
        int btnX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X) + 10;
        int btnY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + 12;

        boolean hovered = isBackHovered(mouseX, mouseY);
        drawText(ctx, "\u2190 Geri", btnX, btnY,
                hovered ? COLOR_BACK_HOVER : COLOR_BACK,
                BACK_SCALE);

        // Aktif alt sayfa adı (küçük başlık)
        if (activeSubTab != null) {
            int scaledH = (int)(this.textRenderer.fontHeight * BACK_SCALE);
            drawText(ctx, activeSubTab.label,
                    btnX, btnY + scaledH + 5,
                    COLOR_SUBTITLE, BACK_SCALE);
        }
    }

    // ── Input ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // 1. Üst sekme
        for (CogTradeMainTab tab : CogTradeMainTab.values()) {
            if (tabHit(tab, mouseX, mouseY)) {
                setActiveTab(tab);
                return true;
            }
        }

        // 2. İçerik modundayken geri butonu
        if (inSubPage && isBackHovered(mouseX, mouseY)) {
            goBack();
            return true;
        }

        // 3. Menü modundayken büyük menü öğesi
        if (!inSubPage) {
            List<CogTradeSubTab> subs = CogTradeSubTab.getSubTabsFor(activeTab);
            if (!subs.isEmpty()) {
                MenuGeometry geo = computeMenuGeometry(subs);
                for (int i = 0; i < subs.size(); i++) {
                    int itemY = geo.startY + i * geo.lineH;
                    if (mouseX >= geo.szX && mouseX < geo.szX + geo.szW
                            && mouseY >= itemY && mouseY < itemY + geo.lineH) {
                        setSubPage(subs.get(i));
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Durum yönetimi ────────────────────────────────────────────────────

    /** Ana sekmeyi değiştirir; alt öğesi varsa menüye, yoksa içeriğe geçer. */
    private void setActiveTab(CogTradeMainTab tab) {
        this.activeTab    = tab;
        this.activeSubTab = null;
        this.inSubPage    = CogTradeSubTab.getSubTabsFor(tab).isEmpty();
    }

    /** Verilen alt sayfayı açar. */
    private void setSubPage(CogTradeSubTab sub) {
        this.activeSubTab = sub;
        this.inSubPage    = true;
    }

    /** Alt sayfadan geri dönüp menüyü yeniden gösterir. */
    private void goBack() {
        this.activeSubTab = null;
        this.inSubPage    = false;
    }

    // ── Geometri hesabı ───────────────────────────────────────────────────

    /**
     * Büyük menü öğelerinin ekran koordinatlarını hesaplar.
     * Hem render hem de hit-detection bu değerleri kullanır
     * (kod tekrarı önlenir).
     */
    private MenuGeometry computeMenuGeometry(List<CogTradeSubTab> subs) {
        int szX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int szY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
        int szW = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        int szH = sc(CogTradeGuiLayout.LEFT_PAGE_H);

        // Tek satır yüksekliği: font yüksekliği × ölçek × boşluk katsayısı
        int scaledFontH = (int)(this.textRenderer.fontHeight * MENU_SCALE);
        int lineH       = (int)(scaledFontH * MENU_LINE_RATIO);

        // Tüm liste yüksekliği: (n-1) aralık + son öğe font yüksekliği
        int totalH = (subs.size() - 1) * lineH + scaledFontH;

        // Dikey ortalama
        int startY = szY + Math.max(0, (szH - totalH) / 2);

        return new MenuGeometry(szX, szY, szW, szH, lineH, startY);
    }

    /** Büyük menünün geometrisini taşıyan değer nesnesi. */
    private static final class MenuGeometry {
        final int szX, szY, szW, szH;  // safe zone ekran koordinatları
        final int lineH;               // satır yüksekliği (px)
        final int startY;              // ilk öğenin Y koordinatı

        MenuGeometry(int szX, int szY, int szW, int szH, int lineH, int startY) {
            this.szX    = szX;
            this.szY    = szY;
            this.szW    = szW;
            this.szH    = szH;
            this.lineH  = lineH;
            this.startY = startY;
        }
    }

    // ── Yardımcılar ────────────────────────────────────────────────────────

    /** Farenin geri butonu üzerinde olup olmadığını kontrol eder. */
    private boolean isBackHovered(double mouseX, double mouseY) {
        int btnX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X) + 10;
        int btnY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + 12;
        int btnW = (int)(this.textRenderer.getWidth("\u2190 Geri") * BACK_SCALE) + 8;
        int btnH = (int)(this.textRenderer.fontHeight * BACK_SCALE) + 4;
        return mouseX >= btnX && mouseX < btnX + btnW
            && mouseY >= btnY && mouseY < btnY + btnH;
    }

    /** Book-native değeri ekran piksellerine çevirir. */
    private int sc(int bookValue) {
        return (int) Math.round(bookValue * scale);
    }

    /**
     * Bir texture'ı book-native koordinatlarda çizer.
     * NineSliceRenderer UV-tabanlı drawFullTexture kullanır
     * (matrix scaling yok → köşe artefaktı yok).
     */
    private void draw(DrawContext ctx, Identifier tex,
                      int bookX, int bookY,
                      int nativeW, int nativeH) {
        NineSliceRenderer.drawFullTexture(
                ctx, tex,
                guiX + sc(bookX),
                guiY + sc(bookY),
                sc(nativeW),
                sc(nativeH),
                nativeW, nativeH
        );
    }

    /**
     * Metni verilen ekran konumuna ölçeklenmiş olarak çizer.
     *
     * @param screenX  ekran X (piksel)
     * @param screenY  ekran Y (piksel)
     * @param color    0xRRGGBB renk
     * @param scale    büyütme katsayısı (1.0 = Minecraft varsayılan font)
     */
    private void drawText(DrawContext ctx, String text,
                          int screenX, int screenY,
                          int color, float scale) {
        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(screenX, screenY, 0);
        m.scale(scale, scale, 1f);
        ctx.drawText(this.textRenderer, Text.literal(text), 0, 0, color, false);
        m.pop();
    }

    /** Farenin üst sekme tıklama alanında olup olmadığını kontrol eder. */
    private boolean tabHit(CogTradeMainTab tab, double mouseX, double mouseY) {
        int x = guiX + sc(tab.tabX);
        int y = guiY + sc(tab.tabY);
        int w = sc(tab.tabW);
        int h = sc(tab.tabH);
        return mouseX >= x && mouseX < x + w
            && mouseY >= y && mouseY < y + h;
    }

    // ── Screen davranışı ───────────────────────────────────────────────────

    @Override
    public boolean shouldPause() {
        return false;
    }
}
