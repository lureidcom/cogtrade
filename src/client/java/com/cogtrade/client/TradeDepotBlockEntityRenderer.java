package com.cogtrade.client;

import com.cogtrade.block.TradeDepotBlockEntity;
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
public class TradeDepotBlockEntityRenderer implements BlockEntityRenderer<TradeDepotBlockEntity> {

    private static final int    LABEL_COLOR = 0xFF55FF55; // yeşil
    private static final int    LABEL_SEE   = 0x6055FF55;
    private static final int    BG_FULL     = 0xC0000000;
    private static final int    BG_SEE      = 0x20000000;
    private static final double MAX_DIST_SQ = 16.0 * 16.0;

    private final TextRenderer textRenderer;
    private final EntityRenderDispatcher dispatcher;

    public TradeDepotBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.textRenderer = ctx.getTextRenderer();
        this.dispatcher   = ctx.getEntityRenderDispatcher();
    }

    @Override
    public void render(TradeDepotBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null) return;

        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        if (entity.getPos().getSquaredDistance(cameraPos) > MAX_DIST_SQ) return;

        String ownerName = entity.getOwnerName();
        if (ownerName == null || ownerName.isBlank()) return;

        String label = "✦ " + ownerName + "'in Deposu ✦";

        int maxLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        matrices.push();
        matrices.translate(0.5, 1.5, 0.5);

        Quaternionf rotation = dispatcher.getRotation();
        matrices.multiply(rotation);
        matrices.scale(-0.025f, -0.025f, 0.025f);

        float halfWidth = textRenderer.getWidth(label) / 2.0f;
        float yOffset   = -4.5f;
        Text text  = Text.literal(label);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        textRenderer.draw(text, -halfWidth, yOffset, LABEL_SEE, false, mat,
                vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, BG_SEE, maxLight);
        textRenderer.draw(text, -halfWidth, yOffset, LABEL_COLOR, false, mat,
                vertexConsumers, TextRenderer.TextLayerType.NORMAL, BG_FULL, maxLight);

        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(TradeDepotBlockEntity blockEntity) {
        return true;
    }
}
