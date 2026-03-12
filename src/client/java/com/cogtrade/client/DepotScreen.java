package com.cogtrade.client;

import com.cogtrade.market.PlayerShopManager;
import com.cogtrade.network.DepotActionPacket;
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

@Environment(EnvType.CLIENT)
public class DepotScreen extends Screen {

    // ── Colors ──────────────────────────────────────────────────────────
    private static final int C_PANEL    = 0xFF282828;
    private static final int C_BORDER   = 0xFF141414;
    private static final int C_GOLD     = 0xFFFFD54F;
    private static final int C_GREEN    = 0xFF66BB6A;
    private static final int C_RED      = 0xFFEF5350;
    private static final int C_TEXT     = 0xFFDDDDDD;
    private static final int C_SUBTEXT  = 0xFF888888;
    private static final int C_SLOT     = 0xFF373737;
    private static final int C_HOVER    = 0xFF505050;
    private static final int C_SEP      = 0xFF404040;
    private static final int C_CYAN     = 0xFF4DD0E1;
    private static final int C_SELECTED = 0xFF3A2E0E;
    private static final int C_DARK     = 0xFF1E1E1E;

    private static final String[] CATEGORIES = { "Misc", "Blok", "Yiyecek", "Araç", "Hammadde" };

    // ── Data ─────────────────────────────────────────────────────────────
    private final Map<String, Integer> available;
    private final List<PlayerShopManager.ShopListing> listings;

    // Filtered/sorted views
    private List<Map.Entry<String, Integer>> filteredAvailable;
    private List<PlayerShopManager.ShopListing> filteredListings;

    // ── UI State ──────────────────────────────────────────────────────────
    private int scrollAvailable = 0;
    private int scrollListings  = 0;
    private int selectedAvailableIndex = -1;
    private int selectedListingIndex   = -1;
    private String selectedItemId = null;
    private int selectedCategoryIdx = 0;  // for "category" when adding
    private int availSortMode = 0;         // 0=Name↑ 1=Name↓ 2=Count↑ 3=Count↓
    private int listingSortMode = 0;       // 0=Name  1=Price↑ 2=Price↓

    // ── Layout ────────────────────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int leftX, leftY, leftW, leftH;
    private int rightX, rightY, rightW, rightH;
    private int footerY;

    // ── Widgets ────────────────────────────────────────────────────────────
    private TextFieldWidget searchField;
    private TextFieldWidget priceField;
    private ButtonWidget addButton;
    private ButtonWidget removeButton;
    private ButtonWidget categoryButton;
    private ButtonWidget availSortButton;
    private ButtonWidget listingSortButton;

    public DepotScreen(Map<String, Integer> available,
                       List<PlayerShopManager.ShopListing> listings) {
        super(Text.literal("Trade Depot — Stok Yönetimi"));
        this.available = available;
        this.listings  = new ArrayList<>(listings);
        this.filteredAvailable = new ArrayList<>(available.entrySet());
        this.filteredListings  = new ArrayList<>(listings);
    }

    @Override
    protected void init() {
        panelW = Math.min(720, width - 20);
        panelH = Math.min(460, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int halfW = (panelW - 18) / 2;
        leftW  = halfW;
        rightW = panelW - halfW - 18;
        leftX  = panelX + 6;
        rightX = panelX + halfW + 12;
        leftY  = panelY + 50;
        rightY = panelY + 50;
        footerY = panelY + panelH - 54;
        leftH  = footerY - leftY - 6;
        rightH = leftH;

        // ── Close ──────────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), btn -> close())
                .dimensions(panelX + panelW - 17, panelY + 4, 13, 13).build());

        // ── Search field (left column) ────────────────────────────────────
        searchField = new TextFieldWidget(textRenderer,
                leftX + 1, panelY + 28, leftW - 64, 14, Text.literal("Ara"));
        searchField.setMaxLength(40);
        searchField.setSuggestion("İtem ara...");
        searchField.setChangedListener(s -> applyFilters());
        addDrawableChild(searchField);

        // ── Available sort button ─────────────────────────────────────────
        availSortButton = ButtonWidget.builder(
                Text.literal(getAvailSortLabel()),
                btn -> {
                    availSortMode = (availSortMode + 1) % 4;
                    applyFilters();
                    btn.setMessage(Text.literal(getAvailSortLabel()));
                })
                .dimensions(leftX + leftW - 60, panelY + 26, 60, 16).build();
        addDrawableChild(availSortButton);

        // ── Listing sort button ───────────────────────────────────────────
        listingSortButton = ButtonWidget.builder(
                Text.literal(getListingSortLabel()),
                btn -> {
                    listingSortMode = (listingSortMode + 1) % 3;
                    applyFilters();
                    btn.setMessage(Text.literal(getListingSortLabel()));
                })
                .dimensions(rightX + rightW - 62, panelY + 26, 62, 16).build();
        addDrawableChild(listingSortButton);

        // ── Footer widgets ────────────────────────────────────────────────
        priceField = new TextFieldWidget(textRenderer,
                panelX + 6, footerY + 30, 100, 14, Text.literal("Fiyat"));
        priceField.setMaxLength(10);
        priceField.setSuggestion("Fiyat...");
        addDrawableChild(priceField);

        categoryButton = ButtonWidget.builder(
                Text.literal("§7Kat: §f" + CATEGORIES[selectedCategoryIdx]),
                btn -> {
                    selectedCategoryIdx = (selectedCategoryIdx + 1) % CATEGORIES.length;
                    btn.setMessage(Text.literal("§7Kat: §f" + CATEGORIES[selectedCategoryIdx]));
                })
                .dimensions(panelX + 110, footerY + 28, 84, 16).build();
        addDrawableChild(categoryButton);

        addButton = ButtonWidget.builder(Text.literal("§a+ Satışa Ekle"), btn -> handleAdd())
                .dimensions(panelX + 198, footerY + 28, 110, 16).build();
        addDrawableChild(addButton);

        removeButton = ButtonWidget.builder(Text.literal("§c− Kaldır"), btn -> handleRemove())
                .dimensions(panelX + 312, footerY + 28, 80, 16).build();
        addDrawableChild(removeButton);

        applyFilters();
    }

