package com.cogtrade.client;

import com.cogtrade.market.PlayerShopManager;
import com.cogtrade.network.PostActionPacket;
import com.cogtrade.network.PostRefreshPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Trade Post yönetim ekranı (sahip için).
 * Depot'taki stok görüntülenir, buradan fiyat + ilan yönetimi yapılır.
 * Alıcılar için ayrı PlayerShopScreen kullanılır.
 */
@Environment(EnvType.CLIENT)
public class TradePostScreen extends Screen {

    // ── Colors ──────────────────────────────────────────────────────────────
    private static final int C_PANEL        = 0xFF1E1E1E;
    private static final int C_BORDER       = 0xFF0A0A0A;
    private static final int C_GOLD         = 0xFFFFD54F;
    private static final int C_GREEN        = 0xFF66BB6A;
    private static final int C_RED          = 0xFFEF5350;
    private static final int C_TEXT         = 0xFFDDDDDD;
    private static final int C_SUBTEXT      = 0xFF888888;
    private static final int C_SLOT         = 0xFF2D2D2D;
    private static final int C_SLOT_HOVER   = 0xFF424242;
    private static final int C_SLOT_SEL     = 0xFF8B6914;
    private static final int C_SLOT_LISTED  = 0xFF1A3020;
    private static final int C_SEP          = 0xFF3A3A3A;
    private static final int C_DARK         = 0xFF141414;
    private static final int C_CAT_ACT      = 0xFF3A2E0E;
    private static final int C_CAT_IN       = 0xFF252525;
    private static final int C_DETAIL_BG    = 0xFF1A1A1A;
    private static final int C_CYAN         = 0xFF4DD0E1;

    private static final int    SLOT_SIZE  = 36;
    private static final int    SLOT_GAP   = 4;
    private static final String[] CATEGORIES       = {"Tümü", "Satışta", "Satışta Değil"};
    private static final String[] ITEM_CATEGORIES  = {
            "Hammadde",     // Cevher, külçe, kütük, hayvan ürünleri
            "Blok",         // İnşaat blokları, dekoratif
            "Yiyecek",      // Yenilebilir itemler
            "Araç/Ekipman", // Alet, silah, zırh, balık oltası
            "Ekim",         // Tohum, fidan, çiçek, bitki
            "Yaratık",      // Mob dropu, spawner, yumurta
            "Redstone",     // Redstone bileşenleri
            "Enchant/Kitap",// Kitaplar, büyülü itemler
            "Misc"          // Diğer
    };

    // ── Data ────────────────────────────────────────────────────────────────
    private Map<String, Integer>                available;
    private List<PlayerShopManager.ShopListing> listings;

    private List<Map.Entry<String, Integer>>        gridItems;
    private List<PlayerShopManager.ShopListing>     filteredListings;

    // ── UI State ────────────────────────────────────────────────────────────
    private int    selectedCatIdx    = 0;
    private String selectedItemId    = null;
    private int    gridScroll        = 0;
    private int    listScroll        = 0;
    private int    selectedListingIdx = -1;
    private int    catListingIdx     = 0;
    private int    refreshTick       = 0;

    // Tracked during render so click/scroll handlers use matching bounds
    private int    activeListStartY  = 0;
    private int    activeListBottomY = 0;

    // ── Layout ──────────────────────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int catX,   catY,   catW,   catH;
    private int gridX,  gridY,  gridW,  gridH;
    private int detailX, detailY, detailW, detailH;

    // ── Widgets ─────────────────────────────────────────────────────────────
    private TextFieldWidget searchField;
    private TextFieldWidget priceField;
    private ButtonWidget    catListingButton;
    private ButtonWidget    addButton;
    private ButtonWidget    removeButton;

    private String searchQuery = "";

    // ── Constructor ─────────────────────────────────────────────────────────
    public TradePostScreen(Map<String, Integer> available,
                           List<PlayerShopManager.ShopListing> listings) {
        super(Text.literal("Trade Post Yönetimi"));
        this.available = new LinkedHashMap<>(available);
        this.listings  = new ArrayList<>(listings);
    }

