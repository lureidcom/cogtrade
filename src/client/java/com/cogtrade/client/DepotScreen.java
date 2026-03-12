package com.cogtrade.client;

import com.cogtrade.market.PlayerShopManager;
import com.cogtrade.network.DepotRefreshPacket;
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
 * Trade Depot yönetim ekranı.
 * Sadece depo stok görünümü — fiyat ayarı / ilan yönetimi burada YAPILMAZ.
 * İlanlar Trade Post bloğundan yönetilir.
 */
@Environment(EnvType.CLIENT)
public class DepotScreen extends Screen {

    // ── Colors ──────────────────────────────────────────────────────────────
    private static final int C_PANEL        = 0xFF1E1E1E;
    private static final int C_BORDER       = 0xFF0A0A0A;
    private static final int C_GOLD         = 0xFFFFD54F;
    private static final int C_GREEN        = 0xFF66BB6A;
    private static final int C_TEXT         = 0xFFDDDDDD;
    private static final int C_SUBTEXT      = 0xFF888888;
    private static final int C_SLOT         = 0xFF2D2D2D;
    private static final int C_SLOT_HOVER   = 0xFF424242;
    private static final int C_SLOT_SEL     = 0xFF8B6914;
    private static final int C_SLOT_LISTED  = 0xFF1A3020;   // yeşil tint — Trade Post'ta satışta
    private static final int C_SEP          = 0xFF3A3A3A;
    private static final int C_CYAN         = 0xFF4DD0E1;
    private static final int C_DARK         = 0xFF141414;
    private static final int C_CAT_ACT      = 0xFF3A2E0E;
    private static final int C_CAT_IN       = 0xFF252525;
    private static final int C_DETAIL_BG    = 0xFF1A1A1A;
    private static final int C_RED          = 0xFFEF5350;

    private static final int  SLOT_SIZE  = 36;
    private static final int  SLOT_GAP   = 4;
    private static final String[] CATEGORIES = {"Tümü", "Satışta", "Satışta Değil"};

    // ── Data ────────────────────────────────────────────────────────────────
    private Map<String, Integer>                    available;
    private List<PlayerShopManager.ShopListing>     listings;  // read-only, Trade Post'tan gelir

    // Filtered views
    private List<Map.Entry<String, Integer>>        gridItems;

    // ── UI State ────────────────────────────────────────────────────────────
    private int    selectedCatIdx = 0;
    private String selectedItemId = null;
    private int    gridScroll     = 0;
    private int    refreshTick    = 0;

    // ── Layout ──────────────────────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int catX,   catY,   catW,   catH;
    private int gridX,  gridY,  gridW,  gridH;
    private int detailX, detailY, detailW, detailH;

    // ── Widgets ─────────────────────────────────────────────────────────────
    private TextFieldWidget searchField;
    private String          searchQuery = "";

    // ── Constructor ─────────────────────────────────────────────────────────
    public DepotScreen(Map<String, Integer> available,
                       List<PlayerShopManager.ShopListing> listings) {
        super(Text.literal("Trade Depot"));
        this.available = new LinkedHashMap<>(available);
        this.listings  = new ArrayList<>(listings);
    }

    /** Sunucudan gelen güncel veriyle ekranı yenile (yeniden init yapmadan). */
    public void refresh(Map<String, Integer> newAvailable,
                        List<PlayerShopManager.ShopListing> newListings) {
        this.available = new LinkedHashMap<>(newAvailable);
        this.listings  = new ArrayList<>(newListings);
        applyFilters();
    }

    // ── Init ────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        computeLayout();

