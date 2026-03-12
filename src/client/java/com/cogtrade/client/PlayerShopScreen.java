package com.cogtrade.client;

import com.cogtrade.network.PlayerShopBuyPacket;
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
public class PlayerShopScreen extends Screen {

    // ── Colors ──────────────────────────────────────────────────────────
    private static final int C_GOLD     = 0xFFFFD54F;
    private static final int C_GREEN    = 0xFF66BB6A;
    private static final int C_RED      = 0xFFEF5350;
    private static final int C_ORANGE   = 0xFFFF9800;
    private static final int C_TEXT     = 0xFFDDDDDD;
    private static final int C_SUBTEXT  = 0xFF888888;
    private static final int C_SLOT     = 0xFF373737;
    private static final int C_HOVER    = 0xFF505050;
    private static final int C_BORDER   = 0xFF141414;
    private static final int C_PANEL    = 0xFF282828;
    private static final int C_SEP      = 0xFF404040;
    private static final int C_CYAN     = 0xFF4DD0E1;
    private static final int C_DARK     = 0xFF1E1E1E;
    private static final int C_SELECTED = 0xFF3A2E0E;
    private static final int C_TAB_ACT  = 0xFF3A2E0A;
    private static final int C_TAB_IN   = 0xFF252525;

    private static final int TAB_H = 16;

    // ── Data ─────────────────────────────────────────────────────────────
    private final String ownerUuid;
    private final String ownerName;
    private final List<ClientMarketItem> allItems;
    private final double sellMultiplier;

    // ── Filter / Sort state ───────────────────────────────────────────────
    private List<ClientMarketItem> displayItems;
    private List<String> categories;  // "Tümü" + unique categories
    private int selectedCategoryIdx = 0;
    // 0=Ad↑  1=Ad↓  2=Fiyat↑  3=Fiyat↓  4=Stok↑  5=Stok↓
    private int sortMode = 0;

    // ── Selection ─────────────────────────────────────────────────────────
    private int selectedIndex = -1;
    private int tradeAmount   = 1;
    private int scroll        = 0;

    // ── Layout ────────────────────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int listX,  listY,  listW,  listH;
    private int detailX, detailY, detailW, detailH;
    private int tabBarY;

    // ── Widgets ────────────────────────────────────────────────────────────
    private TextFieldWidget searchField;
    private ButtonWidget    buyButton;
    private ButtonWidget    sortButton;
    private ButtonWidget    minusButton;
    private ButtonWidget    plusButton;

    public PlayerShopScreen(String ownerUuid, String ownerName,
                            List<ClientMarketItem> items, double sellMultiplier) {
        super(Text.literal(ownerName + "'in Pazarı"));
        this.ownerUuid      = ownerUuid;
        this.ownerName      = ownerName;
        this.allItems       = new ArrayList<>(items);
        this.sellMultiplier = sellMultiplier;

        // Build ordered category list
        Set<String> catSet = new LinkedHashSet<>();
        for (ClientMarketItem item : items) catSet.add(item.getCategory());
        this.categories = new ArrayList<>();
        this.categories.add("Tümü");
        this.categories.addAll(catSet);

        this.displayItems = new ArrayList<>(items);
    }

    // ══════════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        panelW = Math.min(700, width - 20);
        panelH = Math.min(470, height - 20);
        panelX = (width  - panelW) / 2;
        panelY = (height - panelH) / 2;

        // List: ~60 %,  Detail: rest
        listW   = panelW * 60 / 100;
        detailW = panelW - listW - 12;
        listX   = panelX + 4;
        detailX = listX + listW + 4;

        tabBarY = panelY + 22;                     // below title
        int controlsY  = tabBarY + TAB_H + 4;     // search / sort row
        int colHeaderY = controlsY + 18;           // column labels
        listY  = colHeaderY + 14;
        listH  = panelH - (listY - panelY) - 6;

        detailY = panelY + 14;
        detailH = panelH - 26;