    /** In-place refresh — ekran kapatılmadan güncellenir. */
    public void refresh(Map<String, Integer> newAvailable,
                        List<PlayerShopManager.ShopListing> newListings) {
        this.available = new LinkedHashMap<>(newAvailable);
        this.listings  = new ArrayList<>(newListings);
        applyFilters();
        // Sync price field if selected item's listing changed
        if (selectedItemId != null) syncPriceField();
    }

    // ── Init ────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        computeLayout();

        // Search
        searchField = new TextFieldWidget(textRenderer,
                gridX + 4, panelY + 6, gridW - 8, 14, Text.literal("Ara"));
        searchField.setMaxLength(40);
        searchField.setSuggestion("İtem ara...");
        searchField.setText(searchQuery);
        searchField.setChangedListener(s -> { searchQuery = s; applyFilters(); });
        addDrawableChild(searchField);

        // Price field + action buttons (in detail panel)
        int pfY = detailY + detailH - 94;

        priceField = new TextFieldWidget(textRenderer,
                detailX + 6, pfY, detailW - 12, 14, Text.literal("Fiyat"));
        priceField.setMaxLength(12);
        priceField.setSuggestion("Fiyat (⬡)...");
        addDrawableChild(priceField);

        catListingButton = ButtonWidget.builder(
                Text.literal("§7Kat: §f" + ITEM_CATEGORIES[catListingIdx]),
                btn -> {
                    catListingIdx = (catListingIdx + 1) % ITEM_CATEGORIES.length;
                    btn.setMessage(Text.literal("§7Kat: §f" + ITEM_CATEGORIES[catListingIdx]));
                })
                .dimensions(detailX + 6, pfY + 18, detailW - 12, 14).build();
        addDrawableChild(catListingButton);

        addButton = ButtonWidget.builder(Text.literal("§a+ Satışa Ekle"), btn -> handleAdd())
                .dimensions(detailX + 6, pfY + 36, detailW - 12, 16).build();
        addDrawableChild(addButton);

