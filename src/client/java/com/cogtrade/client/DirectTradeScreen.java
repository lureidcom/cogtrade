package com.cogtrade.client;

import com.cogtrade.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

/**
 * Direct player-to-player trade screen.
 *
 * Layout (in panel-relative coords, px/py = panel top-left):
 *   Title row           y = +8
 *   Section headers     y = +20
 *   3×3 offer grids     y = +32..+94   (left: x=+63, right: x=+247)
 *   Coin area           y = +104..+130
 *   Ready status        y = +136
 *   Separator           y = +153
 *   Buttons             y = +157
 *   Inventory header    y = +175
 *   Main inv rows       y = +183..+218
 *   Hotbar              y = +241
 *
 * Panel: 374×266
 */
public class DirectTradeScreen extends Screen {

    // ── Layout constants (all relative to panelX / panelY) ───────────────

    private static final int PANEL_W = 374;
    private static final int PANEL_H = 266;

    private static final int SLOT_SIZE  = 20; // background square size
    private static final int SLOT_PITCH = 22; // spacing between slot origins

    private static final int LEFT_GRID_X  = 63;  // panel-relative X for left offer grid
    private static final int RIGHT_GRID_X = 247;  // panel-relative X for right offer grid
    private static final int OFFER_GRID_Y = 32;   // panel-relative Y for offer rows

    private static final int COIN_LABEL_Y = 105;
    private static final int COIN_INPUT_Y = 116;  // TextField Y

    private static final int BTN_Y        = 157;
    private static final int BTN_W        = 76;
    private static final int BTN_H        = 14;
    // Centered in left section (center=95) and right section (center=279)
    private static final int READY_BTN_X  = 57;   // panel-relative
    private static final int CANCEL_BTN_X = 241;  // panel-relative

    private static final int INV_START_X  = 106;  // panel-relative, 9 slots × 18px = 162px
    private static final int INV_MAIN_Y   = 183;  // panel-relative, main rows (9-35)
    private static final int INV_HOTBAR_Y = 241;  // panel-relative, hotbar (0-8)

    // ── Colours (matching CogTrade UI palette) ────────────────────────────
    private static final int C_PANEL   = 0xFF1E1E1E;
    private static final int C_DARK    = 0xFF141414;
    private static final int C_SLOT    = 0xFF3A3A3A;
    private static final int C_SLOT_HL = 0xFF555555; // hover highlight
    private static final int C_READY   = 0xFF66BB6A; // green
    private static final int C_WAITING = 0xFF888888; // grey
    private static final int C_GOLD    = 0xFFFFD54F;
    private static final int C_RED     = 0xFFEF5350;
    private static final int C_TEXT    = 0xFFDDDDDD;
    private static final int C_SUBTEXT = 0xFF888888;
    private static final int C_DIV     = 0xFF2A2A2A;

    // ── Static reference for packet handlers ─────────────────────────────

    /** Set when the screen is open; cleared in removed(). */
    public static DirectTradeScreen currentScreen = null;

    // ── Instance state ───────────────────────────────────────────────────

    /** Session this screen belongs to; nulled out when closed gracefully. */
    private String sessionId;
    private final String partnerName;

    private final ItemStack[] myOffer      = new ItemStack[9];
    private final ItemStack[] partnerOffer = new ItemStack[9];
    private double myCoins;
    private double partnerCoins;
    private boolean myReady;
    private boolean partnerReady;

    /** Tracks coin input; inhibits feedback loop when we programmatically update the field. */
    private TextFieldWidget coinInput;
    private boolean updatingCoinInput  = false;
    private int     coinDirtyTicks     = 0;
    private double  lastSentCoinAmount = 0;

    /**
     * Set to true before client.setScreen(null) so removed() does not fire a cancel packet.
     */
    private boolean closedByServer = false;

    /** Pending tooltip to draw at end of render. */
    private ItemStack tooltipStack = ItemStack.EMPTY;

    // Computed panel origin (refreshed at the start of render)
    private int panelX, panelY;

    // ── Constructor / static factory ─────────────────────────────────────

