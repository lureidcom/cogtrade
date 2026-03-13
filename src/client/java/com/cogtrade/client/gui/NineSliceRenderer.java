package com.cogtrade.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/**
 * Reusable 9-slice texture rendering utility for scalable GUI panels.
 *
 * Nine-slice rendering divides a texture into 9 regions:
 * - 4 corners (fixed size)
 * - 4 edges (stretched in one direction)
 * - 1 center (stretched in both directions)
 *
 * This allows a single texture to be scaled to any size while maintaining
 * clean corners and edge details.
 */
public class NineSliceRenderer {

    /**
     * Render a 9-slice texture panel.
     *
     * @param ctx DrawContext for rendering
     * @param texture Texture identifier
     * @param x Destination X coordinate
     * @param y Destination Y coordinate
     * @param width Destination width
     * @param height Destination height
     * @param textureWidth Full texture width
     * @param textureHeight Full texture height
     * @param cornerSize Size of corner regions (assumes square corners)
     * @param edgeSize Size of edge regions
     */
    public static void drawNineSlice(
            DrawContext ctx,
            Identifier texture,
            int x, int y,
            int width, int height,
            int textureWidth, int textureHeight,
            int cornerSize, int edgeSize
    ) {
        // Top-left corner
        ctx.drawTexture(texture, x, y, 0, 0, cornerSize, cornerSize, textureWidth, textureHeight);

        // Top-right corner
        ctx.drawTexture(texture, x + width - cornerSize, y,
                textureWidth - cornerSize, 0, cornerSize, cornerSize, textureWidth, textureHeight);

        // Bottom-left corner
        ctx.drawTexture(texture, x, y + height - cornerSize,
                0, textureHeight - cornerSize, cornerSize, cornerSize, textureWidth, textureHeight);

        // Bottom-right corner
        ctx.drawTexture(texture, x + width - cornerSize, y + height - cornerSize,
                textureWidth - cornerSize, textureHeight - cornerSize, cornerSize, cornerSize, textureWidth, textureHeight);

        // Top edge
        ctx.drawTexture(texture, x + cornerSize, y,
                width - 2 * cornerSize, edgeSize,
                cornerSize, 0, textureWidth - 2 * cornerSize, edgeSize, textureWidth, textureHeight);

        // Bottom edge
        ctx.drawTexture(texture, x + cornerSize, y + height - edgeSize,
                width - 2 * cornerSize, edgeSize,
                cornerSize, textureHeight - edgeSize, textureWidth - 2 * cornerSize, edgeSize, textureWidth, textureHeight);

        // Left edge
        ctx.drawTexture(texture, x, y + cornerSize,
                edgeSize, height - 2 * cornerSize,
                0, cornerSize, edgeSize, textureHeight - 2 * cornerSize, textureWidth, textureHeight);

        // Right edge
        ctx.drawTexture(texture, x + width - edgeSize, y + cornerSize,
                edgeSize, height - 2 * cornerSize,
                textureWidth - edgeSize, cornerSize, edgeSize, textureHeight - 2 * cornerSize, textureWidth, textureHeight);

        // Center
        ctx.drawTexture(texture, x + cornerSize, y + cornerSize,
                width - 2 * cornerSize, height - 2 * cornerSize,
                cornerSize, cornerSize, textureWidth - 2 * cornerSize, textureHeight - 2 * cornerSize, textureWidth, textureHeight);
    }

    /**
     * Draw a full texture with uniform scaling using matrix transformations.
     * This properly scales the entire texture without cropping.
     *
     * @param ctx DrawContext for rendering
     * @param texture Texture identifier
     * @param x Destination X coordinate (top-left of scaled result)
     * @param y Destination Y coordinate (top-left of scaled result)
     * @param destWidth Desired render width
     * @param destHeight Desired render height
     * @param textureWidth Source texture width
     * @param textureHeight Source texture height
     */
    public static void drawFullTexture(
            DrawContext ctx,
            Identifier texture,
            int x, int y,
            int destWidth, int destHeight,
            int textureWidth, int textureHeight
    ) {
        // Calculate scale factors
        float scaleX = (float) destWidth / textureWidth;
        float scaleY = (float) destHeight / textureHeight;

        MatrixStack matrices = ctx.getMatrices();
        matrices.push();

        // Translate to the target position
        matrices.translate(x, y, 0);

        // Apply scaling
        matrices.scale(scaleX, scaleY, 1.0f);

        // Draw the full texture at 0,0 in the scaled coordinate space
        // This renders the complete texture scaled to fit destWidth x destHeight
        ctx.drawTexture(texture, 0, 0, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);

        matrices.pop();
    }
}