        searchField = new TextFieldWidget(textRenderer,
                gridX + 4, panelY + 6, gridW - 8, 14, Text.literal("Ara"));
        searchField.setMaxLength(40);
        searchField.setSuggestion("Stokta ara...");
        searchField.setText(searchQuery);
        searchField.setChangedListener(s -> { searchQuery = s; applyFilters(); });
        addDrawableChild(searchField);

        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), btn -> close())
                .dimensions(panelX + panelW - 17, panelY + 4, 13, 13).build());

        applyFilters();
    }

    private void computeLayout() {
        panelW = Math.min(760, width - 20);
        panelH = Math.min(470, height - 20);
        panelX = (width  - panelW) / 2;
        panelY = (height - panelH) / 2;

        catW = 110;
        catX = panelX + 4;
        catY = panelY + 26;
        catH = panelH - 30;

        detailW = Math.max(165, Math.min(185, panelW / 4));
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

        gridScroll = 0;
        if (selectedItemId != null &&
                gridItems.stream().noneMatch(e -> e.getKey().equals(selectedItemId)))
            selectedItemId = null;
    }

    // ── Tick (real-time refresh) ─────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();
        if (++refreshTick >= 40) {
            refreshTick = 0;
            PacketByteBuf buf = PacketByteBufs.create();
            ClientPlayNetworking.send(DepotRefreshPacket.ID, buf);
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
                "§6✦ Trade Depot — Depo Görünümü ✦",
                panelX + panelW / 2, panelY + 8, C_GOLD);
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
                    sel ? C_GOLD : C_SEP);
            ctx.drawText(textRenderer, sel ? "§6" + CATEGORIES[i] : "§7" + CATEGORIES[i],
                    catX + 7, ty + 6, sel ? C_GOLD : C_SUBTEXT, false);
        }

        int badgeY = catY + CATEGORIES.length * (tabH + 2) + 8;
        ctx.drawText(textRenderer, "§8Stok: §f" + available.size(),
                catX + 5, badgeY, C_SUBTEXT, false);
        ctx.drawText(textRenderer, "§8Satışta: §e" + listings.size(),
                catX + 5, badgeY + 11, C_SUBTEXT, false);

        // Info note
        int noteY = badgeY + 26;
        ctx.drawText(textRenderer, "§7İlanlar Trade", catX + 5, noteY,      C_SUBTEXT, false);
        ctx.drawText(textRenderer, "§7Post'tan yön.", catX + 5, noteY + 10, C_SUBTEXT, false);
    }

    // ── Grid ─────────────────────────────────────────────────────────────────
    private void drawGrid(DrawContext ctx, int mouseX, int mouseY) {
        int gx = gridX + 4;
        int gy = gridY + 24;
        int gw = gridW - 8;
        int gh = gridH - 28;

        ctx.fill(gridX, gridY + 22, gridX + gridW, gridY + gridH, C_DARK);

        if (gridItems.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    available.isEmpty() ? "§7Sandıklarda item yok" : "§7Sonuç bulunamadı",
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

                // Count badge
                String countStr = count >= 1000 ? (count / 1000) + "k" : String.valueOf(count);
                int cw = textRenderer.getWidth(countStr);
                ctx.fill(sx + SLOT_SIZE - cw - 3, sy + SLOT_SIZE - 9,
                        sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xAA000000);
                ctx.drawText(textRenderer, countStr,
                        sx + SLOT_SIZE - cw - 2, sy + SLOT_SIZE - 8,
                        listed ? C_GREEN : C_TEXT, false);

                // Sold indicator
                if (listed) {
                    ctx.fill(sx + 1, sy + 1, sx + 8, sy + 7, 0xAA1A4020);
                    ctx.drawText(textRenderer, "§a✓", sx + 1, sy + 1, C_GREEN, false);
                }
            }
        }

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = gridX + gridW - 4;
            ctx.fill(sbX, gy, sbX + 2, gy + gh, 0xFF2A2A2A);
            int barH = Math.max(12, gh * visRows / totalRows);
            int barY = gy + gridScroll * (gh - barH) / maxScroll;
            ctx.fill(sbX, barY, sbX + 2, barY + barH, 0xFF666666);
        }
    }

    // ── Detail panel (read-only) ──────────────────────────────────────────────
    private void drawDetailPanel(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(detailX, detailY, detailX + detailW, detailY + detailH, C_BORDER);
        ctx.fill(detailX + 1, detailY + 1, detailX + detailW - 1, detailY + detailH - 1, C_DETAIL_BG);

        int cx = detailX + detailW / 2;

        if (selectedItemId == null) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Grid'den item seçin", cx, detailY + 40, C_SUBTEXT);
            drawInfoNote(ctx, cx);
            return;
        }

        int y = detailY + 8;

        // Icon box
        int boxS = 20;
        int boxX = cx - boxS / 2;
        ctx.fill(boxX - 3, y - 3, boxX + boxS + 3, y + boxS + 3, 0xFF333333);
        ctx.fill(boxX - 2, y - 2, boxX + boxS + 2, y + boxS + 2, 0xFF1A1A1A);
        ItemStack stack = getStack(selectedItemId);
        if (!stack.isEmpty()) ctx.drawItem(stack, boxX + 2, y + 2);
        y += boxS + 8;

        // Name
        String name = truncate(getDisplayName(selectedItemId), detailW - 12);
        ctx.drawCenteredTextWithShadow(textRenderer, "§f" + name, cx, y, C_TEXT);
        y += 12;

        // Count in chests
        int chestCount = available.getOrDefault(selectedItemId, 0);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§7Sandıkta: §a×" + chestCount, cx, y, C_SUBTEXT);
        y += 14;

        // Separator
        ctx.fill(detailX + 6, y, detailX + detailW - 6, y + 1, C_SEP);
        y += 8;

        // Listing status (read-only)
        Optional<PlayerShopManager.ShopListing> listing = listings.stream()
                .filter(l -> l.itemId().equals(selectedItemId))
                .findFirst();

        if (listing.isPresent()) {
            PlayerShopManager.ShopListing cl = listing.get();
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§a● Trade Post'ta Satışta", cx, y, C_GREEN);
            y += 12;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§6⬡" + formatPrice(cl.price()) + " §8/ adet", cx, y, C_GOLD);
            y += 12;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§8Kat: §7" + cl.category(), cx, y, C_SUBTEXT);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§c○ Trade Post'ta Değil", cx, y, C_RED);
            y += 12;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§8Trade Post'u aç ve", cx, y, C_SUBTEXT);
            y += 10;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§8buradan satışa ekle.", cx, y, C_SUBTEXT);
        }

        y += 20;
        drawInfoNote(ctx, cx);
    }

    private void drawInfoNote(DrawContext ctx, int cx) {
        int iy = detailY + detailH - 40;
        ctx.fill(detailX + 4, iy - 4, detailX + detailW - 4, detailY + detailH - 4, 0xFF0D1A10);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§8İlan yönetimi için", cx, iy,      C_SUBTEXT);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§8Trade Post'a tıkla", cx, iy + 11, C_SUBTEXT);
    }

    // ── Mouse ────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int tabH = 20;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int ty = catY + i * (tabH + 2);
            if (mx >= catX && mx < catX + catW && my >= ty && my < ty + tabH) {
                if (selectedCatIdx != i) { selectedCatIdx = i; applyFilters(); }
                return true;
            }
        }

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
                    return true;
                }
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

    @Override
    public boolean shouldPause() { return false; }
}