    // ── Sort label helpers ─────────────────────────────────────────────────
    private String getAvailSortLabel() {
        return switch (availSortMode) {
            case 1 -> "Ad ↓";
            case 2 -> "Adet ↑";
            case 3 -> "Adet ↓";
            default -> "Ad ↑";
        };
    }

    private String getListingSortLabel() {
        return switch (listingSortMode) {
            case 1 -> "Fiyat ↑";
            case 2 -> "Fiyat ↓";
            default -> "Ad";
        };
    }

    // ── Filter / Sort ──────────────────────────────────────────────────────
    private void applyFilters() {
        String query = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";

        filteredAvailable = available.entrySet().stream()
                .filter(e -> query.isEmpty() ||
                        getDisplayName(e.getKey()).toLowerCase(Locale.ROOT).contains(query))
                .collect(Collectors.toList());

        Comparator<Map.Entry<String, Integer>> availComp = switch (availSortMode) {
            case 1 -> Comparator.<Map.Entry<String, Integer>, String>
                    comparing(e -> getDisplayName(e.getKey())).reversed();
            case 2 -> Comparator.comparingInt(Map.Entry::getValue);
            case 3 -> Comparator.<Map.Entry<String, Integer>>
                    comparingInt(Map.Entry::getValue).reversed();
            default -> Comparator.comparing(e -> getDisplayName(e.getKey()));
        };
        filteredAvailable.sort(availComp);

        filteredListings = new ArrayList<>(listings);
        Comparator<PlayerShopManager.ShopListing> listComp = switch (listingSortMode) {
            case 1 -> Comparator.comparingDouble(PlayerShopManager.ShopListing::price);
            case 2 -> Comparator.<PlayerShopManager.ShopListing>
                    comparingDouble(PlayerShopManager.ShopListing::price).reversed();
            default -> Comparator.comparing(PlayerShopManager.ShopListing::displayName);
        };
        filteredListings.sort(listComp);

        scrollAvailable = 0;
        scrollListings  = 0;
        selectedAvailableIndex = -1;
        selectedListingIndex   = -1;
        selectedItemId = null;
    }

    // ── Action handlers ────────────────────────────────────────────────────
    private void handleAdd() {
        if (selectedItemId == null) return;
        double price;
        try { price = Double.parseDouble(priceField.getText()); }
        catch (NumberFormatException e) { return; }
        if (price <= 0) return;

        String category = CATEGORIES[selectedCategoryIdx];
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(DepotActionPacket.ACTION_ADD);
        buf.writeString(selectedItemId);
        buf.writeDouble(price);
        buf.writeString(category);
        ClientPlayNetworking.send(DepotActionPacket.ID, buf);
    }

    private void handleRemove() {
        if (selectedListingIndex < 0 || selectedListingIndex >= filteredListings.size()) return;
        String itemId = filteredListings.get(selectedListingIndex).itemId();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(DepotActionPacket.ACTION_REMOVE);
        buf.writeString(itemId);
        buf.writeDouble(0);
        buf.writeString("");
        ClientPlayNetworking.send(DepotActionPacket.ID, buf);
    }

