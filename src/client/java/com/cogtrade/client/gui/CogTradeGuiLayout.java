package com.cogtrade.client.gui;

/**
 * Layout constants for the CogTrade book GUI.
 *
 * <p>All element positions are expressed in <em>book-native space</em>
 * (1623 × 1080 px, matching the Figma canvas).  Convert to screen pixels
 * with {@code sc(value)} inside {@link CogTradeBookScreen}.</p>
 *
 * <h3>Safe-zone derivation</h3>
 * Figma absolute → book-relative:  {@code bookX = figmaX − 148},  {@code bookY = figmaY}
 * <pre>
 *   Left  page safe zone (Figma) : figma x=327  y=213  w=599  h=649
 *   Right page safe zone (Figma) : figma x=992  y=213  w=599  h=649
 *   Internal padding applied     : 20px on every side
 * </pre>
 */
public class CogTradeGuiLayout {

    // ── Book texture ───────────────────────────────────────────────────────

    /** Native pixel width  of {@code book.png}. */
    public static final int BOOK_W = 1623;
    /** Native pixel height of {@code book.png}. */
    public static final int BOOK_H = 1080;

    /** Maximum fraction of the screen the book may occupy. */
    private static final double MAX_W_RATIO = 0.90;
    private static final double MAX_H_RATIO = 0.90;

    // ── Left page safe zone (book-native, 20px internal padding applied) ──

    public static final int LEFT_PAGE_X = 199;   // 327 − 148 + 20
    public static final int LEFT_PAGE_Y = 233;   // 213 + 20
    public static final int LEFT_PAGE_W = 559;   // 599 − 40 (20px her iki yan)
    public static final int LEFT_PAGE_H = 609;   // 649 − 40 (20px üst + alt)

    // ── Right page safe zone (book-native, 20px internal padding applied) ─

    public static final int RIGHT_PAGE_X = 864;  // 992 − 148 + 20
    public static final int RIGHT_PAGE_Y = 233;  // 213 + 20
    public static final int RIGHT_PAGE_W = 559;  // 599 − 40
    public static final int RIGHT_PAGE_H = 609;  // 649 − 40

    // ─────────────────────────────────────────────────────────────────────

    /**
     * Uniform scale factor that fits the book inside the given screen area
     * while preserving aspect ratio.
     */
    public static double getScale(int screenW, int screenH) {
        return Math.min(
                (screenW * MAX_W_RATIO) / BOOK_W,
                (screenH * MAX_H_RATIO) / BOOK_H
        );
    }
}
