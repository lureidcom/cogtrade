package com.cogtrade.client.gui;

/**
 * Centralized layout constants and bounds calculation for CogTrade GUI system.
 * Provides consistent dimensions and scaling for all CogTrade screens.
 */
public class CogTradeGuiLayout {

    // Book texture dimensions (from assets/cogtrade/textures/gui/book.png)
    public static final int BOOK_TEXTURE_WIDTH = 1024;
    public static final int BOOK_TEXTURE_HEIGHT = 576;

    // Screen coverage targets (percentage of screen to use at maximum)
    private static final double MAX_WIDTH_RATIO = 0.84;
    private static final double MAX_HEIGHT_RATIO = 0.80;

    // Page layout constants (offsets relative to scaled book panel)
    public static final int LEFT_PAGE_X_OFFSET = 50;
    public static final int LEFT_PAGE_Y_OFFSET = 50;
    public static final int LEFT_PAGE_WIDTH = 430;
    public static final int LEFT_PAGE_HEIGHT = 476;

    public static final int RIGHT_PAGE_X_OFFSET = 544;
    public static final int RIGHT_PAGE_Y_OFFSET = 50;
    public static final int RIGHT_PAGE_WIDTH = 430;
    public static final int RIGHT_PAGE_HEIGHT = 476;

    // Margins and spacing
    public static final int CONTENT_MARGIN = 20;
    public static final int ELEMENT_SPACING = 10;

    /**
     * Calculate the appropriate scale factor to fit the book texture on screen
     * while preserving aspect ratio and leaving visible margins.
     *
     * @param screenWidth Current screen width in GUI coordinates
     * @param screenHeight Current screen height in GUI coordinates
     * @return Scale factor (e.g., 0.75 means render at 75% of original size)
     */
    public static double getPanelScale(int screenWidth, int screenHeight) {
        double maxWidth = screenWidth * MAX_WIDTH_RATIO;
        double maxHeight = screenHeight * MAX_HEIGHT_RATIO;

        double scaleX = maxWidth / BOOK_TEXTURE_WIDTH;
        double scaleY = maxHeight / BOOK_TEXTURE_HEIGHT;

        // Use the smaller scale to ensure the entire texture fits
        return Math.min(scaleX, scaleY);
    }

    /**
     * Get the scaled render dimensions for the book panel.
     *
     * @param screenWidth Current screen width in GUI coordinates
     * @param screenHeight Current screen height in GUI coordinates
     * @return ScaledDimensions containing width and height
     */
    public static ScaledDimensions getScaledPanelSize(int screenWidth, int screenHeight) {
        double scale = getPanelScale(screenWidth, screenHeight);
        return new ScaledDimensions(
                (int) Math.round(BOOK_TEXTURE_WIDTH * scale),
                (int) Math.round(BOOK_TEXTURE_HEIGHT * scale)
        );
    }

    /**
     * Calculate the X position to center the scaled book panel on screen.
     *
     * @param screenWidth Current screen width in GUI coordinates
     * @param panelWidth Scaled panel width
     * @return Centered X coordinate
     */
    public static int getCenteredPanelX(int screenWidth, int panelWidth) {
        return (screenWidth - panelWidth) / 2;
    }

    /**
     * Calculate the Y position to center the scaled book panel on screen.
     *
     * @param screenHeight Current screen height in GUI coordinates
     * @param panelHeight Scaled panel height
     * @return Centered Y coordinate
     */
    public static int getCenteredPanelY(int screenHeight, int panelHeight) {
        return (screenHeight - panelHeight) / 2;
    }

    /**
     * Get absolute left page bounds for a given screen size.
     * Coordinates are scaled proportionally to the panel scale.
     */
    public static Bounds getLeftPageBounds(int screenWidth, int screenHeight) {
        double scale = getPanelScale(screenWidth, screenHeight);
        ScaledDimensions panelSize = getScaledPanelSize(screenWidth, screenHeight);

        int guiX = getCenteredPanelX(screenWidth, panelSize.width);
        int guiY = getCenteredPanelY(screenHeight, panelSize.height);

        return new Bounds(
                guiX + (int) Math.round(LEFT_PAGE_X_OFFSET * scale),
                guiY + (int) Math.round(LEFT_PAGE_Y_OFFSET * scale),
                (int) Math.round(LEFT_PAGE_WIDTH * scale),
                (int) Math.round(LEFT_PAGE_HEIGHT * scale)
        );
    }

    /**
     * Get absolute right page bounds for a given screen size.
     * Coordinates are scaled proportionally to the panel scale.
     */
    public static Bounds getRightPageBounds(int screenWidth, int screenHeight) {
        double scale = getPanelScale(screenWidth, screenHeight);
        ScaledDimensions panelSize = getScaledPanelSize(screenWidth, screenHeight);

        int guiX = getCenteredPanelX(screenWidth, panelSize.width);
        int guiY = getCenteredPanelY(screenHeight, panelSize.height);

        return new Bounds(
                guiX + (int) Math.round(RIGHT_PAGE_X_OFFSET * scale),
                guiY + (int) Math.round(RIGHT_PAGE_Y_OFFSET * scale),
                (int) Math.round(RIGHT_PAGE_WIDTH * scale),
                (int) Math.round(RIGHT_PAGE_HEIGHT * scale)
        );
    }

    /**
     * Container for scaled dimensions.
     */
    public static class ScaledDimensions {
        public final int width;
        public final int height;

        public ScaledDimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Simple bounds container for GUI regions.
     */
    public static class Bounds {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public Bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}