        // ── Close ─────────────────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), btn -> close())
                .dimensions(panelX + panelW - 17, panelY + 4, 13, 13).build());

        // ── Search ────────────────────────────────────────────────────────
        searchField = new TextFieldWidget(textRenderer,
                listX + 1, controlsY + 1, listW - 72, 14, Text.literal("Ara"));
        searchField.setMaxLength(40);
        searchField.setSuggestion("İtem ara...");
        searchField.setChangedListener(s -> applyFilters());
        addDrawableChild(searchField);

        // ── Sort button ───────────────────────────────────────────────────
        sortButton = ButtonWidget.builder(
                Text.literal(getSortLabel()),
                btn -> {
                    sortMode = (sortMode + 1) % 6;
                    applyFilters();
                    btn.setMessage(Text.literal(getSortLabel()));
                })
                .dimensions(listX + listW - 68, controlsY - 1, 68, 16).build();
        addDrawableChild(sortButton);

        // ── Amount −/+ ────────────────────────────────────────────────────
        int amtY = panelY + panelH - 68;
        minusButton = ButtonWidget.builder(Text.literal("§c−"), btn -> {
            tradeAmount = Math.max(1, tradeAmount - 1);
            updateButtons();
        }).dimensions(detailX + 4, amtY, 22, 16).build();
        addDrawableChild(minusButton);

        plusButton = ButtonWidget.builder(Text.literal("§a+"), btn -> {
            int maxStock = (selectedIndex >= 0 && selectedIndex < displayItems.size())
                    ? displayItems.get(selectedIndex).getStock() : 64;
            tradeAmount = Math.min(tradeAmount + 1, Math.min(64, maxStock));
            updateButtons();
        }).dimensions(detailX + detailW - 26, amtY, 22, 16).build();
        addDrawableChild(plusButton);

        // ── Buy ───────────────────────────────────────────────────────────
        buyButton = ButtonWidget.builder(Text.literal("§aSatın Al"), btn -> handleBuy())
                .dimensions(detailX + 4, panelY + panelH - 28, detailW - 8, 20).build();
        addDrawableChild(buyButton);

        applyFilters();
        updateButtons();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Sort label
    // ══════════════════════════════════════════════════════════════════════
    private String getSortLabel() {
        return switch (sortMode) {
            case 1 -> "Ad ↓";
            case 2 -> "Fiyat ↑";
            case 3 -> "Fiyat ↓";
            case 4 -> "Stok ↑";
            case 5 -> "Stok ↓";
            default -> "Ad ↑";
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // Filter / Sort
    // ══════════════════════════════════════════════════════════════════════
    private void applyFilters() {
        String query  = searchField != null
                ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String selCat = categories.get(selectedCategoryIdx);

        displayItems = allItems.stream()
                .filter(item -> {
                    boolean catOk = selCat.equals("Tümü") ||
                            item.getCategory().equals(selCat);
                    boolean srcOk = query.isEmpty() ||
                            item.getDisplayName().toLowerCase(Locale.ROOT).contains(query);
                    return catOk && srcOk;
                })
                .collect(Collectors.toList());

        Comparator<ClientMarketItem> comp = switch (sortMode) {
            case 1 -> Comparator.comparing(ClientMarketItem::getDisplayName).reversed();
            case 2 -> Comparator.comparingDouble(ClientMarketItem::getPrice);
            case 3 -> Comparator.<ClientMarketItem>comparingDouble(
                    ClientMarketItem::getPrice).reversed();
            case 4 -> Comparator.comparingInt(ClientMarketItem::getStock);
            case 5 -> Comparator.<ClientMarketItem>comparingInt(
                    ClientMarketItem::getStock).reversed();
            default -> Comparator.comparing(ClientMarketItem::getDisplayName);
        };
        displayItems.sort(comp);

        scroll        = 0;
        selectedIndex = -1;
        tradeAmount   = 1;
        updateButtons();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Render
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        drawMainPanel(ctx, mouseX, mouseY);
        drawDetailPanel(ctx);
        super.render(ctx, mouseX, mouseY, delta);
    }

    // ── Main (list) panel ─────────────────────────────────────────────────
    private void drawMainPanel(DrawContext ctx, int mouseX, int mouseY) {
        // Outer frame
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_BORDER);
        ctx.fill(panelX + 1, panelY + 1,
                panelX + panelW - 1, panelY + panelH - 1, C_PANEL);

        // Title (centered over list area)
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§b✦ " + ownerName + "'in Pazarı ✦",
                listX + listW / 2, panelY + 7, 0xFF55FFFF);
        ctx.fill(panelX + 3, panelY + 20,
                panelX + panelW - 3, panelY + 21, C_SEP);

        // Vertical divider
        ctx.fill(detailX - 3, panelY + 22,
                detailX - 2, panelY + panelH - 4, C_SEP);

        // Category tabs
        drawCategoryTabs(ctx, mouseX, mouseY);

        // Column header bar
        int colY = tabBarY + TAB_H + 22;
        ctx.fill(listX, colY - 2, listX + listW, colY + 12, 0xFF202020);
        ctx.drawText(textRenderer, "§7İtem",
                listX + 22, colY, C_SUBTEXT, false);
        ctx.drawText(textRenderer, "§7Kategori",
                listX + listW - 152, colY, C_SUBTEXT, false);
        ctx.drawText(textRenderer, "§7Fiyat",
                listX + listW - 96, colY, C_SUBTEXT, false);
        ctx.drawText(textRenderer, "§7Stok",
                listX + listW - 42, colY, C_SUBTEXT, false);

        // List box
        ctx.fill(listX, listY, listX + listW, listY + listH, C_BORDER);
        ctx.fill(listX + 1, listY + 1,
                listX + listW - 1, listY + listH - 1, C_DARK);

        if (displayItems.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    allItems.isEmpty() ? "Bu pazar boş" : "Sonuç bulunamadı",
                    listX + listW / 2, listY + listH / 2 - 4, C_SUBTEXT);
            return;
        }

        int rowH       = 20;
        int visRows    = (listH - 2) / rowH;
        int maxScroll  = Math.max(0, displayItems.size() - visRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        for (int i = 0; i < visRows && (i + scroll) < displayItems.size(); i++) {
            int idx = i + scroll;
            ClientMarketItem item = displayItems.get(idx);
            int ry = listY + 1 + i * rowH;

            boolean hov = mouseX >= listX + 1 && mouseX < listX + listW - 2
                    && mouseY >= ry && mouseY < ry + rowH;
            boolean sel = idx == selectedIndex;

            ctx.fill(listX + 1, ry, listX + listW - 2, ry + rowH - 1,
                    sel ? C_SELECTED : (hov ? C_HOVER : C_SLOT));

            // Icon
            ItemStack stack = getStack(item.getItemId());
            if (!stack.isEmpty()) ctx.drawItem(stack, listX + 3, ry + 2);

            // Name
            String name = truncate(item.getDisplayName(), listW - 172);
            ctx.drawText(textRenderer, name, listX + 22, ry + 6,
                    sel ? C_GOLD : C_TEXT, false);

            // Category
            String cat = truncate(item.getCategory(), 48);
            ctx.drawText(textRenderer, cat, listX + listW - 150, ry + 6, 0xFF6699AA, false);

            // Price
            String priceStr = "⬡" + formatPrice(item.getPrice());
            ctx.drawText(textRenderer, priceStr, listX + listW - 94, ry + 6, C_GOLD, false);

            // Stock (colour by amount)
            int    stock     = item.getStock();
            int    stockCol  = stock <= 5 ? C_RED : (stock <= 20 ? C_ORANGE : C_GREEN);
            String stockStr  = String.valueOf(stock);
            ctx.drawText(textRenderer, stockStr, listX + listW - 40, ry + 6, stockCol, false);
        }

        drawScrollbar(ctx, listX + listW - 3, listY, listH,
                displayItems.size(), visRows, scroll);
    }

    private void drawCategoryTabs(DrawContext ctx, int mouseX, int mouseY) {
        int x = listX;
        for (int i = 0; i < categories.size(); i++) {
            String cat = categories.get(i);
            int    w   = textRenderer.getWidth(cat) + 12;
            boolean sel = i == selectedCategoryIdx;
            boolean hov = mouseX >= x && mouseX < x + w
                    && mouseY >= tabBarY && mouseY < tabBarY + TAB_H;

            ctx.fill(x, tabBarY, x + w, tabBarY + TAB_H,
                    sel ? C_TAB_ACT : (hov ? 0xFF303030 : C_TAB_IN));
            ctx.fill(x, tabBarY + TAB_H - 1, x + w, tabBarY + TAB_H,
                    sel ? C_GOLD : C_SEP);
            ctx.drawText(textRenderer, sel ? "§6" + cat : "§7" + cat,
                    x + 6, tabBarY + 4, sel ? C_GOLD : C_SUBTEXT, false);
            x += w + 2;
        }
    }

    // ── Detail panel ──────────────────────────────────────────────────────
    private void drawDetailPanel(DrawContext ctx) {
        ctx.fill(detailX, detailY,
                detailX + detailW, detailY + detailH, C_BORDER);
        ctx.fill(detailX + 1, detailY + 1,
                detailX + detailW - 1, detailY + detailH - 1, C_DARK);

        if (selectedIndex < 0 || selectedIndex >= displayItems.size()) {
            int midY = detailY + detailH / 2;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§7Bir ürün seçin",
                    detailX + detailW / 2, midY - 8, C_SUBTEXT);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§8← listeden tıklayın",
                    detailX + detailW / 2, midY + 6, 0xFF555555);
            return;
        }

        ClientMarketItem item = displayItems.get(selectedIndex);
        int cx = detailX + detailW / 2;
        int y  = detailY + 12;

        // ── Icon with box ────────────────────────────────────────────────
        int boxS = 24;
        int boxX = cx - boxS / 2;
        ctx.fill(boxX - 4, y - 4, boxX + boxS + 4, y + boxS + 4, 0xFF333333);
        ctx.fill(boxX - 3, y - 3, boxX + boxS + 3, y + boxS + 3, 0xFF1A1A1A);
        ItemStack stack = getStack(item.getItemId());
        if (!stack.isEmpty()) ctx.drawItem(stack, boxX + 4, y + 4);
        y += boxS + 12;

        // ── Name ────────────────────────────────────────────────────────
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§f" + item.getDisplayName(), cx, y, C_TEXT);
        y += 13;

        // ── Category badge ───────────────────────────────────────────────
        String badge = "  " + item.getCategory() + "  ";
        int    bw    = textRenderer.getWidth(badge);
        ctx.fill(cx - bw / 2 - 1, y - 2, cx + bw / 2 + 1, y + 10, 0xFF1A2A3A);
        ctx.drawCenteredTextWithShadow(textRenderer, "§b" + badge, cx, y, C_CYAN);
        y += 20;

        // ── Info separator ───────────────────────────────────────────────
        ctx.fill(detailX + 8, y, detailX + detailW - 8, y + 1, C_SEP);
        y += 8;

        // ── Info rows ────────────────────────────────────────────────────
        double balance = ClientEconomyData.getBalance();
        double total   = item.getPrice() * tradeAmount;

        infoRow(ctx, y, "Birim Fiyat",
                "⬡" + formatPrice(item.getPrice()), C_GOLD);  y += 14;

        infoRow(ctx, y, "Bakiyeniz",
                "⬡" + formatPrice(balance),
                balance >= total ? C_GREEN : C_RED);            y += 14;

        int stock = item.getStock();
        infoRow(ctx, y, "Stok",
                String.valueOf(stock),
                stock <= 5 ? C_RED : (stock <= 20 ? C_ORANGE : C_GREEN)); y += 14;

        // ── Amount separator ─────────────────────────────────────────────
        ctx.fill(detailX + 8, y, detailX + detailW - 8, y + 1, C_SEP);
        y += 8;

        // ── Amount row (−  n  +) ──────────────────────────────────────────
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§7Miktar:  §f" + tradeAmount,
                cx, y, C_TEXT);
        y += 20;

        // ── Total ────────────────────────────────────────────────────────
        ctx.drawCenteredTextWithShadow(textRenderer,
                "§7Toplam: §6⬡" + formatPrice(total),
                cx, y, balance >= total ? C_GOLD : C_RED);
    }

    /** Right-aligned value, left-aligned label — inside the detail panel. */
    private void infoRow(DrawContext ctx, int y, String label, String value, int valueCol) {
        ctx.drawText(textRenderer, "§8" + label,
                detailX + 8, y, C_SUBTEXT, false);
        int vw = textRenderer.getWidth(value);
        ctx.drawText(textRenderer, value,
                detailX + detailW - 8 - vw, y, valueCol, false);
    }

    private void drawScrollbar(DrawContext ctx, int x, int y, int h,
                                int total, int visible, int scroll) {
        if (total <= visible) return;
        ctx.fill(x, y, x + 2, y + h, 0xFF2A2A2A);
        int barH = Math.max(12, h * visible / total);
        int barY = y + scroll * (h - barH) / Math.max(1, total - visible);
        ctx.fill(x, barY, x + 2, barY + barH, 0xFF666666);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Mouse / Key
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Category tabs
        int tx = listX;
        for (int i = 0; i < categories.size(); i++) {
            int w = textRenderer.getWidth(categories.get(i)) + 12;
            if (mx >= tx && mx < tx + w && my >= tabBarY && my < tabBarY + TAB_H) {
                if (selectedCategoryIdx != i) { selectedCategoryIdx = i; applyFilters(); }
                return true;
            }
            tx += w + 2;
        }

        // List rows
        int rowH = 20;
        int vis  = (listH - 2) / rowH;
        for (int i = 0; i < vis && (i + scroll) < displayItems.size(); i++) {
            int idx = i + scroll;
            int ry  = listY + 1 + i * rowH;
            if (mx >= listX + 1 && mx < listX + listW - 2 && my >= ry && my < ry + rowH) {
                selectedIndex = idx;
                tradeAmount   = 1;
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int maxScroll = Math.max(0, displayItems.size() - (listH - 2) / 20);
            scroll = Math.max(0, Math.min(scroll - (int) amount, maxScroll));
            return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Action / update
    // ══════════════════════════════════════════════════════════════════════
    private void handleBuy() {
        if (selectedIndex < 0 || selectedIndex >= displayItems.size()) return;
        ClientMarketItem item = displayItems.get(selectedIndex);
        if (ClientEconomyData.getBalance() < item.getPrice() * tradeAmount) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(ownerUuid);
        buf.writeString(item.getItemId());
        buf.writeInt(tradeAmount);
        ClientPlayNetworking.send(PlayerShopBuyPacket.ID, buf);
    }

    private void updateButtons() {
        boolean hasSel = selectedIndex >= 0 && selectedIndex < displayItems.size();
        if (!hasSel) {
            buyButton.active   = false;
            minusButton.active = false;
            plusButton.active  = false;
            return;
        }
        ClientMarketItem item  = displayItems.get(selectedIndex);
        double total           = item.getPrice() * tradeAmount;
        boolean canAfford      = ClientEconomyData.isInitialized()
                && ClientEconomyData.getBalance() >= total;
        boolean hasStock       = item.getStock() >= tradeAmount;

        buyButton.active   = canAfford && hasStock;
        minusButton.active = tradeAmount > 1;
        plusButton.active  = tradeAmount < Math.min(64, item.getStock());
    }

    @Override
    public void tick() { super.tick(); updateButtons(); }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════
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

    @Override
    public boolean shouldPause() { return false; }
}
