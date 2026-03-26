package com.cogtrade.client.gui;

import com.cogtrade.CogTrade;
import com.cogtrade.client.ClientEconomyData;
import com.cogtrade.client.ClientMarketData;
import com.cogtrade.client.ClientMarketItem;
import com.cogtrade.client.ClientTransactionData;
import com.cogtrade.client.TransactionEntry;
import com.cogtrade.market.EffectMarketEntry;
import com.cogtrade.market.EffectMarketRegistry;
import com.cogtrade.network.AllShopListingsPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
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
import java.util.Objects;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class CogTradeBookScreen extends Screen {

    // ── Textures ──────────────────────────────────────────────────────────

    private static final Identifier BOOK_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/book.png");
    private static final Identifier BOOK_MEDIUM_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/book-medium.png");
    private static final Identifier OFFERS_TITLE_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/offers_title.png");
    private static final Identifier ITEM_FRAME_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/item-frame.png");
    private static final Identifier TAB_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/tab.png");
    private static final Identifier TAB_ACTIVE_TEX =
            new Identifier(CogTrade.MOD_ID, "textures/gui/tab-active.png");

    // ── Pazar — kategori ikonu ve Türkçe isim tabloları ───────────────────

    private static final java.util.Map<String, Item> CAT_REP = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, String> CAT_TR = new java.util.LinkedHashMap<>();
    static {
        CAT_REP.put("Building Blocks",   Items.STONE);
        CAT_REP.put("Natural Blocks",    Items.OAK_LOG);
        CAT_REP.put("Functional Blocks", Items.CRAFTING_TABLE);
        CAT_REP.put("Redstone Blocks",   Items.REDSTONE);
        CAT_REP.put("Combat",            Items.DIAMOND_SWORD);
        CAT_REP.put("Tools",             Items.IRON_PICKAXE);
        CAT_REP.put("Food & Drinks",     Items.APPLE);
        CAT_REP.put("Ingredients",       Items.WHEAT);
        CAT_REP.put("Spawn Eggs",        Items.PIG_SPAWN_EGG);
        CAT_REP.put("Enchantments",      Items.ENCHANTED_BOOK);
        CAT_REP.put("Potions",           Items.POTION);
        CAT_REP.put("Misc",              Items.CHEST);
        CAT_TR.put("Building Blocks",    "Yapı Blokları");
        CAT_TR.put("Natural Blocks",     "Doğal Bloklar");
        CAT_TR.put("Functional Blocks",  "İşlevsel Bloklar");
        CAT_TR.put("Redstone Blocks",    "Kızıltaş Blokları");
        CAT_TR.put("Combat",             "Savaş");
        CAT_TR.put("Tools",              "Aletler");
        CAT_TR.put("Food & Drinks",      "Yiyecek & İçecek");
        CAT_TR.put("Ingredients",        "Malzemeler");
        CAT_TR.put("Spawn Eggs",         "Doğma Yumurtaları");
        CAT_TR.put("Enchantments",       "Büyüler");
        CAT_TR.put("Potions",            "İksirler");
        CAT_TR.put("Misc",               "Diğer");
    }

    // ── Pazar — hiyerarşik kategori sistemi ───────────────────────────────

    private static final java.util.List<PazarSection> PAZAR_SECTIONS;
    static {
        PAZAR_SECTIONS = new java.util.ArrayList<>();

        // ── BLOKLAR ───────────────────────────────────────────────────────
        PAZAR_SECTIONS.add(new PazarSection("BLOKLAR", java.util.List.of(
                new PazarSubCat("Odunlar",   Items.OAK_LOG,          "", "log,plank,wood,bark,sapling,stripped"),
                new PazarSubCat("Taşlar",    Items.STONE,            "", "stone,cobble,granite,diorite,andesite,deepslate,tuff,calcite,basalt,blackstone"),
                new PazarSubCat("Toprak",    Items.DIRT,             "", "dirt,sand,gravel,clay,mud,podzol,mycelium,soul_soil,coarse"),
                new PazarSubCat("Cam&Işık",  Items.GLASS,            "", "glass,lantern,torch,glowstone,shroomlight,sea_lantern,candle"),
                new PazarSubCat("Bitkiler",  Items.POPPY,            "", "leaf,leaves,sapling,fern,vine,bamboo,cactus,sugar_cane,mushroom,azalea,moss,dripleaf,lily,poppy,dandelion,orchid,allium,cornflower,tulip,sunflower,flower,spore"),
                new PazarSubCat("Yapı",      Items.BRICKS,           "Building Blocks", ""),
                new PazarSubCat("İşlevsel",  Items.CRAFTING_TABLE,   "Functional Blocks", ""),
                new PazarSubCat("Kızıltaş",  Items.REDSTONE,         "Redstone Blocks", "")
        )));

        // ── SAVAŞ & ARAÇLAR ────────────────────────────────────────────────
        PAZAR_SECTIONS.add(new PazarSection("SAVAŞ & ARAÇLAR", java.util.List.of(
                new PazarSubCat("Kılıçlar",  Items.DIAMOND_SWORD,    "", "sword"),
                new PazarSubCat("Okçuluk",   Items.BOW,              "", "bow,arrow,crossbow,trident"),
                new PazarSubCat("Zırhlar",   Items.IRON_CHESTPLATE,  "", "helmet,chestplate,leggings,boots,shield"),
                new PazarSubCat("Aletler",   Items.IRON_PICKAXE,     "Tools", "")
        )));

        // ── YİYECEK & MALZEME ──────────────────────────────────────────────
        PAZAR_SECTIONS.add(new PazarSection("YİYECEK & MALZEME", java.util.List.of(
                new PazarSubCat("Yiyecek",   Items.APPLE,            "Food & Drinks", ""),
                new PazarSubCat("Malzeme",   Items.WHEAT,            "Ingredients", "")
        )));

        // ── YARATIKLAR ────────────────────────────────────────────────────
        PAZAR_SECTIONS.add(new PazarSection("YARATIKLAR", java.util.List.of(
                new PazarSubCat("Dost Mob",  Items.CHICKEN_SPAWN_EGG, "Spawn Eggs",
                        "pig_spawn,cow_spawn,sheep_spawn,chicken_spawn,horse_spawn,cat_spawn,wolf_spawn,bee_spawn,fox_spawn,axolotl_spawn,frog_spawn,allay_spawn,turtle_spawn,squid_spawn,panda_spawn,rabbit_spawn,ocelot_spawn,villager_spawn"),
                new PazarSubCat("Düşman",    Items.ZOMBIE_SPAWN_EGG, "Spawn Eggs",
                        "zombie_spawn,creeper_spawn,skeleton_spawn,spider_spawn,enderman_spawn,witch_spawn,phantom_spawn,drowned_spawn,pillager_spawn,blaze_spawn,ghast_spawn,magma_cube,slime_spawn,wither_spawn,guardian_spawn,shulker_spawn,vindicator_spawn,hoglin_spawn,piglin_spawn,zombie_pig,cave_spider")
        )));

        // ── DİĞER ─────────────────────────────────────────────────────────
        PAZAR_SECTIONS.add(new PazarSection("DİĞER", java.util.List.of(
                new PazarSubCat("Büyüler",   Items.ENCHANTED_BOOK,   "Enchantments", ""),
                new PazarSubCat("İksirler",  Items.POTION,           "Potions", ""),
                new PazarSubCat("Çeşitli",   Items.CHEST,            "Misc", ""),
                new PazarSubCat("Tüm İtem",  Items.COMPASS,          "", "")
        )));
    }

    /** Item popup için iç veri tipi. */
    private record PazarListing(
            boolean isServerMarket,
            String  sellerName,
            String  sellerUuid,
            double  price,
            int     stock) {}

    /** Sol sayfa alt kategori. */
    private record PazarSubCat(String label, Item icon, String catFilter, String idFilter) {}

    /** Sol sayfa bölüm başlığı + alt kategoriler. */
    private record PazarSection(String title, java.util.List<PazarSubCat> subs) {}

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

    // ── Takas teklifleri popup state ──────────────────────────────────────

    private boolean offersPopupOpen    = false;
    private int     offersScrollOffset = 0;

    // ── Takas sayfası state ───────────────────────────────────────────────

    private String       tradeSelectedPlayer     = null;
    private int          tradePlayerScrollOffset = 0;
    private List<String> tradeAllPlayers         = new ArrayList<>();
    private List<String> tradeFilteredPlayers    = new ArrayList<>();
    private String       tradeSearchQuery        = "";
    private int          tradeSearchCursor       = 0;
    private int          tradeSearchViewStart    = 0;
    private boolean      tradeSearchActive       = false;

    // ── Bakiye sayfası state ──────────────────────────────────────────────
    private int              txScrollOffset = 0;
    private TransactionEntry selectedTx     = null;

    // ── Büyü Marketi state ────────────────────────────────────────────────
    private int              effectScrollOffset  = 0;
    private EffectMarketEntry selectedEffect      = null;
    private int              selectedAmplifier    = 0;
    private int              selectedDurationIdx  = 1; // varsayılan: 5 dk

    // ── Pazar sayfası state ───────────────────────────────────────────────
    private String                pazarSearchQuery     = "";
    private int                   pazarSearchCursor    = 0;
    private int                   pazarSearchViewStart = 0;
    private boolean               pazarSearchActive    = false;
    private PazarSubCat           pazarSelectedSubCat  = null;    // null = Tümü
    private int                   pazarLeftScroll      = 0;
    private int                   pazarItemScroll      = 0;
    private List<ClientMarketItem> pazarFiltered       = new ArrayList<>();
    // Pazar item popup
    private ClientMarketItem      pazarPopupItem       = null;
    private boolean               pazarPopupOpen       = false;
    private int                   pazarListingScroll   = 0;
    private int                   pazarSelectedListing = -1;
    // Satın alma popup state (marketOnly modunda)
    private ClientMarketItem  buyPopupItem      = null;
    private boolean           buyPopupOpen      = false;
    private int               buyPopupAmount    = 1;
    private String            buyAmountBuffer   = "1";
    private boolean           buyAmountEditing  = false;

    // ── Layout ────────────────────────────────────────────────────────────

    private int    guiX, guiY;
    private double scale;

    /** Sadece PAZAR sayfasını göster, sekme/menü yok (market bloğundan açılırken). */
    private final boolean marketOnly;

    // ─────────────────────────────────────────────────────────────────────

    public CogTradeBookScreen() {
        this(false);
    }

    public CogTradeBookScreen(boolean marketOnly) {
        super(Text.translatable("gui.cogtrade.book"));
        this.marketOnly = marketOnly;
        if (marketOnly) {
            this.activeSubTab = CogTradeSubTab.PAZAR;
            this.inSubPage    = true;
        }
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

        // Pazar sayfası — market verisi yoksa sunucudan iste
        if (inSubPage && activeSubTab == CogTradeSubTab.PAZAR) {
            pazarSearchQuery  = ""; pazarSearchCursor = 0; pazarSearchViewStart = 0;
            pazarSearchActive    = false;
            pazarSelectedSubCat  = null;
            pazarLeftScroll      = 0;
            pazarItemScroll   = 0;
            pazarPopupItem    = null;
            pazarPopupOpen    = false;
            pazarListingScroll   = 0;
            pazarSelectedListing = -1;
            if (!ClientMarketData.loaded) {
                // /market komutu → sunucu OpenMarketPacket gönderir
                assert client != null && client.player != null;
                client.player.networkHandler.sendChatCommand("market");
                // Oyuncu mağaza ilanlarını da iste
                ClientPlayNetworking.send(
                        com.cogtrade.network.RequestAllShopListingsPacket.ID,
                        PacketByteBufs.empty());
            }
            updatePazarFiltered();
        }

        // Takas sayfası — oyuncu listesini yükle
        if (inSubPage && activeSubTab == CogTradeSubTab.TAKASLAR) {
            tradeSearchQuery = ""; tradeSearchCursor = 0; tradeSearchViewStart = 0;
            tradeSearchActive = false;
            tradeSelectedPlayer = null;
            tradePlayerScrollOffset = 0;
            tradeAllPlayers      = getOnlinePlayers();
            tradeFilteredPlayers = new ArrayList<>(tradeAllPlayers);
        }
    }

    // ── Rendering ──────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);

        draw(ctx, BOOK_TEX, 0, 0, CogTradeGuiLayout.BOOK_W, CogTradeGuiLayout.BOOK_H);

        if (!marketOnly) {
            for (CogTradeMainTab tab : CogTradeMainTab.values()) {
                draw(ctx, tab == activeTab ? TAB_ACTIVE_TEX : TAB_TEX,
                        tab.tabX, tab.tabY, tab.tabW, tab.tabH);
                draw(ctx, tab.iconTexture,
                        tab.iconX, tab.iconY, tab.iconW, tab.iconH);
            }
        }

        if (inSubPage) {
            if (!marketOnly) renderBackButton(ctx, mouseX, mouseY);
            if (activeSubTab == CogTradeSubTab.PAZAR) {
                if (!pazarPopupOpen && !buyPopupOpen) renderPazarLeft(ctx, mouseX, mouseY);
                if (!pazarPopupOpen && !buyPopupOpen) renderPazarRight(ctx, mouseX, mouseY);
            } else if (activeSubTab == CogTradeSubTab.ODEME_YAP) {
                renderPaymentSearchField(ctx, mouseX, mouseY);
                renderPlayerList(ctx, mouseX, mouseY);
                renderPaymentRight(ctx, mouseX, mouseY);
            } else if (activeSubTab == CogTradeSubTab.TAKASLAR) {
                renderTradeSearchField(ctx, mouseX, mouseY);
                renderTradePlayerList(ctx, mouseX, mouseY);
                renderTradeRight(ctx, mouseX, mouseY);
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

        // Takas teklifleri bildirim butonu (yalnızca TAKASLAR sub-sayfasında)
        if (inSubPage && activeSubTab == CogTradeSubTab.TAKASLAR) {
            renderTradeOffersButton(ctx, mouseX, mouseY);
        }

        super.render(ctx, mouseX, mouseY, delta); // widget'ları çizer

        // Popup'lar en üstte çizilmeli (her şeyin üzerinde)
        if (pazarPopupOpen && activeSubTab == CogTradeSubTab.PAZAR) {
            renderPazarItemPopup(ctx, mouseX, mouseY);
        }
        if (buyPopupOpen && (activeSubTab == CogTradeSubTab.PAZAR || marketOnly)) {
            renderBuyPopup(ctx, mouseX, mouseY);
        }
        if (offersPopupOpen) {
            renderOffersPopup(ctx, mouseX, mouseY);
        }
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

            // Skin head (10×10) + player name (Chat Heads style)
            if (selected) ctx.drawText(textRenderer, Text.literal("▶"), lpX + 1, y + 3, COLOR_LABEL, false);
            drawPlayerHead(ctx, name, lpX + (selected ? 11 : 3), y + 2, 10);
            ctx.drawText(textRenderer,
                    Text.literal(name),
                    lpX + (selected ? 23 : 15), y + 3,
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
        if (hasPlayer) {
            // Skin head + check mark + player name
            int headY = boxY + (boxH - 10) / 2;
            ctx.drawText(textRenderer, Text.literal("\u2714"), rpX + 5, boxY + (boxH - textRenderer.fontHeight) / 2, COLOR_LABEL, false);
            drawPlayerHead(ctx, selectedPlayer, rpX + 5 + textRenderer.getWidth("\u2714") + 2, headY, 10);
            ctx.drawText(textRenderer, Text.literal(selectedPlayer),
                    rpX + 5 + textRenderer.getWidth("\u2714") + 14, boxY + (boxH - textRenderer.fontHeight) / 2,
                    COLOR_LABEL, false);
        } else {
            ctx.drawText(textRenderer, Text.literal("\u2190 Sol sayfadan oyuncu se\u00e7in"),
                    rpX + 5, boxY + (boxH - textRenderer.fontHeight) / 2, COLOR_DIM, false);
        }

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

    // ── Takas sayfası — sol sayfa arama alanı ────────────────────────────

    private void renderTradeSearchField(DrawContext ctx, int mouseX, int mouseY) {
        int lpX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int lpY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
        int lpW = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        renderTextField(ctx, tradeSearchQuery, tradeSearchCursor, tradeSearchViewStart,
                "Oyuncu ara...", lpX, lpY + BACK_AREA_H, lpW, 18, tradeSearchActive);
    }

    // ── Takas sayfası — sol sayfa oyuncu listesi ──────────────────────────

    private void renderTradePlayerList(DrawContext ctx, int mouseX, int mouseY) {
        int lpX    = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int listY  = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + BACK_AREA_H + 24;
        int lpW    = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        int lpH    = sc(CogTradeGuiLayout.LEFT_PAGE_H) - BACK_AREA_H - 24;
        int itemH  = 14;
        int visible = lpH / itemH;

        int maxScroll = Math.max(0, tradeFilteredPlayers.size() - visible);
        tradePlayerScrollOffset = Math.min(tradePlayerScrollOffset, maxScroll);

        int y = listY;
        for (int i = tradePlayerScrollOffset; i < Math.min(tradeFilteredPlayers.size(), tradePlayerScrollOffset + visible); i++) {
            String name      = tradeFilteredPlayers.get(i);
            boolean selected = name.equals(tradeSelectedPlayer);
            boolean hovered  = !selected
                    && mouseX >= lpX && mouseX < lpX + lpW - 5
                    && mouseY >= y   && mouseY < y + itemH;

            if (selected) ctx.fill(lpX, y, lpX + lpW - 5, y + itemH, 0x44AA6600);
            else if (hovered) ctx.fill(lpX, y, lpX + lpW - 5, y + itemH, 0x22AA6600);

            // Chat Heads: skin face + player name
            if (selected) ctx.drawText(textRenderer, Text.literal("▶"), lpX + 1, y + 3, COLOR_LABEL, false);
            drawPlayerHead(ctx, name, lpX + (selected ? 11 : 3), y + 2, 10);
            ctx.drawText(textRenderer, Text.literal(name),
                    lpX + (selected ? 23 : 15), y + 3,
                    selected ? COLOR_LABEL : COLOR_DIM, false);
            y += itemH;
        }

        if (tradeFilteredPlayers.isEmpty()) {
            String msg = tradeAllPlayers.isEmpty() ? "Çevrimiçi oyuncu yok" : "Sonuç bulunamadı";
            ctx.drawText(textRenderer, Text.literal(msg), lpX + 6, listY + 8, COLOR_DIM, false);
        }

        // Kaydırma çubuğu
        if (tradeFilteredPlayers.size() > visible && visible > 0) {
            int sbX    = lpX + lpW - 4;
            int thumbH = Math.max(8, lpH * visible / tradeFilteredPlayers.size());
            int thumbY = maxScroll > 0
                    ? listY + (lpH - thumbH) * tradePlayerScrollOffset / maxScroll
                    : listY;
            ctx.fill(sbX, listY, sbX + 3, listY + lpH, 0x22000000);
            ctx.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xBB9B6200);
        }
    }

    // ── Takas sayfası — sağ sayfa (takas isteği gönderme) ────────────────

    private void renderTradeRight(DrawContext ctx, int mouseX, int mouseY) {
        int rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
        int rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
        int rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W);

        boolean hasPlayer = (tradeSelectedPlayer != null);

        // ─ Başlık ────────────────────────────────────────────────────────
        ctx.drawText(textRenderer, Text.literal("Doğrudan Takas"),
                rpX, rpY + 2, COLOR_LABEL, false);
        ctx.fill(rpX, rpY + 14, rpX + rpW, rpY + 15, 0x44000000);

        // ─ Seçili oyuncu kutusu ───────────────────────────────────────────
        int boxY = rpY + 20;
        int boxH = 22;
        ctx.fill(rpX, boxY, rpX + rpW, boxY + boxH, hasPlayer ? 0x55AA6600 : 0x22604000);
        int boxBc = hasPlayer ? 0xBBAA6600 : 0x66604000;
        ctx.fill(rpX,         boxY,          rpX + rpW, boxY + 1,      boxBc);
        ctx.fill(rpX,         boxY + boxH-1, rpX + rpW, boxY + boxH,   boxBc);
        ctx.fill(rpX,         boxY,          rpX + 1,   boxY + boxH,   boxBc);
        ctx.fill(rpX + rpW-1, boxY,          rpX + rpW, boxY + boxH,   boxBc);

        if (hasPlayer) {
            int headY = boxY + (boxH - 10) / 2;
            drawPlayerHead(ctx, tradeSelectedPlayer, rpX + 5, headY, 10);
            ctx.drawText(textRenderer, Text.literal(tradeSelectedPlayer),
                    rpX + 17, boxY + (boxH - textRenderer.fontHeight) / 2,
                    COLOR_LABEL, false);
        } else {
            ctx.drawText(textRenderer, Text.literal("← Sol sayfadan oyuncu seçin"),
                    rpX + 5, boxY + (boxH - textRenderer.fontHeight) / 2, COLOR_DIM, false);
        }

        // ─ Ayırıcı ───────────────────────────────────────────────────────
        ctx.fill(rpX, rpY + 48, rpX + rpW, rpY + 49, 0x44000000);

        // ─ Bilgi metni ────────────────────────────────────────────────────
        ctx.drawText(textRenderer, Text.literal("Takas isteği gönderildiğinde"),
                rpX, rpY + 56, COLOR_DIM, false);
        ctx.drawText(textRenderer, Text.literal("oyuncu 30 saniye içinde"),
                rpX, rpY + 68, COLOR_DIM, false);
        ctx.drawText(textRenderer, Text.literal("kabul veya reddedebilir."),
                rpX, rpY + 80, COLOR_DIM, false);

        ctx.fill(rpX, rpY + 95, rpX + rpW, rpY + 96, 0x44000000);

        ctx.drawText(textRenderer, Text.literal("Takas ekranında:"),
                rpX, rpY + 103, COLOR_DIM, false);
        ctx.drawText(textRenderer, Text.literal("  • Eşya teklifleri (3×3 grid)"),
                rpX, rpY + 115, COLOR_DIM, false);
        ctx.drawText(textRenderer, Text.literal("  • Coin teklifleri"),
                rpX, rpY + 127, COLOR_DIM, false);
        ctx.drawText(textRenderer, Text.literal("  • İki taraflı onay"),
                rpX, rpY + 139, COLOR_DIM, false);

        // ─ Takas İsteği Gönder butonu ─────────────────────────────────────
        int sbW = 130, sbH = 24;
        int sbX = rpX + (rpW - sbW) / 2;
        int sbY = rpY + 160;
        boolean canSend = hasPlayer;
        boolean sbHov   = canSend && mouseX >= sbX && mouseX < sbX + sbW
                       && mouseY >= sbY && mouseY < sbY + sbH;
        int sbBg   = !canSend ? 0x44604000 : sbHov ? 0xFF604000 : 0xFF9B6200;
        int sbBord = !canSend ? 0x44AA6600 : 0xFFFFD080;
        ctx.fill(sbX, sbY, sbX + sbW, sbY + sbH, sbBg);
        ctx.fill(sbX,         sbY,          sbX + sbW, sbY + 1,      sbBord);
        ctx.fill(sbX,         sbY + sbH-1,  sbX + sbW, sbY + sbH,    sbBord);
        ctx.fill(sbX,         sbY,          sbX + 1,   sbY + sbH,    sbBord);
        ctx.fill(sbX + sbW-1, sbY,          sbX + sbW, sbY + sbH,    sbBord);
        String sbLbl = "Takas İsteği Gönder";
        ctx.drawText(textRenderer, Text.literal(sbLbl),
                sbX + (sbW - textRenderer.getWidth(sbLbl)) / 2,
                sbY + (sbH - textRenderer.fontHeight) / 2,
                canSend ? 0xFFFFFFFF : 0x88FFFFFF, false);
    }

    // ── Takas filtresi güncelle ────────────────────────────────────────────

    private void updateTradeFilteredPlayers() {
        String lq = tradeSearchQuery.toLowerCase(java.util.Locale.ROOT);
        tradeFilteredPlayers = tradeAllPlayers.stream()
                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).contains(lq))
                .collect(Collectors.toList());
        tradePlayerScrollOffset = 0;
        tradeSelectedPlayer = null;
    }

    // ── Chat Heads: oyuncu skin kafası çizici ──────────────────────────────

    /**
     * Draw a player's skin head (face + hat overlay, 8×8 region from 64×64 texture).
     * Uses Chat Heads style: face layer UV(8,8)→(16,16) + hat UV(40,8)→(48,16).
     * Falls back to a grey initial box if skin is not yet available.
     */
    private void drawPlayerHead(DrawContext ctx, String playerName, int x, int y, int size) {
        if (size <= 0 || client == null || client.getNetworkHandler() == null) return;
        for (net.minecraft.client.network.PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().getName().equalsIgnoreCase(playerName)) {
                Identifier skin = entry.getSkinTexture();
                // Base face layer:    UV region (8,8)→(16,16) on 64×64 skin
                ctx.drawTexture(skin, x, y, size, size, 8f, 8f, 8, 8, 64, 64);
                // Hat/overlay layer:  UV region (40,8)→(48,16) on 64×64 skin
                ctx.drawTexture(skin, x, y, size, size, 40f, 8f, 8, 8, 64, 64);
                return;
            }
        }
        // Fallback: grey box with first initial
        ctx.fill(x, y, x + size, y + size, 0xFF887766);
        if (!playerName.isEmpty() && size >= 8) {
            String init = playerName.substring(0, 1).toUpperCase();
            ctx.drawText(textRenderer, init,
                    x + (size - textRenderer.getWidth(init)) / 2,
                    y + (size - textRenderer.fontHeight) / 2,
                    0xFFFFFFFF, false);
        }
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
        // Buy popup miktar girişi
        if (buyPopupOpen) {
            if (buyAmountEditing && Character.isDigit(chr) && buyAmountBuffer.length() < 6) {
                String next = buyAmountBuffer + chr;
                try {
                    int parsed = Integer.parseInt(next);
                    if (parsed > 0) { buyAmountBuffer = next; buyPopupAmount = parsed; }
                } catch (NumberFormatException ignored) {}
            }
            return true;
        }
        // Pazar popup açıksa karakter girişini blokla
        if (pazarPopupOpen && inSubPage && activeSubTab == CogTradeSubTab.PAZAR) return true;
        // Pazar arama
        if (inSubPage && activeSubTab == CogTradeSubTab.PAZAR && pazarSearchActive) {
            if (pazarSearchQuery.length() < 48) {
                pazarSearchQuery = pazarSearchQuery.substring(0, pazarSearchCursor) + chr
                        + pazarSearchQuery.substring(pazarSearchCursor);
                pazarSearchCursor++;
                pazarSearchViewStart = adjustViewStart(pazarSearchQuery, pazarSearchCursor,
                        pazarSearchViewStart, sc(CogTradeGuiLayout.LEFT_PAGE_W) - 10);
                updatePazarFiltered();
            }
            return true;
        }
        if (inSubPage && activeSubTab == CogTradeSubTab.TAKASLAR && tradeSearchActive) {
            if (tradeSearchQuery.length() < 32) {
                tradeSearchQuery = tradeSearchQuery.substring(0, tradeSearchCursor) + chr + tradeSearchQuery.substring(tradeSearchCursor);
                tradeSearchCursor++;
                int innerW = sc(CogTradeGuiLayout.LEFT_PAGE_W) - 8;
                tradeSearchViewStart = adjustViewStart(tradeSearchQuery, tradeSearchCursor, tradeSearchViewStart, innerW);
                updateTradeFilteredPlayers();
            }
            return true;
        }
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
        // Buy popup klavye
        if (buyPopupOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (buyAmountEditing) { buyAmountEditing = false; }
                else { buyPopupOpen = false; buyPopupItem = null; }
                return true;
            }
            if (buyAmountEditing) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!buyAmountBuffer.isEmpty()) {
                        buyAmountBuffer = buyAmountBuffer.substring(0, buyAmountBuffer.length() - 1);
                        if (!buyAmountBuffer.isEmpty()) {
                            try { buyPopupAmount = Math.max(1, Integer.parseInt(buyAmountBuffer)); }
                            catch (NumberFormatException e) { buyPopupAmount = 1; }
                        } else { buyPopupAmount = 1; }
                    }
                } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    if (buyAmountBuffer.isEmpty()) buyAmountBuffer = "1";
                    buyAmountEditing = false;
                }
            }
            return true;
        }
        // Popup açıksa ESC kapatır, diğer tuşları blokla
        if (offersPopupOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                offersPopupOpen = false;
                offersScrollOffset = 0;
            }
            return true;
        }
        // Pazar popup ESC
        if (pazarPopupOpen && inSubPage && activeSubTab == CogTradeSubTab.PAZAR) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pazarPopupOpen = false; pazarPopupItem = null;
                pazarSelectedListing = -1; pazarListingScroll = 0;
            }
            return true;
        }
        // Pazar arama tuşları
        if (inSubPage && activeSubTab == CogTradeSubTab.PAZAR && pazarSearchActive) {
            int innerW = sc(CogTradeGuiLayout.LEFT_PAGE_W) - 10;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pazarSearchActive = false; return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && pazarSearchCursor > 0) {
                pazarSearchQuery = pazarSearchQuery.substring(0, pazarSearchCursor - 1)
                        + pazarSearchQuery.substring(pazarSearchCursor);
                pazarSearchCursor--;
                if (pazarSearchCursor < pazarSearchViewStart) pazarSearchViewStart = pazarSearchCursor;
                updatePazarFiltered();
            } else if (keyCode == GLFW.GLFW_KEY_DELETE && pazarSearchCursor < pazarSearchQuery.length()) {
                pazarSearchQuery = pazarSearchQuery.substring(0, pazarSearchCursor)
                        + pazarSearchQuery.substring(pazarSearchCursor + 1);
                updatePazarFiltered();
            } else if (keyCode == GLFW.GLFW_KEY_LEFT && pazarSearchCursor > 0) {
                pazarSearchCursor--;
                if (pazarSearchCursor < pazarSearchViewStart) pazarSearchViewStart = pazarSearchCursor;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT && pazarSearchCursor < pazarSearchQuery.length()) {
                pazarSearchCursor++;
                pazarSearchViewStart = adjustViewStart(pazarSearchQuery, pazarSearchCursor, pazarSearchViewStart, innerW);
            } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                pazarSearchCursor = 0; pazarSearchViewStart = 0;
            } else if (keyCode == GLFW.GLFW_KEY_END) {
                pazarSearchCursor = pazarSearchQuery.length();
                pazarSearchViewStart = adjustViewStart(pazarSearchQuery, pazarSearchCursor, pazarSearchViewStart, innerW);
            }
            return true;
        }
        if (inSubPage && activeSubTab == CogTradeSubTab.TAKASLAR && tradeSearchActive) {
            int innerW = sc(CogTradeGuiLayout.LEFT_PAGE_W) - 8;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                tradeSearchActive = false; return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && tradeSearchCursor > 0) {
                tradeSearchQuery = tradeSearchQuery.substring(0, tradeSearchCursor - 1) + tradeSearchQuery.substring(tradeSearchCursor);
                tradeSearchCursor--;
                if (tradeSearchCursor < tradeSearchViewStart) tradeSearchViewStart = tradeSearchCursor;
                updateTradeFilteredPlayers();
            } else if (keyCode == GLFW.GLFW_KEY_DELETE && tradeSearchCursor < tradeSearchQuery.length()) {
                tradeSearchQuery = tradeSearchQuery.substring(0, tradeSearchCursor) + tradeSearchQuery.substring(tradeSearchCursor + 1);
                updateTradeFilteredPlayers();
            } else if (keyCode == GLFW.GLFW_KEY_LEFT && tradeSearchCursor > 0) {
                tradeSearchCursor--;
                if (tradeSearchCursor < tradeSearchViewStart) tradeSearchViewStart = tradeSearchCursor;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT && tradeSearchCursor < tradeSearchQuery.length()) {
                tradeSearchCursor++;
                tradeSearchViewStart = adjustViewStart(tradeSearchQuery, tradeSearchCursor, tradeSearchViewStart, innerW);
            } else if (keyCode == GLFW.GLFW_KEY_HOME) {
                tradeSearchCursor = 0; tradeSearchViewStart = 0;
            } else if (keyCode == GLFW.GLFW_KEY_END) {
                tradeSearchCursor = tradeSearchQuery.length();
                tradeSearchViewStart = adjustViewStart(tradeSearchQuery, tradeSearchCursor, tradeSearchViewStart, innerW);
            }
            return true;
        }
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

        // 0a. Satın alma popup (marketOnly) — tüm tıklamaları yakala
        if (buyPopupOpen) {
            int bmW = sc(1030);
            int bmH = sc(1080);
            int bmX = (width  - bmW) / 2;
            int bmY = (height - bmH) / 2;

            // Kapatma butonu
            int closeS = Math.max(16, sc(38));
            int closeX = bmX + bmW - closeS - 4;
            int closeY = bmY + sc(70);
            if (mouseX >= closeX && mouseX < closeX + closeS
                    && mouseY >= closeY && mouseY < closeY + closeS) {
                buyPopupOpen = false; buyPopupItem = null;
                return true;
            }

            int listX  = bmX + sc(198);
            int listY  = bmY + sc(230);
            int listW  = sc(634);
            int listH  = sc(619);

            // Layout koordinatları render ile eşleşmeli
            int fH2     = textRenderer.fontHeight;
            int frameY2 = listY + 10;
            int nameY2  = frameY2 + 48 + 6;
            int priceY2 = nameY2 + fH2 + 5;
            int stockY2 = priceY2 + fH2 + 3;
            int sepY2   = stockY2 + fH2 + 10;
            int labelY2 = sepY2 + 8;
            int amtBtnH = 20;
            int amtBtnY = labelY2 + fH2 + 6;
            int[] amounts = {1, 8, 16, 32, 64};
            int btnW2   = (listW - 4 * 8) / 5;
            int rowW    = btnW2 * 5 + 4 * 8;
            int startX  = listX + (listW - rowW) / 2;

            // Miktar hızlı butonları
            for (int i = 0; i < amounts.length; i++) {
                int bx = startX + i * (btnW2 + 8);
                if (mouseX >= bx && mouseX < bx + btnW2
                        && mouseY >= amtBtnY && mouseY < amtBtnY + amtBtnH) {
                    int clamp = buyPopupItem != null
                            ? Math.min(amounts[i], buyPopupItem.getStock()) : amounts[i];
                    buyPopupAmount   = clamp;
                    buyAmountBuffer  = String.valueOf(clamp);
                    buyAmountEditing = false;
                    return true;
                }
            }

            // Miktar yazma kutusu
            int tfH = 16, tfW = 80;
            int tfY = amtBtnY + amtBtnH + 8;
            int tfX = listX + (listW - tfW) / 2;
            if (mouseX >= tfX && mouseX < tfX + tfW && mouseY >= tfY && mouseY < tfY + tfH) {
                buyAmountEditing = true;
                if (buyAmountBuffer.isEmpty()) buyAmountBuffer = "1";
                return true;
            }
            buyAmountEditing = false; // herhangi başka yere tıkladı

            // Satın al butonu
            if (buyPopupItem != null) {
                int buyBtnH = 24;
                int buyBtnY = listY + listH - buyBtnH - 8;
                int buyBtnW = 130;
                int buyBtnX = listX + (listW - buyBtnW) / 2;
                int clampedAmt = Math.min(buyPopupAmount, buyPopupItem.getStock());
                int invSpace   = availableInventorySpace(itemStackFor(buyPopupItem.getItemId()));
                boolean canAfford = clampedAmt >= 1
                        && ClientEconomyData.getBalance() >= buyPopupItem.getPrice() * clampedAmt;
                boolean canFit = invSpace >= clampedAmt;
                if (canAfford && canFit && mouseX >= buyBtnX && mouseX < buyBtnX + buyBtnW
                        && mouseY >= buyBtnY && mouseY < buyBtnY + buyBtnH) {
                    net.minecraft.network.PacketByteBuf buyBuf = PacketByteBufs.create();
                    buyBuf.writeInt(buyPopupItem.getId());
                    buyBuf.writeInt(clampedAmt);
                    ClientPlayNetworking.send(com.cogtrade.network.BuyItemPacket.ID, buyBuf);
                    buyPopupOpen = false; buyPopupItem = null;
                    return true;
                }
            }

            // Popup dışına tıklama → kapat
            if (mouseX < bmX || mouseX > bmX + bmW || mouseY < bmY || mouseY > bmY + bmH) {
                buyPopupOpen = false; buyPopupItem = null;
            }
            return true;
        }

        // 0. Takas teklifleri popup — tüm tıklamaları yakala
        if (offersPopupOpen) {
            // Popup koordinatları render metoduyla eşleşmeli (bmX/bmY merkez bazlı)
            int bmW = sc(1030);
            int bmH = sc(1080);
            int bmX = (width  - bmW) / 2;
            int bmY = (height - bmH) / 2;

            // Kapatma butonu
            int closeS = Math.max(16, sc(38));
            int closeX = bmX + bmW - closeS - 4;
            int closeY = bmY + sc(70);
            if (mouseX >= closeX && mouseX < closeX + closeS
                    && mouseY >= closeY && mouseY < closeY + closeS) {
                offersPopupOpen = false;
                offersScrollOffset = 0;
                return true;
            }

            // Teklif satırlarındaki butonlar — safe zone koordinatları
            java.util.List<String> offers = com.cogtrade.client.ClientTradeOffers.getOffers();
            int listX  = bmX + sc(198);   // 643 - 445 = 198
            int listY  = bmY + sc(230);
            int listW  = sc(634);
            int listH  = sc(619);
            int rowH   = Math.max(22, sc(50));
            int maxVis = Math.max(1, listH / rowH);
            int btnH   = 18;
            int btnGap = 6;
            int acceptW = textRenderer.getWidth("Kabul Et") + 12;
            int rejectW = textRenderer.getWidth("Reddet") + 12;

            int ry = listY;
            for (int i = offersScrollOffset; i < Math.min(offers.size(), offersScrollOffset + maxVis); i++) {
                int btnY2   = ry + (rowH - btnH) / 2;
                int rejectX = listX + listW - rejectW - 4;
                int acceptX = rejectX - btnGap - acceptW;

                if (mouseX >= acceptX && mouseX < acceptX + acceptW
                        && mouseY >= btnY2 && mouseY < btnY2 + btnH) {
                    assert client != null && client.player != null;
                    client.player.networkHandler.sendCommand("trade accept");
                    offersPopupOpen = false;
                    offersScrollOffset = 0;
                    return true;
                }
                if (mouseX >= rejectX && mouseX < rejectX + rejectW
                        && mouseY >= btnY2 && mouseY < btnY2 + btnH) {
                    assert client != null && client.player != null;
                    client.player.networkHandler.sendCommand("trade reject");
                    offersPopupOpen = false;
                    offersScrollOffset = 0;
                    return true;
                }
                ry += rowH;
            }

            // book-medium dışına tıklama → popup'ı kapat
            if (mouseX < bmX || mouseX > bmX + bmW || mouseY < bmY || mouseY > bmY + bmH) {
                offersPopupOpen = false;
                offersScrollOffset = 0;
            }
            return true; // popup açıkken diğer click'leri blokla
        }

        // 0b. Pazar item popup — tüm tıklamaları yakala
        if (pazarPopupOpen && inSubPage && activeSubTab == CogTradeSubTab.PAZAR) {
            int bmW = sc(1030);
            int bmH = sc(1080);
            int bmX = (width  - bmW) / 2;
            int bmY = (height - bmH) / 2;

            // Kapatma butonu
            int closeS = Math.max(16, sc(38));
            int closeX = bmX + bmW - closeS - 4;
            int closeY = bmY + sc(70);
            if (mouseX >= closeX && mouseX < closeX + closeS
                    && mouseY >= closeY && mouseY < closeY + closeS) {
                pazarPopupOpen = false; pazarPopupItem = null;
                pazarSelectedListing = -1; pazarListingScroll = 0;
                return true;
            }

            // "Bul" butonu
            List<PazarListing> bListings = buildPazarListings();
            int listX = bmX + sc(198);
            int listY = bmY + sc(230);
            int listW = sc(634);
            int listH = sc(619);
            int safeX2b = listX + listW / 2;
            int frameDispB = 32;
            int frameYB   = listY + 4;
            int nameYB    = frameYB + frameDispB + 3;
            int headingYB = nameYB + textRenderer.fontHeight * 2 + 7;
            int actualListYB = headingYB + textRenderer.fontHeight + 4;
            int btnH2b   = 20;
            int actualListHB = listY + listH - actualListYB - btnH2b - 8;
            int rowH  = Math.max(22, Math.min(50, actualListHB / 5));
            int maxVis = Math.max(1, actualListHB / rowH);
            int btnW  = 90; int btnH = btnH2b;
            int btnX  = listX + (listW - btnW) / 2;
            int btnY  = listY + listH - btnH - 4;
            boolean btnEnabled = pazarSelectedListing >= 0
                    && pazarSelectedListing < bListings.size()
                    && bListings.get(pazarSelectedListing).stock() > 0;
            if (btnEnabled && mouseX >= btnX && mouseX < btnX + btnW
                    && mouseY >= btnY && mouseY < btnY + btnH) {
                PazarListing sel = bListings.get(pazarSelectedListing);
                if (sel.isServerMarket()) {
                    ClientPlayNetworking.send(com.cogtrade.network.LocateRequestPacket.ID, PacketByteBufs.empty());
                } else {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeString(sel.sellerUuid());
                    ClientPlayNetworking.send(com.cogtrade.network.RequestLocateTradePostPacket.ID, buf);
                }
                pazarPopupOpen = false; pazarPopupItem = null;
                pazarSelectedListing = -1; pazarListingScroll = 0;
                close();
                return true;
            }

            // Listing satır tıklamaları
            int ry = actualListYB;
            for (int i = pazarListingScroll; i < Math.min(bListings.size(), pazarListingScroll + maxVis); i++) {
                if (mouseX >= listX && mouseX < listX + listW
                        && mouseY >= ry && mouseY < ry + rowH) {
                    pazarSelectedListing = i;
                    return true;
                }
                ry += rowH;
            }

            // Dışına tıklama → kapat
            if (mouseX < bmX || mouseX > bmX + bmW || mouseY < bmY || mouseY > bmY + bmH) {
                pazarPopupOpen = false; pazarPopupItem = null; pazarSelectedListing = -1;
            }
            return true;
        }

        // 1. Üst sekmeler (marketOnly modunda sekme yok)
        if (!marketOnly) {
            for (CogTradeMainTab tab : CogTradeMainTab.values()) {
                if (tabHit(tab, mouseX, mouseY)) {
                    setActiveTab(tab);
                    return true;
                }
            }
        }

        // 2. Geri butonu (marketOnly modunda geri yok)
        if (!marketOnly && inSubPage && isBackHovered(mouseX, mouseY)) {
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

        // 3.5. Takas sayfası — tıklama işlemleri
        if (inSubPage && activeSubTab == CogTradeSubTab.TAKASLAR) {
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
                tradeSearchActive = true;
                int clickRel = (int) mouseX - lpX - 4;
                tradeSearchCursor = charIndexAtPixel(tradeSearchQuery, tradeSearchViewStart, clickRel);
                return true;
            }

            // Sol: oyuncu listesi
            int listY = lpY + BACK_AREA_H + 24;
            int listH = lpH - BACK_AREA_H - 24;
            int itemH = 14;
            if (mouseX >= lpX && mouseX < lpX + lpW - 5
                    && mouseY >= listY && mouseY < listY + listH) {
                int index = tradePlayerScrollOffset + (int) ((mouseY - listY) / itemH);
                if (index >= 0 && index < tradeFilteredPlayers.size()) {
                    tradeSelectedPlayer = tradeFilteredPlayers.get(index);
                }
                tradeSearchActive = false;
                return true;
            }

            // Sağ: Takas İsteği Gönder butonu
            int sbW = 130, sbH = 24;
            int sbX = rpX + (rpW - sbW) / 2;
            int sbY = rpY + 160;
            if (mouseX >= sbX && mouseX < sbX + sbW && mouseY >= sbY && mouseY < sbY + sbH) {
                sendTradeOffer();
                return true;
            }

            // Sağ üst: Takas Teklifleri butonu
            String offersLabel = "Takas Teklifleri";
            int offersBtnW = textRenderer.getWidth(offersLabel) + 14;
            int offersBtnH = 16;
            int offersBtnX = rpX + rpW - offersBtnW;
            int offersBtnY = rpY;
            if (mouseX >= offersBtnX && mouseX < offersBtnX + offersBtnW
                    && mouseY >= offersBtnY && mouseY < offersBtnY + offersBtnH) {
                offersPopupOpen = !offersPopupOpen;
                if (offersPopupOpen) offersScrollOffset = 0;
                return true;
            }

            tradeSearchActive = false;
        }

        // 3.5. Pazar sayfası — arama, kategori, item grid
        if (inSubPage && activeSubTab == CogTradeSubTab.PAZAR) {
            int lpX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int lpY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
            int lpW = sc(CogTradeGuiLayout.LEFT_PAGE_W);
            int lpH = sc(CogTradeGuiLayout.LEFT_PAGE_H);
            int rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
            int rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
            int rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W);
            int rpH = sc(CogTradeGuiLayout.RIGHT_PAGE_H);
            int contentY = lpY + (marketOnly ? 0 : BACK_AREA_H);
            int sfH = 14;

            // Arama kutusu
            if (mouseX >= lpX && mouseX < lpX + lpW
                    && mouseY >= contentY && mouseY < contentY + sfH) {
                pazarSearchActive = true;
                return true;
            }
            // Temizle butonu
            if (!pazarSearchQuery.isEmpty()) {
                int clrX = lpX + lpW - 12;
                if (mouseX >= clrX && mouseX < clrX + 10
                        && mouseY >= contentY + 2 && mouseY < contentY + sfH - 2) {
                    pazarSearchQuery = ""; pazarSearchCursor = 0; pazarSearchViewStart = 0;
                    updatePazarFiltered();
                    return true;
                }
            }
            // Arama kutusu dışı → deaktif et
            pazarSearchActive = false;

            // Hiyerarşik kategori grid tıklaması
            int listTop2 = contentY + sfH + 4;
            int cellH2   = SC_FRAME + textRenderer.fontHeight + 2;
            int cols2    = Math.max(3, (lpW - SC_HGAP) / (SC_FRAME + SC_HGAP));
            int yOff2    = listTop2 - pazarLeftScroll;

            // "Tümü" hücresi
            if (mouseX >= lpX && mouseX < lpX + SC_FRAME
                    && mouseY >= yOff2 && mouseY < yOff2 + SC_FRAME) {
                pazarSelectedSubCat = null;
                pazarSearchQuery = ""; pazarSearchCursor = 0; pazarSearchViewStart = 0;
                updatePazarFiltered();
                return true;
            }
            yOff2 += cellH2 + SC_VGAP;

            for (PazarSection sec2 : PAZAR_SECTIONS) {
                yOff2 += SC_HDR_H; // başlık satırını atla
                java.util.List<PazarSubCat> subs2 = sec2.subs();
                boolean clicked = false;
                for (int i = 0; i < subs2.size(); i++) {
                    int col2 = i % cols2;
                    int row2 = i / cols2;
                    int cx2  = lpX + col2 * (SC_FRAME + SC_HGAP);
                    int cy2  = yOff2 + row2 * (cellH2 + SC_VGAP);
                    if (mouseX >= cx2 && mouseX < cx2 + SC_FRAME
                            && mouseY >= cy2 && mouseY < cy2 + SC_FRAME) {
                        pazarSelectedSubCat = subs2.get(i);
                        pazarSearchQuery = ""; pazarSearchCursor = 0; pazarSearchViewStart = 0;
                        updatePazarFiltered();
                        clicked = true;
                        break;
                    }
                }
                if (clicked) return true;
                int rowsInSec2 = (subs2.size() + cols2 - 1) / cols2;
                yOff2 += rowsInSec2 * (cellH2 + SC_VGAP);
            }

            // Sağ sayfa — item grid
            if (ClientMarketData.loaded && !pazarFiltered.isEmpty()) {
                int cols = Math.max(1, rpW / PAZAR_PITCH);
                int rows = Math.max(1, rpH / PAZAR_PITCH);
                int startIdx = pazarItemScroll * cols;
                for (int i = startIdx; i < Math.min(pazarFiltered.size(), startIdx + cols * rows); i++) {
                    int col = (i - startIdx) % cols;
                    int row = (i - startIdx) / cols;
                    int fx  = rpX + col * PAZAR_PITCH;
                    int fy  = rpY + row * PAZAR_PITCH;
                    if (mouseX >= fx && mouseX < fx + PAZAR_FRAME
                            && mouseY >= fy && mouseY < fy + PAZAR_FRAME) {
                        if (marketOnly) {
                            // Market bloğu modunda: direkt satın alma popup
                            buyPopupItem     = pazarFiltered.get(i);
                            buyPopupOpen     = true;
                            buyPopupAmount   = 1;
                            buyAmountBuffer  = "1";
                            buyAmountEditing = false;
                        } else {
                            // Normal mod: satıcı listesi popup
                            pazarPopupItem       = pazarFiltered.get(i);
                            pazarPopupOpen       = true;
                            pazarSelectedListing = -1;
                            pazarListingScroll   = 0;
                            ClientPlayNetworking.send(
                                    com.cogtrade.network.RequestAllShopListingsPacket.ID,
                                    PacketByteBufs.empty());
                        }
                        return true;
                    }
                }
            }
        }

        // 3.6. Bakiye sayfası — işlem listesi tıklaması
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
        // Popup açıksa scroll teklif listesine gider
        if (offersPopupOpen) {
            java.util.List<String> offers = com.cogtrade.client.ClientTradeOffers.getOffers();
            int rowH      = Math.max(22, sc(50));
            int listH     = sc(619);   // safe zone yüksekliği
            int maxVis    = Math.max(1, listH / rowH);
            int maxScroll = Math.max(0, offers.size() - maxVis);
            offersScrollOffset = Math.max(0, Math.min(
                    offersScrollOffset - (int) Math.signum(amount), maxScroll));
            return true;
        }
        // Pazar popup scroll
        if (pazarPopupOpen && inSubPage && activeSubTab == CogTradeSubTab.PAZAR) {
            List<PazarListing> pLst = buildPazarListings();
            int listH2b = sc(619);
            int rowH2b  = Math.max(22, Math.min(50, (listH2b - 80) / 5));
            int maxVis2 = Math.max(1, (listH2b - 80) / rowH2b);
            pazarListingScroll = Math.max(0, Math.min(
                    pazarListingScroll - (int) Math.signum(amount),
                    Math.max(0, pLst.size() - maxVis2)));
            return true;
        }
        // Pazar kategori / item scroll
        if (inSubPage && activeSubTab == CogTradeSubTab.PAZAR) {
            int lpX   = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int lpY   = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
            int lpW   = sc(CogTradeGuiLayout.LEFT_PAGE_W);
            int lpH   = sc(CogTradeGuiLayout.LEFT_PAGE_H);
            int rpX   = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
            int rpY   = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
            int rpW   = sc(CogTradeGuiLayout.RIGHT_PAGE_W);
            int rpH   = sc(CogTradeGuiLayout.RIGHT_PAGE_H);
            // Sol: kategori grid scroll (piksel bazlı)
            if (mouseX >= lpX && mouseX < lpX + lpW && mouseY >= lpY && mouseY < lpY + lpH) {
                int cols3   = Math.max(3, (lpW - SC_HGAP) / (SC_FRAME + SC_HGAP));
                int totalH3 = pazarLeftContentH(cols3);
                int listH3  = lpH - BACK_AREA_H - 14 - 4;
                int scrollAmt = 24;
                pazarLeftScroll = Math.max(0, Math.min(
                        pazarLeftScroll - (int) Math.signum(amount) * scrollAmt,
                        Math.max(0, totalH3 - listH3)));
                return true;
            }
            // Sağ: item grid scroll
            if (mouseX >= rpX && mouseX < rpX + rpW && mouseY >= rpY && mouseY < rpY + rpH) {
                int cols2 = Math.max(1, rpW / PAZAR_PITCH);
                int rows2 = Math.max(1, rpH / PAZAR_PITCH);
                int max2  = Math.max(0, (pazarFiltered.size() + cols2 - 1) / cols2 - rows2);
                pazarItemScroll = Math.max(0, Math.min(
                        pazarItemScroll - (int) Math.signum(amount), max2));
                return true;
            }
        }
        if (inSubPage && activeSubTab == CogTradeSubTab.TAKASLAR) {
            int lpX   = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
            int listY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y) + BACK_AREA_H + 24;
            int lpW   = sc(CogTradeGuiLayout.LEFT_PAGE_W);
            int lpH   = sc(CogTradeGuiLayout.LEFT_PAGE_H) - BACK_AREA_H - 24;
            if (mouseX >= lpX && mouseX < lpX + lpW
                    && mouseY >= listY && mouseY < listY + lpH) {
                int visible   = lpH / 14;
                int maxScroll = Math.max(0, tradeFilteredPlayers.size() - visible);
                tradePlayerScrollOffset = Math.max(0, Math.min(
                        tradePlayerScrollOffset - (int) Math.signum(amount), maxScroll));
                return true;
            }
        }
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
        this.offersPopupOpen = false;
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
        this.activeSubTab    = null;
        this.inSubPage       = false;
        this.offersPopupOpen = false;
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

    // ── Takas teklifleri butonu (TAKASLAR sağ sayfa, sağ üst) ────────────

    private void renderTradeOffersButton(DrawContext ctx, int mouseX, int mouseY) {
        int rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
        int rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
        int rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W);

        boolean hasOffers = com.cogtrade.client.ClientTradeOffers.hasOffers();
        int     count     = com.cogtrade.client.ClientTradeOffers.getCount();

        String label = "Takas Teklifleri";
        int btnW = textRenderer.getWidth(label) + 14;
        int btnH = 16;
        int btnX = rpX + rpW - btnW;
        int btnY = rpY;

        boolean hovered = mouseX >= btnX && mouseX < btnX + btnW
                       && mouseY >= btnY && mouseY < btnY + btnH;

        // Buton rengi: teklif varsa kırmızı, yoksa normal
        int bg     = hasOffers ? (hovered ? 0xFFAA0000 : 0xFF882222)
                               : (hovered ? 0xFF604000 : 0xAA604000);
        int border = hasOffers ? 0xFFFF8888 : 0xFFFFD080;

        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH, bg);
        ctx.fill(btnX,          btnY,          btnX + btnW, btnY + 1,       border);
        ctx.fill(btnX,          btnY + btnH-1, btnX + btnW, btnY + btnH,    border);
        ctx.fill(btnX,          btnY,          btnX + 1,    btnY + btnH,    border);
        ctx.fill(btnX + btnW-1, btnY,          btnX + btnW, btnY + btnH,    border);

        ctx.drawText(textRenderer, Text.literal(label),
                btnX + (btnW - textRenderer.getWidth(label)) / 2,
                btnY + (btnH - textRenderer.fontHeight) / 2,
                0xFFFFFFFF, false);

        // Bildirim rozeti (kırmızı sayaç)
        if (hasOffers) {
            String badge  = String.valueOf(count);
            int badgeW    = Math.max(10, textRenderer.getWidth(badge) + 4);
            int badgeH    = 9;
            int badgeX    = btnX + btnW - badgeW / 2 - 1;
            int badgeY    = btnY - badgeH / 2;
            ctx.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, 0xFFDD0000);
            ctx.drawText(textRenderer, Text.literal(badge),
                    badgeX + (badgeW - textRenderer.getWidth(badge)) / 2,
                    badgeY + (badgeH - textRenderer.fontHeight) / 2,
                    0xFFFFFFFF, false);
        }
    }

    // ── Takas teklifleri popup ────────────────────────────────────────────

    /**
     * book-medium (1030×1080 native) ekranın ortasında bağımsız olarak konumlanır.
     * Tüm içerik koordinatları bu merkez-bazlı bmX/bmY'ye göre hesaplanır.
     * Safe zone: native x=643, y=230, w=634, h=619 (book-medium'un x=445'ten başladığı kabul edilir).
     */
    private void renderOffersPopup(DrawContext ctx, int mouseX, int mouseY) {
        // 1. Tam ekran karartı
        ctx.fill(0, 0, width, height, 0xAA000000);

        // 2. book-medium.png — ekran ortasına bağımsız konumlandır
        int bmW = sc(1030);
        int bmH = sc(1080);
        int bmX = (width  - bmW) / 2;
        int bmY = (height - bmH) / 2;
        NineSliceRenderer.drawFullTexture(ctx, BOOK_MEDIUM_TEX, bmX, bmY, bmW, bmH, 1030, 1080);

        // 3. offers_title.png — book-medium içi native offset (562-445=117, 66)
        NineSliceRenderer.drawFullTexture(ctx, OFFERS_TITLE_TEX,
                bmX + sc(117), bmY + sc(66), sc(796), sc(60), 796, 60);

        // 4. Kapatma butonu (✕) — book-medium sağ üst köşesi
        int closeS   = Math.max(16, sc(38));
        int closeX   = bmX + bmW - closeS - 4;
        int closeY   = bmY + sc(70);
        boolean closeHov = mouseX >= closeX && mouseX < closeX + closeS
                        && mouseY >= closeY && mouseY < closeY + closeS;
        ctx.fill(closeX, closeY, closeX + closeS, closeY + closeS,
                closeHov ? 0xCCDD3333 : 0x99882222);
        ctx.fill(closeX,            closeY,            closeX + closeS, closeY + 1,            0xFFAA6655);
        ctx.fill(closeX,            closeY + closeS-1, closeX + closeS, closeY + closeS,        0xFFAA6655);
        ctx.fill(closeX,            closeY,            closeX + 1,      closeY + closeS,        0xFFAA6655);
        ctx.fill(closeX + closeS-1, closeY,            closeX + closeS, closeY + closeS,        0xFFAA6655);
        String closeLbl = "\u2715";
        ctx.drawText(textRenderer, Text.literal(closeLbl),
                closeX + (closeS - textRenderer.getWidth(closeLbl)) / 2,
                closeY + (closeS - textRenderer.fontHeight) / 2,
                0xFFFFFFFF, false);

        // 5. Teklif listesi — safe zone: native x=643-445=198 offset, y=230, w=634, h=619
        java.util.List<String> offers = com.cogtrade.client.ClientTradeOffers.getOffers();

        int listX  = bmX + sc(198);   // 643 - 445 = 198
        int listY  = bmY + sc(230);
        int listW  = sc(634);
        int listH  = sc(619);
        int rowH   = Math.max(22, sc(50));
        int maxVis = Math.max(1, listH / rowH);

        if (offers.isEmpty()) {
            String noMsg = "Bekleyen takas teklifi yok.";
            ctx.drawText(textRenderer, Text.literal(noMsg),
                    listX + (listW - textRenderer.getWidth(noMsg)) / 2,
                    listY + listH / 2 - textRenderer.fontHeight / 2,
                    COLOR_DIM, false);
            return;
        }

        int maxScroll = Math.max(0, offers.size() - maxVis);
        offersScrollOffset = Math.min(offersScrollOffset, maxScroll);

        int acceptTxtW = textRenderer.getWidth("Kabul Et");
        int rejectTxtW = textRenderer.getWidth("Reddet");
        int acceptW    = acceptTxtW + 12;
        int rejectW    = rejectTxtW + 12;
        int btnH       = 18;
        int btnGap     = 6;

        int ry = listY;
        for (int i = offersScrollOffset; i < Math.min(offers.size(), offersScrollOffset + maxVis); i++) {
            String initiator = offers.get(i);

            // Satır arka planı
            ctx.fill(listX, ry, listX + listW, ry + rowH, 0x22AA6600);
            ctx.fill(listX, ry + rowH - 1, listX + listW, ry + rowH, 0x55000000);

            // Oyuncu kafası
            int headSize = Math.min(20, rowH - 4);
            int headX    = listX + 8;
            int headY    = ry + (rowH - headSize) / 2;
            drawPlayerHead(ctx, initiator, headX, headY, headSize);

            // Oyuncu adı
            ctx.drawText(textRenderer, Text.literal(initiator),
                    headX + headSize + 8,
                    ry + (rowH - textRenderer.fontHeight) / 2,
                    COLOR_LABEL, false);

            // "Reddet" butonu
            int rejectX = listX + listW - rejectW - 4;
            int btnY2   = ry + (rowH - btnH) / 2;
            boolean rejHov = mouseX >= rejectX && mouseX < rejectX + rejectW
                          && mouseY >= btnY2    && mouseY < btnY2 + btnH;
            ctx.fill(rejectX, btnY2, rejectX + rejectW, btnY2 + btnH,
                    rejHov ? 0xFF6B1122 : 0xFF993344);
            ctx.fill(rejectX,             btnY2,          rejectX + rejectW, btnY2 + 1,       0xFFFF9999);
            ctx.fill(rejectX,             btnY2 + btnH-1, rejectX + rejectW, btnY2 + btnH,    0xFFFF9999);
            ctx.fill(rejectX,             btnY2,          rejectX + 1,       btnY2 + btnH,    0xFFFF9999);
            ctx.fill(rejectX + rejectW-1, btnY2,          rejectX + rejectW, btnY2 + btnH,    0xFFFF9999);
            ctx.drawText(textRenderer, Text.literal("Reddet"),
                    rejectX + (rejectW - rejectTxtW) / 2,
                    btnY2 + (btnH - textRenderer.fontHeight) / 2,
                    0xFFFFFFFF, false);

            // "Kabul Et" butonu
            int acceptX = rejectX - btnGap - acceptW;
            boolean accHov = mouseX >= acceptX && mouseX < acceptX + acceptW
                          && mouseY >= btnY2    && mouseY < btnY2 + btnH;
            ctx.fill(acceptX, btnY2, acceptX + acceptW, btnY2 + btnH,
                    accHov ? 0xFF226622 : 0xFF337733);
            ctx.fill(acceptX,             btnY2,          acceptX + acceptW, btnY2 + 1,       0xFF99FF99);
            ctx.fill(acceptX,             btnY2 + btnH-1, acceptX + acceptW, btnY2 + btnH,    0xFF99FF99);
            ctx.fill(acceptX,             btnY2,          acceptX + 1,       btnY2 + btnH,    0xFF99FF99);
            ctx.fill(acceptX + acceptW-1, btnY2,          acceptX + acceptW, btnY2 + btnH,    0xFF99FF99);
            ctx.drawText(textRenderer, Text.literal("Kabul Et"),
                    acceptX + (acceptW - acceptTxtW) / 2,
                    btnY2 + (btnH - textRenderer.fontHeight) / 2,
                    0xFFFFFFFF, false);

            ry += rowH;
        }

        // Scroll göstergesi
        if (offers.size() > maxVis) {
            String scrollInfo = (offersScrollOffset + 1) + "-"
                    + Math.min(offers.size(), offersScrollOffset + maxVis)
                    + " / " + offers.size();
            ctx.drawText(textRenderer, Text.literal(scrollInfo),
                    listX + listW - textRenderer.getWidth(scrollInfo),
                    listY - textRenderer.fontHeight - 3, COLOR_DIM, false);
        }
    }

    // ── Pazar sol sayfa (arama + hiyerarşik kategori grid) ────────────────

    // Subkat frame sabitleri (ekran pikseli)
    private static final int SC_FRAME  = 32;   // subcat frame boyutu
    private static final int SC_HGAP   = 4;    // yatay boşluk
    private static final int SC_VGAP   = 6;    // dikey boşluk
    private static final int SC_HDR_H  = 14;   // bölüm başlığı yüksekliği

    /** Sol sayfa kategorilerin toplam içerik yüksekliği (piksel). */
    private int pazarLeftContentH(int cols) {
        int cellH = SC_FRAME + textRenderer.fontHeight + 2;
        int total = 0;
        // "Tümü" hücresi
        total += cellH + SC_VGAP;
        for (PazarSection sec : PAZAR_SECTIONS) {
            total += SC_HDR_H;
            int rows = (sec.subs().size() + cols - 1) / cols;
            total += rows * (cellH + SC_VGAP);
        }
        return total;
    }

    private void renderPazarLeft(DrawContext ctx, int mouseX, int mouseY) {
        int lpX = guiX + sc(CogTradeGuiLayout.LEFT_PAGE_X);
        int lpY = guiY + sc(CogTradeGuiLayout.LEFT_PAGE_Y);
        int lpW = sc(CogTradeGuiLayout.LEFT_PAGE_W);
        int lpH = sc(CogTradeGuiLayout.LEFT_PAGE_H);
        int contentY = lpY + (marketOnly ? 0 : BACK_AREA_H);

        // ── Arama kutusu ──────────────────────────────────────────────────
        int sfH = 14;
        boolean sfHov = !pazarSearchActive
                && mouseX >= lpX && mouseX < lpX + lpW
                && mouseY >= contentY && mouseY < contentY + sfH;
        ctx.fill(lpX, contentY, lpX + lpW, contentY + sfH,
                pazarSearchActive ? 0x44BB9922 : (sfHov ? 0x33AA8811 : 0x22997700));
        ctx.fill(lpX, contentY + sfH - 1, lpX + lpW, contentY + sfH, 0x88AA8844);

        if (pazarSearchQuery.isEmpty() && !pazarSearchActive) {
            ctx.drawText(textRenderer, Text.literal("Pazar'da ara..."),
                    lpX + 4, contentY + (sfH - textRenderer.fontHeight) / 2, COLOR_DIM, false);
        } else {
            int maxW = lpW - 10;
            while (pazarSearchCursor > pazarSearchViewStart
                    && textRenderer.getWidth(pazarSearchQuery.substring(pazarSearchViewStart, pazarSearchCursor)) > maxW) {
                pazarSearchViewStart++;
            }
            while (pazarSearchCursor < pazarSearchViewStart) pazarSearchViewStart = pazarSearchCursor;
            int visEnd = pazarSearchQuery.length();
            while (visEnd > pazarSearchViewStart
                    && textRenderer.getWidth(pazarSearchQuery.substring(pazarSearchViewStart, visEnd)) > maxW) {
                visEnd--;
            }
            ctx.drawText(textRenderer, Text.literal(pazarSearchQuery.substring(pazarSearchViewStart, visEnd)),
                    lpX + 4, contentY + (sfH - textRenderer.fontHeight) / 2, COLOR_LABEL, false);
            if (pazarSearchActive) {
                int curX = lpX + 4 + textRenderer.getWidth(
                        pazarSearchQuery.substring(pazarSearchViewStart, Math.min(pazarSearchCursor, visEnd)));
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    ctx.fill(curX, contentY + 2, curX + 1, contentY + sfH - 2, COLOR_LABEL);
                }
            }
            if (!pazarSearchQuery.isEmpty()) {
                int clrX = lpX + lpW - 12;
                ctx.fill(clrX, contentY + 2, clrX + 10, contentY + sfH - 2, 0x66AA6633);
                ctx.drawText(textRenderer, Text.literal("×"), clrX + 2, contentY + 2, COLOR_DIM, false);
            }
        }

        // ── Hiyerarşik kategori grid ──────────────────────────────────────
        int listTop = contentY + sfH + 4;
        int listH2  = lpH - (marketOnly ? 0 : BACK_AREA_H) - sfH - 4;
        int cellH   = SC_FRAME + textRenderer.fontHeight + 2;
        int cols    = Math.max(3, (lpW - SC_HGAP) / (SC_FRAME + SC_HGAP));
        int totalH  = pazarLeftContentH(cols);
        int maxSc   = Math.max(0, totalH - listH2);
        pazarLeftScroll = Math.min(pazarLeftScroll, maxSc);

        int yOff = listTop - pazarLeftScroll;

        // "Tümü" hücresi
        {
            boolean sel = pazarSelectedSubCat == null && pazarSearchQuery.isBlank();
            boolean hov = mouseX >= lpX && mouseX < lpX + SC_FRAME
                       && mouseY >= yOff && mouseY < yOff + cellH;
            // Sadece tamamen liste alanı içinde olan hücreleri çiz (overflow engeli)
            if (yOff >= listTop && yOff + cellH <= listTop + listH2) {
                NineSliceRenderer.drawFullTexture(ctx, ITEM_FRAME_TEX, lpX, yOff, SC_FRAME, SC_FRAME, 32, 32);
                if (sel)        ctx.fill(lpX, yOff, lpX + SC_FRAME, yOff + SC_FRAME, 0x66AA8800);
                else if (hov)   ctx.fill(lpX, yOff, lpX + SC_FRAME, yOff + SC_FRAME, 0x33AA8800);
                ctx.drawItem(new ItemStack(Items.COMPASS), lpX + (SC_FRAME - 16) / 2, yOff + (SC_FRAME - 16) / 2);
                String lbl = "Tümü";
                ctx.drawText(textRenderer, Text.literal(lbl),
                        lpX + Math.max(0, (SC_FRAME - textRenderer.getWidth(lbl)) / 2),
                        yOff + SC_FRAME + 2, sel ? COLOR_LABEL : COLOR_DIM, false);
            }
            yOff += cellH + SC_VGAP;
        }

        // Bölümler
        for (PazarSection sec : PAZAR_SECTIONS) {
            // Bölüm başlığı — sadece görünür alanda
            if (yOff >= listTop && yOff + SC_HDR_H <= listTop + listH2) {
                ctx.fill(lpX, yOff + SC_HDR_H - 1, lpX + lpW - 4, yOff + SC_HDR_H, 0x44AA8844);
                ctx.drawText(textRenderer, Text.literal("◆ " + sec.title()),
                        lpX, yOff + (SC_HDR_H - textRenderer.fontHeight) / 2, COLOR_SUBTITLE, false);
            }
            yOff += SC_HDR_H;

            // Alt kategori hücreleri
            java.util.List<PazarSubCat> subs = sec.subs();
            int rowsInSec = (subs.size() + cols - 1) / cols;
            for (int i = 0; i < subs.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int cx  = lpX + col * (SC_FRAME + SC_HGAP);
                int cy  = yOff + row * (cellH + SC_VGAP);
                // Tamamen görünür alanda değilse çizme (overflow engeli)
                if (cy < listTop || cy + cellH > listTop + listH2) continue;
                PazarSubCat sub = subs.get(i);
                boolean sel = Objects.equals(pazarSelectedSubCat, sub) && pazarSearchQuery.isBlank();
                boolean hov = mouseX >= cx && mouseX < cx + SC_FRAME
                           && mouseY >= cy && mouseY < cy + SC_FRAME;
                NineSliceRenderer.drawFullTexture(ctx, ITEM_FRAME_TEX, cx, cy, SC_FRAME, SC_FRAME, 32, 32);
                if (sel)        ctx.fill(cx, cy, cx + SC_FRAME, cy + SC_FRAME, 0x66AA8800);
                else if (hov)   ctx.fill(cx, cy, cx + SC_FRAME, cy + SC_FRAME, 0x33AA8800);
                ctx.drawItem(new ItemStack(sub.icon()), cx + (SC_FRAME - 16) / 2, cy + (SC_FRAME - 16) / 2);
                String lbl = sub.label();
                int lw = textRenderer.getWidth(lbl);
                if (lw > SC_FRAME) {
                    // trim to fit
                    while (lbl.length() > 1 && textRenderer.getWidth(lbl + ".") > SC_FRAME)
                        lbl = lbl.substring(0, lbl.length() - 1);
                    lbl = lbl + ".";
                }
                ctx.drawText(textRenderer, Text.literal(lbl),
                        cx + Math.max(0, (SC_FRAME - textRenderer.getWidth(lbl)) / 2),
                        cy + SC_FRAME + 2, sel ? COLOR_LABEL : COLOR_DIM, false);
            }
            yOff += rowsInSec * (cellH + SC_VGAP);
        }

        // Kaydırma çubuğu
        if (totalH > listH2) {
            int sbX    = lpX + lpW - 3;
            int thumbH = Math.max(8, listH2 * listH2 / totalH);
            int thumbY = listTop + (maxSc > 0 ? (listH2 - thumbH) * pazarLeftScroll / maxSc : 0);
            ctx.fill(sbX, listTop, sbX + 3, listTop + listH2, 0x22000000);
            ctx.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xBB9B6200);
        }
    }

    // ── Pazar sağ sayfa (item grid) ───────────────────────────────────────

    private static final int PAZAR_FRAME = 28;
    private static final int PAZAR_GAP   = 3;
    private static final int PAZAR_PITCH = PAZAR_FRAME + PAZAR_GAP;

    private void renderPazarRight(DrawContext ctx, int mouseX, int mouseY) {
        int rpX = guiX + sc(CogTradeGuiLayout.RIGHT_PAGE_X);
        int rpY = guiY + sc(CogTradeGuiLayout.RIGHT_PAGE_Y);
        int rpW = sc(CogTradeGuiLayout.RIGHT_PAGE_W);
        int rpH = sc(CogTradeGuiLayout.RIGHT_PAGE_H);

        if (!ClientMarketData.loaded) {
            String msg = "Yükleniyor...";
            ctx.drawText(textRenderer, Text.literal(msg),
                    rpX + (rpW - textRenderer.getWidth(msg)) / 2,
                    rpY + rpH / 2, COLOR_DIM, false);
            return;
        }

        List<ClientMarketItem> list = pazarFiltered;
        if (list.isEmpty()) {
            String msg = pazarSearchQuery.isBlank() ? "Bu kategoride ürün yok." : "Sonuç bulunamadı.";
            ctx.drawText(textRenderer, Text.literal(msg),
                    rpX + (rpW - textRenderer.getWidth(msg)) / 2,
                    rpY + rpH / 2, COLOR_DIM, false);
            return;
        }

        int cols    = Math.max(1, rpW / PAZAR_PITCH);
        int rows    = Math.max(1, rpH / PAZAR_PITCH);
        int visible = cols * rows;
        int maxSc   = Math.max(0, (list.size() + cols - 1) / cols - rows);
        pazarItemScroll = Math.min(pazarItemScroll, maxSc);

        int startIdx = pazarItemScroll * cols;
        int itemOff  = (PAZAR_FRAME - 16) / 2;   // = 6

        ClientMarketItem hovItem   = null;
        int              hovMouseX = 0;
        int              hovMouseY = 0;

        for (int i = startIdx; i < Math.min(list.size(), startIdx + visible); i++) {
            int col = (i - startIdx) % cols;
            int row = (i - startIdx) / cols;
            int fx  = rpX + col * PAZAR_PITCH;
            int fy  = rpY + row * PAZAR_PITCH;

            ClientMarketItem item = list.get(i);

            // Frame arkaplanı (item-frame.png, native 32×32)
            NineSliceRenderer.drawFullTexture(ctx, ITEM_FRAME_TEX, fx, fy, PAZAR_FRAME, PAZAR_FRAME, 32, 32);

            // Item ikonu
            ItemStack stack = itemStackFor(item.getItemId());
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, fx + itemOff, fy + itemOff);
            }

            // Stok bitti göstergesi
            if (item.getStock() <= 0) {
                ctx.fill(fx + 2, fy + 2, fx + PAZAR_FRAME - 2, fy + PAZAR_FRAME - 2, 0x55FF0000);
            }

            // Hover: sadece parlama (tooltip loop sonrası çizilecek)
            boolean hov = mouseX >= fx && mouseX < fx + PAZAR_FRAME
                       && mouseY >= fy && mouseY < fy + PAZAR_FRAME;
            if (hov) {
                ctx.fill(fx, fy, fx + PAZAR_FRAME, fy + PAZAR_FRAME, 0x33FFFF00);
                hovItem   = item;
                hovMouseX = mouseX;
                hovMouseY = mouseY;
            }
        }

        // Tooltip — item'ların (Z≈200) ÜSTÜNDE çiz; Z=300'e taşı
        if (hovItem != null) {
            String tt  = hovItem.getDisplayName();
            String tt2 = String.format("%.0f \u2b21 | Stok: %d", hovItem.getPrice(), hovItem.getStock());
            int ttW = Math.max(textRenderer.getWidth(tt), textRenderer.getWidth(tt2)) + 12;
            int ttX = Math.min(hovMouseX + 6, width - ttW - 2);
            int ttY = Math.max(4, hovMouseY - textRenderer.fontHeight * 2 - 10);
            MatrixStack mstt = ctx.getMatrices();
            mstt.push();
            mstt.translate(0, 0, 300);
            ctx.fill(ttX - 3, ttY - 3, ttX + ttW + 3, ttY + textRenderer.fontHeight * 2 + 6, 0xF0100800);
            ctx.fill(ttX - 2, ttY - 2, ttX + ttW + 2, ttY + textRenderer.fontHeight * 2 + 5, 0xF0201000);
            ctx.fill(ttX - 2, ttY - 2, ttX + ttW + 2, ttY - 1, 0xFFAA8844);
            ctx.drawText(textRenderer, Text.literal(tt),  ttX + 4, ttY, 0xFFFFEE88, false);
            ctx.drawText(textRenderer, Text.literal(tt2), ttX + 4, ttY + textRenderer.fontHeight + 2, 0xFFCCBB88, false);
            mstt.pop();
        }

        // Scroll göstergesi
        if (list.size() > visible) {
            String info = (pazarItemScroll * cols + 1) + "-"
                    + Math.min(list.size(), (pazarItemScroll + rows) * cols)
                    + " / " + list.size();
            ctx.drawText(textRenderer, Text.literal(info),
                    rpX + rpW - textRenderer.getWidth(info),
                    rpY + rpH + 2, COLOR_DIM, false);
        }
    }

    // ── Satın alma popup (marketOnly modu) ───────────────────────────────

    private void renderBuyPopup(DrawContext ctx, int mouseX, int mouseY) {
        if (buyPopupItem == null) { buyPopupOpen = false; return; }

        // Karartı
        ctx.fill(0, 0, width, height, 0xAA000000);

        // book-medium.png
        int bmW = sc(1030);
        int bmH = sc(1080);
        int bmX = (width  - bmW) / 2;
        int bmY = (height - bmH) / 2;
        NineSliceRenderer.drawFullTexture(ctx, BOOK_MEDIUM_TEX, bmX, bmY, bmW, bmH, 1030, 1080);

        // Kapatma butonu
        int closeS = Math.max(16, sc(38));
        int closeX = bmX + bmW - closeS - 4;
        int closeY = bmY + sc(70);
        boolean closeHov = mouseX >= closeX && mouseX < closeX + closeS
                        && mouseY >= closeY && mouseY < closeY + closeS;
        ctx.fill(closeX, closeY, closeX + closeS, closeY + closeS,
                closeHov ? 0xCCDD3333 : 0x99882222);
        ctx.fill(closeX,            closeY,            closeX + closeS, closeY + 1,            0xFFAA6655);
        ctx.fill(closeX,            closeY + closeS-1, closeX + closeS, closeY + closeS,        0xFFAA6655);
        ctx.fill(closeX,            closeY,            closeX + 1,      closeY + closeS,        0xFFAA6655);
        ctx.fill(closeX + closeS-1, closeY,            closeX + closeS, closeY + closeS,        0xFFAA6655);
        String closeLbl = "\u2715";
        ctx.drawText(textRenderer, Text.literal(closeLbl),
                closeX + (closeS - textRenderer.getWidth(closeLbl)) / 2,
                closeY + (closeS - textRenderer.fontHeight) / 2,
                0xFFFFFFFF, false);

        // Safe zone
        int listX  = bmX + sc(198);
        int listY  = bmY + sc(230);
        int listW  = sc(634);
        int listH  = sc(619);
        int safeX2 = listX + listW / 2;

        // Item frame + ikon (2× ölçek, ortalanmış)
        int frameDisp = 48;
        int frameX    = safeX2 - frameDisp / 2;
        int frameY    = listY + 10;
        NineSliceRenderer.drawFullTexture(ctx, ITEM_FRAME_TEX, frameX, frameY, frameDisp, frameDisp, 32, 32);
        ItemStack popStack = itemStackFor(buyPopupItem.getItemId());
        if (!popStack.isEmpty()) {
            MatrixStack ms = ctx.getMatrices();
            ms.push();
            float fScale    = 2.0f;
            int   iconSize  = (int)(16 * fScale); // 32px
            int   iconOff   = (frameDisp - iconSize) / 2; // 8px padding
            ms.translate(frameX + iconOff, frameY + iconOff, 0);
            ms.scale(fScale, fScale, 1.0f);
            ctx.drawItem(popStack, 0, 0);
            ms.pop();
        }

        // İsim
        int nameY = frameY + frameDisp + 6;
        String name = buyPopupItem.getDisplayName();
        ctx.drawText(textRenderer, Text.literal(name),
                safeX2 - textRenderer.getWidth(name) / 2, nameY, COLOR_LABEL, false);

        // Fiyat / adet
        int priceY = nameY + textRenderer.fontHeight + 5;
        String priceStr = String.format("%.0f \u2b21 / adet", buyPopupItem.getPrice());
        ctx.drawText(textRenderer, Text.literal(priceStr),
                safeX2 - textRenderer.getWidth(priceStr) / 2, priceY, 0x8B6000, false);

        // Stok
        int stockY = priceY + textRenderer.fontHeight + 3;
        String stockStr = "Stok: " + buyPopupItem.getStock();
        int stockCol = buyPopupItem.getStock() > 0 ? 0x556633 : 0xCC3333;
        ctx.drawText(textRenderer, Text.literal(stockStr),
                safeX2 - textRenderer.getWidth(stockStr) / 2, stockY, stockCol, false);

        // Ayırıcı
        int sepY = stockY + textRenderer.fontHeight + 10;
        ctx.fill(listX + 10, sepY, listX + listW - 10, sepY + 1, 0x55AA8844);

        // "Miktar seçin:"
        int labelY = sepY + 8;
        String label = "Miktar seçin:";
        ctx.drawText(textRenderer, Text.literal(label),
                safeX2 - textRenderer.getWidth(label) / 2, labelY, COLOR_SUBTITLE, false);

        // Envanter alanı
        int invSpace = availableInventorySpace(popStack);

        // Hızlı miktar butonları: 1 | 8 | 16 | 32 | 64
        int[] amounts = {1, 8, 16, 32, 64};
        int amtBtnH = 20;
        int amtBtnY = labelY + textRenderer.fontHeight + 6;
        int btnW2   = (listW - 4 * 8) / 5;
        int rowW    = btnW2 * 5 + 4 * 8;
        int startX  = listX + (listW - rowW) / 2;
        for (int i = 0; i < amounts.length; i++) {
            int bx  = startX + i * (btnW2 + 8);
            int amt = amounts[i];
            boolean sel = buyPopupAmount == amt;
            boolean oor = amt > buyPopupItem.getStock() || amt > invSpace;
            boolean hov = !oor && mouseX >= bx && mouseX < bx + btnW2
                       && mouseY >= amtBtnY && mouseY < amtBtnY + amtBtnH;
            // Arkaplan
            ctx.fill(bx, amtBtnY, bx + btnW2, amtBtnY + amtBtnH,
                    oor ? 0x44222222 : sel ? 0xFFAA7700 : (hov ? 0xCC886600 : 0xBB604800));
            // Kenarlık
            int borderCol = oor ? 0x44555544 : 0xFFFFD080;
            ctx.fill(bx, amtBtnY, bx + btnW2, amtBtnY + 1, borderCol);
            ctx.fill(bx, amtBtnY + amtBtnH - 1, bx + btnW2, amtBtnY + amtBtnH, borderCol);
            ctx.fill(bx, amtBtnY, bx + 1, amtBtnY + amtBtnH, borderCol);
            ctx.fill(bx + btnW2 - 1, amtBtnY, bx + btnW2, amtBtnY + amtBtnH, borderCol);
            // Metin — beyaz bazlı, her zaman okunabilir
            String amtLbl = String.valueOf(amt);
            ctx.drawText(textRenderer, Text.literal(amtLbl),
                    bx + (btnW2 - textRenderer.getWidth(amtLbl)) / 2,
                    amtBtnY + (amtBtnH - textRenderer.fontHeight) / 2,
                    oor ? 0xFF888877 : (sel ? 0xFFFFFFFF : 0xFFEEDDAA), false);
        }

        // Miktar yazma kutusu
        int tfH = 16, tfW = 80;
        int tfY = amtBtnY + amtBtnH + 8;
        int tfX = listX + (listW - tfW) / 2;
        boolean tfHov = mouseX >= tfX && mouseX < tfX + tfW && mouseY >= tfY && mouseY < tfY + tfH;
        ctx.fill(tfX, tfY, tfX + tfW, tfY + tfH,
                buyAmountEditing ? 0x44BB9922 : (tfHov ? 0x33AA8811 : 0x22997700));
        ctx.fill(tfX, tfY + tfH - 1, tfX + tfW, tfY + tfH, 0x88AA8844);
        String bufDisplay = buyAmountBuffer.isEmpty() ? "1" : buyAmountBuffer;
        ctx.drawText(textRenderer, Text.literal(bufDisplay),
                tfX + (tfW - textRenderer.getWidth(bufDisplay)) / 2,
                tfY + (tfH - textRenderer.fontHeight) / 2,
                0xFFFFEEAA, false);
        if (buyAmountEditing && (System.currentTimeMillis() / 500) % 2 == 0) {
            int curX = tfX + (tfW - textRenderer.getWidth(bufDisplay)) / 2 + textRenderer.getWidth(bufDisplay) + 1;
            ctx.fill(curX, tfY + 2, curX + 1, tfY + tfH - 2, 0xFFFFEEAA);
        }

        // Toplam maliyet + envanter uyarısı
        int clampedAmt = Math.min(buyPopupAmount, buyPopupItem.getStock());
        int totalY = tfY + tfH + 8;
        String totalStr = String.format("Toplam: %.0f \u2b21", buyPopupItem.getPrice() * clampedAmt);
        ctx.drawText(textRenderer, Text.literal(totalStr),
                safeX2 - textRenderer.getWidth(totalStr) / 2, totalY, COLOR_LABEL, false);

        // Bakiye
        int balY = totalY + textRenderer.fontHeight + 4;
        double balance = ClientEconomyData.getBalance();
        String balStr = String.format("Bakiyen: %.0f \u2b21", balance);
        boolean canAfford = clampedAmt >= 1 && balance >= buyPopupItem.getPrice() * clampedAmt;
        ctx.drawText(textRenderer, Text.literal(balStr),
                safeX2 - textRenderer.getWidth(balStr) / 2, balY,
                canAfford ? 0x336633 : 0xCC3333, false);

        // Envanter uyarısı
        int warnY = balY + textRenderer.fontHeight + 3;
        boolean canFit = invSpace >= clampedAmt;
        if (!canFit && buyPopupItem.getStock() > 0) {
            String warnStr = invSpace <= 0
                    ? "Envanter dolu!"
                    : "Envanter: en fazla " + invSpace + " adet alabilirsin";
            ctx.drawText(textRenderer, Text.literal(warnStr),
                    safeX2 - textRenderer.getWidth(warnStr) / 2, warnY, 0xFFCC4400, false);
        }

        // Satın Al butonu
        int buyBtnH = 24;
        int buyBtnY = listY + listH - buyBtnH - 8;
        int buyBtnW = 150;
        int buyBtnX = listX + (listW - buyBtnW) / 2;
        boolean btnEnabled = buyPopupItem.getStock() > 0 && canAfford && canFit;
        boolean btnHov = btnEnabled && mouseX >= buyBtnX && mouseX < buyBtnX + buyBtnW
                      && mouseY >= buyBtnY && mouseY < buyBtnY + buyBtnH;
        ctx.fill(buyBtnX, buyBtnY, buyBtnX + buyBtnW, buyBtnY + buyBtnH,
                !btnEnabled ? 0x55604000 : (btnHov ? 0xFFBB9900 : 0xFF806000));
        ctx.fill(buyBtnX, buyBtnY, buyBtnX + buyBtnW, buyBtnY + 1, 0xFFFFD080);
        ctx.fill(buyBtnX, buyBtnY + buyBtnH - 1, buyBtnX + buyBtnW, buyBtnY + buyBtnH, 0xFFFFD080);
        ctx.fill(buyBtnX, buyBtnY, buyBtnX + 1, buyBtnY + buyBtnH, 0xFFFFD080);
        ctx.fill(buyBtnX + buyBtnW - 1, buyBtnY, buyBtnX + buyBtnW, buyBtnY + buyBtnH, 0xFFFFD080);
        String buyLbl = !btnEnabled
                ? (buyPopupItem.getStock() <= 0 ? "Stok Yok"
                   : !canFit ? "Envanter Dolu" : "Yetersiz Bakiye")
                : "\u2713  Satın Al";
        ctx.drawText(textRenderer, Text.literal(buyLbl),
                buyBtnX + (buyBtnW - textRenderer.getWidth(buyLbl)) / 2,
                buyBtnY + (buyBtnH - textRenderer.fontHeight) / 2,
                btnEnabled ? 0xFFFFEE88 : 0xFF887755, false);
    }

    /** Envanterde bu item için kaç adet daha sığar. */
    private int availableInventorySpace(ItemStack prototype) {
        if (client == null || client.player == null || prototype.isEmpty()) return 64;
        net.minecraft.entity.player.PlayerInventory inv = client.player.getInventory();
        int maxCount = prototype.getItem().getMaxCount();
        int available = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = inv.getStack(i);
            if (slot.isEmpty()) available += maxCount;
            else if (ItemStack.canCombine(slot, prototype))
                available += Math.max(0, maxCount - slot.getCount());
        }
        // Offhand
        ItemStack offhand = inv.getStack(40);
        if (offhand.isEmpty()) available += maxCount;
        else if (ItemStack.canCombine(offhand, prototype))
            available += Math.max(0, maxCount - offhand.getCount());
        return available;
    }

    // ── Pazar item popup (book-medium.png overlay) ────────────────────────

    private void renderPazarItemPopup(DrawContext ctx, int mouseX, int mouseY) {
        if (pazarPopupItem == null) { pazarPopupOpen = false; return; }

        // 1. Karartı
        ctx.fill(0, 0, width, height, 0xAA000000);

        // 2. book-medium.png — ekran ortasında
        int bmW = sc(1030);
        int bmH = sc(1080);
        int bmX = (width  - bmW) / 2;
        int bmY = (height - bmH) / 2;
        NineSliceRenderer.drawFullTexture(ctx, BOOK_MEDIUM_TEX, bmX, bmY, bmW, bmH, 1030, 1080);

        // 3. Kapatma butonu — sağ üst köşe
        int closeS = Math.max(16, sc(38));
        int closeX = bmX + bmW - closeS - 4;
        int closeY = bmY + sc(70);
        boolean closeHov = mouseX >= closeX && mouseX < closeX + closeS
                        && mouseY >= closeY && mouseY < closeY + closeS;
        ctx.fill(closeX, closeY, closeX + closeS, closeY + closeS,
                closeHov ? 0xCCDD3333 : 0x99882222);
        ctx.fill(closeX,            closeY,            closeX + closeS, closeY + 1,            0xFFAA6655);
        ctx.fill(closeX,            closeY + closeS-1, closeX + closeS, closeY + closeS,        0xFFAA6655);
        ctx.fill(closeX,            closeY,            closeX + 1,      closeY + closeS,        0xFFAA6655);
        ctx.fill(closeX + closeS-1, closeY,            closeX + closeS, closeY + closeS,        0xFFAA6655);
        String closeLbl = "\u2715";
        ctx.drawText(textRenderer, Text.literal(closeLbl),
                closeX + (closeS - textRenderer.getWidth(closeLbl)) / 2,
                closeY + (closeS - textRenderer.fontHeight) / 2,
                0xFFFFFFFF, false);

        // 4. İçerik — safe zone içinde: başlık + listing listesi + bul butonu
        int listX = bmX + sc(198);
        int listY = bmY + sc(230);
        int listW = sc(634);
        int listH = sc(619);
        int safeX2 = listX + listW / 2; // yatay merkez

        // Item frame + ikon
        int frameDisp = 32;
        int frameX    = safeX2 - frameDisp / 2;
        int frameY    = listY + 4;
        NineSliceRenderer.drawFullTexture(ctx, ITEM_FRAME_TEX, frameX, frameY, frameDisp, frameDisp, 32, 32);

        ItemStack popStack = itemStackFor(pazarPopupItem.getItemId());
        if (!popStack.isEmpty()) {
            MatrixStack ms = ctx.getMatrices();
            ms.push();
            ms.translate(frameX + (frameDisp - 16) / 2, frameY + (frameDisp - 16) / 2, 0);
            ctx.drawItem(popStack, 0, 0);
            ms.pop();
        }

        // İsim + MC etiketi
        int nameY = frameY + frameDisp + 3;
        ctx.drawText(textRenderer, Text.literal(pazarPopupItem.getDisplayName()),
                safeX2 - textRenderer.getWidth(pazarPopupItem.getDisplayName()) / 2,
                nameY, COLOR_LABEL, false);

        String rawId = pazarPopupItem.getItemId().contains("|")
                ? pazarPopupItem.getItemId().split("\\|")[0]
                : pazarPopupItem.getItemId();
        String mcId = rawId.contains(":") ? rawId : "minecraft:" + rawId;
        ctx.drawText(textRenderer, Text.literal(mcId),
                safeX2 - textRenderer.getWidth(mcId) / 2,
                nameY + textRenderer.fontHeight + 2, COLOR_DIM, false);

        // "Satış İlanları" alt başlık
        int headingY = nameY + textRenderer.fontHeight * 2 + 7;
        String heading = "── Satış İlanları ──";
        ctx.drawText(textRenderer, Text.literal(heading),
                safeX2 - textRenderer.getWidth(heading) / 2,
                headingY, COLOR_SUBTITLE, false);

        // 5. Listing listesi
        int actualListY = headingY + textRenderer.fontHeight + 4;
        int btnH2  = 20;
        int actualListH = listY + listH - actualListY - btnH2 - 8;
        int rowH   = Math.max(22, Math.min(50, actualListH / 5));
        int maxVis = Math.max(1, actualListH / rowH);

        List<PazarListing> listings = buildPazarListings();

        if (listings.isEmpty()) {
            String noMsg = "Bu item için satış ilanı bulunamadı.";
            ctx.drawText(textRenderer, Text.literal(noMsg),
                    listX + (listW - textRenderer.getWidth(noMsg)) / 2,
                    actualListY + actualListH / 2, COLOR_DIM, false);
        } else {
            int maxSc2 = Math.max(0, listings.size() - maxVis);
            pazarListingScroll = Math.min(pazarListingScroll, maxSc2);

            // En ucuz + popüler indeksleri bul
            int cheapIdx = -1; double cheapPrice = Double.MAX_VALUE;
            int popIdx   = -1; int   popStock    = -1;
            for (int i = 0; i < listings.size(); i++) {
                PazarListing l = listings.get(i);
                if (l.price() < cheapPrice) { cheapPrice = l.price(); cheapIdx = i; }
                if (l.stock() > popStock)   { popStock   = l.stock(); popIdx   = i; }
            }

            int ry = actualListY;
            for (int i = pazarListingScroll; i < Math.min(listings.size(), pazarListingScroll + maxVis); i++) {
                PazarListing l = listings.get(i);
                boolean sel = pazarSelectedListing == i;
                boolean hov = mouseX >= listX && mouseX < listX + listW
                           && mouseY >= ry     && mouseY < ry + rowH;

                ctx.fill(listX, ry, listX + listW, ry + rowH,
                        sel ? 0x44AA6600 : (hov ? 0x1999AA44 : 0x11000000));
                ctx.fill(listX, ry + rowH - 1, listX + listW, ry + rowH, 0x33AA8844);

                // Satıcı ikonu
                int headS = Math.min(20, rowH - 4);
                int headX = listX + 6;
                int headY = ry + (rowH - headS) / 2;
                if (l.isServerMarket()) {
                    ctx.fill(headX, headY, headX + headS, headY + headS, 0xFF224466);
                    ctx.fill(headX + 1, headY + 1, headX + headS - 1, headY + headS - 1, 0xFF335577);
                    String sv = "S";
                    ctx.drawText(textRenderer, Text.literal(sv),
                            headX + (headS - textRenderer.getWidth(sv)) / 2,
                            headY + (headS - textRenderer.fontHeight) / 2,
                            0xFFAADDFF, false);
                } else {
                    drawPlayerHead(ctx, l.sellerName(), headX, headY, headS);
                }

                int tx = headX + headS + 6;
                int nameRow = ry + (rowH - textRenderer.fontHeight * 2 - 2) / 2;
                String sellerLabel = l.isServerMarket() ? "Sunucu Pazarı" : l.sellerName();
                // Cream arka plan üzerinde okunabilir koyu renkler
                ctx.drawText(textRenderer, Text.literal(sellerLabel), tx, nameRow,
                        l.isServerMarket() ? 0x1155AA : 0x3D2200, false);
                String stockStr = "Stok: " + l.stock();
                ctx.drawText(textRenderer, Text.literal(stockStr), tx, nameRow + textRenderer.fontHeight + 2,
                        l.stock() > 0 ? 0x556633 : 0xCC3333, false);

                // Fiyat (sağa hizalı) — koyu altın, cream üzerinde okunabilir
                String priceStr = String.format("%.0f \u2b21", l.price());
                int priceX = listX + listW - textRenderer.getWidth(priceStr) - 6;
                ctx.drawText(textRenderer, Text.literal(priceStr), priceX, ry + (rowH - textRenderer.fontHeight) / 2,
                        0x8B6000, false);

                // Rozet
                int badgeRX = priceX - 4;
                if (i == cheapIdx && i != popIdx) {
                    String badge = " En Ucuz ";
                    int bw = textRenderer.getWidth(badge) + 4;
                    badgeRX -= bw;
                    ctx.fill(badgeRX, ry + 3, badgeRX + bw, ry + rowH - 3, 0xFF226622);
                    ctx.drawText(textRenderer, Text.literal(badge), badgeRX + 2,
                            ry + (rowH - textRenderer.fontHeight) / 2, 0xFF99FF99, false);
                } else if (i == popIdx && l.stock() > 0) {
                    String badge = " Popüler ";
                    int bw = textRenderer.getWidth(badge) + 4;
                    badgeRX -= bw;
                    ctx.fill(badgeRX, ry + 3, badgeRX + bw, ry + rowH - 3, 0xFF774422);
                    ctx.drawText(textRenderer, Text.literal(badge), badgeRX + 2,
                            ry + (rowH - textRenderer.fontHeight) / 2, 0xFFFFCC88, false);
                }

                ry += rowH;
            }
        }

        // 6. "Bul" butonu — safe zone içinde alt kısım
        int btnW = 90;
        int btnH = btnH2;
        int btnX = listX + (listW - btnW) / 2;
        int btnY = listY + listH - btnH - 4;
        List<PazarListing> bListings = listings;
        boolean btnEnabled = pazarSelectedListing >= 0
                && pazarSelectedListing < bListings.size()
                && bListings.get(pazarSelectedListing).stock() > 0;
        boolean btnHov = btnEnabled && mouseX >= btnX && mouseX < btnX + btnW
                      && mouseY >= btnY && mouseY < btnY + btnH;
        ctx.fill(btnX, btnY, btnX + btnW, btnY + btnH,
                !btnEnabled ? 0x55604000 : (btnHov ? 0xFFBB9900 : 0xFF806000));
        ctx.fill(btnX, btnY, btnX + btnW, btnY + 1, 0xFFFFD080);
        ctx.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, 0xFFFFD080);
        ctx.fill(btnX, btnY, btnX + 1, btnY + btnH, 0xFFFFD080);
        ctx.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, 0xFFFFD080);
        String btnTxt = "\uD83D\uDCCD  Bul";
        ctx.drawText(textRenderer, Text.literal(btnTxt),
                btnX + (btnW - textRenderer.getWidth(btnTxt)) / 2,
                btnY + (btnH - textRenderer.fontHeight) / 2,
                btnEnabled ? 0xFFFFFFFF : 0xFF888888, false);
    }

    // ── Pazar yardımcı metotlar ───────────────────────────────────────────

    private List<String> pazarCategories() {
        return ClientMarketData.items.stream()
                .map(ClientMarketItem::getCategory)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private void updatePazarFiltered() {
        List<ClientMarketItem> base;
        if (!pazarSearchQuery.isBlank()) {
            String q = pazarSearchQuery.toLowerCase(Locale.ROOT).trim();
            base = ClientMarketData.items.stream()
                    .filter(i -> i.getDisplayName().toLowerCase(Locale.ROOT).contains(q)
                              || i.getItemId().toLowerCase(Locale.ROOT).contains(q))
                    .collect(Collectors.toCollection(ArrayList::new));
        } else {
            base = ClientMarketData.items.stream()
                    .filter(i -> subCatMatches(pazarSelectedSubCat, i))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        base.sort(Comparator.comparing(ClientMarketItem::getDisplayName));
        pazarFiltered = base;
        pazarItemScroll = 0;
    }

    private boolean subCatMatches(PazarSubCat sub, ClientMarketItem item) {
        if (sub == null) return true;  // Tümü
        // Eğer idFilter boşsa sadece catFilter'a bak
        if (sub.idFilter().isBlank()) {
            return sub.catFilter().isBlank() || item.getCategory().equals(sub.catFilter());
        }
        // idFilter doluysa: catFilter varsa kategoriyi de kontrol et
        boolean catOk = sub.catFilter().isBlank() || item.getCategory().equals(sub.catFilter());
        if (!catOk) return false;
        String id = item.getItemId().toLowerCase(Locale.ROOT);
        String[] patterns = sub.idFilter().split(",");
        return java.util.Arrays.stream(patterns).anyMatch(p -> id.contains(p.trim()));
    }

    private List<PazarListing> buildPazarListings() {
        List<PazarListing> result = new ArrayList<>();
        if (pazarPopupItem == null) return result;
        // Sunucu pazarı ilanı
        if (pazarPopupItem.getStock() > 0) {
            result.add(new PazarListing(true, "Sunucu Pazarı", "", pazarPopupItem.getPrice(), pazarPopupItem.getStock()));
        }
        // Oyuncu mağazası ilanları
        String targetId = pazarPopupItem.getItemId();
        for (AllShopListingsPacket.Entry e : ClientMarketData.shopListings) {
            if (e.itemId().equals(targetId)) {
                result.add(new PazarListing(false, e.ownerName(), e.ownerUuid(), e.price(), e.stock()));
            }
        }
        result.sort(Comparator.comparingInt((PazarListing l) -> l.stock() > 0 ? 0 : 1)
                .thenComparingDouble(PazarListing::price));
        return result;
    }

    private ItemStack itemStackFor(String itemId) {
        try {
            if (itemId.contains("|")) {
                String[] parts = itemId.split("\\|");
                // Büyü kitabı: minecraft:enchanted_book|enchant_id|level
                if (parts.length == 3 && "minecraft:enchanted_book".equals(parts[0])) {
                    net.minecraft.enchantment.Enchantment enchant =
                            Registries.ENCHANTMENT.get(new Identifier("minecraft", parts[1]));
                    if (enchant != null) {
                        int level = Integer.parseInt(parts[2]);
                        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                        net.minecraft.item.EnchantedBookItem.addEnchantment(book,
                                new net.minecraft.enchantment.EnchantmentLevelEntry(enchant, level));
                        return book;
                    }
                    return new ItemStack(Items.ENCHANTED_BOOK);
                }
                // İksir: minecraft:potion|swiftness vb.
                if (parts.length == 2) {
                    net.minecraft.util.Identifier baseId = net.minecraft.util.Identifier.tryParse(parts[0]);
                    if (baseId != null) {
                        Item baseItem = Registries.ITEM.get(baseId);
                        if (baseItem != Items.AIR) {
                            ItemStack stack = new ItemStack(baseItem);
                            net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
                            nbt.putString("Potion", "minecraft:" + parts[1]);
                            stack.setNbt(nbt);
                            return stack;
                        }
                    }
                }
                return ItemStack.EMPTY;
            }
            net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(itemId);
            if (id == null) return ItemStack.EMPTY;
            Item item = Registries.ITEM.get(id);
            return new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ── Takas isteği gönderme ─────────────────────────────────────────────

    private void sendTradeOffer() {
        if (tradeSelectedPlayer == null || tradeSelectedPlayer.isEmpty()) return;
        assert client != null && client.player != null;
        client.player.networkHandler.sendCommand("trade offer " + tradeSelectedPlayer);
        client.player.sendMessage(
                Text.literal("§a" + tradeSelectedPlayer + " §7adlı oyuncuya takas isteği gönderildi."),
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
