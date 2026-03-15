package com.cogtrade.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Sol sayfa navigasyon öğeleri.
 *
 * <p>Her giriş, bir {@link CogTradeMainTab}'a bağlıdır.
 * {@link CogTradeMainTab#LANDS} ve {@link CogTradeMainTab#TELEPORT} için
 * alt öğe yoktur; {@link #getSubTabsFor} bu durumda boş liste döner.</p>
 */
@Environment(EnvType.CLIENT)
public enum CogTradeSubTab {

    // ── MARKET (STORE) ───────────────────────────────────────────────────
    PAZAR              (CogTradeMainTab.STORE,   "Pazar"),
    IHALELER           (CogTradeMainTab.STORE,   "İhaleler"),
    ILANLARIM          (CogTradeMainTab.STORE,   "İlanlarım"),
    TAKASLAR           (CogTradeMainTab.STORE,   "Takaslar"),
    ODEME_YAP          (CogTradeMainTab.STORE,   "Ödeme Yap"),

    // ── MAĞAZA (SHOP) ────────────────────────────────────────────────────
    BUYU_MARKETI       (CogTradeMainTab.SHOP,    "Büyü Marketi"),
    BUYU_PAKETLERI     (CogTradeMainTab.SHOP,    "Büyü Paketleri"),
    MESLEK_PAKETLERI   (CogTradeMainTab.SHOP,    "Meslek Paketleri"),
    KONTRATLAR         (CogTradeMainTab.SHOP,    "Kontratlar"),

    // ── PROFİL (PROFILE) ─────────────────────────────────────────────────
    PROFILIM           (CogTradeMainTab.PROFILE, "Profilim"),
    ISTATISTIKLERIM    (CogTradeMainTab.PROFILE, "İstatistiklerim"),
    BAKIYE             (CogTradeMainTab.PROFILE, "Bakiye");

    // LANDS  → alt öğe yok (içerik doğrudan sağ sayfada)
    // TELEPORT → alt öğe yok (içerik doğrudan sağ sayfada)

    // ─────────────────────────────────────────────────────────────────────

    public final CogTradeMainTab mainTab;
    /** Sol sayfada gösterilecek etiket. */
    public final String label;

    CogTradeSubTab(CogTradeMainTab mainTab, String label) {
        this.mainTab = mainTab;
        this.label   = label;
    }

    /**
     * {@code main} tabına ait tüm alt öğeleri, tanımlama sırasına göre döner.
     * Alt öğesi olmayan (LANDS, TELEPORT) için boş liste döner.
     */
    public static List<CogTradeSubTab> getSubTabsFor(CogTradeMainTab main) {
        List<CogTradeSubTab> result = new ArrayList<>();
        for (CogTradeSubTab sub : values()) {
            if (sub.mainTab == main) result.add(sub);
        }
        return result;
    }

    /**
     * {@code main} tabının ilk alt öğesini, yoksa {@code null} döner.
     * Ana tab değiştiğinde varsayılan alt sekmeyi seçmek için kullanılır.
     */
    public static CogTradeSubTab firstFor(CogTradeMainTab main) {
        for (CogTradeSubTab sub : values()) {
            if (sub.mainTab == main) return sub;
        }
        return null;
    }
}
