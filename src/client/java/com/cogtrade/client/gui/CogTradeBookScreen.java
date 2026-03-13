package com.cogtrade.client.gui;

import com.cogtrade.CogTrade;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * CogTrade Book GUI Screen - First working version.
 *
 * This screen displays the steampunk book texture as a centered, properly-scaled GUI panel.
 * It serves as the foundation for all future CogTrade custom GUI components.
 *
 * Features:
 * - Darkened vanilla-style background
 * - Centered book texture rendering with proper scaling
 * - Preserves aspect ratio across all window sizes
 * - ESC and inventory key support for closing
 * - Does not pause the game (suitable for multiplayer)
 * - Automatically scales to fit screen with visible margins
 */
@Environment(EnvType.CLIENT)
public class CogTradeBookScreen extends Screen {

    private static final Identifier BOOK_TEXTURE =
            new Identifier(CogTrade.MOD_ID, "textures/gui/book.png");

    private int guiX;
    private int guiY;
    private int guiWidth;
    private int guiHeight;

    public CogTradeBookScreen() {
        super(Text.translatable("gui.cogtrade.book"));
    }

    @Override
    protected void init() {
        super.init();

        // Calculate scaled dimensions based on current screen size
        CogTradeGuiLayout.ScaledDimensions scaledSize =
                CogTradeGuiLayout.getScaledPanelSize(this.width, this.height);

        this.guiWidth = scaledSize.width;
        this.guiHeight = scaledSize.height;

        // Calculate centered position for the scaled panel
        this.guiX = CogTradeGuiLayout.getCenteredPanelX(this.width, this.guiWidth);
        this.guiY = CogTradeGuiLayout.getCenteredPanelY(this.height, this.guiHeight);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Render darkened background (vanilla style)
        this.renderBackground(ctx);

        // Render the book texture centered and scaled to fit screen with margins
        NineSliceRenderer.drawFullTexture(
                ctx,
                BOOK_TEXTURE,
                guiX,
                guiY,
                guiWidth,
                guiHeight,
                CogTradeGuiLayout.BOOK_TEXTURE_WIDTH,
                CogTradeGuiLayout.BOOK_TEXTURE_HEIGHT
        );

        // Render any child widgets/buttons (none yet in this first version)
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        // Don't pause the game - this is appropriate for a trading/market GUI
        // that might be used in multiplayer
        return false;
    }

    @Override
    public void close() {
        super.close();
    }
}