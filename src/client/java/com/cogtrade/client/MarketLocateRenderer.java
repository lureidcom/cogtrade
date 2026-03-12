package com.cogtrade.client;

import com.cogtrade.block.MarketBlock;
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

    private static BlockPos targetPos  = null;
    private static long locateTime = 0L;

    private static final long DURATION_MS = 30_000L;
    private static final float BEAM_HEIGHT = 6.0f;

    private static final float R = 1.00f;
    private static final float G = 0.84f;
    private static final float B = 0.10f;

    public static void setTarget(BlockPos pos) {
        targetPos = pos;
        locateTime = pos == null ? 0L : System.currentTimeMillis();
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(MarketLocateRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        if (targetPos == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || context.camera() == null) {
            setTarget(null);
            return;
        }

        long elapsed = System.currentTimeMillis() - locateTime;
        if (elapsed > DURATION_MS) {
            setTarget(null);
            return;
        }

        // Hedef blok artık market değilse efekti kapat
        if (client.world.getBlockState(targetPos).getBlock() != MarketBlock.INSTANCE) {
            setTarget(null);
            return;
        }

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        Vec3d cam = context.camera().getPos();
        float pulse = 0.65f + 0.35f * (float) Math.sin((elapsed / 1000.0f) * 6.0f);

        float fillAlphaSeeThrough = 0.16f + 0.10f * pulse;
        float lineAlphaSeeThrough = 0.70f + 0.20f * pulse;
        float lineAlphaVisible    = 0.95f;
        float beamAlpha           = 0.20f + 0.10f * pulse;
        float tracerAlpha         = 0.45f + 0.15f * pulse;

        matrices.push();
        matrices.translate(
                targetPos.getX() - cam.x,
                targetPos.getY() - cam.y,
                targetPos.getZ() - cam.z
        );

        Matrix4f posMat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        // 1) Duvar arkasından görünen dolu kutu
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        drawFilledBox(posMat, fillAlphaSeeThrough);

        // 2) Duvar arkasından görünen güçlü outline
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(4.0f);
        drawOutlineBox(posMat, lineAlphaSeeThrough);

        // 3) Yukarı uzanan ışık sütunu
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        drawBeam(posMat, beamAlpha);

        // 4) Kameradan bloğa tracer line
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(2.5f);
        drawTracer(posMat, cam, tracerAlpha);

        // 5) Normal depth ile ikinci pass: direkt bakınca daha keskin gözüksün
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(2.0f);
        drawOutlineBox(posMat, lineAlphaVisible);

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }

    private static void drawFilledBox(Matrix4f posMat, float alpha) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // Alt
        quad(buf, posMat,
                0f, 0f, 0f,
                1f, 0f, 0f,
                1f, 0f, 1f,
                0f, 0f, 1f,
                alpha);

        // Üst
        quad(buf, posMat,
                0f, 1f, 1f,
                1f, 1f, 1f,
                1f, 1f, 0f,
                0f, 1f, 0f,
                alpha);

        // Kuzey
        quad(buf, posMat,
                0f, 0f, 0f,
                1f, 0f, 0f,
                1f, 1f, 0f,
                0f, 1f, 0f,
                alpha);

        // Güney
        quad(buf, posMat,
                0f, 1f, 1f,
                1f, 1f, 1f,
                1f, 0f, 1f,
                0f, 0f, 1f,
                alpha);

        // Batı
        quad(buf, posMat,
                0f, 0f, 1f,
                0f, 0f, 0f,
                0f, 1f, 0f,
                0f, 1f, 1f,
                alpha);

        // Doğu
        quad(buf, posMat,
                1f, 0f, 0f,
                1f, 0f, 1f,
                1f, 1f, 1f,
                1f, 1f, 0f,
                alpha);

        tess.draw();
    }

    private static void drawOutlineBox(Matrix4f posMat, float alpha) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);

        line(buf, posMat, 0, 0, 0, 1, 0, 0, alpha, 1, 0, 0);
        line(buf, posMat, 0, 1, 0, 1, 1, 0, alpha, 1, 0, 0);
        line(buf, posMat, 0, 0, 1, 1, 0, 1, alpha, 1, 0, 0);
        line(buf, posMat, 0, 1, 1, 1, 1, 1, alpha, 1, 0, 0);

        line(buf, posMat, 0, 0, 0, 0, 1, 0, alpha, 0, 1, 0);
        line(buf, posMat, 1, 0, 0, 1, 1, 0, alpha, 0, 1, 0);
        line(buf, posMat, 0, 0, 1, 0, 1, 1, alpha, 0, 1, 0);
        line(buf, posMat, 1, 0, 1, 1, 1, 1, alpha, 0, 1, 0);

        line(buf, posMat, 0, 0, 0, 0, 0, 1, alpha, 0, 0, 1);
        line(buf, posMat, 1, 0, 0, 1, 0, 1, alpha, 0, 0, 1);
        line(buf, posMat, 0, 1, 0, 0, 1, 1, alpha, 0, 0, 1);
        line(buf, posMat, 1, 1, 0, 1, 1, 1, alpha, 0, 0, 1);

        tess.draw();
    }

    private static void drawBeam(Matrix4f posMat, float alpha) {
        float minX = 0.35f;
        float maxX = 0.65f;
        float minZ = 0.35f;
        float maxZ = 0.65f;
        float minY = 1.0f;
        float maxY = 1.0f + BEAM_HEIGHT;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        quad(buf, posMat, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, alpha);
        quad(buf, posMat, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, alpha);
        quad(buf, posMat, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, alpha);
        quad(buf, posMat, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, alpha);

        tess.draw();
    }

    private static void drawTracer(Matrix4f posMat, Vec3d cam, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.player == null) return;

        Vec3d start = client.player.getCameraPosVec(1.0f);
        Vec3d end = Vec3d.ofCenter(targetPos);

        float sx = (float) (start.x - targetPos.getX());
        float sy = (float) (start.y - targetPos.getY());
        float sz = (float) (start.z - targetPos.getZ());

        float ex = (float) (end.x - targetPos.getX());
        float ey = (float) (end.y - targetPos.getY());
        float ez = (float) (end.z - targetPos.getZ());

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        line(buf, posMat, sx, sy, sz, ex, ey, ez, alpha, 0, 1, 0);
        tess.draw();
    }

    private static void quad(BufferBuilder buf, Matrix4f posMat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float alpha) {
        buf.vertex(posMat, x1, y1, z1).color(R, G, B, alpha).next();
        buf.vertex(posMat, x2, y2, z2).color(R, G, B, alpha).next();
        buf.vertex(posMat, x3, y3, z3).color(R, G, B, alpha).next();
        buf.vertex(posMat, x4, y4, z4).color(R, G, B, alpha).next();
    }

    private static void line(BufferBuilder buf, Matrix4f posMat,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float alpha,
                             float nx, float ny, float nz) {
        buf.vertex(posMat, x1, y1, z1).color(R, G, B, alpha).normal(nx, ny, nz).next();
        buf.vertex(posMat, x2, y2, z2).color(R, G, B, alpha).normal(nx, ny, nz).next();
    }
}
