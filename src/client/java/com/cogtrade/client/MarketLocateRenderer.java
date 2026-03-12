package com.cogtrade.client;

import com.cogtrade.block.MarketBlock;
import com.cogtrade.block.TradePostBlock;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class MarketLocateRenderer {

    // Market block target (gold)
    private static BlockPos marketTarget = null;
    private static long marketTime = 0L;
    private static final float MR = 1.00f;
    private static final float MG = 0.84f;
    private static final float MB = 0.10f;

    // Trade post target (cyan)
    private static BlockPos tradePostTarget = null;
    private static long tradePostTime = 0L;
    private static final float TR = 0.10f;
    private static final float TG = 0.85f;
    private static final float TB = 1.00f;

    private static final long DURATION_MS = 60_000L;
    private static final float BEAM_HEIGHT = 6.0f;

    public static void setTarget(BlockPos pos) {
        marketTarget = pos;
        marketTime = pos == null ? 0L : System.currentTimeMillis();
        if (pos != null) lookAt(pos);
    }

    public static void setTradePostTarget(BlockPos pos) {
        tradePostTarget = pos;
        tradePostTime = pos == null ? 0L : System.currentTimeMillis();
        if (pos != null) lookAt(pos);
    }

    private static void lookAt(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d eye = client.player.getCameraPosVec(1.0f);
        Vec3d target = Vec3d.ofCenter(pos);
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, hDist)));

        client.player.setYaw(yaw);
        client.player.setPitch(pitch);
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(MarketLocateRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || context.camera() == null) return;

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d cam = context.camera().getPos();
        long now = System.currentTimeMillis();

        // --- Market block (gold) ---
        if (marketTarget != null) {
            long elapsed = now - marketTime;
            if (elapsed > DURATION_MS || client.world.getBlockState(marketTarget).getBlock() != MarketBlock.INSTANCE) {
                marketTarget = null;
            } else {
                renderTarget(matrices, cam, marketTarget, elapsed, MR, MG, MB);
            }
        }

        // --- Trade post (cyan) ---
        if (tradePostTarget != null) {
            long elapsed = now - tradePostTime;
            if (elapsed > DURATION_MS || !(client.world.getBlockState(tradePostTarget).getBlock() instanceof TradePostBlock)) {
                tradePostTarget = null;
            } else {
                renderTarget(matrices, cam, tradePostTarget, elapsed, TR, TG, TB);
            }
        }
    }

    private static void renderTarget(MatrixStack matrices, Vec3d cam, BlockPos pos, long elapsed,
                                     float r, float g, float b) {
        float pulse = 0.65f + 0.35f * (float) Math.sin((elapsed / 1000.0f) * 6.0f);

        float fillAlphaSeeThrough = 0.16f + 0.10f * pulse;
        float lineAlphaSeeThrough = 0.70f + 0.20f * pulse;
        float lineAlphaVisible    = 0.95f;

        matrices.push();
        matrices.translate(
                pos.getX() - cam.x,
                pos.getY() - cam.y,
                pos.getZ() - cam.z
        );

        Matrix4f posMat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        // 1) See-through filled box
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        drawFilledBox(posMat, fillAlphaSeeThrough, r, g, b);

        // 2) See-through outline
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(4.0f);
        drawOutlineBox(posMat, lineAlphaSeeThrough, r, g, b);

        // 3) Solid-depth second pass (sharper when looking directly)
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(2.0f);
        drawOutlineBox(posMat, lineAlphaVisible, r, g, b);

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static void drawFilledBox(Matrix4f posMat, float alpha, float r, float g, float b) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        quad(buf, posMat, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, alpha, r, g, b);
        quad(buf, posMat, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, alpha, r, g, b);
        quad(buf, posMat, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f, alpha, r, g, b);
        quad(buf, posMat, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 0f, 1f, alpha, r, g, b);
        quad(buf, posMat, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, alpha, r, g, b);
        quad(buf, posMat, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, alpha, r, g, b);

        tess.draw();
    }

    private static void drawOutlineBox(Matrix4f posMat, float alpha, float r, float g, float b) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

        line(buf, posMat, 0, 0, 0, 1, 0, 0, alpha, 1, 0, 0, r, g, b);
        line(buf, posMat, 0, 1, 0, 1, 1, 0, alpha, 1, 0, 0, r, g, b);
        line(buf, posMat, 0, 0, 1, 1, 0, 1, alpha, 1, 0, 0, r, g, b);
        line(buf, posMat, 0, 1, 1, 1, 1, 1, alpha, 1, 0, 0, r, g, b);

        line(buf, posMat, 0, 0, 0, 0, 1, 0, alpha, 0, 1, 0, r, g, b);
        line(buf, posMat, 1, 0, 0, 1, 1, 0, alpha, 0, 1, 0, r, g, b);
        line(buf, posMat, 0, 0, 1, 0, 1, 1, alpha, 0, 1, 0, r, g, b);
        line(buf, posMat, 1, 0, 1, 1, 1, 1, alpha, 0, 1, 0, r, g, b);

        line(buf, posMat, 0, 0, 0, 0, 0, 1, alpha, 0, 0, 1, r, g, b);
        line(buf, posMat, 1, 0, 0, 1, 0, 1, alpha, 0, 0, 1, r, g, b);
        line(buf, posMat, 0, 1, 0, 0, 1, 1, alpha, 0, 0, 1, r, g, b);
        line(buf, posMat, 1, 1, 0, 1, 1, 1, alpha, 0, 0, 1, r, g, b);

        tess.draw();
    }

    private static void drawBeam(Matrix4f posMat, float alpha, float r, float g, float b) {
        float minX = 0.35f, maxX = 0.65f;
        float minZ = 0.35f, maxZ = 0.65f;
        float minY = 1.0f,  maxY = 1.0f + BEAM_HEIGHT;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        quad(buf, posMat, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, alpha, r, g, b);
        quad(buf, posMat, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, alpha, r, g, b);
        quad(buf, posMat, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, alpha, r, g, b);
        quad(buf, posMat, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, alpha, r, g, b);

        tess.draw();
    }

    private static void drawTracer(Matrix4f posMat, BlockPos pos, float alpha, float r, float g, float b) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d start = client.player.getCameraPosVec(1.0f);
        Vec3d end   = Vec3d.ofCenter(pos);

        float sx = (float) (start.x - pos.getX());
        float sy = (float) (start.y - pos.getY());
        float sz = (float) (start.z - pos.getZ());
        float ex = (float) (end.x - pos.getX());
        float ey = (float) (end.y - pos.getY());
        float ez = (float) (end.z - pos.getZ());

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        line(buf, posMat, sx, sy, sz, ex, ey, ez, alpha, 0, 1, 0, r, g, b);
        tess.draw();
    }

    private static void quad(BufferBuilder buf, Matrix4f posMat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float alpha, float r, float g, float b) {
        buf.vertex(posMat, x1, y1, z1).color(r, g, b, alpha).next();
        buf.vertex(posMat, x2, y2, z2).color(r, g, b, alpha).next();
        buf.vertex(posMat, x3, y3, z3).color(r, g, b, alpha).next();
        buf.vertex(posMat, x4, y4, z4).color(r, g, b, alpha).next();
    }

    private static void line(BufferBuilder buf, Matrix4f posMat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float alpha,
                             float nx, float ny, float nz,
                             float r, float g, float b) {
        buf.vertex(posMat, x1, y1, z1).color(r, g, b, alpha).normal(nx, ny, nz).next();
        buf.vertex(posMat, x2, y2, z2).color(r, g, b, alpha).normal(nx, ny, nz).next();
    }
}
