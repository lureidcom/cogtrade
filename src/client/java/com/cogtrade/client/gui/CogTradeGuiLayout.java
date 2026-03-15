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
 *   Left  page safe zone : figma x=327  y=213  w=599  h=649
 *   Right page safe zone : figma x=992  y=213  w=599  h=649
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

    // ── Left page safe zone (book-native) ─────────────────────────────────

    public static final int LEFT_PAGE_X = 179;   // 327 − 148
    public static final int LEFT_PAGE_Y = 213;
    public static final int LEFT_PAGE_W = 599;
    public static final int LEFT_PAGE_H = 649;

    // ── Right page safe zone (book-native) ────────────────────────────────

    public static final int RIGHT_PAGE_X = 844;  // 992 − 148
    public static final int RIGHT_PAGE_Y = 213;
    public static final int RIGHT_PAGE_W = 599;
    public static final int RIGHT_PAGE_H = 649;

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
