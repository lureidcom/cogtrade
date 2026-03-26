package com.cogtrade.client;

import com.cogtrade.CogTrade;
import com.cogtrade.client.gui.NineSliceRenderer;
import com.cogtrade.market.PlayerShopManager;
import com.cogtrade.network.PostActionPacket;
import com.cogtrade.network.PostRefreshPacket;
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
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class TradePostScreen extends Screen {

    // ── Textures ──────────────────────────────────────────────────────────
    private static final Identifier BOOK_TEX  = new Identifier(CogTrade.MOD_ID, "textures/gui/book.png");
    private static final Identifier FRAME_TEX = new Identifier(CogTrade.MOD_ID, "textures/gui/item-frame.png");

    private static final int BOOK_W = 1623, BOOK_H = 1080;
    private static final int LP_X = 199, LP_Y = 233, LP_W = 559, LP_H = 609;
    private static final int RP_X = 864, RP_Y = 233, RP_W = 559, RP_H = 609;

    // ── Content sizes — SCREEN PIXELS (not scaled) ────────────────────────
    private static final int TITLE_H   = 28;
    private static final int TABS_H    = 20;
    private static final int SF_H      = 14;
    private static final int FRAME_S   = 28;
    private static final int PITCH     = 31;
    private static final int BIG_FRAME = 52;
    private static final int BIG_ITEM  =  2;
    private static final int ROW_H     = 18;  // listing row

    // ── Colors ────────────────────────────────────────────────────────────
    private static final int C_TITLE = 0xFF3D2200;
    private static final int C_LABEL = 0xFF5A3F10;
    private static final int C_DIM   = 0xFF7A5B2A;
    private static final int C_SEP   = 0x887A5B2A;
    private static final int C_GREEN = 0xFF1A6E28;
    private static final int C_RED   = 0xFF6E1A1A;
    private static final int C_GOLD  = 0xFF9B6A00;

    private static final String[] TABS = {"Tümü", "Satışta", "Satışta Değil"};
    private static final String[] ITEM_CATS = {
            "Hammadde","Blok","Yiyecek","Araç/Ekipman",
            "Ekim","Yaratık","Redstone","Enchant/Kitap","Misc"
    };

    // ── Data ──────────────────────────────────────────────────────────────
    private Map<String, Integer>                available;
    private List<PlayerShopManager.ShopListing> listings;
    private List<Map.Entry<String, Integer>>    grid;
    private List<PlayerShopManager.ShopListing> sorted;

    // ── UI state ──────────────────────────────────────────────────────────
    private int     catIdx    = 0;
    private String  selId     = null;
    private int     gridScroll= 0;
    private int     listScroll= 0;
    private int     selListing= -1;
    private int     tick      = 0;
    private String  sfBuf     = "";
    private boolean sfActive  = false;

    // ── Popup state ───────────────────────────────────────────────────────
    private boolean popOpen    = false;
    private String  popPrice   = "";
    private boolean popPriceA  = false;
    private int     popCatIdx  = 0;

    // ── Layout ────────────────────────────────────────────────────────────
    private int guiX, guiY;
    private double scale;
    private int lpX, lpY, lpW, lpH;
    private int rpX, rpY, rpW;

    // Set during renderRight, consumed by mouse handler
    private int listStartY, listEndY;

    // ── Constructor ───────────────────────────────────────────────────────
    public TradePostScreen(Map<String, Integer> available,
                           List<PlayerShopManager.ShopListing> listings) {
        super(Text.literal("Trade Post"));
        this.available = new LinkedHashMap<>(available);
        this.listings  = new ArrayList<>(listings);
    }

    public void refresh(Map<String, Integer> a, List<PlayerShopManager.ShopListing> l) {
        this.available = new LinkedHashMap<>(a);
        this.listings  = new ArrayList<>(l);
        filter();
    }

    private int sc(int v) { return Math.max(1, (int)(v * scale)); }

    @Override
    protected void init() {
        scale = Math.min(width * 0.9 / BOOK_W, height * 0.9 / BOOK_H);
        guiX  = (width  - sc(BOOK_W)) / 2;
        guiY  = (height - sc(BOOK_H)) / 2;
        lpX = guiX + sc(LP_X); lpY = guiY + sc(LP_Y);
        lpW = sc(LP_W);         lpH = sc(LP_H);
        rpX = guiX + sc(RP_X); rpY = guiY + sc(RP_Y);
        rpW = sc(RP_W);
        listStartY = height; listEndY = height;
        filter();
    }

    @Override
    public void tick() {
        if (++tick >= 40) {
            tick = 0;
            ClientPlayNetworking.send(PostRefreshPacket.ID, PacketByteBufs.create());
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
        sorted = new ArrayList<>(listings);
        sorted.sort(Comparator.comparing(PlayerShopManager.ShopListing::displayName));
        gridScroll = 0;
        if (selId != null && grid.stream().noneMatch(e -> e.getKey().equals(selId))) selId = null;
    }

    private void doAdd() {
        if (selId == null) return;
        String ps = popPrice.trim().replace(',', '.');
        double p; try { p = Double.parseDouble(ps); } catch (NumberFormatException e) { return; }
        if (p <= 0) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(PostActionPacket.ACTION_ADD);
        buf.writeString(selId);
        buf.writeDouble(p);
        buf.writeString(ITEM_CATS[popCatIdx]);
        ClientPlayNetworking.send(PostActionPacket.ID, buf);
        popOpen = false;
    }

    private void doRemove() {
        String id = null;
        if (selListing >= 0 && sorted != null && selListing < sorted.size())
            id = sorted.get(selListing).itemId();
        else if (selId != null && listings.stream().anyMatch(l -> l.itemId().equals(selId)))
            id = selId;
        if (id == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(PostActionPacket.ACTION_REMOVE);
        buf.writeString(id);
        buf.writeDouble(0);
        buf.writeString("");
        ClientPlayNetworking.send(PostActionPacket.ID, buf);
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        NineSliceRenderer.drawFullTexture(ctx, BOOK_TEX, guiX, guiY, sc(BOOK_W), sc(BOOK_H), BOOK_W, BOOK_H);
        renderLeft(ctx, mx, my);
        renderRight(ctx, mx, my);
        if (popOpen) renderPopup(ctx, mx, my);
    }

    // ── Left page ─────────────────────────────────────────────────────────
    private void renderLeft(DrawContext ctx, int mx, int my) {
        int y = lpY;

        title2x(ctx, "Trade Post", lpX + lpW/2, y + (TITLE_H-16)/2, C_TITLE);
        y += TITLE_H;
        ctx.fill(lpX+8, y, lpX+lpW-8, y+1, C_SEP);
        y += 5;

        // Tabs
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
        ctx.fill(lpX+8, y, lpX+lpW-8, y+1, C_SEP);
        y += 4;

        // Search
        renderField(ctx, mx, my, lpX+4, y, lpW-8, SF_H, sfBuf, sfActive, "Ara...");
        y += SF_H + 4;
        ctx.fill(lpX+8, y, lpX+lpW-8, y+1, C_SEP);
        y += 3;

        renderGrid(ctx, mx, my, y);
    }

    private void renderGrid(DrawContext ctx, int mx, int my, int startY) {
        int gH = lpY + lpH - startY;

        if (grid == null || grid.isEmpty()) {
            String msg = available.isEmpty() ? "Depoda item yok" : "Sonuç bulunamadı";
            cText(ctx, msg, lpX + lpW/2, startY + gH/2 - 4, C_DIM);
            return;
        }

        int cols  = Math.max(1, lpW / PITCH);
        int vrows = Math.max(1, gH / PITCH);
        int total = (grid.size() + cols - 1) / cols;
        gridScroll = Math.max(0, Math.min(gridScroll, Math.max(0, total-vrows)));

        Set<String> listed = listings.stream()
                .map(PlayerShopManager.ShopListing::itemId).collect(Collectors.toSet());

        for (int r = 0; r < vrows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (r + gridScroll) * cols + c;
                if (idx >= grid.size()) break;
                var e  = grid.get(idx);
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
                if (!stk.isEmpty()) ctx.drawItem(stk, sx+(FRAME_S-16)/2, sy+(FRAME_S-16)/2);

                String badge = cnt >= 1000 ? (cnt/1000)+"k" : String.valueOf(cnt);
                int bw = textRenderer.getWidth(badge);
                ctx.fill(sx+FRAME_S-bw-3, sy+FRAME_S-9, sx+FRAME_S, sy+FRAME_S, 0xCC000000);
                ctx.drawText(textRenderer, badge, sx+FRAME_S-bw-2, sy+FRAME_S-8,
                        lst ? 0xFF80FF80 : 0xFFEEEEEE, false);
            }
        }

        if (total > vrows) {
            int sbX = lpX + lpW - 3;
            int barH = Math.max(8, gH * vrows / total);
            int barY = startY + gridScroll * (gH - barH) / Math.max(1, total-vrows);
            ctx.fill(sbX, startY, sbX+3, startY+gH, 0x22000000);
            ctx.fill(sbX, barY,   sbX+3, barY+barH,  0xBB9B6200);
        }
    }

    // ── Right page ────────────────────────────────────────────────────────
    private void renderRight(DrawContext ctx, int mx, int my) {
        int cx = rpX + rpW/2;
        int y  = rpY;

        // Title
        title2x(ctx, "İlan Yönetimi", cx, y + (TITLE_H-16)/2, C_TITLE);
        y += TITLE_H;
        ctx.fill(rpX+8, y, rpX+rpW-8, y+1, C_SEP);
        y += 6;

        // ── Top: item detail ──────────────────────────────────────────────
        if (selId != null) {
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

            // Name
            title15x(ctx, truncate(name(selId), rpW-20), cx, y, C_TITLE);
            y += 14;
            cText(ctx, "Depoda: ×" + available.getOrDefault(selId, 0), cx, y, C_LABEL);
            y += 14;

            // Status badge
            boolean isListed = listings.stream().anyMatch(l -> l.itemId().equals(selId));
            Optional<PlayerShopManager.ShopListing> opt = listings.stream()
                    .filter(l -> l.itemId().equals(selId)).findFirst();
            if (isListed) {
                String lbl = "● Satışta: ⬡ " + opt.map(l -> price(l.price())).orElse("?");
                int bw = textRenderer.getWidth(lbl) + 12;
                ctx.fill(cx-bw/2, y-2, cx+bw/2, y+10, 0x33228822);
                cText(ctx, lbl, cx, y, C_GREEN);
            } else {
                String lbl = "○ Satışta değil";
                int bw = textRenderer.getWidth(lbl) + 12;
                ctx.fill(cx-bw/2, y-2, cx+bw/2, y+10, 0x33882222);
                cText(ctx, lbl, cx, y, C_RED);
            }
            y += 18;

            ctx.fill(rpX+16, y, rpX+rpW-16, y+1, C_SEP);
            y += 6;

            // Add listing button
            int bX = rpX + 8, bW = rpW - 16, bH = 22;
            boolean addHov = over(mx, my, bX, y, bW, bH);
            ctx.fill(bX, y, bX+bW, y+bH, addHov ? 0xBB005520 : 0x88003318);
            ctx.fill(bX+1, y,     bX+bW-1, y+1,    0xFF00AA55);
            ctx.fill(bX+1, y+bH-1, bX+bW-1, y+bH, 0xFF001A0A);
            ctx.fill(bX, y+1, bX+1, y+bH-1, 0xFF007740);
            ctx.fill(bX+bW-1, y+1, bX+bW, y+bH-1, 0xFF007740);
            String addLbl = isListed ? "✎  Fiyatı Güncelle" : "+ Yeni İlan Ekle";
            title10x(ctx, addLbl, bX + bW/2, y + (bH-8)/2,
                    addHov ? 0xFF80FF80 : 0xFF44CC77);
            y += bH + 6;

            ctx.fill(rpX+8, y, rpX+rpW-8, y+1, C_SEP);
            y += 5;
        } else {
            cText(ctx, "◄ Sol sayfadan item seçin", cx, y + 40, C_DIM);
            y += 80;
            ctx.fill(rpX+8, y, rpX+rpW-8, y+1, C_SEP);
            y += 5;
        }

        // ── Bottom: active listings ───────────────────────────────────────
        // Header
        String hdr = "Aktif İlanlar (" + (sorted == null ? 0 : sorted.size()) + ")";
        ctx.drawText(textRenderer, hdr, rpX+8, y, C_DIM, false);
        y += 12;

        // Listings list
        int remBtnH = 24;
        int remBtnY = rpY + lpH - 30;
        listStartY  = y;
        listEndY    = remBtnY - 4;

        if (sorted != null && !sorted.isEmpty()) {
            int listH  = listEndY - listStartY;
            int vrows  = Math.max(0, listH / ROW_H);
            int maxSc  = Math.max(0, sorted.size() - vrows);
            listScroll = Math.max(0, Math.min(listScroll, maxSc));

            ctx.fill(rpX+4, listStartY, rpX+rpW-4, listStartY + vrows*ROW_H, 0x15000000);

            for (int i = 0; i < vrows && i+listScroll < sorted.size(); i++) {
                int idx = i + listScroll;
                var sl  = sorted.get(idx);
                int ry  = listStartY + i * ROW_H;
                boolean hov = over(mx, my, rpX+4, ry, rpW-8, ROW_H);
                boolean sel = (idx == selListing);

                ctx.fill(rpX+4, ry, rpX+rpW-4, ry+ROW_H-1,
                        sel ? 0x66FFCC00 : hov ? 0x33AA7700 : 0);

                ItemStack stk = stack(sl.itemId());
                if (!stk.isEmpty()) ctx.drawItem(stk, rpX+6, ry+(ROW_H-16)/2);

                String nm = truncate(sl.displayName(), rpW-60);
                ctx.drawText(textRenderer, nm, rpX+26, ry+(ROW_H-8)/2,
                        sel ? C_GOLD : C_LABEL, false);

                String pr = "⬡" + price(sl.price());
                ctx.drawText(textRenderer, pr,
                        rpX+rpW-textRenderer.getWidth(pr)-8, ry+(ROW_H-8)/2,
                        C_GOLD, false);

                ctx.fill(rpX+4, ry+ROW_H-1, rpX+rpW-4, ry+ROW_H, 0x22000000);
            }

            if (maxSc > 0) {
                int sbX = rpX + rpW - 4;
                int sbH = vrows * ROW_H;
                int barH = Math.max(8, sbH * vrows / sorted.size());
                int barY = listStartY + listScroll * (sbH - barH) / maxSc;
                ctx.fill(sbX, listStartY, sbX+3, listStartY+sbH, 0x22000000);
                ctx.fill(sbX, barY, sbX+3, barY+barH, 0xBB9B6200);
            }
        } else {
            cText(ctx, "Aktif ilan yok", cx, listStartY + 14, C_DIM);
        }

        // Remove button
        int bX = rpX + 8, bW = rpW - 16;
        boolean remHov = over(mx, my, bX, remBtnY, bW, remBtnH);
        ctx.fill(bX, remBtnY, bX+bW, remBtnY+remBtnH, remHov ? 0xBB551111 : 0x88331111);
        ctx.fill(bX+1, remBtnY,          bX+bW-1, remBtnY+1,          0xFFCC3333);
        ctx.fill(bX+1, remBtnY+remBtnH-1,bX+bW-1, remBtnY+remBtnH,    0xFF110000);
        ctx.fill(bX, remBtnY+1, bX+1, remBtnY+remBtnH-1, 0xFFAA2222);
        ctx.fill(bX+bW-1, remBtnY+1, bX+bW, remBtnY+remBtnH-1, 0xFFAA2222);
        title10x(ctx, "− Seçili İlanı Kaldır", bX+bW/2, remBtnY+(remBtnH-8)/2,
                remHov ? 0xFFFF9999 : 0xFFDD6666);
    }

    // ── Add listing popup ─────────────────────────────────────────────────
    private void renderPopup(DrawContext ctx, int mx, int my) {
        ctx.fill(0, 0, width, height, 0xAA000000);

        // Layout constants — keep in sync with handlePopupClick
        int popW = Math.min(sc(360), width - 40);
        int hdrH = 32;
        int m    = 14;
        int fldW = popW - m*2;
        int fldH = 14;
        int btnH = 22;
        int iconS = 24;
        // Total content height: icon(28) + name(14) + sep(9) + lbl(12) + field(24) + sep(9) + btns(22) + pad(10) = 128
        int popH = Math.max(hdrH + 138, sc(240));
        int popX = (width  - popW) / 2;
        int popY = (height - popH) / 2;
        int cx   = popX + popW/2;

        // Drop shadow
        ctx.fill(popX+4, popY+4, popX+popW+4, popY+popH+4, 0x66000000);
        // Golden border (double ring)
        ctx.fill(popX-2, popY-2, popX+popW+2, popY+popH+2, 0xFF7A4A00);
        ctx.fill(popX-1, popY-1, popX+popW+1, popY+popH+1, 0xFFD4A832);
        // Body
        ctx.fill(popX, popY, popX+popW, popY+popH, 0xFFF5EDDA);

        // Header band
        ctx.fill(popX, popY, popX+popW, popY+hdrH, 0xFF3D2200);
        ctx.fill(popX, popY+hdrH, popX+popW, popY+hdrH+1, 0xFFD4A832);
        title2x(ctx, "Satışa Ekle", cx, popY + (hdrH-16)/2, 0xFFEEDDAA);

        int y = popY + hdrH + 10;

        // Item icon
        int iconX = cx - iconS/2;
        NineSliceRenderer.drawFullTexture(ctx, FRAME_TEX, iconX, y, iconS, iconS, 32, 32);
        if (selId != null) {
            ItemStack stk = stack(selId);
            if (!stk.isEmpty()) ctx.drawItem(stk, iconX + (iconS-16)/2, y + (iconS-16)/2);
        }
        y += iconS + 4;  // +28

        // Item name (1.5×, no shadow)
        String nm = selId != null ? truncate(name(selId), fldW) : "—";
        title15x(ctx, nm, cx, y, C_TITLE);
        y += 14;  // +14

        // Separator
        ctx.fill(popX+m, y, popX+m+fldW, y+1, C_SEP);
        y += 8;   // +8 (sep is 1px visual, 8px spacing)

        // Price label + field
        ctx.drawText(textRenderer, "Fiyat (⬡):", popX+m, y, C_DIM, false);
        y += 12;  // +12
        renderField(ctx, mx, my, popX+m, y, fldW, fldH, popPrice, popPriceA, "örn: 250");
        y += fldH + 10;  // +24

        // Separator
        ctx.fill(popX+m, y, popX+m+fldW, y+1, C_SEP);
        y += 8;   // +8

        // Buttons
        int bW = (fldW - 8) / 2;
        boolean cancelHov = over(mx, my, popX+m, y, bW, btnH);
        drawBtn(ctx, popX+m, y, bW, btnH, "İptal",
                cancelHov, 0xBB551111, 0xFFCC3333, 0xFFFF9999);
        int ekleX = popX+m+bW+8;
        boolean ekleHov = over(mx, my, ekleX, y, bW, btnH);
        drawBtn(ctx, ekleX, y, bW, btnH, "✔  Ekle",
                ekleHov, 0xBB005520, 0xFF00AA55, 0xFF80FF80);
    }

    private void drawBtn(DrawContext ctx, int x, int y, int w, int h,
                         String lbl, boolean hov,
                         int bg, int border, int tc) {
        ctx.fill(x, y, x+w, y+h, hov ? bg | 0x33000000 : bg);
        ctx.fill(x+1,   y,      x+w-1, y+1,    border);
        ctx.fill(x+1,   y+h-1,  x+w-1, y+h,    (border >> 1) & 0x007F7F7F | 0xFF000000);
        ctx.fill(x,     y+1,    x+1,   y+h-1,  border);
        ctx.fill(x+w-1, y+1,    x+w,   y+h-1,  border);
        ctx.drawText(textRenderer, lbl,
                x + w/2 - textRenderer.getWidth(lbl)/2, y+(h-8)/2,
                hov ? 0xFFFFFFFF : tc, false);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (popOpen) { handlePopupClick(mx, my); return true; }

        // Search
        int sfY = lpY + TITLE_H + 5 + TABS_H + 4 + 4 + 1 + 4;
        if (over(mx, my, lpX+4, sfY, lpW-8, SF_H)) { sfActive = true; return true; }
        sfActive = false;

        // Tabs
        int tabY = lpY + TITLE_H + 5;
        int tabW = lpW / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            if (over(mx, my, lpX+i*tabW, tabY, tabW, TABS_H)) {
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
                    int idx = (r + gridScroll) * cols + c;
                    if (idx >= grid.size()) break;
                    int sx = lpX + c * PITCH, sy = gridY + r * PITCH;
                    if (over(mx, my, sx, sy, FRAME_S, FRAME_S)) {
                        String id = grid.get(idx).getKey();
                        if (!id.equals(selId)) {
                            selId     = id;
                            popCatIdx = catFor(id);
                            popPrice  = "";
                            listings.stream().filter(l -> l.itemId().equals(id)).findFirst()
                                    .ifPresent(l -> popPrice = String.format("%.0f", l.price()));
                        }
                        return true;
                    }
                }
            }
        }

        // Right page — add button
        if (selId != null) {
            // Compute add button Y same as in renderRight
            int y = rpY + TITLE_H + 6 + BIG_FRAME + 8 + 14 + 14 + 18 + 6;
            if (over(mx, my, rpX+8, y, rpW-16, 22)) {
                popOpen   = true;
                popPriceA = false;
                return true;
            }
        }

        // Remove button
        int remY = rpY + lpH - 30;
        if (over(mx, my, rpX+8, remY, rpW-16, 24)) { doRemove(); return true; }

        // Listing rows
        if (sorted != null && listStartY < listEndY) {
            int listH  = listEndY - listStartY;
            int vrows  = Math.max(0, listH / ROW_H);
            for (int i = 0; i < vrows && i+listScroll < sorted.size(); i++) {
                int idx = i + listScroll;
                int ry  = listStartY + i * ROW_H;
                if (over(mx, my, rpX+4, ry, rpW-8, ROW_H)) {
                    selListing = idx;
                    var sl = sorted.get(idx);
                    selId    = sl.itemId();
                    popPrice = String.format("%.0f", sl.price());
                    popCatIdx = catFor(sl.itemId());
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    private void handlePopupClick(double mx, double my) {
        // Must mirror renderPopup layout exactly
        int popW = Math.min(sc(360), width - 40);
        int hdrH = 32;
        int m    = 14;
        int fldW = popW - m*2;
        int fldH = 14;
        int btnH = 22;
        int iconS = 24;
        int popH = Math.max(hdrH + 138, sc(240));
        int popX = (width  - popW) / 2;
        int popY = (height - popH) / 2;

        // y mirrors renderPopup: hdrH+10 → icon(+28) → name(+14) → sep(+8) = hdrH+60
        int y = popY + hdrH + 10 + iconS + 4 + 14 + 8;
        y += 12; // price label
        // Price field
        if (over(mx, my, popX+m, y, fldW, fldH)) { popPriceA = true; return; }
        popPriceA = false;
        y += fldH + 10 + 8; // field + sep
        // Buttons
        int bW = (fldW - 8) / 2;
        if (over(mx, my, popX+m, y, bW, btnH))       { popOpen = false; return; }
        if (over(mx, my, popX+m+bW+8, y, bW, btnH)) { doAdd();         return; }
        // Outside click = close
        if (!over(mx, my, popX, popY, popW, popH)) popOpen = false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amt) {
        if (popOpen) return true;
        int gridY = lpY + TITLE_H + 5 + TABS_H + 4 + 4 + 1 + 4 + SF_H + 4 + 3;
        int gH    = lpY + lpH - gridY;
        if (over(mx, my, lpX, gridY, lpW, gH) && grid != null) {
            int cols  = Math.max(1, lpW / PITCH);
            int vrows = Math.max(1, gH / PITCH);
            int total = (grid.size() + cols - 1) / cols;
            gridScroll = Math.max(0, Math.min(gridScroll-(int)amt, Math.max(0, total-vrows)));
            return true;
        }
        if (sorted != null && listStartY < listEndY) {
            int listH  = listEndY - listStartY;
            int vrows  = Math.max(0, listH / ROW_H);
            if (over(mx, my, rpX, listStartY, rpW, listH)) {
                listScroll = Math.max(0, Math.min(listScroll-(int)amt,
                        Math.max(0, sorted.size()-vrows)));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, amt);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (popOpen) {
            if (popPriceA && (Character.isDigit(c) || (c=='.' && !popPrice.contains("."))))
                popPrice += c;
            return true;
        }
        if (sfActive) { sfBuf += c; filter(); return true; }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (popOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) { popOpen = false; return true; }
            if (popPriceA) {
                if (key == GLFW.GLFW_KEY_BACKSPACE && !popPrice.isEmpty()) {
                    popPrice = popPrice.substring(0, popPrice.length()-1); return true;
                }
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    doAdd(); return true;
                }
            }
            return true;
        }
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
    private void title2x(DrawContext ctx, String text, int cx, int y, int color) {
        MatrixStack ms = ctx.getMatrices();
        ms.push(); ms.translate(cx, y, 0); ms.scale(2f, 2f, 1f);
        ctx.drawText(textRenderer, text, -textRenderer.getWidth(text)/2, 0, color, false);
        ms.pop();
    }
    private void title15x(DrawContext ctx, String text, int cx, int y, int color) {
        MatrixStack ms = ctx.getMatrices();
        ms.push(); ms.translate(cx, y, 0); ms.scale(1.5f, 1.5f, 1f);
        ctx.drawText(textRenderer, text, -textRenderer.getWidth(text)/2, 0, color, false);
        ms.pop();
    }
    private void title10x(DrawContext ctx, String text, int cx, int y, int color) {
        ctx.drawText(textRenderer, text, cx - textRenderer.getWidth(text)/2, y, color, false);
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
        ctx.fill(fx,     fy,      fx+fw,    fy+1,   bc);
        ctx.fill(fx,     fy+fh-1, fx+fw,    fy+fh,  bc);
        ctx.fill(fx,     fy,      fx+1,     fy+fh,  bc);
        ctx.fill(fx+fw-1,fy,      fx+fw,    fy+fh,  bc);
        boolean empty = buf.isEmpty() && !active;
        String vis = empty ? hint : buf;
        int tc = empty ? 0xAA7A5B2A : 0xFF5A3F10;
        int tx = fx+4, ty = fy+(fh-8)/2;
        while (textRenderer.getWidth(vis) > fw-8 && !vis.isEmpty()) vis = vis.substring(1);
        ctx.drawText(textRenderer, vis, tx, ty, tc, false);
        if (active && (System.currentTimeMillis()/500)%2==0) {
            int curX = tx + textRenderer.getWidth(vis);
            ctx.fill(curX, ty-1, curX+1, ty+9, 0xFF7A5B2A);
        }
    }

    private boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }

    private String truncate(String s, int maxPx) {
        if (textRenderer.getWidth(s) <= maxPx) return s;
        while (!s.isEmpty() && textRenderer.getWidth(s+"…") > maxPx)
            s = s.substring(0, s.length()-1);
        return s+"…";
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

    private int catFor(String itemId) {
        try {
            Item it = Registries.ITEM.get(new Identifier(itemId));
            if (it != Items.AIR) {
                if (it.getFoodComponent() != null) return idx("Yiyecek");
                if (it instanceof net.minecraft.item.ArmorItem ||
                    it instanceof net.minecraft.item.ToolItem  ||
                    it instanceof net.minecraft.item.SwordItem ||
                    it instanceof net.minecraft.item.RangedWeaponItem ||
                    it instanceof net.minecraft.item.ShieldItem ||
                    it instanceof net.minecraft.item.TridentItem)
                    return idx("Araç/Ekipman");
            }
        } catch (Exception ignored) {}
        String id = itemId.replace("minecraft:", "").toLowerCase(Locale.ROOT);
        if (id.contains("redstone")||id.contains("piston")||id.contains("repeater")||
            id.contains("comparator")||id.contains("observer")||id.contains("hopper")||
            id.contains("dropper")||id.contains("dispenser")||id.contains("rail"))
            return idx("Redstone");
        if (id.contains("enchanted_book")||id.contains("_book")) return idx("Enchant/Kitap");
        if (id.endsWith("_seeds")||id.endsWith("_sapling")||id.contains("flower")||
            id.equals("wheat")||id.equals("carrot")||id.equals("potato")||
            id.equals("beetroot")||id.equals("nether_wart"))
            return idx("Ekim");
        if (id.contains("bone")||id.contains("feather")||id.contains("leather")||
            id.contains("ender_pearl")||id.contains("blaze")||id.contains("ghast")||
            id.contains("slime")||id.contains("gunpowder")||id.endsWith("_spawn_egg"))
            return idx("Yaratık");
        if (id.endsWith("_ingot")||id.endsWith("_nugget")||id.contains("_ore")||
            id.startsWith("raw_"))
            return idx("Hammadde");
        try {
            Item it = Registries.ITEM.get(new Identifier(itemId));
            if (it instanceof net.minecraft.item.BlockItem) return idx("Blok");
        } catch (Exception ignored) {}
        return idx("Misc");
    }

    private int idx(String cat) {
        for (int i = 0; i < ITEM_CATS.length; i++) if (ITEM_CATS[i].equals(cat)) return i;
        return ITEM_CATS.length - 1;
    }

    @Override public boolean shouldPause() { return false; }
}
