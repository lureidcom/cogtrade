package com.cogtrade.client.gui;

import com.cogtrade.CogTrade;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;

/**
 * The five main navigation tabs of the CogTrade book GUI.
 *
 * <p>All coordinates are in <em>book-native space</em> (1623 × 1080), meaning
 * they are relative to the top-left corner of {@code book.png} as exported
 * from Figma.  The renderer multiplies every value by a uniform scale factor
 * to obtain actual screen-space pixels.</p>
 *
 * <p>Coordinate derivation (Figma absolute → book-relative):
 * book origin in Figma is at x = 148, y = 0, so
 * {@code book_x = figma_x − 148},  {@code book_y = figma_y − 0}.</p>
 *
 * <pre>
 *  Tab       figmaX  figmaY  bookX  bookY   tabW  tabH
 *  STORE       402     64     254    64      100   100
 *  SHOP        523     64     375    64      100   100
 *  LANDS       644     64     496    64      100   100
 *  PROFILE    1296     64    1148    64      100   100
 *  TELEPORT   1417     64    1269    64      100   100
 *
 *  Icon         figmaX  figmaY  bookX  bookY   iconW  iconH
 *  icon-store    428     83      280    83       50     64
 *  icon-shop     547     83      399    83       53     60
 *  icon-lands    673     85      525    85       43     59
 *  icon-profile 1319     88     1171    88       55     54
 *  icon-teleport1441     88     1293    88       54     54
 * </pre>
 */
@Environment(EnvType.CLIENT)
public enum CogTradeMainTab {

    //              tabX   tabY  tabW  tabH   iconFile           iconX  iconY  iconW  iconH   displayName
    STORE   (  254,  64, 100, 100,  "icon-store",      280,  83,  50,  64,  "Market"),
    SHOP    (  375,  64, 100, 100,  "icon-shop",       399,  83,  53,  60,  "Mağaza"),
    LANDS   (  496,  64, 100, 100,  "icon-lands",      525,  85,  43,  59,  "Chunk Yönetimi"),
    PROFILE ( 1148,  64, 100, 100,  "icon-profile",   1171,  88,  55,  54,  "Profil"),
    TELEPORT( 1269,  64, 100, 100,  "icon-teleport",  1293,  88,  54,  54,  "Teleport");

    // ── Tab background bounds (book-native space) ──────────────────────────
    /** Left edge of the tab background, in book-native pixels. */
    public final int tabX;
    /** Top edge of the tab background, in book-native pixels. */
    public final int tabY;
    /** Width of the tab background, in book-native pixels (= PNG native width). */
    public final int tabW;
    /** Height of the tab background, in book-native pixels (= PNG native height). */
    public final int tabH;

    // ── Icon texture + bounds (book-native space) ──────────────────────────
    /** Resolved {@link Identifier} for this tab's icon PNG. */
    public final Identifier iconTexture;
    /** Left edge of the icon, in book-native pixels. */
    public final int iconX;
    /** Top edge of the icon, in book-native pixels. */
    public final int iconY;
    /** Width of the icon PNG (native), in book-native pixels. */
    public final int iconW;
    /** Height of the icon PNG (native), in book-native pixels. */
    public final int iconH;

    // ── Display ────────────────────────────────────────────────────────────
    /** Sol sayfa başlığı olarak kullanılan Türkçe görünen isim. */
    public final String displayName;

    // ──────────────────────────────────────────────────────────────────────

    CogTradeMainTab(
            int tabX, int tabY, int tabW, int tabH,
            String iconFile,
            int iconX, int iconY, int iconW, int iconH,
            String displayName
    ) {
        this.tabX = tabX;
        this.tabY = tabY;
        this.tabW = tabW;
        this.tabH = tabH;

        this.iconTexture = new Identifier(CogTrade.MOD_ID,
                "textures/gui/" + iconFile + ".png");
        this.iconX = iconX;
        this.iconY = iconY;
        this.iconW = iconW;
        this.iconH = iconH;

        this.displayName = displayName;
    }
}
