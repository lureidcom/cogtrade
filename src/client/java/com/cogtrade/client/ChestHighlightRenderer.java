package com.cogtrade.client;

import com.cogtrade.block.TradeDepotBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Seçili sandıkları yeşil outline ile vurgular.
 * Trade Depot item elde tutulurken aktif olur.
 */
@Environment(EnvType.CLIENT)
public class ChestHighlightRenderer {

    private static final float R = 0.20f;
    private static final float G = 0.90f;
    private static final float B = 0.35f;

    private static List<BlockPos> highlightedChests = new ArrayList<>();

    public static void setChests(List<BlockPos> positions) {
        highlightedChests = new ArrayList<>(positions);
    }

    public static void clear() {
        highlightedChests.clear();
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ChestHighlightRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        if (highlightedChests.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // Yalnızca Trade Depot item elde tutulurken göster
        ItemStack main = client.player.getMainHandStack();
        ItemStack off  = client.player.getOffHandStack();
        boolean holdingDepot = main.isOf(TradeDepotBlock.BLOCK_ITEM)
                || off.isOf(TradeDepotBlock.BLOCK_ITEM);
        if (!holdingDepot) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;
        Vec3d cam = context.camera().getPos();
        long now  = System.currentTimeMillis();
        float pulse = 0.60f + 0.40f * (float) Math.sin((now / 600.0f));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        for (BlockPos pos : highlightedChests) {
            matrices.push();
            matrices.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            Matrix4f posMat = matrices.peek().getPositionMatrix();

            float fillAlpha = 0.10f + 0.06f * pulse;
            float lineAlpha = 0.70f + 0.25f * pulse;

            // Filled box (see-through)
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            drawFilledBox(posMat, fillAlpha);

            // Outline (see-through)
            RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
            RenderSystem.lineWidth(3.0f);
            drawOutlineBox(posMat, lineAlpha);

            // Solid outline (depth-aware, sharper look)
            RenderSystem.enableDepthTest();
            RenderSystem.lineWidth(1.5f);
            drawOutlineBox(posMat, 0.95f);

            RenderSystem.lineWidth(1.0f);
            matrices.pop();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void drawFilledBox(Matrix4f m, float alpha) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        quad(buf, m, 0,0,0, 1,0,0, 1,0,1, 0,0,1, alpha);
        quad(buf, m, 0,1,1, 1,1,1, 1,1,0, 0,1,0, alpha);
        quad(buf, m, 0,0,0, 1,0,0, 1,1,0, 0,1,0, alpha);
        quad(buf, m, 0,1,1, 1,1,1, 1,0,1, 0,0,1, alpha);
        quad(buf, m, 0,0,1, 0,0,0, 0,1,0, 0,1,1, alpha);
        quad(buf, m, 1,0,0, 1,0,1, 1,1,1, 1,1,0, alpha);
        tess.draw();
    }

    private static void quad(BufferBuilder buf, Matrix4f m,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              float x4, float y4, float z4,
                              float alpha) {
        buf.vertex(m, x1, y1, z1).color(R, G, B, alpha).next();
        buf.vertex(m, x2, y2, z2).color(R, G, B, alpha).next();
        buf.vertex(m, x3, y3, z3).color(R, G, B, alpha).next();
        buf.vertex(m, x4, y4, z4).color(R, G, B, alpha).next();
    }

    private static void drawOutlineBox(Matrix4f m, float alpha) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        // Bottom edges
        line(buf, m, 0,0,0, 1,0,0, alpha, 1,0,0);
        line(buf, m, 0,1,0, 1,1,0, alpha, 1,0,0);
        line(buf, m, 0,0,1, 1,0,1, alpha, 1,0,0);
        line(buf, m, 0,1,1, 1,1,1, alpha, 1,0,0);
        // Vertical edges
        line(buf, m, 0,0,0, 0,1,0, alpha, 0,1,0);
        line(buf, m, 1,0,0, 1,1,0, alpha, 0,1,0);
        line(buf, m, 0,0,1, 0,1,1, alpha, 0,1,0);
        line(buf, m, 1,0,1, 1,1,1, alpha, 0,1,0);
        // Depth edges
        line(buf, m, 0,0,0, 0,0,1, alpha, 0,0,1);
        line(buf, m, 1,0,0, 1,0,1, alpha, 0,0,1);
        line(buf, m, 0,1,0, 0,1,1, alpha, 0,0,1);
        line(buf, m, 1,1,0, 1,1,1, alpha, 0,0,1);
        tess.draw();
    }

    private static void line(BufferBuilder buf, Matrix4f m,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float alpha, float nx, float ny, float nz) {
        buf.vertex(m, x1, y1, z1).color(R, G, B, alpha).normal(nx, ny, nz).next();
        buf.vertex(m, x2, y2, z2).color(R, G, B, alpha).normal(nx, ny, nz).next();
    }
}