    // ── Render ─────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        drawPanel(ctx);
        drawAvailableList(ctx, mouseX, mouseY);
        drawListingsList(ctx, mouseX, mouseY);
        drawFooter(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawPanel(DrawContext ctx) {
        // Outer border + panel
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_BORDER);
        ctx.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + panelH - 1, C_PANEL);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§6✦ Trade Depot — Stok Yönetimi ✦",
                panelX + panelW / 2, panelY + 8, C_GOLD);
        ctx.fill(panelX + 3, panelY + 20, panelX + panelW - 3, panelY + 21, C_SEP);

        // Footer separator
        ctx.fill(panelX + 3, footerY - 2, panelX + panelW - 3, footerY - 1, C_SEP);

        // Mid divider (vertical between columns)
        int midX = rightX - 3;
        ctx.fill(midX, panelY + 22, midX + 1, footerY - 2, C_SEP);
    }

    private void drawAvailableList(DrawContext ctx, int mouseX, int mouseY) {
        // Column header
        ctx.drawText(textRenderer,
                "§7Sandıklardaki İtemler §8(§a" + filteredAvailable.size() + "§8)",
                leftX, panelY + 23, C_SUBTEXT, false);

        // List box
        ctx.fill(leftX, leftY, leftX + leftW, leftY + leftH, C_BORDER);
        ctx.fill(leftX + 1, leftY + 1, leftX + leftW - 1, leftY + leftH - 1, C_DARK);

        if (filteredAvailable.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    available.isEmpty() ? "Sandıklarda item yok" : "Sonuç bulunamadı",
                    leftX + leftW / 2, leftY + leftH / 2 - 4, C_SUBTEXT);
            return;
        }

        int rowH = 20;
        int visibleRows = (leftH - 2) / rowH;
        int maxScroll = Math.max(0, filteredAvailable.size() - visibleRows);
        scrollAvailable = Math.max(0, Math.min(scrollAvailable, maxScroll));

        for (int i = 0; i < visibleRows && (i + scrollAvailable) < filteredAvailable.size(); i++) {
            int idx = i + scrollAvailable;
            Map.Entry<String, Integer> entry = filteredAvailable.get(idx);
            int ry = leftY + 1 + i * rowH;
            boolean hov = mouseX >= leftX + 1 && mouseX < leftX + leftW - 2
                    && mouseY >= ry && mouseY < ry + rowH;
            boolean sel = entry.getKey().equals(selectedItemId);

            ctx.fill(leftX + 1, ry, leftX + leftW - 2, ry + rowH - 1,
                    sel ? C_SELECTED : (hov ? C_HOVER : C_SLOT));

            ItemStack stack = getStack(entry.getKey());
            if (!stack.isEmpty()) ctx.drawItem(stack, leftX + 3, ry + 2);

            String name = truncate(getDisplayName(entry.getKey()), leftW - 58);
            ctx.drawText(textRenderer, name, leftX + 22, ry + 6,
                    sel ? C_GOLD : C_TEXT, false);

            String countStr = "×" + entry.getValue();
            ctx.drawText(textRenderer, countStr,
                    leftX + leftW - 6 - textRenderer.getWidth(countStr), ry + 6,
                    C_GREEN, false);
        }

        drawScrollbar(ctx, leftX + leftW - 3, leftY, leftH,
                filteredAvailable.size(), visibleRows, scrollAvailable);
    }

    private void drawListingsList(DrawContext ctx, int mouseX, int mouseY) {
        // Column header
        ctx.drawText(textRenderer,
                "§7Aktif Satışlar §8(§e" + filteredListings.size() + "§8)",
                rightX, panelY + 23, C_SUBTEXT, false);

        // List box
        ctx.fill(rightX, rightY, rightX + rightW, rightY + rightH, C_BORDER);
        ctx.fill(rightX + 1, rightY + 1, rightX + rightW - 1, rightY + rightH - 1, C_DARK);

        if (filteredListings.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Satışta ürün yok",
                    rightX + rightW / 2, rightY + rightH / 2 - 4, C_SUBTEXT);
            return;
        }

        int rowH = 20;
        int visibleRows = (rightH - 2) / rowH;
        int maxScroll = Math.max(0, filteredListings.size() - visibleRows);
        scrollListings = Math.max(0, Math.min(scrollListings, maxScroll));

        for (int i = 0; i < visibleRows && (i + scrollListings) < filteredListings.size(); i++) {
            int idx = i + scrollListings;
            PlayerShopManager.ShopListing listing = filteredListings.get(idx);
            int ry = rightY + 1 + i * rowH;
            boolean hov = mouseX >= rightX + 1 && mouseX < rightX + rightW - 2
                    && mouseY >= ry && mouseY < ry + rowH;
            boolean sel = idx == selectedListingIndex;

            ctx.fill(rightX + 1, ry, rightX + rightW - 2, ry + rowH - 1,
                    sel ? C_SELECTED : (hov ? C_HOVER : C_SLOT));

            ItemStack stack = getStack(listing.itemId());
            if (!stack.isEmpty()) ctx.drawItem(stack, rightX + 3, ry + 2);

            // Category chip
            String cat = listing.category();
            int catW = textRenderer.getWidth(cat) + 6;
            ctx.fill(rightX + 22, ry + 4, rightX + 22 + catW, ry + 15, 0xFF1A2A3A);
            ctx.drawText(textRenderer, cat, rightX + 25, ry + 6, C_CYAN, false);

            // Item name
            String name = truncate(listing.displayName(), rightW - catW - 60);
            ctx.drawText(textRenderer, name, rightX + 24 + catW, ry + 6,
                    sel ? C_GOLD : C_TEXT, false);

            // Price
            String priceStr = "⬡" + formatPrice(listing.price());
            ctx.drawText(textRenderer, priceStr,
                    rightX + rightW - 6 - textRenderer.getWidth(priceStr), ry + 6,
                    C_GOLD, false);
        }

        drawScrollbar(ctx, rightX + rightW - 3, rightY, rightH,
                filteredListings.size(), visibleRows, scrollListings);
    }

    private void drawFooter(DrawContext ctx) {
        // Background
        ctx.fill(panelX + 1, footerY, panelX + panelW - 1, panelY + panelH - 1, 0xFF222222);

        // Selected item row
        if (selectedItemId != null) {
            ItemStack stack = getStack(selectedItemId);
            if (!stack.isEmpty()) ctx.drawItem(stack, panelX + 6, footerY + 4);
            ctx.drawText(textRenderer, "§f" + truncate(getDisplayName(selectedItemId), 110),
                    panelX + 26, footerY + 8, C_TEXT, false);

            // Sandık count hint
            Integer count = available.get(selectedItemId);
            if (count != null) {
                String hint = "§7(§a×" + count + " §7sandıkta)";
                ctx.drawText(textRenderer, hint, panelX + 26 + textRenderer.getWidth(
                        getDisplayName(selectedItemId)) + 4, footerY + 8, C_SUBTEXT, false);
            }
        } else {
            ctx.drawText(textRenderer, "§7Sol panelden bir item seçin →",
                    panelX + 6, footerY + 8, C_SUBTEXT, false);
        }

        // Price label
        ctx.drawText(textRenderer, "§7Fiyat:", panelX + 6, footerY + 32, C_SUBTEXT, false);
    }

    private void drawScrollbar(DrawContext ctx, int x, int y, int h,
                                int total, int visible, int scroll) {
        if (total <= visible) return;
        ctx.fill(x, y, x + 2, y + h, 0xFF2A2A2A);
        int barH = Math.max(12, h * visible / total);
        int barY = y + scroll * (h - barH) / Math.max(1, total - visible);
        ctx.fill(x, barY, x + 2, barY + barH, 0xFF666666);
    }

    // ── Mouse / Key ────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int rowH = 20;

        // Left panel
        int visL = (leftH - 2) / rowH;
        for (int i = 0; i < visL && (i + scrollAvailable) < filteredAvailable.size(); i++) {
            int idx = i + scrollAvailable;
            int ry = leftY + 1 + i * rowH;
            if (mx >= leftX + 1 && mx < leftX + leftW - 2 && my >= ry && my < ry + rowH) {
                selectedAvailableIndex = idx;
                selectedItemId = filteredAvailable.get(idx).getKey();
                priceField.setText("");
                priceField.setSuggestion("Fiyat gir...");
                return true;
            }
        }

        // Right panel
        int visR = (rightH - 2) / rowH;
        for (int i = 0; i < visR && (i + scrollListings) < filteredListings.size(); i++) {
            int idx = i + scrollListings;
            int ry = rightY + 1 + i * rowH;
            if (mx >= rightX + 1 && mx < rightX + rightW - 2 && my >= ry && my < ry + rowH) {
                selectedListingIndex = idx;
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (mx >= leftX && mx < leftX + leftW && my >= leftY && my < leftY + leftH) {
            scrollAvailable = Math.max(0, scrollAvailable - (int) amount);
            return true;
        }
        if (mx >= rightX && mx < rightX + rightW && my >= rightY && my < rightY + rightH) {
            scrollListings = Math.max(0, scrollListings - (int) amount);
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private String truncate(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        while (s.length() > 0 && textRenderer.getWidth(s + "…") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private String formatPrice(double p) {
        if (p >= 1_000_000) return String.format("%.1fM", p / 1_000_000);
        if (p >= 1_000) return String.format("%.1fK", p / 1_000);
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
            String name = item.getDefaultStack().getName().getString();
            return name.isBlank() ? itemId : name;
        } catch (Exception e) { return itemId; }
    }

    @Override
    public boolean shouldPause() { return false; }
}
