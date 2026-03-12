package com.cogtrade.client;

import com.cogtrade.block.MarketBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class MarketBlockEntityRenderer implements BlockEntityRenderer<MarketBlockEntity> {

    private static final String LABEL       = "✦ Sunucu Marketi ✦";
    private static final int    LABEL_COLOR = 0xFFFFAA00; // parlak altın sarısı (ARGB)
    private static final int    LABEL_SEE   = 0x60FFAA00; // ghost rengi (duvar arkası)
    private static final int    BG_FULL     = 0xC0000000; // 75% opak siyah arka plan
    private static final int    BG_SEE      = 0x20000000; // ghost arka planı
    private static final double MAX_DIST_SQ = 16.0 * 16.0;

    private final TextRenderer         textRenderer;
    private final EntityRenderDispatcher dispatcher;

    public MarketBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
        this.dispatcher   = ctx.getEntityRenderDispatcher();
    }

    @Override
    public void render(MarketBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null) return;

        // ── Mesafe kontrolü (16 blok) ──────────────────────────────────────
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        double distSq   = entity.getPos().getSquaredDistance(cameraPos);
        if (distSq > MAX_DIST_SQ) return;

        // ── Her zaman maksimum parlaklıkta render et ───────────────────────
        int maxLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        // ── Bloğun 1.5 blok üstünde, ortalanmış ──────────────────────────
        matrices.push();
        matrices.translate(0.5, 1.5, 0.5);

        // ── Billboard: kamera yönüne dön ──────────────────────────────────
        Quaternionf rotation = dispatcher.getRotation();
        matrices.multiply(rotation);

        // ── Ölçek (MC koordinat sistemi: X ve Y ters) ─────────────────────
        matrices.scale(-0.025f, -0.025f, 0.025f);

        float halfWidth = textRenderer.getWidth(LABEL) / 2.0f;
        float yOffset   = -4.5f; // fontHeight=9 → dikey ortalama

        Text text  = Text.literal(LABEL);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        // ── Pass 1: SEE_THROUGH — duvardan soluk ghost olarak görünsün ────
        textRenderer.draw(text, -halfWidth, yOffset,
                LABEL_SEE, false, mat,
                vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH,
                BG_SEE, maxLight);

        // ── Pass 2: NORMAL — doğrudan görüşte tam parlak sarı ─────────────
        textRenderer.draw(text, -halfWidth, yOffset,
                LABEL_COLOR, false, mat,
                vertexConsumers, TextRenderer.TextLayerType.NORMAL,
                BG_FULL, maxLight);

        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(MarketBlockEntity blockEntity) {
        return true;
    }
}
