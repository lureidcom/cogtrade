package com.cogtrade.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Reusable texture rendering utilities for the CogTrade GUI.
 *
 * <h3>Why no matrix scaling?</h3>
 * The old approach used {@code MatrixStack.scale(scaleX, scaleY, 1)} which
 * introduces floating-point precision errors: the computed vertex positions
 * land on sub-pixel boundaries, causing colour bleeding and pixel artifacts
 * at the edges and corners of pixel-art textures.
 *
 * <p>Instead, {@link #drawFullTexture} uses the {@code DrawContext.drawTexture}
 * overload that accepts explicit destination and source dimensions.  Minecraft
 * converts those integers into exact UV coordinates internally, so all quad
 * vertices land on integer pixel boundaries — no artifacts.</p>
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
     * Draws an entire texture scaled to fit the given destination rectangle.
     *
     * <p>Uses Minecraft's UV-based {@code drawTexture} overload so that all
     * four quad vertices are placed at exact integer pixel coordinates.
     * This eliminates the sub-pixel precision errors that the old matrix-scale
     * approach produced at corners and edges of pixel-art textures.</p>
     *
     * @param ctx           DrawContext for rendering
     * @param texture       texture identifier
     * @param x             destination X (screen pixels, integer)
     * @param y             destination Y (screen pixels, integer)
     * @param destWidth     desired render width  in screen pixels
     * @param destHeight    desired render height in screen pixels
     * @param textureWidth  native width  of the PNG file
     * @param textureHeight native height of the PNG file
     */
    public static void drawFullTexture(
            DrawContext ctx,
            Identifier texture,
            int x, int y,
            int destWidth, int destHeight,
            int textureWidth, int textureHeight
    ) {
        // drawTexture(id, x, y, dstW, dstH, u, v, srcW, srcH, texW, texH)
        // Maps the full source region [0,0 → texW×texH] onto [x,y → x+dstW, y+dstH].
        // All positions are integer → no floating-point vertex drift.
        ctx.drawTexture(texture,
                x, y,
                destWidth, destHeight,
                0f, 0f,
                textureWidth, textureHeight,
                textureWidth, textureHeight);
    }
}