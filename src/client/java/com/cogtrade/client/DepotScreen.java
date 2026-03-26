package com.cogtrade.client;

import com.cogtrade.CogTrade;
import com.cogtrade.client.gui.NineSliceRenderer;
import com.cogtrade.market.PlayerShopManager;
import com.cogtrade.network.DepotRefreshPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class DepotScreen extends Screen {

    // ── Textures ──────────────────────────────────────────────────────────
    private static final Identifier BOOK_TEX  = new Identifier(CogTrade.MOD_ID, "textures/gui/book.png");
    private static final Identifier FRAME_TEX = new Identifier(CogTrade.MOD_ID, "textures/gui/item-frame.png");

    // ── Book native dimensions ─────────────────────────────────────────────
    private static final int BOOK_W = 1623, BOOK_H = 1080;

    // ── Page safe-zone native offsets ──────────────────────────────────────
    private static final int LP_X = 199, LP_Y = 233, LP_W = 559, LP_H = 609;
    private static final int RP_X = 864, RP_Y = 233, RP_W = 559, RP_H = 609;

    // ── Content sizes — SCREEN PIXELS (not scaled) ────────────────────────
    // Left page
    private static final int TITLE_H  = 28;   // zone height for the 2× scaled title
    private static final int TABS_H   = 20;   // category tab row height
    private static final int SF_H     = 14;   // search field height
    private static final int FRAME_S  = 28;   // item slot frame (matches market)
    private static final int PITCH    = 31;   // slot pitch = FRAME_S + 3

    // Right page
    private static final int BIG_FRAME = 52;  // detail icon frame
    private static final int BIG_ITEM  =  2;  // item scale factor inside big frame

    // ── Colors ────────────────────────────────────────────────────────────
    private static final int C_TITLE = 0xFF3D2200;
    private static final int C_LABEL = 0xFF5A3F10;
    private static final int C_DIM   = 0xFF7A5B2A;
    private static final int C_SEP   = 0x887A5B2A;
    private static final int C_GREEN = 0xFF1A6E28;
    private static final int C_RED   = 0xFF6E1A1A;
    private static final int C_GOLD  = 0xFF9B6A00;

    private static final String[] TABS = {"Tümü", "Satışta", "Satışta Değil"};

    // ── Data ──────────────────────────────────────────────────────────────
    private Map<String, Integer>                available;
    private List<PlayerShopManager.ShopListing> listings;
    private List<Map.Entry<String, Integer>>    grid;

    // ── UI state ──────────────────────────────────────────────────────────
    private int     catIdx    = 0;
    private String  selId     = null;
    private int     scroll    = 0;
    private int     tick      = 0;
    private String  sfBuf     = "";
    private boolean sfActive  = false;

    // ── Layout (computed once in init) ────────────────────────────────────
    private int guiX, guiY;
    private double scale;

    // ── Left page bounds (screen px, set in init) ─────────────────────────
    private int lpX, lpY, lpW, lpH;
    private int rpX, rpY, rpW;

    public DepotScreen(Map<String, Integer> available,
                       List<PlayerShopManager.ShopListing> listings) {
        super(Text.literal("Trade Depot"));
        this.available = new LinkedHashMap<>(available);
        this.listings  = new ArrayList<>(listings);
    }

    public void refresh(Map<String, Integer> a, List<PlayerShopManager.ShopListing> l) {
        this.available = new LinkedHashMap<>(a);
        this.listings  = new ArrayList<>(l);
        filter();
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private int sc(int v) { return Math.max(1, (int)(v * scale)); }

    @Override
    protected void init() {
        scale = Math.min(width * 0.9 / BOOK_W, height * 0.9 / BOOK_H);
        guiX  = (width  - sc(BOOK_W)) / 2;
        guiY  = (height - sc(BOOK_H)) / 2;
        lpX = guiX + sc(LP_X);  lpY = guiY + sc(LP_Y);
        lpW = sc(LP_W);          lpH = sc(LP_H);
        rpX = guiX + sc(RP_X);  rpY = guiY + sc(RP_Y);
        rpW = sc(RP_W);
        filter();
    }

    @Override
    public void tick() {
        if (++tick >= 40) {
            tick = 0;
            ClientPlayNetworking.send(DepotRefreshPacket.ID, PacketByteBufs.create());
        }
    }

    private void filter() {
        String q = sfBuf.trim().toLowerCase(Locale.ROOT);
        Set<String> listed = listings.stream()
                .map(PlayerShopManager.ShopListing::itemId).collect(Collectors.toSet());

        Stream<Map.Entry<String, Integer>> s = available.entrySet().stream();
        if (!q.isEmpty()) s = s.filter(e -> name(e.getKey()).toLowerCase(Locale.ROOT).contains(q));
        if (catIdx == 1) s = s.filter(e ->  listed.contains(e.getKey()));
        if (catIdx == 2) s = s.filter(e -> !listed.contains(e.getKey()));

        grid   = s.sorted(Comparator.comparing(e -> name(e.getKey()))).collect(Collectors.toList());
        scroll = 0;
        if (selId != null && grid.stream().noneMatch(e -> e.getKey().equals(selId))) selId = null;
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        NineSliceRenderer.drawFullTexture(ctx, BOOK_TEX, guiX, guiY, sc(BOOK_W), sc(BOOK_H), BOOK_W, BOOK_H);
        renderLeft(ctx, mx, my);
        renderRight(ctx, mx, my);
    }

    // ── Left page ─────────────────────────────────────────────────────────
    private void renderLeft(DrawContext ctx, int mx, int my) {
        int y = lpY;

        // Title (2× scale)
        title2x(ctx, "Trade Depot", lpX + lpW/2, y + (TITLE_H - 16)/2, C_TITLE);
        y += TITLE_H;

        // Separator
        ctx.fill(lpX + 8, y, lpX + lpW - 8, y + 1, C_SEP);
        y += 5;

        // Category tabs
        int tabW = lpW / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tx = lpX + i * tabW;
            boolean sel = (i == catIdx);
            boolean hov = over(mx, my, tx, y, tabW, TABS_H);
            ctx.fill(tx+1, y+1, tx+tabW-1, y+TABS_H-1,
                    sel ? 0x88BB9922 : hov ? 0x44AA7700 : 0);
            ctx.fill(tx, y, tx+tabW, y+1, C_SEP);
            ctx.fill(tx, y+TABS_H-1, tx+tabW, y+TABS_H, C_SEP);
            if (i > 0) ctx.fill(tx, y+1, tx+1, y+TABS_H-1, C_SEP);
            if (sel) ctx.fill(tx+2, y+TABS_H-2, tx+tabW-2, y+TABS_H-1, C_GOLD);
            String lbl = TABS[i];
            ctx.drawText(textRenderer, lbl,
                    tx + (tabW - textRenderer.getWidth(lbl))/2,
                    y + (TABS_H - 8)/2,
                    sel || hov ? C_LABEL : C_DIM, false);
        }
        y += TABS_H + 4;

        // Separator
        ctx.fill(lpX + 8, y, lpX + lpW - 8, y + 1, C_SEP);
        y += 4;

        // Search field
        renderField(ctx, mx, my, lpX + 4, y, lpW - 8, SF_H, sfBuf, sfActive, "Ara...");
        y += SF_H + 4;

        // Separator
        ctx.fill(lpX + 8, y, lpX + lpW - 8, y + 1, C_SEP);
        y += 3;

        // Item grid
        renderGrid(ctx, mx, my, y);
    }

    private void renderGrid(DrawContext ctx, int mx, int my, int startY) {
        int gH = lpY + lpH - startY;

        if (grid == null || grid.isEmpty()) {
            String msg = available.isEmpty() ? "Sandıklarda item yok" : "Sonuç bulunamadı";
            cText(ctx, msg, lpX + lpW/2, startY + gH/2 - 4, C_DIM);
            return;
        }

        int cols  = Math.max(1, lpW / PITCH);
        int vrows = Math.max(1, gH / PITCH);
        int total = (grid.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - vrows)));

        Set<String> listed = listings.stream()
                .map(PlayerShopManager.ShopListing::itemId).collect(Collectors.toSet());

        for (int r = 0; r < vrows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (r + scroll) * cols + c;
                if (idx >= grid.size()) break;
                var e   = grid.get(idx);
                String id  = e.getKey();
                int    cnt = e.getValue();
                int sx = lpX + c * PITCH;
                int sy = startY + r * PITCH;

                boolean hov = over(mx, my, sx, sy, FRAME_S, FRAME_S);
                boolean sel = id.equals(selId);
                boolean lst = listed.contains(id);

                NineSliceRenderer.drawFullTexture(ctx, FRAME_TEX, sx, sy, FRAME_S, FRAME_S, 32, 32);
                if (sel)      ctx.fill(sx+2, sy+2, sx+FRAME_S-2, sy+FRAME_S-2, 0x77FFCC00);
                else if (lst) ctx.fill(sx+2, sy+2, sx+FRAME_S-2, sy+FRAME_S-2, 0x4400CC44);
                else if (hov) ctx.fill(sx+2, sy+2, sx+FRAME_S-2, sy+FRAME_S-2, 0x44FFDD88);

                ItemStack stk = stack(id);
                if (!stk.isEmpty()) ctx.drawItem(stk, sx + (FRAME_S-16)/2, sy + (FRAME_S-16)/2);

                // Badge
                String badge = cnt >= 1000 ? (cnt/1000)+"k" : String.valueOf(cnt);
                int bw = textRenderer.getWidth(badge);
                ctx.fill(sx+FRAME_S-bw-3, sy+FRAME_S-9, sx+FRAME_S, sy+FRAME_S, 0xCC000000);
                ctx.drawText(textRenderer, badge, sx+FRAME_S-bw-2, sy+FRAME_S-8,
                        lst ? 0xFF80FF80 : 0xFFEEEEEE, false);
            }
        }

        // Scrollbar
        if (total > vrows) {
            int sbX = lpX + lpW - 3;
            int barH = Math.max(8, gH * vrows / total);
            int barY = startY + scroll * (gH - barH) / Math.max(1, total - vrows);
            ctx.fill(sbX, startY, sbX+3, startY+gH, 0x22000000);
            ctx.fill(sbX, barY,   sbX+3, barY+barH,  0xBB9B6200);
        }
    }

    // ── Right page ────────────────────────────────────────────────────────
    private void renderRight(DrawContext ctx, int mx, int my) {
        int cx = rpX + rpW/2;
        int y  = rpY;

        // Title
        title2x(ctx, "Depo Görünümü", cx, y + (TITLE_H-16)/2, C_TITLE);
        y += TITLE_H;
        ctx.fill(rpX + 8, y, rpX + rpW - 8, y+1, C_SEP);
        y += 6;

        // Note box (always at bottom)
        int nbY  = rpY + lpH - 72;
        int nbX  = rpX + 10;
        int nbW  = rpW - 20;
        ctx.fill(nbX, nbY, nbX+nbW, nbY+64, 0x22CC8800);
        ctx.fill(nbX, nbY,   nbX+nbW, nbY+1,  0x99AA7700);
        ctx.fill(nbX, nbY+63, nbX+nbW, nbY+64, 0x99AA7700);
        ctx.fill(nbX, nbY+1, nbX+1, nbY+63, 0x66AA7700);
        ctx.fill(nbX+nbW-1, nbY+1, nbX+nbW, nbY+63, 0x66AA7700);
        cText(ctx, "İlan yönetimi için", cx, nbY+14, C_DIM);
        cText(ctx, "Trade Post'a sağ tıkla", cx, nbY+30, C_DIM);
        cText(ctx, "→", cx, nbY+46, C_DIM);

        if (selId == null) {
            cText(ctx, "◄ Sol sayfadan item seçin", cx, rpY + lpH/2 - 4, C_DIM);
            return;
        }

        // Big icon
        int imgX = cx - BIG_FRAME/2;
        NineSliceRenderer.drawFullTexture(ctx, FRAME_TEX, imgX, y, BIG_FRAME, BIG_FRAME, 32, 32);
        ItemStack stk = stack(selId);
        if (!stk.isEmpty()) {
            MatrixStack ms = ctx.getMatrices();
            ms.push();
            int margin = (BIG_FRAME - 16*BIG_ITEM) / 2;
            ms.translate(imgX + margin, y + margin, 150);
            ms.scale(BIG_ITEM, BIG_ITEM, BIG_ITEM);
            ctx.drawItem(stk, 0, 0);
            ms.pop();
        }
        y += BIG_FRAME + 8;

        // Item name (1.5×)
        title15x(ctx, truncate(name(selId), rpW - 20), cx, y, C_TITLE);
        y += 14;

        // Count
        cText(ctx, "Sandıkta: ×" + available.getOrDefault(selId, 0), cx, y, C_LABEL);
        y += 14;

        // Separator
        ctx.fill(rpX + 20, y, rpX + rpW - 20, y+1, C_SEP);
        y += 8;

        // Listing status
        Optional<PlayerShopManager.ShopListing> opt = listings.stream()
                .filter(l -> l.itemId().equals(selId)).findFirst();

        if (opt.isPresent()) {
            var cl = opt.get();
            // Green badge
            String sLbl = "● Satışta";
            int bw = textRenderer.getWidth(sLbl) + 12;
            ctx.fill(cx-bw/2, y-2, cx+bw/2, y+10, 0x33228822);
            cText(ctx, sLbl, cx, y, C_GREEN);
            y += 16;
            cText(ctx, "⬡ " + price(cl.price()) + " / adet", cx, y, C_GOLD);
            y += 14;
            cText(ctx, "Kat: " + cl.category(), cx, y, C_DIM);
        } else {
            String sLbl = "○ Satışta değil";
            int bw = textRenderer.getWidth(sLbl) + 12;
            ctx.fill(cx-bw/2, y-2, cx+bw/2, y+10, 0x33882222);
            cText(ctx, sLbl, cx, y, C_RED);
            y += 16;
            cText(ctx, "Trade Post'ta satışa ekle.", cx, y, C_DIM);
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Search field
        int sfY = lpY + TITLE_H + 5 + TABS_H + 4 + 4 + 1 + 4;
        if (over(mx, my, lpX+4, sfY, lpW-8, SF_H)) { sfActive = true; return true; }
        sfActive = false;

        // Tabs
        int tabY = lpY + TITLE_H + 5;
        int tabW = lpW / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            if (over(mx, my, lpX + i*tabW, tabY, tabW, TABS_H)) {
                if (catIdx != i) { catIdx = i; filter(); }
                return true;
            }
        }

        // Grid
        int gridY = sfY + SF_H + 4 + 3;
        if (grid != null) {
            int cols  = Math.max(1, lpW / PITCH);
            int gH    = lpY + lpH - gridY;
            int vrows = Math.max(1, gH / PITCH);
            for (int r = 0; r < vrows; r++) {
                for (int c = 0; c < cols; c++) {
                    int idx = (r + scroll) * cols + c;
                    if (idx >= grid.size()) break;
                    int sx = lpX + c * PITCH;
                    int sy = gridY + r * PITCH;
                    if (over(mx, my, sx, sy, FRAME_S, FRAME_S)) {
                        selId = grid.get(idx).getKey();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amt) {
        int gridY = lpY + TITLE_H + 5 + TABS_H + 4 + 4 + 1 + 4 + SF_H + 4 + 3;
        int gH    = lpY + lpH - gridY;
        if (over(mx, my, lpX, gridY, lpW, gH) && grid != null) {
            int cols  = Math.max(1, lpW / PITCH);
            int vrows = Math.max(1, gH / PITCH);
            int total = (grid.size() + cols - 1) / cols;
            scroll = Math.max(0, Math.min(scroll - (int)amt, Math.max(0, total-vrows)));
            return true;
        }
        return super.mouseScrolled(mx, my, amt);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (sfActive) { sfBuf += c; filter(); return true; }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (sfActive) { sfActive = false; return true; }
            close(); return true;
        }
        if (sfActive) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !sfBuf.isEmpty()) {
                sfBuf = sfBuf.substring(0, sfBuf.length()-1); filter(); return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                sfActive = false; return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ── Draw helpers ──────────────────────────────────────────────────────

    /** 2× title text, centered at cx, no shadow. */
    private void title2x(DrawContext ctx, String text, int cx, int y, int color) {
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(cx, y, 0);
        ms.scale(2f, 2f, 1f);
        ctx.drawText(textRenderer, text, -textRenderer.getWidth(text)/2, 0, color, false);
        ms.pop();
    }

    /** 1.5× text, centered at cx, no shadow. */
    private void title15x(DrawContext ctx, String text, int cx, int y, int color) {
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(cx, y, 0);
        ms.scale(1.5f, 1.5f, 1f);
        ctx.drawText(textRenderer, text, -textRenderer.getWidth(text)/2, 0, color, false);
        ms.pop();
    }

    /** Centered text, no shadow. */
    private void cText(DrawContext ctx, String t, int cx, int y, int c) {
        ctx.drawText(textRenderer, t, cx - textRenderer.getWidth(t)/2, y, c, false);
    }

    private void renderField(DrawContext ctx, int mx, int my,
                             int fx, int fy, int fw, int fh,
                             String buf, boolean active, String hint) {
        boolean hov = over(mx, my, fx, fy, fw, fh);
        ctx.fill(fx, fy, fx+fw, fy+fh,
                active ? 0x44BB9900 : hov ? 0x22AA7700 : 0x22000000);
        int bc = active ? 0xFFBB9900 : 0x887A5B2A;
        ctx.fill(fx,     fy,      fx+fw,    fy+1,    bc);
        ctx.fill(fx,     fy+fh-1, fx+fw,    fy+fh,   bc);
        ctx.fill(fx,     fy,      fx+1,     fy+fh,   bc);
        ctx.fill(fx+fw-1,fy,      fx+fw,    fy+fh,   bc);

        boolean empty = buf.isEmpty() && !active;
        String vis  = empty ? hint : buf;
        int    tc   = empty ? 0xAA7A5B2A : 0xFF5A3F10;
        int    tx   = fx + 4;
        int    ty   = fy + (fh - 8) / 2;
        int    maxW = fw - 8;
        while (textRenderer.getWidth(vis) > maxW && !vis.isEmpty()) vis = vis.substring(1);
        ctx.drawText(textRenderer, vis, tx, ty, tc, false);
        if (active && (System.currentTimeMillis()/500) % 2 == 0) {
            int cx2 = tx + textRenderer.getWidth(vis);
            ctx.fill(cx2, ty-1, cx2+1, ty+9, 0xFF7A5B2A);
        }
    }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }

    private String truncate(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        while (!s.isEmpty() && textRenderer.getWidth(s+"…") > maxPx)
            s = s.substring(0, s.length()-1);
        return s + "…";
    }

    private String price(double p) {
        if (p >= 1_000_000) return String.format("%.1fM", p/1_000_000);
        if (p >= 1_000)     return String.format("%.1fK", p/1_000);
        return String.format("%.0f", p);
    }

    private ItemStack stack(String id) {
        try {
            Item it = Registries.ITEM.get(new Identifier(id));
            return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    private String name(String id) {
        try {
            Item it = Registries.ITEM.get(new Identifier(id));
            if (it == Items.AIR) return id;
            String n = it.getDefaultStack().getName().getString();
            return n.isBlank() ? id : n;
        } catch (Exception e) { return id; }
    }

    @Override public boolean shouldPause() { return false; }
}