    private DirectTradeScreen(String sessionId, String partnerName,
                               ItemStack[] myOffer, ItemStack[] partnerOffer,
                               double myCoins, double partnerCoins,
                               boolean myReady, boolean partnerReady) {
        super(Text.literal("Doğrudan Takas"));
        this.sessionId   = sessionId;
        this.partnerName = partnerName;

        for (int i = 0; i < 9; i++) {
            this.myOffer[i]      = myOffer[i] != null      ? myOffer[i].copy()      : ItemStack.EMPTY;
            this.partnerOffer[i] = partnerOffer[i] != null ? partnerOffer[i].copy() : ItemStack.EMPTY;
        }
        this.myCoins      = myCoins;
        this.partnerCoins = partnerCoins;
        this.myReady      = myReady;
        this.partnerReady = partnerReady;
    }

    /**
     * Called by OpenDirectTradePacket handler to open the trade screen.
     */
    public static void open(MinecraftClient client, String sessionId, String partnerName,
                             ItemStack[] myOffer, ItemStack[] partnerOffer,
                             double myCoins, double partnerCoins,
                             boolean myReady, boolean partnerReady) {
        DirectTradeScreen screen = new DirectTradeScreen(
                sessionId, partnerName, myOffer, partnerOffer,
                myCoins, partnerCoins, myReady, partnerReady);
        currentScreen = screen;
        client.setScreen(screen);
    }

    /**
     * Called by TradeStateUpdatePacket handler to update offer state in-place.
     */
    public static void updateState(ItemStack[] myOffer, ItemStack[] partnerOffer,
                                    double myCoins, double partnerCoins,
                                    boolean myReady, boolean partnerReady) {
        DirectTradeScreen s = currentScreen;
        if (s == null) return;

        for (int i = 0; i < 9; i++) {
            s.myOffer[i]      = myOffer[i] != null      ? myOffer[i].copy()      : ItemStack.EMPTY;
            s.partnerOffer[i] = partnerOffer[i] != null ? partnerOffer[i].copy() : ItemStack.EMPTY;
        }
        s.partnerCoins = partnerCoins;
        s.myReady      = myReady;
        s.partnerReady = partnerReady;

        // Sync coin amount if server clamped/adjusted it (avoid fighting user input)
        if (Math.abs(myCoins - s.myCoins) > 0.5) {
            s.myCoins = myCoins;
            if (s.coinInput != null) {
                s.updatingCoinInput = true;
                s.coinInput.setText(String.format("%.0f", myCoins));
                s.lastSentCoinAmount = myCoins;
                s.updatingCoinInput  = false;
            }
        }
    }

    /**
     * Called by DirectTradeCancelledPacket / TradeCompletePacket to close GUI from server side.
     */
    public static void closeFromServer(MinecraftClient client, String message) {
        DirectTradeScreen s = currentScreen;
        if (s != null) s.closedByServer = true;
        client.setScreen(null);
        currentScreen = null;
        if (client.player != null && !message.isEmpty()) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    // ── Screen lifecycle ──────────────────────────────────────────────────

    @Override
    protected void init() {
        int px = (this.width  - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        // Coin TextFieldWidget
        coinInput = new TextFieldWidget(
                textRenderer,
                px + LEFT_GRID_X, py + COIN_INPUT_Y, 155, 14,
                Text.empty());
        coinInput.setMaxLength(12);
        coinInput.setText(String.format("%.0f", myCoins));
        coinInput.setPlaceholder(Text.literal("0"));
        coinInput.setChangedListener(text -> {
            if (!updatingCoinInput) coinDirtyTicks = 5;
        });
        addDrawableChild(coinInput);

        // HAZIR button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("✓ HAZIR"),
                btn -> sendReadyPacket())
                .dimensions(px + READY_BTN_X, py + BTN_Y, BTN_W, BTN_H)
                .build());

        // İPTAL button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("✗ İPTAL"),
                btn -> sendCancelPacket())
                .dimensions(px + CANCEL_BTN_X, py + BTN_Y, BTN_W, BTN_H)
                .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (coinInput != null) coinInput.tick();
        if (coinDirtyTicks > 0 && --coinDirtyTicks == 0) {
            sendCoinUpdate();
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        panelX = (this.width  - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        drawPanel(ctx, mouseX, mouseY);

        // Children (TextFieldWidget, ButtonWidgets)
        super.render(ctx, mouseX, mouseY, delta);

        // Tooltip on top of everything
        if (!tooltipStack.isEmpty()) {
            ctx.drawItemTooltip(textRenderer, tooltipStack, mouseX, mouseY);
            tooltipStack = ItemStack.EMPTY;
        }
    }

    @Override
    public void removed() {
        // Guard: don't send cancel if the server already closed us
        if (!closedByServer && sessionId != null && client != null
                && client.getNetworkHandler() != null) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(sessionId);
            ClientPlayNetworking.send(TradeCancelPacket.ID, buf);
        }
        sessionId = null;
        if (currentScreen == this) currentScreen = null;
    }

