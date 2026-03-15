package com.cogtrade.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * A renderable, clickable tab button in the CogTrade book GUI.
 *
 * <p>Stores the tab's enum identity and its current screen-space bounds
 * so the screen can perform hit-detection without coupling to any specific
 * enum type.</p>
 *
 * @param <T> {@link CogTradeMainTab} for main tabs,
 *            {@link CogTradeSubTab} for sub-tabs.
 */
@Environment(EnvType.CLIENT)
public class CogTradeTab<T extends Enum<T>> {

    /** The enum constant this tab represents (main or sub). */
    public final T tabType;

    /** Screen-space position and size of this tab button. */
    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public CogTradeTab(T tabType, int x, int y, int width, int height) {
        this.tabType = tabType;
        this.x       = x;
        this.y       = y;
        this.width   = width;
        this.height  = height;
    }

    /**
     * Returns {@code true} if the given screen-space coordinates fall
     * within this tab's bounding rectangle.
     */
    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width
            && mouseY >= y && mouseY < y + height;
    }
}