        removeButton = ButtonWidget.builder(Text.literal("§c− Kaldır"), btn -> handleRemove())
                .dimensions(detailX + 6, pfY + 56, detailW - 12, 16).build();
        addDrawableChild(removeButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), btn -> close())
                .dimensions(panelX + panelW - 17, panelY + 4, 13, 13).build());

        applyFilters();
    }

    private void computeLayout() {
        panelW = Math.min(820, width - 20);
        panelH = Math.min(490, height - 20);
        panelX = (width  - panelW) / 2;
        panelY = (height - panelH) / 2;

        catW = 110;
        catX = panelX + 4;
        catY = panelY + 26;
        catH = panelH - 30;

        detailW = Math.max(170, Math.min(195, panelW / 4));
        detailX = panelX + panelW - detailW - 4;
        detailY = panelY + 26;
        detailH = panelH - 30;

        gridX = catX + catW + 4;
        gridY = panelY + 26;
        gridW = detailX - gridX - 4;
        gridH = panelH - 30;
    }

    // ── Filter ──────────────────────────────────────────────────────────────
    private void applyFilters() {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        Set<String> listedIds = listings.stream()
                .map(PlayerShopManager.ShopListing::itemId)
                .collect(Collectors.toSet());

        Stream<Map.Entry<String, Integer>> stream = available.entrySet().stream();
        if (!q.isEmpty())
            stream = stream.filter(e ->
                    getDisplayName(e.getKey()).toLowerCase(Locale.ROOT).contains(q));
        if (selectedCatIdx == 1)
            stream = stream.filter(e -> listedIds.contains(e.getKey()));
        else if (selectedCatIdx == 2)
            stream = stream.filter(e -> !listedIds.contains(e.getKey()));

        gridItems = stream
                .sorted(Comparator.comparing(e -> getDisplayName(e.getKey())))
                .collect(Collectors.toList());

        filteredListings = new ArrayList<>(listings);
        filteredListings.sort(Comparator.comparing(PlayerShopManager.ShopListing::displayName));

        gridScroll = 0;
        if (selectedItemId != null &&
                gridItems.stream().noneMatch(e -> e.getKey().equals(selectedItemId))) {
            selectedItemId = null;
            if (priceField != null) priceField.setText("");
        }
    }

    private void syncPriceField() {
        if (selectedItemId == null || priceField == null) return;
        listings.stream()
                .filter(l -> l.itemId().equals(selectedItemId))
                .findFirst()
                .ifPresent(l -> {
                    priceField.setText(String.format("%.0f", l.price()));
                    for (int ci = 0; ci < ITEM_CATEGORIES.length; ci++) {
                        if (ITEM_CATEGORIES[ci].equals(l.category())) {
                            catListingIdx = ci;
                            if (catListingButton != null)
                                catListingButton.setMessage(
                                        Text.literal("§7Kat: §f" + ITEM_CATEGORIES[ci]));
                            break;
                        }
                    }
                });
    }

    // ── Action handlers ──────────────────────────────────────────────────────
    private void handleAdd() {
        if (selectedItemId == null) return;
        String priceText = priceField.getText().trim().replace(',', '.');
        double price;
        try { price = Double.parseDouble(priceText); }
        catch (NumberFormatException e) { return; }
        if (price <= 0) return;

        String category = ITEM_CATEGORIES[catListingIdx];
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(PostActionPacket.ACTION_ADD);
        buf.writeString(selectedItemId);
        buf.writeDouble(price);
        buf.writeString(category);
        ClientPlayNetworking.send(PostActionPacket.ID, buf);
    }

    private void handleRemove() {
        String removeId = null;
        if (selectedListingIdx >= 0 && selectedListingIdx < filteredListings.size()) {
            removeId = filteredListings.get(selectedListingIdx).itemId();
        } else if (selectedItemId != null) {
            boolean isListed = listings.stream().anyMatch(l -> l.itemId().equals(selectedItemId));
            if (isListed) removeId = selectedItemId;
        }
        if (removeId == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(PostActionPacket.ACTION_REMOVE);
        buf.writeString(removeId);
        buf.writeDouble(0);
        buf.writeString("");
        ClientPlayNetworking.send(PostActionPacket.ID, buf);
    }

    // ── Tick (real-time refresh) ─────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();
        if (++refreshTick >= 40) {
            refreshTick = 0;
            PacketByteBuf buf = PacketByteBufs.create();
            ClientPlayNetworking.send(PostRefreshPacket.ID, buf);
        }
    }

    // ── Render ───────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        drawFrame(ctx);
        drawCategoryPanel(ctx, mouseX, mouseY);
        drawGrid(ctx, mouseX, mouseY);
        drawDetailPanel(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawFrame(DrawContext ctx) {
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_BORDER);
        ctx.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, C_PANEL);

        ctx.drawCenteredTextWithShadow(textRenderer,
                "§b✦ Trade Post — İlan Yönetimi ✦",
                panelX + panelW / 2, panelY + 8, C_CYAN);
        ctx.fill(panelX + 3, panelY + 22, panelX + panelW - 3, panelY + 23, C_SEP);
        ctx.fill(catX + catW + 1, panelY + 24, catX + catW + 2, panelY + panelH - 3, C_SEP);
        ctx.fill(detailX - 3,     panelY + 24, detailX - 2,     panelY + panelH - 3, C_SEP);
    }

    // ── Category panel ────────────────────────────────────────────────────────
    private void drawCategoryPanel(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(catX, catY, catX + catW, catY + catH, C_DARK);

        int tabH = 20;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int ty  = catY + i * (tabH + 2);
            boolean sel = i == selectedCatIdx;
            boolean hov = mouseX >= catX && mouseX < catX + catW
                    && mouseY >= ty && mouseY < ty + tabH;

            ctx.fill(catX, ty, catX + catW, ty + tabH,
                    sel ? C_CAT_ACT : (hov ? 0xFF303030 : C_CAT_IN));
            ctx.fill(catX, ty + tabH - 1, catX + catW, ty + tabH,
                    sel ? C_CYAN : C_SEP);
            ctx.drawText(textRenderer, sel ? "§b" + CATEGORIES[i] : "§7" + CATEGORIES[i],
                    catX + 7, ty + 6, sel ? C_CYAN : C_SUBTEXT, false);
        }

        int badgeY = catY + CATEGORIES.length * (tabH + 2) + 8;
        ctx.drawText(textRenderer, "§8Depoda: §f" + available.size() + " çeşit",
                catX + 5, badgeY, C_SUBTEXT, false);
        ctx.drawText(textRenderer, "§8Satışta: §e" + listings.size() + " ilan",
                catX + 5, badgeY + 11, C_SUBTEXT, false);
    }

    // ── Grid ─────────────────────────────────────────────────────────────────
    private void drawGrid(DrawContext ctx, int mouseX, int mouseY) {
        int gx = gridX + 4, gy = gridY + 24, gw = gridW - 8, gh = gridH - 28;
        ctx.fill(gridX, gridY + 22, gridX + gridW, gridY + gridH, C_DARK);

        if (gridItems.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    available.isEmpty() ? "§7Depoda item yok" : "§7Sonuç bulunamadı",
                    gx + gw / 2, gy + gh / 2 - 4, C_SUBTEXT);
            return;
        }

        int totalCols = Math.max(1, gw / (SLOT_SIZE + SLOT_GAP));
        int visRows   = Math.max(1, gh / (SLOT_SIZE + SLOT_GAP));
        int totalRows = (gridItems.size() + totalCols - 1) / totalCols;
        int maxScroll = Math.max(0, totalRows - visRows);
        gridScroll    = Math.max(0, Math.min(gridScroll, maxScroll));

        Set<String> listedIds = listings.stream()
                .map(PlayerShopManager.ShopListing::itemId)
                .collect(Collectors.toSet());

        for (int row = 0; row < visRows; row++) {
            int absRow = row + gridScroll;
            for (int col = 0; col < totalCols; col++) {
                int idx = absRow * totalCols + col;
                if (idx >= gridItems.size()) break;

                Map.Entry<String, Integer> entry = gridItems.get(idx);
                String itemId = entry.getKey();
                int count     = entry.getValue();

                int sx = gx + col * (SLOT_SIZE + SLOT_GAP);
                int sy = gy + row * (SLOT_SIZE + SLOT_GAP);

                boolean hov    = mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + SLOT_SIZE;
                boolean sel    = itemId.equals(selectedItemId);
                boolean listed = listedIds.contains(itemId);

                int bgCol = sel ? C_SLOT_SEL : listed ? C_SLOT_LISTED : hov ? C_SLOT_HOVER : C_SLOT;
                ctx.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, C_BORDER);
                ctx.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, bgCol);

                ItemStack stack = getStack(itemId);
                if (!stack.isEmpty())
                    ctx.drawItem(stack, sx + (SLOT_SIZE - 16) / 2, sy + (SLOT_SIZE - 16) / 2 - 2);

                String countStr = count >= 1000 ? (count / 1000) + "k" : String.valueOf(count);
                int cw = textRenderer.getWidth(countStr);
                ctx.fill(sx + SLOT_SIZE - cw - 3, sy + SLOT_SIZE - 9,
                        sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xAA000000);
                ctx.drawText(textRenderer, countStr,
                        sx + SLOT_SIZE - cw - 2, sy + SLOT_SIZE - 8,
                        listed ? C_GREEN : C_TEXT, false);

                if (listed) {
                    ctx.fill(sx + 1, sy + 1, sx + 8, sy + 7, 0xAA1A4020);
                    ctx.drawText(textRenderer, "§a✓", sx + 1, sy + 1, C_GREEN, false);
                }
            }
        }

        if (maxScroll > 0) {
            int sbX = gridX + gridW - 4;
            ctx.fill(sbX, gy, sbX + 2, gy + gh, 0xFF2A2A2A);
            int barH = Math.max(12, gh * visRows / totalRows);
            int barY = gy + gridScroll * (gh - barH) / maxScroll;
            ctx.fill(sbX, barY, sbX + 2, barY + barH, 0xFF666666);
        }
    }

    // ── Detail panel ─────────────────────────────────────────────────────────
    private void drawDetailPanel(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(detailX, detailY, detailX + detailW, detailY + detailH, C_BORDER);
        ctx.fill(detailX + 1, detailY + 1, detailX + detailW - 1, detailY + detailH - 1, C_DETAIL_BG);

        int cx = detailX + detailW / 2;
        int y  = detailY + 8;

        if (selectedItemId == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Grid'den item seçin", cx, detailY + detailH / 3, C_SUBTEXT);
            activeListStartY  = detailY + detailH / 2;
            activeListBottomY = detailY + detailH - 4;
            drawActiveListings(ctx, mouseX, mouseY, activeListStartY, activeListBottomY);
            return;
        }

        // Icon box
        int boxS = 20, boxX = cx - boxS / 2;
        ctx.fill(boxX - 3, y - 3, boxX + boxS + 3, y + boxS + 3, 0xFF333333);
        ctx.fill(boxX - 2, y - 2, boxX + boxS + 2, y + boxS + 2, 0xFF1A1A1A);
        ItemStack stack = getStack(selectedItemId);
        if (!stack.isEmpty()) ctx.drawItem(stack, boxX + 2, y + 2);
        y += boxS + 8;

        // Name
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§f" + truncate(getDisplayName(selectedItemId), detailW - 12), cx, y, C_TEXT);
        y += 12;

        // Stock in depot
        int depotCount = available.getOrDefault(selectedItemId, 0);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§7Depoda: §a×" + depotCount, cx, y, C_SUBTEXT);
        y += 12;

        // Listing status
        Optional<PlayerShopManager.ShopListing> listing = listings.stream()
                .filter(l -> l.itemId().equals(selectedItemId))
                .findFirst();
        if (listing.isPresent()) {
            PlayerShopManager.ShopListing cl = listing.get();
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§a● Satışta: §6⬡" + formatPrice(cl.price()), cx, y, C_GREEN);
            y += 11;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§8Kat: §7" + cl.category(), cx, y, C_SUBTEXT);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§c○ Satışta değil", cx, y, C_RED);
        }

        // Separator before controls
        y += 14;
        ctx.fill(detailX + 6, y, detailX + detailW - 6, y + 1, C_SEP);
        y += 6;

        // Price label
        ctx.drawText(textRenderer, "§7Fiyat (⬡):", detailX + 6, y, C_SUBTEXT, false);

        // Active listings — fill the gap between price-label and the button block
        int pfY = detailY + detailH - 94; // mirrors init() button position
        activeListStartY  = y + 14;
        activeListBottomY = pfY - 4;
        drawActiveListings(ctx, mouseX, mouseY, activeListStartY, activeListBottomY);
    }

    /** Aktif satışlar — detail panelinin orta kısmı (bottomY'ye kadar) */
    private void drawActiveListings(DrawContext ctx, int mouseX, int mouseY,
                                    int startY, int bottomY) {
        int lx = detailX + 2, lw = detailW - 4;
        int listMaxH = bottomY - startY - 4;
        if (listMaxH < 20) return;

        ctx.fill(lx, startY - 2, lx + lw, startY - 1, C_SEP);
        ctx.drawText(textRenderer,
                "§7Aktif İlanlar §8(" + filteredListings.size() + ")",
                lx + 3, startY + 1, C_SUBTEXT, false);

        int rowH    = 16;
        int listY   = startY + 12;
        int listH   = bottomY - listY - 4;
        int visRows = listH / rowH;
        int maxScroll = Math.max(0, filteredListings.size() - visRows);
        listScroll  = Math.max(0, Math.min(listScroll, maxScroll));

        ctx.fill(lx, listY, lx + lw, listY + listH, C_DARK);

        for (int i = 0; i < visRows && i + listScroll < filteredListings.size(); i++) {
            int idx = i + listScroll;
            PlayerShopManager.ShopListing sl = filteredListings.get(idx);
            int ry = listY + i * rowH;
            boolean hov = mouseX >= lx && mouseX < lx + lw
                    && mouseY >= ry && mouseY < ry + rowH;
            boolean sel = idx == selectedListingIdx;

            ctx.fill(lx, ry, lx + lw, ry + rowH - 1,
                    sel ? C_SLOT_SEL : hov ? C_SLOT_HOVER : C_SLOT);

            ItemStack s = getStack(sl.itemId());
            if (!s.isEmpty()) ctx.drawItem(s, lx + 1, ry);

            ctx.drawText(textRenderer, truncate(sl.displayName(), lw - 48),
                    lx + 18, ry + 4, sel ? C_GOLD : C_TEXT, false);

            String pr = "⬡" + formatPrice(sl.price());
            ctx.drawText(textRenderer, pr,
                    lx + lw - 2 - textRenderer.getWidth(pr), ry + 4, C_GOLD, false);
        }
    }

    // ── Mouse ────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Category tabs
        int tabH = 20;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int ty = catY + i * (tabH + 2);
            if (mx >= catX && mx < catX + catW && my >= ty && my < ty + tabH) {
                if (selectedCatIdx != i) { selectedCatIdx = i; applyFilters(); }
                return true;
            }
        }

        // Grid
        int gx = gridX + 4, gy = gridY + 24, gw = gridW - 8, gh = gridH - 28;
        int totalCols = Math.max(1, gw / (SLOT_SIZE + SLOT_GAP));
        int visRows   = Math.max(1, gh / (SLOT_SIZE + SLOT_GAP));

        for (int row = 0; row < visRows; row++) {
            int absRow = row + gridScroll;
            for (int col = 0; col < totalCols; col++) {
                int idx = absRow * totalCols + col;
                if (idx >= gridItems.size()) break;
                int sx = gx + col * (SLOT_SIZE + SLOT_GAP);
                int sy = gy + row * (SLOT_SIZE + SLOT_GAP);
                if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                    selectedItemId = gridItems.get(idx).getKey();
                    priceField.setText("");
                    // İlan varsa fiyat + kategori doldur
                    Optional<PlayerShopManager.ShopListing> existing = listings.stream()
                            .filter(l -> l.itemId().equals(selectedItemId))
                            .findFirst();
                    if (existing.isPresent()) {
                        PlayerShopManager.ShopListing l = existing.get();
                        priceField.setText(String.format("%.0f", l.price()));
                        // Mevcut kategoriye göre index bul
                        int foundIdx = -1;
                        for (int ci = 0; ci < ITEM_CATEGORIES.length; ci++) {
                            if (ITEM_CATEGORIES[ci].equals(l.category())) {
                                foundIdx = ci; break;
                            }
                        }
                        catListingIdx = foundIdx >= 0 ? foundIdx : ITEM_CATEGORIES.length - 1;
                    } else {
                        // İlan yoksa → otomatik kategori tespit et
                        String auto = autoDetectCategory(selectedItemId);
                        catListingIdx = ITEM_CATEGORIES.length - 1; // fallback: Misc
                        for (int ci = 0; ci < ITEM_CATEGORIES.length; ci++) {
                            if (ITEM_CATEGORIES[ci].equals(auto)) {
                                catListingIdx = ci; break;
                            }
                        }
                    }
                    catListingButton.setMessage(Text.literal("§7Kat: §f" + ITEM_CATEGORIES[catListingIdx]));
                    return true;
                }
            }
        }

        // Active listing mini list
        int lx = detailX + 2, lw = detailW - 4;
        int startY = activeListStartY;
        int listY  = startY + 12;
        int listH  = activeListBottomY - listY - 4;
        int rowH   = 16;
        int visL   = listH / rowH;
        for (int i = 0; i < visL && i + listScroll < filteredListings.size(); i++) {
            int idx = i + listScroll;
            int ry  = listY + i * rowH;
            if (mx >= lx && mx < lx + lw && my >= ry && my < ry + rowH) {
                selectedListingIdx = idx;
                PlayerShopManager.ShopListing sl = filteredListings.get(idx);
                selectedItemId = sl.itemId();
                priceField.setText(String.format("%.0f", sl.price()));
                // Kategoriyi de güncelle
                int foundIdx = ITEM_CATEGORIES.length - 1;
                for (int ci = 0; ci < ITEM_CATEGORIES.length; ci++) {
                    if (ITEM_CATEGORIES[ci].equals(sl.category())) { foundIdx = ci; break; }
                }
                catListingIdx = foundIdx;
                catListingButton.setMessage(Text.literal("§7Kat: §f" + ITEM_CATEGORIES[catListingIdx]));
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int gx = gridX + 4, gy = gridY + 24, gw = gridW - 8, gh = gridH - 28;
        if (mx >= gx && mx < gx + gw && my >= gy && my < gy + gh) {
            int totalCols = Math.max(1, gw / (SLOT_SIZE + SLOT_GAP));
            int totalRows = (gridItems.size() + totalCols - 1) / totalCols;
            int visRows   = gh / (SLOT_SIZE + SLOT_GAP);
            int maxScroll = Math.max(0, totalRows - visRows);
            gridScroll    = Math.max(0, Math.min(gridScroll - (int) amount, maxScroll));
            return true;
        }

        int startY = activeListStartY;
        int listY  = startY + 12;
        int listH  = activeListBottomY - listY - 4;
        if (mx >= detailX && mx < detailX + detailW && my >= listY && my < listY + listH) {
            int rowH   = 16;
            int visRows = listH / rowH;
            int maxScroll = Math.max(0, filteredListings.size() - visRows);
            listScroll = Math.max(0, Math.min(listScroll - (int) amount, maxScroll));
            return true;
        }

        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private String truncate(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        while (!s.isEmpty() && textRenderer.getWidth(s + "…") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private String formatPrice(double p) {
        if (p >= 1_000_000) return String.format("%.1fM", p / 1_000_000);
        if (p >= 1_000)     return String.format("%.1fK", p / 1_000);
        return String.format("%.0f", p);
    }

    private ItemStack getStack(String itemId) {
        try {
            Item item = Registries.ITEM.get(new Identifier(itemId));
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    private String getDisplayName(String itemId) {
        try {
            Item item = Registries.ITEM.get(new Identifier(itemId));
            if (item == Items.AIR) return itemId;
            String n = item.getDefaultStack().getName().getString();
            return n.isBlank() ? itemId : n;
        } catch (Exception e) { return itemId; }
    }

    /**
     * Item sınıfı ve ID'sine göre otomatik kategori tespit eder.
     * Aşağıdaki öncelik sırasıyla çalışır:
     *  1. Food component → Yiyecek
     *  2. ArmorItem / ToolItem / SwordItem / RangedWeaponItem → Araç/Ekipman
     *  3. Redstone bileşeni (ID içeriğine göre) → Redstone
     *  4. Kitap / enchantment → Enchant/Kitap
     *  5. Tohum / fidan / bitki → Ekim
     *  6. Mob drop / yaratık → Yaratık
     *  7. BlockItem → Blok
     *  8. Hammadde keywords (ore, ingot, log, …)
     *  9. Misc
     */
    private String autoDetectCategory(String itemId) {
        try {
            Item item = Registries.ITEM.get(new Identifier(itemId));
            if (item == Items.AIR) return "Misc";

            // 1. Food
            if (item.getFoodComponent() != null) return "Yiyecek";

            // 2. Equipment
            if (item instanceof net.minecraft.item.ArmorItem
                    || item instanceof net.minecraft.item.ToolItem
                    || item instanceof net.minecraft.item.SwordItem
                    || item instanceof net.minecraft.item.RangedWeaponItem
                    || item instanceof net.minecraft.item.FishingRodItem
                    || item instanceof net.minecraft.item.ShieldItem
                    || item instanceof net.minecraft.item.TridentItem) {
                return "Araç/Ekipman";
            }

            String id = itemId.replace("minecraft:", "").toLowerCase(Locale.ROOT);

            // 3. Redstone
            if (id.contains("redstone") || id.contains("piston") || id.contains("repeater")
                    || id.contains("comparator") || id.contains("observer") || id.contains("hopper")
                    || id.contains("dropper") || id.contains("dispenser") || id.contains("lever")
                    || id.contains("button") || id.contains("pressure_plate") || id.contains("daylight")
                    || id.contains("target") || id.contains("rail") || id.contains("tripwire")
                    || id.contains("detector") || id.contains("powered")) {
                return "Redstone";
            }

            // 4. Enchant / Book
            if (id.contains("enchanted_book") || id.contains("book") || id.contains("knowledge_book")
                    || id.contains("written_book") || id.contains("writable_book")) {
                return "Enchant/Kitap";
            }

            // 5. Seeds / Saplings / Plants
            if (id.endsWith("_seeds") || id.endsWith("_seed") || id.endsWith("_sapling")
                    || id.contains("flower") || id.contains("mushroom") || id.endsWith("_plant")
                    || id.endsWith("_sprout") || id.contains("fern") || id.contains("grass")
                    || id.contains("vine") || id.contains("lily") || id.contains("azalea")
                    || id.equals("wheat") || id.equals("carrot") || id.equals("potato")
                    || id.equals("beetroot") || id.equals("nether_wart")
                    || id.equals("cocoa_beans") || id.equals("sweet_berries")
                    || id.equals("glow_berries") || id.equals("melon_slice")) {
                return "Ekim";
            }

            // 6. Mob drops / creature items
            if (id.contains("bone") || id.contains("feather") || id.contains("leather")
                    || id.contains("spider_eye") || id.contains("ender_pearl") || id.contains("ender_eye")
                    || id.contains("blaze") || id.contains("ghast") || id.contains("slime")
                    || id.contains("magma_cream") || id.contains("gunpowder") || id.contains("string")
                    || id.contains("rotten_flesh") || id.contains("prismarine_crystal")
                    || id.contains("prismarine_shard") || id.contains("rabbit")
                    || id.contains("mutton") || id.contains("beef") || id.contains("pork")
                    || id.contains("chicken") || id.contains("cod") || id.contains("salmon")
                    || id.contains("tropical_fish") || id.contains("pufferfish")
                    || id.contains("ink_sac") || id.contains("scute") || id.contains("phantom")
                    || id.endsWith("_spawn_egg") || id.contains("dragon_breath")
                    || id.contains("shulker_shell") || id.contains("totem")) {
                return "Yaratık";
            }

            // 7. Block items → Blok
            if (item instanceof net.minecraft.item.BlockItem) return "Blok";

            // 8. Raw materials / ores / ingots / logs
            if (id.endsWith("_ingot") || id.endsWith("_nugget") || id.endsWith("_gem")
                    || id.contains("_ore") || id.startsWith("raw_")
                    || id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_plank")
                    || id.endsWith("_stick") || id.endsWith("_slab") || id.contains("crystal")
                    || id.endsWith("_dust") || id.endsWith("_shard") || id.equals("flint")
                    || id.equals("clay") || id.equals("clay_ball") || id.contains("wool")
                    || id.contains("sand") || id.contains("gravel") || id.contains("quartz")) {
                return "Hammadde";
            }

            return "Misc";

        } catch (Exception e) {
            return "Misc";
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