    @Override
    public boolean shouldPause() { return false; }

    // ── Drawing ───────────────────────────────────────────────────────────

    private void drawPanel(DrawContext ctx, int mouseX, int mouseY) {
        int px = panelX, py = panelY;

        // Background
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, C_PANEL);
        ctx.fill(px, py, px + PANEL_W, py + 1, C_DIV);                       // top border
        ctx.fill(px, py + PANEL_H - 1, px + PANEL_W, py + PANEL_H, C_DIV);  // bottom border

        // Title
        String title = "⬟ DOĞRUDAN TAKAS — " + partnerName;
        int titleW = textRenderer.getWidth(title);
        ctx.drawText(textRenderer, "§e" + title, px + (PANEL_W - titleW) / 2, py + 8, C_GOLD, false);

        // Section headers
        ctx.drawText(textRenderer, "§7Teklifiniz", px + 63, py + 22, C_SUBTEXT, false);
        ctx.drawText(textRenderer, "§7" + partnerName + " Teklifi", px + 247, py + 22, C_SUBTEXT, false);

        // Vertical divider
        ctx.fill(px + 186, py + 20, px + 188, py + 153, C_DIV);

        // Offer grids
        drawOfferGrid(ctx, mouseX, mouseY, true);   // my offer (left)
        drawOfferGrid(ctx, mouseX, mouseY, false);  // partner offer (right)

        // Coin area
        drawCoinArea(ctx);

        // Ready status
        drawReadyStatus(ctx);

        // Separator before buttons
        ctx.fill(px + 8, py + 153, px + PANEL_W - 8, py + 154, C_DIV);

        // Separator before inventory
        ctx.fill(px + 8, py + 173, px + PANEL_W - 8, py + 174, C_DIV);

        // Inventory header
        ctx.drawText(textRenderer, "§8ENVANTERİNİZ", px + INV_START_X, py + 177, C_SUBTEXT, false);

