package com.cogtrade.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import com.cogtrade.network.AllShopListingsPacket;
import com.cogtrade.network.RequestAllShopListingsPacket;
import com.cogtrade.network.RequestLocateTradePostPacket;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.*;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class MarketScreen extends Screen {

    private enum SortMode {
        NAME_ASC("Ad ↑"),
        NAME_DESC("Ad ↓"),
        PRICE_ASC("Fiyat ↑"),
        PRICE_DESC("Fiyat ↓"),
        STOCK_ASC("Stok ↑"),
        STOCK_DESC("Stok ↓"),
        INVENTORY_FIRST("Env ↑");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }
    }

    private final List<ClientMarketItem> allItems;
    private final boolean canBuy;
    private final double sellPriceMultiplier;

    private static final String SEARCH_CATEGORY_PREFIX = "🔎 Arama: ";
    private static final String FAVORITES_CATEGORY = "★ Favoriler";
    private static final String RECENT_VIEWED_CATEGORY = "🕘 Son Bakılanlar";
    private static final String RECENT_TRADED_CATEGORY = "⇄ Son İşlemler";

    private List<String> categories = new ArrayList<>();
    private List<ClientMarketItem> filteredItems = new ArrayList<>();
    private int selectedCategoryIndex = 0;
    private int selectedItemIndex = -1;
    private int gridScrollOffset = 0;

    private int panelX, panelY, panelW, panelH;
    private int catX, catY, catW, catH;
    private int gridX, gridY, gridW, gridH;
    private int detailX, detailY, detailW, detailH;
    private int footerY, footerH;

    private int gridCols, gridRows;
    private static final int SLOT_SIZE = 36;
    private static final int SLOT_GAP = 4;

    private int tradeAmount = 1;
    private boolean editingAmount = false;
    private String amountBuffer = "";
    private int cursorBlink = 0;

    private boolean holdingMinus = false;
    private boolean holdingPlus = false;
    private int holdTicks = 0, repeatTicks = 0;
    private static final int HOLD_DELAY = 40, REPEAT_INTERVAL = 2;

    private String searchQuery = "";
    private TextFieldWidget searchField;

    private ButtonWidget buyButton;
    private ButtonWidget sellButton;
    private ButtonWidget locateButton;
    private ButtonWidget sortButton;

    private SortMode sortMode = SortMode.NAME_ASC;

    private static final int C_OUTER        = 0xFF282828;
    private static final int C_INNER        = 0xFF1C1C1C;
    private static final int C_SLOT         = 0xFF373737;
    private static final int C_SLOT_HOVER   = 0xFF505050;
    private static final int C_SLOT_SELECT  = 0xFF8B6914;
    private static final int C_SLOT_BORDER  = 0xFF141414;
    private static final int C_SLOT_SHINE   = 0xFF5A5A5A;
    private static final int C_TEXT         = 0xFFDDDDDD;
    private static final int C_SUBTEXT      = 0xFF888888;
    private static final int C_GOLD         = 0xFFFFD54F;
    private static final int C_GREEN        = 0xFF66BB6A;
    private static final int C_RED          = 0xFFEF5350;
    private static final int C_AMBER        = 0xFFFFA726;
    private static final int C_BLUE         = 0xFF64B5F6;
    private static final int C_HIGHLIGHT    = 0xFFAD8F23;
    private static final int C_CATEGORY_SEL = 0xFF3A2E0E;
    private static final int C_CATEGORY_HOV = 0xFF2A2A2A;
    private static final int C_SEPARATOR    = 0xFF404040;

    private static final java.util.Map<String, String> CAT_ICONS = new java.util.LinkedHashMap<>();
    static {
        CAT_ICONS.put(FAVORITES_CATEGORY, "★");
        CAT_ICONS.put(RECENT_VIEWED_CATEGORY, "🕘");
        CAT_ICONS.put(RECENT_TRADED_CATEGORY, "⇄");
        CAT_ICONS.put("Building Blocks", "🧱");
        CAT_ICONS.put("Natural Blocks", "🌿");
        CAT_ICONS.put("Functional Blocks", "⚙");
        CAT_ICONS.put("Redstone Blocks", "🔴");
        CAT_ICONS.put("Combat", "⚔");
        CAT_ICONS.put("Tools", "⛏");
        CAT_ICONS.put("Food & Drinks", "🍖");
        CAT_ICONS.put("Ingredients", "🧪");
        CAT_ICONS.put("Spawn Eggs", "🥚");
        CAT_ICONS.put("Misc", "📦");
    }

    private String flashText = "";
    private int flashColor = C_GREEN;
    private int flashTicks = 0;
    private int flashItemId = -1;

    private int lastSelectedFilteredIndex = -1;

    // ── Oyuncu Pazarı (Player Market) ───────────────────────────────────────
    private static final String PLAYER_MARKET_CATEGORY = "🏪 Oyuncu Pazarı";
    private static final int C_CYAN    = 0xFF26C6DA;
    private static final int C_CYAN_BG = 0xFF0D2B35;
    private static final int C_CYAN_HL = 0xFF1A4A5A;
    private List<AllShopListingsPacket.Entry> playerShopEntries = new ArrayList<>();
    private Map<String, List<AllShopListingsPacket.Entry>> shopByItem = new LinkedHashMap<>();
    private List<String> shopItemIds = new ArrayList<>();       // filtered unique ids
    private boolean playerShopLoaded = false;
    private boolean playerShopLoading = false;
    private int shopRefreshTick = 0;           // periyodik yenileme sayacı
    private String selectedShopItemId = null;
    private int selectedShopSellerIdx = -1;
    private int shopGridScroll = 0;
    private int shopSellerScroll = 0;
    private int shopTradeAmount = 1;
    private ButtonWidget locatePostButton;

    public MarketScreen(List<ClientMarketItem> items, boolean canBuy, double sellPriceMultiplier) {
        super(Text.literal("Sunucu Pazarı"));
        this.allItems = items;
        this.canBuy = canBuy;
        this.sellPriceMultiplier = sellPriceMultiplier;
    }

    @Override
    protected void init() {
        computeLayout();
        rebuildCategories();

        searchField = new TextFieldWidget(
                textRenderer,
                gridX + 90, panelY + 3,
                gridW - 94, 16,
                Text.literal("")
        );
        searchField.setMaxLength(48);
        searchField.setSuggestion("Ara...");
        searchField.setText(searchQuery);
        searchField.setChangedListener(text -> {
            searchQuery = text;
            searchField.setSuggestion(text.isEmpty() ? "Ara..." : "");
            selectedItemIndex = -1;
            gridScrollOffset = 0;
            tradeAmount = 1;
            rebuildCategories();
            filterItems();
            updateActionButtons();
        });
        addDrawableChild(searchField);
        searchField.setFocused(false);
        setFocused(null);

        int buttonBaseX = detailX + 6;
        int buttonW = detailW - 12;

        sortButton = ButtonWidget.builder(Text.literal(sortMode.label), btn -> {
            cycleSortMode();
        }).dimensions(gridX, panelY + 3, 86, 16).build();
        sortButton.setTooltip(Tooltip.of(Text.literal("Sıralamayı değiştir")));
        addDrawableChild(sortButton);

        buyButton = ButtonWidget.builder(Text.literal("Satın Al"), btn -> {
            if (!editingAmount) handleBuy();
        }).dimensions(buttonBaseX, footerY + 30, buttonW, 18).build();
        addDrawableChild(buyButton);

        sellButton = ButtonWidget.builder(Text.literal("Sat"), btn -> {
            if (!editingAmount) handleSell();
        }).dimensions(buttonBaseX, footerY + 52, buttonW, 18).build();
        addDrawableChild(sellButton);

        locateButton = ButtonWidget.builder(Text.literal("Market'i Bul"), btn -> {
            net.minecraft.network.PacketByteBuf buf =
                    net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    com.cogtrade.network.LocateRequestPacket.ID, buf);
            close();
        }).dimensions(buttonBaseX, footerY + 52, buttonW, 18).build();
        locateButton.setTooltip(Tooltip.of(Text.literal("50 blok içindeki en yakın Market Bloğunu işaretle")));
        addDrawableChild(locateButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), btn -> close())
                .dimensions(panelX + panelW - 17, panelY + 4, 13, 13)
                .build());

        // Oyuncu Pazarı — sadece Konumlandır butonu (Market'ten satın alma kaldırıldı)
        locatePostButton = ButtonWidget.builder(
                Text.literal("§e⊕ Trade Post'u Bul"),
                btn -> handleLocatePost())
                .dimensions(buttonBaseX, footerY + 30, buttonW, 20).build();
        locatePostButton.setTooltip(Tooltip.of(Text.literal(
                "Seçili satıcının Trade Post'unu dünyada işaretle ve oradan satın al")));
        addDrawableChild(locatePostButton);

        filterItems();
        updateActionButtons();
    }

    private void computeLayout() {
        panelW = Math.min(860, Math.max(620, width - 50));
        panelH = Math.min(500, Math.max(360, height - 40));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int headerH = 22;
        footerH = 90;
        footerY = panelY + panelH - footerH;

        int contentY = panelY + headerH + 4;
        int contentH = panelH - headerH - footerH - 4;

        catW = Math.max(125, Math.min(150, panelW / 5));
        catX = panelX + 4;
        catY = contentY;
        catH = contentH;

        detailW = Math.max(165, Math.min(190, panelW / 4));
        detailX = panelX + panelW - detailW - 4;
        detailY = contentY;
        detailH = contentH;

        int gap = 4;
        gridX = catX + catW + gap;
        gridY = contentY;
        gridW = detailX - gridX - gap;
        gridH = contentH;

        gridCols = Math.max(1, (gridW - 8) / (SLOT_SIZE + SLOT_GAP));
        gridRows = Math.max(1, (gridH - 8) / (SLOT_SIZE + SLOT_GAP));
    }

    private void cycleSortMode() {
        SortMode[] values = SortMode.values();
        sortMode = values[(sortMode.ordinal() + 1) % values.length];
        sortButton.setMessage(Text.literal(sortMode.label));
        filterItems();
        updateActionButtons();
    }

    private boolean isPlayerMarketSelected() {
        return !categories.isEmpty()
                && selectedCategoryIndex >= 0
                && selectedCategoryIndex < categories.size()
                && categories.get(selectedCategoryIndex).equals(PLAYER_MARKET_CATEGORY);
    }

    private void rebuildCategories() {
        List<String> base = allItems.stream()
                .map(ClientMarketItem::getCategory)
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));

        categories = new ArrayList<>();

        // Oyuncu Pazarı her zaman ilk kategori olarak eklenir
        categories.add(PLAYER_MARKET_CATEGORY);

        if (!MarketUiState.getFavorites().isEmpty()) {
            categories.add(FAVORITES_CATEGORY);
        }
        if (!MarketUiState.getRecentViewed().isEmpty()) {
            categories.add(RECENT_VIEWED_CATEGORY);
        }
        if (!MarketUiState.getRecentTraded().isEmpty()) {
            categories.add(RECENT_TRADED_CATEGORY);
        }
        if (!searchQuery.isBlank()) {
            categories.add(SEARCH_CATEGORY_PREFIX + searchQuery);
        }

        categories.addAll(base);

        if (categories.isEmpty()) {
            selectedCategoryIndex = 0;
        } else if (selectedCategoryIndex >= categories.size()) {
            selectedCategoryIndex = 0;
        }

        if (!searchQuery.isBlank() && !isSearchCat()) {
            int searchIndex = categories.indexOf(SEARCH_CATEGORY_PREFIX + searchQuery);
            if (searchIndex >= 0) selectedCategoryIndex = searchIndex;
        }
    }

    private boolean isSearchCat() {
        return !categories.isEmpty()
                && selectedCategoryIndex >= 0
                && selectedCategoryIndex < categories.size()
                && categories.get(selectedCategoryIndex).startsWith(SEARCH_CATEGORY_PREFIX);
    }

    private void filterItems() {
        if (categories.isEmpty()) {
            filteredItems = new ArrayList<>();
            return;
        }

        // Oyuncu Pazarı seçiliyse ayrı filtre
        if (isPlayerMarketSelected()) {
            filterShopItems();
            filteredItems = new ArrayList<>(); // normal grid boş
            return;
        }

        String selectedCategory = categories.get(selectedCategoryIndex);
        List<ClientMarketItem> base;

        if (selectedCategory.equals(FAVORITES_CATEGORY)) {
            Set<String> favs = MarketUiState.getFavorites();
            base = allItems.stream()
                    .filter(i -> favs.contains(i.getItemId()))
                    .collect(Collectors.toCollection(ArrayList::new));
        } else if (selectedCategory.equals(RECENT_VIEWED_CATEGORY)) {
            Deque<String> viewed = MarketUiState.getRecentViewed();
            base = sortByOrder(allItems, viewed);
        } else if (selectedCategory.equals(RECENT_TRADED_CATEGORY)) {
            Deque<String> traded = MarketUiState.getRecentTraded();
            base = sortByOrder(allItems, traded);
        } else if (isSearchCat()) {
            base = new ArrayList<>(allItems);
        } else {
            base = allItems.stream()
                    .filter(i -> i.getCategory().equals(selectedCategory))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        filteredItems = applySearchAndSmartFilters(base);
        sortFilteredItems();

        selectedItemIndex = -1;
        gridScrollOffset = 0;
        tradeAmount = 1;
        editingAmount = false;
        amountBuffer = "";
    }

    private List<ClientMarketItem> sortByOrder(List<ClientMarketItem> source, Deque<String> orderedIds) {
        List<ClientMarketItem> result = new ArrayList<>();
        for (String id : orderedIds) {
            for (ClientMarketItem item : source) {
                if (item.getItemId().equals(id)) {
                    result.add(item);
                    break;
                }
            }
        }
        return result;
    }

    private List<ClientMarketItem> applySearchAndSmartFilters(List<ClientMarketItem> items) {
        if (searchQuery.isBlank()) return items;

        String raw = searchQuery.toLowerCase(Locale.ROOT).trim();
        String[] tokens = raw.split("\\s+");

        List<ClientMarketItem> result = new ArrayList<>(items);

        for (String token : tokens) {
            if (token.isBlank()) continue;

            if (token.equals("fav")) {
                result = result.stream()
                        .filter(i -> MarketUiState.isFavorite(i.getItemId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                continue;
            }

            if (token.equals("recent")) {
                result = result.stream()
                        .filter(i -> MarketUiState.getRecentViewed().contains(i.getItemId())
                                || MarketUiState.getRecentTraded().contains(i.getItemId()))
                        .collect(Collectors.toCollection(ArrayList::new));
                continue;
            }

            if (token.equals("canbuy")) {
                result = result.stream()
                        .filter(this::canCurrentlyBuy)
                        .collect(Collectors.toCollection(ArrayList::new));
                continue;
            }

            if (token.equals("cansell")) {
                result = result.stream()
                        .filter(i -> countInInventory(i.getItemId()) > 0)
                        .collect(Collectors.toCollection(ArrayList::new));
                continue;
            }

            if (token.startsWith("price<")) {
                double v = parseDoubleSafe(token.substring(6), Double.NaN);
                if (!Double.isNaN(v)) {
                    result = result.stream()
                            .filter(i -> i.getPrice() < v)
                            .collect(Collectors.toCollection(ArrayList::new));
                }
                continue;
            }

            if (token.startsWith("price>")) {
                double v = parseDoubleSafe(token.substring(6), Double.NaN);
                if (!Double.isNaN(v)) {
                    result = result.stream()
                            .filter(i -> i.getPrice() > v)
                            .collect(Collectors.toCollection(ArrayList::new));
                }
                continue;
            }

            if (token.startsWith("stock<")) {
                int v = parseIntSafe(token.substring(6), Integer.MIN_VALUE);
                if (v != Integer.MIN_VALUE) {
                    result = result.stream()
                            .filter(i -> i.getStock() < v)
                            .collect(Collectors.toCollection(ArrayList::new));
                }
                continue;
            }

            if (token.startsWith("stock>")) {
                int v = parseIntSafe(token.substring(6), Integer.MIN_VALUE);
                if (v != Integer.MIN_VALUE) {
                    result = result.stream()
                            .filter(i -> i.getStock() > v)
                            .collect(Collectors.toCollection(ArrayList::new));
                }
                continue;
            }

            if (token.startsWith("inv<")) {
                int v = parseIntSafe(token.substring(4), Integer.MIN_VALUE);
                if (v != Integer.MIN_VALUE) {
                    result = result.stream()
                            .filter(i -> countInInventory(i.getItemId()) < v)
                            .collect(Collectors.toCollection(ArrayList::new));
                }
                continue;
            }

            if (token.startsWith("inv>")) {
                int v = parseIntSafe(token.substring(4), Integer.MIN_VALUE);
                if (v != Integer.MIN_VALUE) {
                    result = result.stream()
                            .filter(i -> countInInventory(i.getItemId()) > v)
                            .collect(Collectors.toCollection(ArrayList::new));
                }
                continue;
            }

            String textToken = token;
            result = result.stream()
                    .filter(i -> i.getDisplayName().toLowerCase(Locale.ROOT).contains(textToken)
                            || i.getItemId().toLowerCase(Locale.ROOT).contains(textToken)
                            || i.getCategory().toLowerCase(Locale.ROOT).contains(textToken))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        return result;
    }

    private void sortFilteredItems() {
        Comparator<ClientMarketItem> comparator = switch (sortMode) {
            case NAME_ASC -> Comparator.comparing(i -> i.getDisplayName().toLowerCase(Locale.ROOT));
            case NAME_DESC -> Comparator.comparing((ClientMarketItem i) -> i.getDisplayName().toLowerCase(Locale.ROOT)).reversed();
            case PRICE_ASC -> Comparator.comparingDouble(ClientMarketItem::getPrice)
                    .thenComparing(i -> i.getDisplayName().toLowerCase(Locale.ROOT));
            case PRICE_DESC -> Comparator.comparingDouble(ClientMarketItem::getPrice).reversed()
                    .thenComparing(i -> i.getDisplayName().toLowerCase(Locale.ROOT));
            case STOCK_ASC -> Comparator.comparingInt(ClientMarketItem::getStock)
                    .thenComparing(i -> i.getDisplayName().toLowerCase(Locale.ROOT));
            case STOCK_DESC -> Comparator.comparingInt(ClientMarketItem::getStock).reversed()
                    .thenComparing(i -> i.getDisplayName().toLowerCase(Locale.ROOT));
            case INVENTORY_FIRST -> Comparator
                    .comparingInt((ClientMarketItem i) -> countInInventory(i.getItemId()) > 0 ? 0 : 1)
                    .thenComparing(i -> i.getDisplayName().toLowerCase(Locale.ROOT));
        };
        filteredItems.sort(comparator);
    }

    private boolean canCurrentlyBuy(ClientMarketItem item) {
        if (!canBuy) return false;
        return item.getStock() > 0 && ClientEconomyData.getBalance() >= item.getPrice();
    }

    private void updateActionButtons() {
        boolean isPlayerMarket = isPlayerMarketSelected();

        // Oyuncu Pazarı modu
        if (isPlayerMarket) {
            buyButton.visible = false;
            buyButton.active  = false;
            sellButton.visible = false;
            sellButton.active  = false;
            locateButton.visible = false;
            locateButton.active  = false;

            boolean hasSeller = selectedShopSellerIdx >= 0
                    && selectedShopItemId != null
                    && shopByItem.containsKey(selectedShopItemId)
                    && selectedShopSellerIdx < shopByItem.get(selectedShopItemId).size();

            locatePostButton.visible = true;
            locatePostButton.active  = hasSeller;
            return;
        }

        // Normal mod: oyuncu-pazar butonunu gizle
        locatePostButton.visible = false;
        locatePostButton.active  = false;

        boolean hasSelection = selectedItemIndex >= 0 && selectedItemIndex < filteredItems.size();

        if (!canBuy) {
            buyButton.visible = false;
            buyButton.active = false;
            sellButton.visible = false;
            sellButton.active = false;
            locateButton.visible = true;
            locateButton.active = true;
            return;
        }

        locateButton.visible = false;
        locateButton.active = false;
        buyButton.visible = true;
        sellButton.visible = true;

        if (!hasSelection) {
            buyButton.active = false;
            sellButton.active = false;
            return;
        }

        ClientMarketItem item = filteredItems.get(selectedItemIndex);
        int inv = countInInventory(item.getItemId());
        double total = item.getPrice() * tradeAmount;

        buyButton.active = item.getStock() > 0 && ClientEconomyData.getBalance() >= total;
        sellButton.active = inv > 0;
    }

    // ── Oyuncu Pazarı veri metotları ─────────────────────────────────────────

    /** Sunucudan gelen tüm oyuncu mağaza verilerini güncelle */
    public void updatePlayerShopData(List<AllShopListingsPacket.Entry> entries) {
        playerShopEntries = new ArrayList<>(entries);
        playerShopLoaded  = true;
        playerShopLoading = false;
        buildShopByItem();
        if (isPlayerMarketSelected()) filterShopItems();
    }

    private void buildShopByItem() {
        shopByItem.clear();
        for (AllShopListingsPacket.Entry e : playerShopEntries) {
            // Stoksuz ilanları da göster (gri görünüm); satın alma sunucu tarafında kontrol edilir
            shopByItem.computeIfAbsent(e.itemId(), k -> new ArrayList<>()).add(e);
        }
        // Her item için satıcıları: stoklu olanlar önce, sonra fiyata göre
        shopByItem.values().forEach(list ->
                list.sort(Comparator.comparingInt((AllShopListingsPacket.Entry e) -> e.stock() > 0 ? 0 : 1)
                        .thenComparingDouble(AllShopListingsPacket.Entry::price)));
    }

    private void filterShopItems() {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        shopItemIds = shopByItem.keySet().stream()
                .filter(id -> q.isEmpty()
                        || getItemDisplayName(id).toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(this::getItemDisplayName))
                .collect(Collectors.toCollection(ArrayList::new));
        shopGridScroll = 0;
        // Seçimi koru eğer hala geçerliyse
        if (selectedShopItemId != null && !shopItemIds.contains(selectedShopItemId)) {
            selectedShopItemId   = null;
            selectedShopSellerIdx = -1;
        }
    }

    private String getItemDisplayName(String itemId) {
        try {
            net.minecraft.item.Item item = Registries.ITEM.get(new Identifier(itemId));
            if (item == Items.AIR) return itemId;
            String n = item.getDefaultStack().getName().getString();
            return n.isBlank() ? itemId : n;
        } catch (Exception e) { return itemId; }
    }

    private void handleLocatePost() {
        if (selectedShopItemId == null || selectedShopSellerIdx < 0) return;
        List<AllShopListingsPacket.Entry> sellers = shopByItem.get(selectedShopItemId);
        if (sellers == null || selectedShopSellerIdx >= sellers.size()) return;
        AllShopListingsPacket.Entry seller = sellers.get(selectedShopSellerIdx);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(seller.ownerUuid());
        ClientPlayNetworking.send(RequestLocateTradePostPacket.ID, buf);
        close();
    }

    @Override
    public void tick() {
        super.tick();
        cursorBlink++;

        if (holdingMinus || holdingPlus) {
            holdTicks++;
            if (holdTicks >= HOLD_DELAY) {
                repeatTicks++;
                if (repeatTicks >= REPEAT_INTERVAL) {
                    if (holdingMinus) adjustAmount(-1);
                    if (holdingPlus) adjustAmount(1);
                    repeatTicks = 0;
                }
            }
        }

        if (flashTicks > 0) {
            flashTicks--;
            if (flashTicks == 0) {
                flashText = "";
                flashItemId = -1;
            }
        }

        // Oyuncu Pazarı periyodik otomatik yenileme (her ~10 saniye)
        if (isPlayerMarketSelected() && playerShopLoaded) {
            if (++shopRefreshTick >= 200) {
                shopRefreshTick = 0;
                PacketByteBuf buf = PacketByteBufs.create();
                ClientPlayNetworking.send(RequestAllShopListingsPacket.ID, buf);
            }
        } else {
            shopRefreshTick = 0;
        }

        updateActionButtons();
    }

    private void adjustAmount(int delta) {
        if (delta > 0) {
            if (selectedItemIndex < 0 || selectedItemIndex >= filteredItems.size()) return;

            ClientMarketItem item = filteredItems.get(selectedItemIndex);
            int buyMax = Math.min((int) (ClientEconomyData.getBalance() / Math.max(0.0001, item.getPrice())), item.getStock());
            int sellMax = countInInventory(item.getItemId());
            int max = Math.max(1, Math.max(buyMax, sellMax));

            if (tradeAmount < max) tradeAmount++;
        } else {
            if (tradeAmount > 1) tradeAmount--;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);

        drawFrame(ctx, panelX, panelY, panelW, panelH);
        drawHeader(ctx);
        drawCategoryPanel(ctx, mouseX, mouseY);

        if (isPlayerMarketSelected()) {
            drawPlayerMarketGrid(ctx, mouseX, mouseY);
            drawPlayerMarketDetail(ctx, mouseX, mouseY);
        } else {
            drawGrid(ctx, mouseX, mouseY);
            drawDetailPanel(ctx, mouseX, mouseY);
            drawGridTooltip(ctx, mouseX, mouseY);
        }

        drawFooterPanel(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawFrame(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x55000000);
        ctx.fill(x, y, x + w, y + h, C_SLOT_BORDER);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, C_OUTER);
        ctx.fill(x + 2, y + 2, x + w - 2, y + 3, C_SLOT_SHINE);
        ctx.fill(x + 2, y + 2, x + 3, y + h - 2, C_SLOT_SHINE);
        ctx.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, C_SLOT_BORDER);
        ctx.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, C_SLOT_BORDER);
        ctx.fill(x + 3, y + 3, x + w - 3, y + h - 3, C_INNER);
    }

    private void drawSection(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, C_SLOT_BORDER);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, C_OUTER);
        ctx.fill(x + 2, y + 2, x + w - 2, y + h - 2, C_INNER);
    }

    private void drawHeader(DrawContext ctx) {
        int hY2 = panelY + 22;
        ctx.fill(panelX + 3, panelY + 3, panelX + panelW - 3, hY2, 0xFF1E1E1E);
        ctx.fill(panelX + 3, hY2, panelX + panelW - 3, hY2 + 1, C_SEPARATOR);

        String title = canBuy ? "✦ Sunucu Pazarı ✦" : "✦ Sunucu Pazarı [Göz Atma] ✦";
        ctx.drawCenteredTextWithShadow(textRenderer, title, panelX + panelW / 2, panelY + 7, C_GOLD);
    }

    private void drawCategoryPanel(DrawContext ctx, int mouseX, int mouseY) {
        drawSection(ctx, catX, catY, catW, catH);

        int listStartY = catY + 4;
        int rowH = 22;
        int maxRows = Math.max(1, (catH - 8) / rowH);

        for (int i = 0; i < categories.size() && i < maxRows; i++) {
            int rowY = listStartY + i * rowH;
            boolean hov = inBounds(mouseX, mouseY, catX + 3, rowY, catW - 6, rowH - 2);
            boolean sel = i == selectedCategoryIndex;
            String cat = categories.get(i);

            if (sel) {
                ctx.fill(catX + 3, rowY, catX + catW - 3, rowY + rowH - 2, C_CATEGORY_SEL);
                ctx.fill(catX + 3, rowY, catX + 5, rowY + rowH - 2, C_GOLD);
            } else if (hov) {
                ctx.fill(catX + 3, rowY, catX + catW - 3, rowY + rowH - 2, C_CATEGORY_HOV);
            }

            String icon = cat.startsWith(SEARCH_CATEGORY_PREFIX) ? "🔎" : CAT_ICONS.getOrDefault(cat, "•");
            String display = cat.startsWith(SEARCH_CATEGORY_PREFIX) ? "Arama" : cat;

            int col = sel ? C_GOLD : (hov ? C_TEXT : C_SUBTEXT);
            ctx.drawText(textRenderer, icon, catX + 6, rowY + 5, col, false);

            String nameStr = display;
            if (textRenderer.getWidth(nameStr) > catW - 24) {
                while (textRenderer.getWidth(nameStr + "..") > catW - 24 && nameStr.length() > 3) {
                    nameStr = nameStr.substring(0, nameStr.length() - 1);
                }
                nameStr += "..";
            }
            ctx.drawText(textRenderer, nameStr, catX + 18, rowY + 5, col, false);
        }
    }

    private void drawGrid(DrawContext ctx, int mouseX, int mouseY) {
        drawSection(ctx, gridX, gridY, gridW, gridH);

        int startX = gridX + 4;
        int startY = gridY + 4;
        int visible = gridCols * gridRows;
        int total = filteredItems.size();
        int start = gridScrollOffset * gridCols;

        if (filteredItems.isEmpty()) {
            String msg = isSearchCat() ? "Sonuç yok" : "Bu kategoride ürün yok";
            ctx.drawCenteredTextWithShadow(textRenderer, msg,
                    gridX + gridW / 2, gridY + gridH / 2 - 4, C_SUBTEXT);
            return;
        }

        for (int slot = 0; slot < visible; slot++) {
            int idx = start + slot;
            if (idx >= total) break;

            int col = slot % gridCols;
            int row = slot / gridCols;
            int sx = startX + col * (SLOT_SIZE + SLOT_GAP);
            int sy = startY + row * (SLOT_SIZE + SLOT_GAP);

            ClientMarketItem item = filteredItems.get(idx);
            boolean hov = inBounds(mouseX, mouseY, sx, sy, SLOT_SIZE, SLOT_SIZE);
            boolean sel = idx == selectedItemIndex;
            boolean sellable = countInInventory(item.getItemId()) > 0;
            boolean favorite = MarketUiState.isFavorite(item.getItemId());

            int slotColor = sel ? C_SLOT_SELECT : (hov ? C_SLOT_HOVER : C_SLOT);

            ctx.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, C_SLOT_BORDER);
            ctx.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, slotColor);

            ctx.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + 2, C_SLOT_BORDER);
            ctx.fill(sx + 1, sy + 1, sx + 2, sy + SLOT_SIZE - 1, C_SLOT_BORDER);
            ctx.fill(sx + 1, sy + SLOT_SIZE - 2, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, C_SLOT_SHINE);
            ctx.fill(sx + SLOT_SIZE - 2, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, C_SLOT_SHINE);

            ItemStack stack = getItemStack(item.getItemId());
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, sx + 10, sy + 8);
                ctx.drawItemInSlot(textRenderer, stack, sx + 10, sy + 8);
            }

            int sc = item.getStock();
            if (sc <= 0) {
                ctx.fill(sx + SLOT_SIZE - 5, sy + SLOT_SIZE - 5, sx + SLOT_SIZE - 2, sy + SLOT_SIZE - 2, C_RED);
            } else if (sc <= 16) {
                ctx.fill(sx + SLOT_SIZE - 5, sy + SLOT_SIZE - 5, sx + SLOT_SIZE - 2, sy + SLOT_SIZE - 2, C_AMBER);
            }

            if (sellable) {
                ctx.fill(sx + 2, sy + SLOT_SIZE - 5, sx + 5, sy + SLOT_SIZE - 2, C_GREEN);
            }

            if (favorite) {
                ctx.drawText(textRenderer, "★", sx + 3, sy + 2, C_GOLD, false);
            }

            if (flashTicks > 0 && item.getId() == flashItemId) {
                int alpha = Math.min(120, flashTicks * 8);
                int flash = (alpha << 24) | (flashColor & 0x00FFFFFF);
                ctx.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, flash);
            }

            if (sel) {
                ctx.fill(sx, sy, sx + SLOT_SIZE, sy + 1, C_GOLD);
                ctx.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, C_GOLD);
                ctx.fill(sx, sy, sx + 1, sy + SLOT_SIZE, C_GOLD);
                ctx.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, C_GOLD);
            }
        }

        int totalRows = (int) Math.ceil((double) total / gridCols);
        if (totalRows > gridRows) {
            int scrollBarH = gridH - 8;
            int barH = Math.max(20, scrollBarH * gridRows / totalRows);
            int barY = gridY + 4 + (scrollBarH - barH) * gridScrollOffset / Math.max(1, totalRows - gridRows);
            int barX = gridX + gridW - 6;
            ctx.fill(barX, gridY + 4, barX + 3, gridY + gridH - 4, 0xFF222222);
            ctx.fill(barX, barY, barX + 3, barY + barH, C_SEPARATOR);
        }
    }

    private void drawDetailPanel(DrawContext ctx, int mouseX, int mouseY) {
        drawSection(ctx, detailX, detailY, detailW, detailH);

        if (selectedItemIndex < 0 || selectedItemIndex >= filteredItems.size()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Bir ürün seçin", detailX + detailW / 2, detailY + detailH / 2 - 4, C_SUBTEXT);
            return;
        }

        ClientMarketItem item = filteredItems.get(selectedItemIndex);
        int x = detailX + 6;
        int y = detailY + 6;

        int bigSlotX = detailX + (detailW - 36) / 2;
        int bigSlotY = y;
        ctx.fill(bigSlotX, bigSlotY, bigSlotX + 36, bigSlotY + 36, C_SLOT_BORDER);
        ctx.fill(bigSlotX + 1, bigSlotY + 1, bigSlotX + 35, bigSlotY + 35, C_SLOT);
        ctx.fill(bigSlotX + 1, bigSlotY + 1, bigSlotX + 35, bigSlotY + 2, C_SLOT_BORDER);
        ctx.fill(bigSlotX + 1, bigSlotY + 1, bigSlotX + 2, bigSlotY + 35, C_SLOT_BORDER);
        ctx.fill(bigSlotX + 1, bigSlotY + 34, bigSlotX + 35, bigSlotY + 35, C_SLOT_SHINE);
        ctx.fill(bigSlotX + 34, bigSlotY + 1, bigSlotX + 35, bigSlotY + 35, C_SLOT_SHINE);

        ItemStack bigStack = getItemStack(item.getItemId());
        if (!bigStack.isEmpty()) {
            ctx.drawItem(bigStack, bigSlotX + 10, bigSlotY + 10);
            ctx.drawItemInSlot(textRenderer, bigStack, bigSlotX + 10, bigSlotY + 10);
        }

        boolean favorite = MarketUiState.isFavorite(item.getItemId());
        ctx.drawText(textRenderer, favorite ? "★" : "☆", detailX + detailW - 16, detailY + 8, favorite ? C_GOLD : C_SUBTEXT, false);

        y += 42;
        ctx.fill(detailX + 3, y, detailX + detailW - 3, y + 1, C_SEPARATOR);
        y += 5;

        String name = item.getDisplayName();
        if (textRenderer.getWidth(name) > detailW - 12) {
            while (textRenderer.getWidth(name + "..") > detailW - 12 && name.length() > 3) {
                name = name.substring(0, name.length() - 1);
            }
            name += "..";
        }
        ctx.drawCenteredTextWithShadow(textRenderer, name, detailX + detailW / 2, y, C_TEXT);
        y += 13;

        ctx.drawCenteredTextWithShadow(textRenderer, item.getCategory(), detailX + detailW / 2, y, C_SUBTEXT);
        y += 14;

        ctx.fill(detailX + 3, y, detailX + detailW - 3, y + 1, C_SEPARATOR);
        y += 6;

        ctx.drawText(textRenderer, "Alış:", x, y, C_SUBTEXT, false);
        String buyPrice = "⬡" + formatPrice(item.getPrice());
        ctx.drawText(textRenderer, buyPrice, detailX + detailW - 6 - textRenderer.getWidth(buyPrice), y, C_GOLD, false);
        y += 12;

        double sp = item.getPrice() * sellPriceMultiplier;
        ctx.drawText(textRenderer, "Satış:", x, y, C_SUBTEXT, false);
        String sellPrice = "⬡" + formatPrice(sp);
        ctx.drawText(textRenderer, sellPrice, detailX + detailW - 6 - textRenderer.getWidth(sellPrice), y, C_GREEN, false);
        y += 12;

        int sc = item.getStock();
        String stockStr = sc >= 9999 ? "∞" : String.valueOf(sc);
        int stockColor = sc > 32 ? C_GREEN : sc > 0 ? C_AMBER : C_RED;
        ctx.drawText(textRenderer, "Stok:", x, y, C_SUBTEXT, false);
        ctx.drawText(textRenderer, stockStr, detailX + detailW - 6 - textRenderer.getWidth(stockStr), y, stockColor, false);
        y += 12;

        int inv = countInInventory(item.getItemId());
        String invStr = String.valueOf(inv);
        ctx.drawText(textRenderer, "Envanter:", x, y, C_SUBTEXT, false);
        ctx.drawText(textRenderer, invStr, detailX + detailW - 6 - textRenderer.getWidth(invStr), y, inv > 0 ? C_GREEN : C_SUBTEXT, false);
        y += 14;

        ctx.fill(detailX + 3, y, detailX + detailW - 3, y + 1, C_SEPARATOR);
        y += 6;

        int midX = detailX + detailW / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, "Miktar", midX, y, C_SUBTEXT);
        y += 11;

        int btnSz = 14;
        int minusX = detailX + 8;
        int plusX = detailX + detailW - 8 - btnSz;
        int boxX = minusX + btnSz + 4;
        int boxW = plusX - boxX - 4;
        int btnY2 = y;

        boolean minHov = inBounds(mouseX, mouseY, minusX, btnY2, btnSz, btnSz);
        boolean plusHov = inBounds(mouseX, mouseY, plusX, btnY2, btnSz, btnSz);

        ctx.fill(minusX, btnY2, minusX + btnSz, btnY2 + btnSz, C_SLOT_BORDER);
        ctx.fill(minusX + 1, btnY2 + 1, minusX + btnSz - 1, btnY2 + btnSz - 1,
                (holdingMinus ? C_HIGHLIGHT : (minHov ? C_SLOT_HOVER : C_SLOT)));
        ctx.drawCenteredTextWithShadow(textRenderer, "-", minusX + btnSz / 2, btnY2 + 3, C_TEXT);

        ctx.fill(plusX, btnY2, plusX + btnSz, btnY2 + btnSz, C_SLOT_BORDER);
        ctx.fill(plusX + 1, btnY2 + 1, plusX + btnSz - 1, btnY2 + btnSz - 1,
                (holdingPlus ? C_HIGHLIGHT : (plusHov ? C_SLOT_HOVER : C_SLOT)));
        ctx.drawCenteredTextWithShadow(textRenderer, "+", plusX + btnSz / 2, btnY2 + 3, C_TEXT);

        ctx.fill(boxX, btnY2, boxX + boxW, btnY2 + btnSz, C_SLOT_BORDER);
        ctx.fill(boxX + 1, btnY2 + 1, boxX + boxW - 1, btnY2 + btnSz - 1, 0xFF141414);

        if (editingAmount) {
            String disp = amountBuffer + ((cursorBlink / 10) % 2 == 0 ? "|" : "");
            ctx.drawCenteredTextWithShadow(textRenderer, disp, boxX + boxW / 2, btnY2 + 3, C_GOLD);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer, String.valueOf(tradeAmount), boxX + boxW / 2, btnY2 + 3, C_TEXT);
        }
        y += 18;

        // Quick amount buttons
        int[] quickValues = new int[]{1, 16, 32, 64};
        int quickY = y;
        int quickX = detailX + 6;
        int quickW = 26;
        for (int i = 0; i < quickValues.length; i++) {
            int qx = quickX + i * (quickW + 4);
            int value = quickValues[i];
            boolean active = canSetAmountTo(value, item);
            drawMiniButton(ctx, qx, quickY, quickW, 14, String.valueOf(value), active, tradeAmount == value);
        }
        int maxX = quickX + 4 * (quickW + 4);
        drawMiniButton(ctx, maxX, quickY, 32, 14, "Max", true, false);
        y += 18;

        double buyTotal = item.getPrice() * tradeAmount;
        double sellTotal = sp * tradeAmount;
        boolean canAfford = ClientEconomyData.getBalance() >= buyTotal;
        boolean canSell = inv > 0;
        boolean stockOk = item.getStock() > 0;

        ctx.drawCenteredTextWithShadow(textRenderer, "Al: ⬡" + formatPrice(buyTotal), midX, y, canAfford && stockOk ? C_GOLD : C_RED);
        y += 11;
        ctx.drawCenteredTextWithShadow(textRenderer, "Sat: +⬡" + formatPrice(sellTotal), midX, y, canSell ? C_GREEN : C_SUBTEXT);
        y += 13;

        String buyHint;
        int buyHintColor;
        if (!stockOk) {
            buyHint = "✗ Stok yok";
            buyHintColor = C_RED;
        } else if (!canAfford) {
            buyHint = "✗ Yetersiz bakiye";
            buyHintColor = C_RED;
        } else {
            buyHint = "✓ Satın alınabilir";
            buyHintColor = C_GREEN;
        }
        ctx.drawCenteredTextWithShadow(textRenderer, buyHint, midX, y, buyHintColor);
        y += 11;

        String sellHint = canSell ? "✓ Satılabilir" : "✗ Envanterinde yok";
        ctx.drawCenteredTextWithShadow(textRenderer, sellHint, midX, y, canSell ? C_GREEN : C_SUBTEXT);
    }

    private void drawMiniButton(DrawContext ctx, int x, int y, int w, int h, String label, boolean active, boolean selected) {
        int bg = !active ? 0xFF252525 : selected ? C_HIGHLIGHT : C_SLOT;
        ctx.fill(x, y, x + w, y + h, C_SLOT_BORDER);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 3, active ? C_TEXT : C_SUBTEXT);
    }

    private void drawFooterPanel(DrawContext ctx) {
        ctx.fill(panelX + 3, footerY, panelX + panelW - 3, footerY + 1, C_SEPARATOR);

        int fy = footerY + 7;
        ctx.drawText(textRenderer, "Bakiye: ⬡" + formatPrice(ClientEconomyData.getBalance()), panelX + 8, fy, C_GOLD, false);

        if (selectedItemIndex >= 0 && selectedItemIndex < filteredItems.size()) {
            ClientMarketItem item = filteredItems.get(selectedItemIndex);
            ctx.drawText(textRenderer, item.getDisplayName() + " ×" + tradeAmount, panelX + 8, fy + 13, C_TEXT, false);
        } else {
            ctx.drawText(textRenderer, "Bir ürün seç", panelX + 8, fy + 13, C_SUBTEXT, false);
        }

        if (!flashText.isBlank()) {
            ctx.drawCenteredTextWithShadow(textRenderer, flashText, panelX + panelW / 2, footerY + 12, flashColor);
        }

        ctx.drawText(textRenderer, "Klavye: Ok tuşları / Enter al / S sat / F favori / PgUp-PgDn kaydır", panelX + 8, footerY + footerH - 14, C_SUBTEXT, false);
    }

    private void drawGridTooltip(DrawContext ctx, int mouseX, int mouseY) {
        int start = gridScrollOffset * gridCols;
        int startX = gridX + 4;
        int startY = gridY + 4;

        for (int slot = 0; slot < gridCols * gridRows; slot++) {
            int idx = start + slot;
            if (idx >= filteredItems.size()) break;

            int col = slot % gridCols;
            int row = slot / gridCols;
            int sx = startX + col * (SLOT_SIZE + SLOT_GAP);
            int sy = startY + row * (SLOT_SIZE + SLOT_GAP);

            if (inBounds(mouseX, mouseY, sx, sy, SLOT_SIZE, SLOT_SIZE)) {
                ClientMarketItem item = filteredItems.get(idx);
                int inv = countInInventory(item.getItemId());
                double buyTotal = item.getPrice() * tradeAmount;
                double sellUnit = item.getPrice() * sellPriceMultiplier;

                List<Text> lines = new ArrayList<>();
                lines.add(Text.literal((MarketUiState.isFavorite(item.getItemId()) ? "§6★ " : "§f") + item.getDisplayName()));
                lines.add(Text.literal("§7Kategori: §e" + item.getCategory()));
                lines.add(Text.literal("§7ID: §8" + item.getItemId()));
                lines.add(Text.literal("§7Alış: §6⬡" + formatPrice(item.getPrice()) + " §8(" + tradeAmount + "x = ⬡" + formatPrice(buyTotal) + ")"));
                lines.add(Text.literal("§7Satış: §a⬡" + formatPrice(sellUnit)));
                lines.add(Text.literal("§7Stok: " + formatStockColor(item.getStock())));
                lines.add(Text.literal("§7Envanter: " + (inv > 0 ? "§a" + inv : "§c0")));
                lines.add(Text.literal("§7Durum: " + (canCurrentlyBuy(item) ? "§aAlınabilir" : "§cAlınamaz")
                        + " §8/ "
                        + (inv > 0 ? "§aSatılabilir" : "§cSatılamaz")));
                if (!searchQuery.isBlank()) {
                    lines.add(Text.literal("§8Filtre: " + searchQuery));
                }

                ctx.drawTooltip(textRenderer, lines, mouseX, mouseY);
                break;
            }
        }
    }

    private String formatStockColor(int stock) {
        if (stock >= 9999) return "§a∞";
        if (stock > 32) return "§a" + stock;
        if (stock > 0) return "§e" + stock;
        return "§c0";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // Önce Oyuncu Pazarı tıklamalarını işle
            if (handlePlayerMarketClick(mx, my)) return true;

            boolean clickedSearch = inBounds(mx, my, catX + 5, catY + 5, catW - 10, 16);
            searchField.setFocused(clickedSearch);
            if (clickedSearch) {
                setFocused(searchField);
            } else {
                setFocused(null);
            }

            int listStartY = catY + 4;
            int rowH = 22;
            int maxRows = Math.max(1, (catH - 8) / rowH);
            for (int i = 0; i < categories.size() && i < maxRows; i++) {
                int rowY = listStartY + i * rowH;
                if (inBounds(mx, my, catX + 3, rowY, catW - 6, rowH - 2)) {
                    selectedCategoryIndex = i;
                    filterItems();
                    // Oyuncu Pazarı seçildiyse veri yoksa yükle
                    if (isPlayerMarketSelected() && !playerShopLoaded && !playerShopLoading) {
                        playerShopLoading = true;
                        PacketByteBuf buf = PacketByteBufs.create();
                        ClientPlayNetworking.send(RequestAllShopListingsPacket.ID, buf);
                    }
                    updateActionButtons();
                    return true;
                }
            }

            int start = gridScrollOffset * gridCols;
            int startX = gridX + 4;
            int startY = gridY + 4;
            for (int slot = 0; slot < gridCols * gridRows; slot++) {
                int idx = start + slot;
                if (idx >= filteredItems.size()) break;
                int col = slot % gridCols;
                int row = slot / gridCols;
                int sx = startX + col * (SLOT_SIZE + SLOT_GAP);
                int sy = startY + row * (SLOT_SIZE + SLOT_GAP);
                if (inBounds(mx, my, sx, sy, SLOT_SIZE, SLOT_SIZE)) {
                    selectedItemIndex = idx;
                    tradeAmount = 1;
                    stopEditing();
                    onSelectionChanged();
                    updateActionButtons();
                    return true;
                }
            }

            if (selectedItemIndex >= 0 && selectedItemIndex < filteredItems.size()) {
                ClientMarketItem item = filteredItems.get(selectedItemIndex);

                // Favori yıldızı
                if (inBounds(mx, my, detailX + detailW - 20, detailY + 6, 14, 14)) {
                    MarketUiState.toggleFavorite(item.getItemId());
                    rebuildCategories();
                    filterItems();
                    reselectByItemId(item.getItemId());
                    return true;
                }

                int btnSz = 14;
                int minusX = detailX + 8;
                int plusX = detailX + detailW - 8 - btnSz;
                int detY = detailY + 6 + 42 + 5 + 13 + 14 + 12 + 12 + 12 + 14 + 6 + 11;

                if (inBounds(mx, my, minusX, detY, btnSz, btnSz)) {
                    stopEditing();
                    adjustAmount(-1);
                    updateActionButtons();
                    return true;
                }

                if (inBounds(mx, my, plusX, detY, btnSz, btnSz)) {
                    stopEditing();
                    adjustAmount(1);
                    updateActionButtons();
                    return true;
                }

                int boxX = minusX + btnSz + 4;
                int boxW = plusX - boxX - 4;
                if (inBounds(mx, my, boxX, detY, boxW, btnSz)) {
                    editingAmount = true;
                    amountBuffer = String.valueOf(tradeAmount);
                    cursorBlink = 0;
                    return true;
                }

                // Quick amount
                int quickY = detY + 18;
                int quickX = detailX + 6;
                int quickW = 26;
                int[] quickValues = new int[]{1, 16, 32, 64};
                for (int i = 0; i < quickValues.length; i++) {
                    int qx = quickX + i * (quickW + 4);
                    if (inBounds(mx, my, qx, quickY, quickW, 14)) {
                        if (canSetAmountTo(quickValues[i], item)) {
                            tradeAmount = quickValues[i];
                            stopEditing();
                            updateActionButtons();
                        }
                        return true;
                    }
                }
                int maxX = quickX + 4 * (quickW + 4);
                if (inBounds(mx, my, maxX, quickY, 32, 14)) {
                    tradeAmount = getMaxRelevantAmount(item);
                    stopEditing();
                    updateActionButtons();
                    return true;
                }
            }

            if (editingAmount) commitAmount();
        }

        return super.mouseClicked(mx, my, button);
    }

    private boolean canSetAmountTo(int value, ClientMarketItem item) {
        return value <= getMaxRelevantAmount(item);
    }

    private int getMaxRelevantAmount(ClientMarketItem item) {
        int buyMax = Math.min((int) (ClientEconomyData.getBalance() / Math.max(0.0001, item.getPrice())), item.getStock());
        int sellMax = countInInventory(item.getItemId());
        return Math.max(1, Math.max(buyMax, sellMax));
    }

    private void onSelectionChanged() {
        if (selectedItemIndex < 0 || selectedItemIndex >= filteredItems.size()) return;
        ClientMarketItem item = filteredItems.get(selectedItemIndex);
        MarketUiState.pushViewed(item.getItemId());
        rebuildCategories();

        if (selectedItemIndex != lastSelectedFilteredIndex) {
            lastSelectedFilteredIndex = selectedItemIndex;
        }
    }

    private void reselectByItemId(String itemId) {
        for (int i = 0; i < filteredItems.size(); i++) {
            if (filteredItems.get(i).getItemId().equals(itemId)) {
                selectedItemIndex = i;
                return;
            }
        }
        selectedItemIndex = -1;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        holdingMinus = false;
        holdingPlus = false;
        holdTicks = 0;
        repeatTicks = 0;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (isPlayerMarketSelected()) {
            if (inBounds(mx, my, gridX, gridY, gridW, gridH)) {
                int totalCols = Math.max(1, (gridW - 8) / (SLOT_SIZE + SLOT_GAP));
                int totalRows = (int) Math.ceil((double) shopItemIds.size() / totalCols);
                int visRows   = Math.max(1, (gridH - 8) / (SLOT_SIZE + SLOT_GAP));
                int maxScroll = Math.max(0, totalRows - visRows);
                shopGridScroll = Math.max(0, Math.min(shopGridScroll - (int) amount, maxScroll));
                return true;
            }
            // Satıcı listesi kaydırma
            if (selectedShopItemId != null && inBounds(mx, my, detailX, detailY, detailW, detailH)) {
                List<AllShopListingsPacket.Entry> sellers = shopByItem.get(selectedShopItemId);
                if (sellers != null) {
                    int listH = detailH - 100;
                    int rowH  = 18;
                    int visSelRows = Math.max(1, listH / rowH);
                    int maxSel = Math.max(0, sellers.size() - visSelRows);
                    shopSellerScroll = Math.max(0, Math.min(shopSellerScroll - (int) amount, maxSel));
                    return true;
                }
            }
            return super.mouseScrolled(mx, my, amount);
        }

        if (inBounds(mx, my, gridX, gridY, gridW, gridH)) {
            int totalRows = (int) Math.ceil((double) filteredItems.size() / gridCols);
            int maxScroll = Math.max(0, totalRows - gridRows);
            gridScrollOffset = Math.max(0, Math.min(gridScrollOffset - (int) amount, maxScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingAmount) {
            if (chr >= '0' && chr <= '9' && amountBuffer.length() < 5) {
                amountBuffer += chr;
            }
            return true;
        }

        if (searchField != null && searchField.isFocused()) {
            return super.charTyped(chr, modifiers);
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchField != null && searchField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchField.setFocused(false);
                setFocused(null);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (editingAmount) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitAmount();
                updateActionButtons();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                stopEditing();
                updateActionButtons();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !amountBuffer.isEmpty()) {
                amountBuffer = amountBuffer.substring(0, amountBuffer.length() - 1);
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_F && selectedItemIndex >= 0 && selectedItemIndex < filteredItems.size()) {
            ClientMarketItem item = filteredItems.get(selectedItemIndex);
            MarketUiState.toggleFavorite(item.getItemId());
            rebuildCategories();
            filterItems();
            reselectByItemId(item.getItemId());
            updateActionButtons();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            handleBuy();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_S) {
            handleSell();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            adjustAmount(1);
            updateActionButtons();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            adjustAmount(-1);
            updateActionButtons();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int totalRows = (int) Math.ceil((double) filteredItems.size() / gridCols);
            int maxScroll = Math.max(0, totalRows - gridRows);
            gridScrollOffset = Math.min(maxScroll, gridScrollOffset + 1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            gridScrollOffset = Math.max(0, gridScrollOffset - 1);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            moveSelectionByKey(keyCode);
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_1 && selectedItemIndex >= 0) {
            tradeAmount = Math.min(1, getMaxRelevantAmount(filteredItems.get(selectedItemIndex)));
            updateActionButtons();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_2 && selectedItemIndex >= 0) {
            tradeAmount = Math.min(16, getMaxRelevantAmount(filteredItems.get(selectedItemIndex)));
            updateActionButtons();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_3 && selectedItemIndex >= 0) {
            tradeAmount = Math.min(32, getMaxRelevantAmount(filteredItems.get(selectedItemIndex)));
            updateActionButtons();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_4 && selectedItemIndex >= 0) {
            tradeAmount = Math.min(64, getMaxRelevantAmount(filteredItems.get(selectedItemIndex)));
            updateActionButtons();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_5 && selectedItemIndex >= 0) {
            tradeAmount = getMaxRelevantAmount(filteredItems.get(selectedItemIndex));
            updateActionButtons();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveSelectionByKey(int keyCode) {
        if (filteredItems.isEmpty()) return;

        if (selectedItemIndex < 0) {
            selectedItemIndex = 0;
            onSelectionChanged();
            updateActionButtons();
            return;
        }

        int newIndex = selectedItemIndex;
        if (keyCode == GLFW.GLFW_KEY_RIGHT) newIndex++;
        if (keyCode == GLFW.GLFW_KEY_LEFT) newIndex--;
        if (keyCode == GLFW.GLFW_KEY_DOWN) newIndex += gridCols;
        if (keyCode == GLFW.GLFW_KEY_UP) newIndex -= gridCols;

        newIndex = Math.max(0, Math.min(newIndex, filteredItems.size() - 1));
        if (newIndex != selectedItemIndex) {
            selectedItemIndex = newIndex;
            ensureSelectionVisible();
            onSelectionChanged();
            updateActionButtons();
        }
    }

    private void ensureSelectionVisible() {
        if (selectedItemIndex < 0) return;
        int row = selectedItemIndex / gridCols;
        if (row < gridScrollOffset) {
            gridScrollOffset = row;
        } else if (row >= gridScrollOffset + gridRows) {
            gridScrollOffset = row - gridRows + 1;
        }
    }

    private void commitAmount() {
        editingAmount = false;
        if (!amountBuffer.isEmpty() && selectedItemIndex >= 0 && selectedItemIndex < filteredItems.size()) {
            try {
                int val = Integer.parseInt(amountBuffer);
                if (val >= 1) {
                    ClientMarketItem item = filteredItems.get(selectedItemIndex);
                    int max = getMaxRelevantAmount(item);
                    tradeAmount = Math.max(1, Math.min(val, max));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        amountBuffer = "";
    }

    private void stopEditing() {
        editingAmount = false;
        amountBuffer = "";
    }

    private void handleBuy() {
        if (!canBuy) return;
        if (selectedItemIndex < 0 || selectedItemIndex >= filteredItems.size()) return;

        ClientMarketItem item = filteredItems.get(selectedItemIndex);
        if (item.getStock() <= 0) return;
        if (ClientEconomyData.getBalance() < item.getPrice() * tradeAmount) return;

        net.minecraft.network.PacketByteBuf buf =
                net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeInt(item.getId());
        buf.writeInt(tradeAmount);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                com.cogtrade.network.BuyItemPacket.ID, buf);

        MarketUiState.pushTraded(item.getItemId());
        flashText = "Satın alındı: " + item.getDisplayName() + " ×" + tradeAmount;
        flashColor = C_GREEN;
        flashTicks = 20;
        flashItemId = item.getId();
        rebuildCategories();
    }

    private void handleSell() {
        if (!canBuy) return;
        if (selectedItemIndex < 0 || selectedItemIndex >= filteredItems.size()) return;

        ClientMarketItem item = filteredItems.get(selectedItemIndex);
        int inInventory = countInInventory(item.getItemId());
        if (inInventory <= 0 || tradeAmount <= 0) return;

        net.minecraft.network.PacketByteBuf buf =
                net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeInt(item.getId());
        buf.writeInt(Math.min(tradeAmount, inInventory));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                com.cogtrade.network.SellItemPacket.ID, buf);

        MarketUiState.pushTraded(item.getItemId());
        flashText = "Satıldı: " + item.getDisplayName() + " ×" + Math.min(tradeAmount, inInventory);
        flashColor = C_BLUE;
        flashTicks = 20;
        flashItemId = item.getId();
        rebuildCategories();
    }

    private ItemStack getItemStack(String itemIdStr) {
        try {
            Item item = Registries.ITEM.get(new Identifier(itemIdStr));
            if (item == Items.AIR) return ItemStack.EMPTY;
            return new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private int countInInventory(String itemIdStr) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return 0;

        try {
            Item item = Registries.ITEM.get(new Identifier(itemIdStr));
            if (item == Items.AIR) return 0;

            int count = 0;
            for (ItemStack stack : client.player.getInventory().main) {
                if (stack.isOf(item)) count += stack.getCount();
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private double parseDoubleSafe(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private int parseIntSafe(String text, int fallback) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String formatPrice(double price) {
        if (price >= 1_000_000) return String.format("%.1fM", price / 1_000_000);
        if (price >= 1_000) return String.format("%.1fK", price / 1_000);
        return String.format("%.0f", price);
    }

    // ── Oyuncu Pazarı Grid Çizimi ─────────────────────────────────────────────
    private void drawPlayerMarketGrid(DrawContext ctx, int mouseX, int mouseY) {
        drawSection(ctx, gridX, gridY, gridW, gridH);

        if (!playerShopLoaded) {
            if (!playerShopLoading) {
                playerShopLoading = true;
                PacketByteBuf buf = PacketByteBufs.create();
                ClientPlayNetworking.send(RequestAllShopListingsPacket.ID, buf);
            }
            ctx.drawCenteredTextWithShadow(textRenderer, "§7Veriler yükleniyor...",
                    gridX + gridW / 2, gridY + gridH / 2 - 4, C_SUBTEXT);
            return;
        }

        if (shopItemIds.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    searchQuery.isBlank() ? "§7Aktif trade post ilanı yok" : "§7Sonuç bulunamadı",
                    gridX + gridW / 2, gridY + gridH / 2 - 4, C_SUBTEXT);
            return;
        }

        int startX = gridX + 4;
        int startY = gridY + 4;
        int totalCols = Math.max(1, (gridW - 8) / (SLOT_SIZE + SLOT_GAP));
        int totalRows = (int) Math.ceil((double) shopItemIds.size() / totalCols);
        int visRows   = Math.max(1, (gridH - 8) / (SLOT_SIZE + SLOT_GAP));
        int maxScroll = Math.max(0, totalRows - visRows);
        shopGridScroll = Math.max(0, Math.min(shopGridScroll, maxScroll));

        for (int row = 0; row < visRows; row++) {
            int absRow = row + shopGridScroll;
            for (int col = 0; col < totalCols; col++) {
                int idx = absRow * totalCols + col;
                if (idx >= shopItemIds.size()) break;

                String itemId = shopItemIds.get(idx);
                List<AllShopListingsPacket.Entry> sellers = shopByItem.get(itemId);
                int sellerCount = sellers != null ? sellers.size() : 0;
                int totalStock  = sellers != null
                        ? sellers.stream().mapToInt(AllShopListingsPacket.Entry::stock).sum() : 0;
                double minPrice = sellers != null
                        ? sellers.stream().filter(e -> e.stock() > 0)
                                .mapToDouble(AllShopListingsPacket.Entry::price).min()
                                .orElse(sellers.stream().mapToDouble(AllShopListingsPacket.Entry::price).min().orElse(0)) : 0;
                boolean outOfStock = totalStock <= 0;

                int sx = startX + col * (SLOT_SIZE + SLOT_GAP);
                int sy = startY + row * (SLOT_SIZE + SLOT_GAP);

                boolean hov = inBounds(mouseX, mouseY, sx, sy, SLOT_SIZE, SLOT_SIZE);
                boolean sel = itemId.equals(selectedShopItemId);

                int bgCol = sel ? C_SLOT_SELECT : hov ? C_SLOT_HOVER : C_SLOT;
                ctx.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, C_SLOT_BORDER);
                ctx.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, bgCol);

                // Item icon (soluk stok yoksa)
                ItemStack stack = getItemStack(itemId);
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx + 10, sy + 8);
                    ctx.drawItemInSlot(textRenderer, stack, sx + 10, sy + 8);
                }
                // Stok yok → gri kaplama
                if (outOfStock) {
                    ctx.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x99000000);
                    ctx.drawCenteredTextWithShadow(textRenderer, "§7✕",
                            sx + SLOT_SIZE / 2, sy + SLOT_SIZE / 2 - 3, 0xFF666666);
                }

                // Satıcı sayısı badge
                String badge = sellerCount + "x";
                int bw = textRenderer.getWidth(badge);
                ctx.fill(sx + SLOT_SIZE - bw - 3, sy + SLOT_SIZE - 9,
                        sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xAA000000);
                ctx.drawText(textRenderer, badge,
                        sx + SLOT_SIZE - bw - 2, sy + SLOT_SIZE - 8,
                        outOfStock ? 0xFF666666 : C_CYAN, false);

                // Min fiyat (üst-sol küçük)
                String priceStr = "⬡" + formatPrice(minPrice);
                if (textRenderer.getWidth(priceStr) <= SLOT_SIZE - 2) {
                    ctx.fill(sx + 1, sy + 1, sx + textRenderer.getWidth(priceStr) + 2, sy + 8, 0xAA000000);
                    ctx.drawText(textRenderer, priceStr, sx + 2, sy + 1,
                            outOfStock ? 0xFF666666 : C_GOLD, false);
                }

                // Seçim çerçevesi
                if (sel) {
                    int frameCol = outOfStock ? 0xFF666666 : C_CYAN;
                    ctx.fill(sx, sy, sx + SLOT_SIZE, sy + 1, frameCol);
                    ctx.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, frameCol);
                    ctx.fill(sx, sy, sx + 1, sy + SLOT_SIZE, frameCol);
                    ctx.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, frameCol);
                }
            }
        }

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = gridX + gridW - 6;
            int sbH = gridH - 8;
            int sbY = gridY + 4;
            int barH = Math.max(20, sbH * visRows / totalRows);
            int barY = sbY + shopGridScroll * (sbH - barH) / maxScroll;
            ctx.fill(sbX, sbY, sbX + 3, sbY + sbH, 0xFF222222);
            ctx.fill(sbX, barY, sbX + 3, barY + barH, C_SEPARATOR);
        }
    }

    // ── Oyuncu Pazarı Detail Panel ────────────────────────────────────────────
    private void drawPlayerMarketDetail(DrawContext ctx, int mouseX, int mouseY) {
        drawSection(ctx, detailX, detailY, detailW, detailH);

        int cx = detailX + detailW / 2;

        if (selectedShopItemId == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Bir ürün seçin", cx, detailY + detailH / 3, C_SUBTEXT);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§8← grid'den tıklayın", cx, detailY + detailH / 3 + 13, 0xFF444444);
            return;
        }

        List<AllShopListingsPacket.Entry> sellers = shopByItem.get(selectedShopItemId);
        if (sellers == null || sellers.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "§7Satıcı bulunamadı", cx, detailY + detailH / 3, C_SUBTEXT);
            return;
        }

        int y = detailY + 6;

        // Büyük item ikonu
        int boxS = 36;
        int boxX = cx - boxS / 2;
        ctx.fill(boxX, y, boxX + boxS, y + boxS, C_SLOT_BORDER);
        ctx.fill(boxX + 1, y + 1, boxX + boxS - 1, y + boxS - 1, C_SLOT);
        ItemStack stack = getItemStack(selectedShopItemId);
        if (!stack.isEmpty()) {
            ctx.drawItem(stack, boxX + 10, y + 10);
            ctx.drawItemInSlot(textRenderer, stack, boxX + 10, y + 10);
        }
        y += boxS + 5;

        // İtem adı
        String dispName = getItemDisplayName(selectedShopItemId);
        if (textRenderer.getWidth(dispName) > detailW - 10) {
            while (textRenderer.getWidth(dispName + "..") > detailW - 10 && dispName.length() > 3)
                dispName = dispName.substring(0, dispName.length() - 1);
            dispName += "..";
        }
        ctx.drawCenteredTextWithShadow(textRenderer, "§f" + dispName, cx, y, C_TEXT);
        y += 13;

        ctx.drawCenteredTextWithShadow(textRenderer,
                "§7" + sellers.size() + " satıcı · §aStokta",
                cx, y, C_SUBTEXT);
        y += 12;

        ctx.fill(detailX + 3, y, detailX + detailW - 3, y + 1, C_SEPARATOR);
        y += 4;

        // Satıcı listesi
        int listH     = detailH - (y - detailY) - 55;
        int rowH      = 18;
        int visSelRows = Math.max(1, listH / rowH);
        int maxSelScroll = Math.max(0, sellers.size() - visSelRows);
        shopSellerScroll = Math.max(0, Math.min(shopSellerScroll, maxSelScroll));

        ctx.fill(detailX + 2, y, detailX + detailW - 2, y + listH, C_SLOT_BORDER);
        ctx.fill(detailX + 3, y + 1, detailX + detailW - 3, y + listH - 1, 0xFF141414);

        for (int i = 0; i < visSelRows && i + shopSellerScroll < sellers.size(); i++) {
            int idx = i + shopSellerScroll;
            AllShopListingsPacket.Entry seller = sellers.get(idx);
            int ry  = y + 1 + i * rowH;
            boolean hov = inBounds(mouseX, mouseY, detailX + 3, ry, detailW - 6, rowH);
            boolean sel = idx == selectedShopSellerIdx;

            ctx.fill(detailX + 3, ry, detailX + detailW - 3, ry + rowH - 1,
                    sel ? C_SLOT_SELECT : hov ? C_SLOT_HOVER : C_SLOT);

            // Fiyat ve stok X pozisyonlarını önce hesapla (çakışmaması için)
            String stk = "×" + seller.stock();
            String pr  = "⬡" + formatPrice(seller.price());
            int prW  = textRenderer.getWidth(pr);
            int stkW = textRenderer.getWidth(stk);
            int prX  = detailX + detailW - 5 - prW;
            int stkX = prX - 5 - stkW;

            // Satıcı adı (stok alanı başlamadan kesilir)
            String sname = seller.ownerName();
            int maxNamePx = stkX - (detailX + 5) - 3;
            if (textRenderer.getWidth(sname) > maxNamePx) {
                while (textRenderer.getWidth(sname + "..") > maxNamePx && sname.length() > 2)
                    sname = sname.substring(0, sname.length() - 1);
                sname += "..";
            }
            ctx.drawText(textRenderer, "§f" + sname, detailX + 5, ry + 5, sel ? C_GOLD : C_TEXT, false);

            // Stok (fiyatın solunda, sabit boşlukla)
            ctx.drawText(textRenderer, "§a" + stk, stkX, ry + 5, C_GREEN, false);

            // Fiyat (sağ kenara yaslı)
            ctx.drawText(textRenderer, "§6" + pr, prX, ry + 5, C_GOLD, false);
        }
        y += listH + 4;

        // Miktar ve toplam bilgisi
        if (selectedShopSellerIdx >= 0 && selectedShopSellerIdx < sellers.size()) {
            AllShopListingsPacket.Entry sel = sellers.get(selectedShopSellerIdx);
            double total = sel.price() * shopTradeAmount;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Miktar: §f" + shopTradeAmount + "  §7Toplam: §6⬡" + formatPrice(total),
                    cx, y, C_TEXT);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Satıcı seçin ↑", cx, y, C_SUBTEXT);
        }
    }

    // ── mouseClicked override ─────────────────────────────────────────────────
    // Not: Player Market için tıklama işleme eklenir mevcut mouseClicked'a

    private boolean handlePlayerMarketClick(double mx, double my) {
        if (!isPlayerMarketSelected()) return false;

        // Grid click
        int startX = gridX + 4;
        int startY = gridY + 4;
        int totalCols = Math.max(1, (gridW - 8) / (SLOT_SIZE + SLOT_GAP));
        int visRows   = Math.max(1, (gridH - 8) / (SLOT_SIZE + SLOT_GAP));

        for (int row = 0; row < visRows; row++) {
            int absRow = row + shopGridScroll;
            for (int col = 0; col < totalCols; col++) {
                int idx = absRow * totalCols + col;
                if (idx >= shopItemIds.size()) break;
                int sx = startX + col * (SLOT_SIZE + SLOT_GAP);
                int sy = startY + row * (SLOT_SIZE + SLOT_GAP);
                if (inBounds(mx, my, sx, sy, SLOT_SIZE, SLOT_SIZE)) {
                    String itemId = shopItemIds.get(idx);
                    if (!itemId.equals(selectedShopItemId)) {
                        selectedShopItemId    = itemId;
                        selectedShopSellerIdx = -1;
                        shopSellerScroll      = 0;
                        shopTradeAmount       = 1;
                    }
                    updateActionButtons();
                    return true;
                }
            }
        }

        // Seller list click
        if (selectedShopItemId != null && shopByItem.containsKey(selectedShopItemId)) {
            List<AllShopListingsPacket.Entry> sellers = shopByItem.get(selectedShopItemId);
            int y = detailY + 6 + 36 + 5 + 13 + 12 + 4;
            int listH = detailH - (y - detailY) - 55;
            int rowH  = 18;
            int visSelRows = Math.max(1, listH / rowH);
            for (int i = 0; i < visSelRows && i + shopSellerScroll < sellers.size(); i++) {
                int idx = i + shopSellerScroll;
                int ry  = y + 1 + i * rowH;
                if (inBounds(mx, my, detailX + 3, ry, detailW - 6, rowH)) {
                    selectedShopSellerIdx = idx;
                    shopTradeAmount = 1;
                    updateActionButtons();
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}