        // Player inventory
        drawInventory(ctx, mouseX, mouseY);
    }

    private void drawOfferGrid(DrawContext ctx, int mouseX, int mouseY, boolean isMyOffer) {
        int px = panelX, py = panelY;
        int gx = px + (isMyOffer ? LEFT_GRID_X : RIGHT_GRID_X);
        int gy = py + OFFER_GRID_Y;
        ItemStack[] offer = isMyOffer ? myOffer : partnerOffer;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = gx + col * SLOT_PITCH;
                int sy = gy + row * SLOT_PITCH;
                int slot = row * 3 + col;

                boolean hovered = isMyOffer
                        && mouseX >= sx && mouseX < sx + SLOT_SIZE
                        && mouseY >= sy && mouseY < sy + SLOT_SIZE;

                ctx.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, hovered ? C_SLOT_HL : C_SLOT);

                ItemStack stack = offer[slot];
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx + 2, sy + 2);
                    ctx.drawItemInSlot(textRenderer, stack, sx + 2, sy + 2);
                    if (hovered) tooltipStack = stack;
                }
            }
        }
    }

    private void drawCoinArea(DrawContext ctx) {
        int px = panelX, py = panelY;

        // My coin label (above text field)
        ctx.drawText(textRenderer, "§7⬡ Teklif Coin:", px + LEFT_GRID_X, py + COIN_LABEL_Y, C_SUBTEXT, false);

        // Partner coin display
        String pCoinStr = "§7⬡ Teklif: §6" + formatCoins(partnerCoins);
        ctx.drawText(textRenderer, pCoinStr, px + RIGHT_GRID_X, py + COIN_LABEL_Y, C_TEXT, false);

        // Partner coin amount large display
        if (partnerCoins > 0) {
            String pAmt = "§6" + formatCoins(partnerCoins) + " ⬡";
            ctx.drawText(textRenderer, pAmt, px + RIGHT_GRID_X, py + COIN_INPUT_Y + 1, C_GOLD, false);
        } else {
            ctx.drawText(textRenderer, "§80 ⬡", px + RIGHT_GRID_X, py + COIN_INPUT_Y + 1, C_SUBTEXT, false);
        }
    }

    private void drawReadyStatus(DrawContext ctx) {
        int px = panelX, py = panelY;
        int y = py + 136;

        // My ready status
        if (myReady) {
            ctx.fill(px + LEFT_GRID_X, y + 2, px + LEFT_GRID_X + 6, y + 8, C_READY);
            ctx.drawText(textRenderer, "§aHAZIR", px + LEFT_GRID_X + 9, y, C_READY, false);
        } else {
            ctx.fill(px + LEFT_GRID_X, y + 2, px + LEFT_GRID_X + 6, y + 8, C_WAITING);
            ctx.drawText(textRenderer, "§8Bekliyor...", px + LEFT_GRID_X + 9, y, C_SUBTEXT, false);
        }

        // Partner ready status
        if (partnerReady) {
            ctx.fill(px + RIGHT_GRID_X, y + 2, px + RIGHT_GRID_X + 6, y + 8, C_READY);
            ctx.drawText(textRenderer, "§aHAZIR", px + RIGHT_GRID_X + 9, y, C_READY, false);
        } else {
            ctx.fill(px + RIGHT_GRID_X, y + 2, px + RIGHT_GRID_X + 6, y + 8, C_WAITING);
            ctx.drawText(textRenderer, "§8Bekliyor...", px + RIGHT_GRID_X + 9, y, C_SUBTEXT, false);
        }

        // Hint under ready status
        if (myReady) {
            ctx.drawText(textRenderer, "§8Partner onayı bekleniyor...", px + LEFT_GRID_X, y + 11, C_SUBTEXT, false);
        }
    }

    private void drawInventory(DrawContext ctx, int mouseX, int mouseY) {
        if (client == null || client.player == null) return;
        int px = panelX, py = panelY;
        net.minecraft.entity.player.PlayerInventory inv = client.player.getInventory();

        // Main inventory: slots 9-35 (rows 9-17, 18-26, 27-35 displayed as rows 0-1-2)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = px + INV_START_X + col * 18;
                int sy = py + INV_MAIN_Y + row * 18;
                int vanillaSlot = 9 + row * 9 + col;

                boolean hovered = mouseX >= sx && mouseX < sx + 16
                        && mouseY >= sy && mouseY < sy + 16;
                ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17, hovered ? C_SLOT_HL : C_SLOT);

                ItemStack stack = inv.getStack(vanillaSlot);
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx, sy);
                    ctx.drawItemInSlot(textRenderer, stack, sx, sy);
                    if (hovered) tooltipStack = stack;
                }
            }
        }

        // Hotbar: slots 0-8, with a small gap
        ctx.fill(px + INV_START_X - 1, py + INV_HOTBAR_Y - 3,
                px + INV_START_X + 162, py + INV_HOTBAR_Y - 2, C_DIV);

        for (int col = 0; col < 9; col++) {
            int sx = px + INV_START_X + col * 18;
            int sy = py + INV_HOTBAR_Y;
            boolean hovered = mouseX >= sx && mouseX < sx + 16
                    && mouseY >= sy && mouseY < sy + 16;
            ctx.fill(sx - 1, sy - 1, sx + 17, sy + 17, hovered ? C_SLOT_HL : C_SLOT);

            ItemStack stack = inv.getStack(col);
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, sx, sy);
                ctx.drawItemInSlot(textRenderer, stack, sx, sy);
                if (hovered) tooltipStack = stack;
            }
        }
    }

    // ── Mouse click handling ──────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        if (sessionId == null) return false;

        int mx = (int) mouseX, my = (int) mouseY;
        int px = panelX, py = panelY;

        // Click on MY offer slot → return item to inventory
        int myOfferSlot = getOfferSlotAt(true, mx, my);
        if (myOfferSlot >= 0) {
            if (!myOffer[myOfferSlot].isEmpty()) {
                sendItemMove(myOfferSlot, -1);
            }
            return true;
        }

        // Click on partner offer slot → read-only, do nothing
        if (getOfferSlotAt(false, mx, my) >= 0) return true;

        // Click on inventory slot → move to first empty offer slot
        int invSlot = getInvSlotAt(mx, my);
        if (invSlot >= 0) {
            if (client != null && client.player != null) {
                ItemStack inInv = client.player.getInventory().getStack(invSlot);
                if (!inInv.isEmpty()) {
                    sendItemMove(-1, invSlot); // -1 = auto-pick offer slot
                }
            }
            return true;
        }

        return false;
    }

    /** Returns the offer slot index (0–8) if the mouse is over one, else -1. */
    private int getOfferSlotAt(boolean isMyOffer, int mx, int my) {
        int gx = panelX + (isMyOffer ? LEFT_GRID_X : RIGHT_GRID_X);
        int gy = panelY + OFFER_GRID_Y;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = gx + col * SLOT_PITCH;
                int sy = gy + row * SLOT_PITCH;
                if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) {
                    return row * 3 + col;
                }
            }
        }
        return -1;
    }

    /**
     * Returns the vanilla inventory slot index (0–35) for the given screen coordinates,
     * or -1 if not over any inventory slot.
     */
    private int getInvSlotAt(int mx, int my) {
        int invX = panelX + INV_START_X;

        // Main inventory rows (vanilla slots 9–35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = invX + col * 18;
                int sy = panelY + INV_MAIN_Y + row * 18;
                if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                    return 9 + row * 9 + col;
                }
            }
        }

        // Hotbar (vanilla slots 0–8)
        for (int col = 0; col < 9; col++) {
            int sx = invX + col * 18;
            int sy = panelY + INV_HOTBAR_Y;
            if (mx >= sx && mx < sx + 16 && my >= sy && my < sy + 16) {
                return col;
            }
        }

        return -1;
    }

    // ── Packet senders ────────────────────────────────────────────────────

    private void sendItemMove(int offerSlot, int invSlot) {
        if (sessionId == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sessionId);
        buf.writeInt(offerSlot);
        buf.writeInt(invSlot);
        ClientPlayNetworking.send(TradeItemMovePacket.ID, buf);
    }

    private void sendCoinUpdate() {
        if (sessionId == null || coinInput == null) return;
        double amount;
        try {
            String text = coinInput.getText().trim();
            amount = text.isEmpty() ? 0.0 : Double.parseDouble(text);
            if (amount < 0) amount = 0;
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (amount == lastSentCoinAmount) return;
        lastSentCoinAmount = amount;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sessionId);
        buf.writeDouble(amount);
        ClientPlayNetworking.send(TradeCoinOfferPacket.ID, buf);
    }

    private void sendReadyPacket() {
        if (sessionId == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sessionId);
        ClientPlayNetworking.send(TradeSetReadyPacket.ID, buf);
    }

    private void sendCancelPacket() {
        if (sessionId == null) return;
        closedByServer = true;  // prevent removed() from double-sending
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(sessionId);
        ClientPlayNetworking.send(TradeCancelPacket.ID, buf);
        sessionId = null;
        if (client != null) client.setScreen(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String formatCoins(double amount) {
        if (amount == 0) return "0";
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000);
        if (amount >= 1_000)     return String.format("%.1fK", amount / 1_000);
        return String.format("%.0f", amount);
    }
}
