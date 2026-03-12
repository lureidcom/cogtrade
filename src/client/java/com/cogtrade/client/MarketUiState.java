package com.cogtrade.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MarketUiState {

    private static final int MAX_RECENT_VIEWED = 24;
    private static final int MAX_RECENT_TRADED = 24;

    private static final Set<String> FAVORITES = new LinkedHashSet<>();
    private static final Deque<String> RECENT_VIEWED = new ArrayDeque<>();
    private static final Deque<String> RECENT_TRADED = new ArrayDeque<>();

    private MarketUiState() {
    }

    public static boolean isFavorite(String itemId) {
        return FAVORITES.contains(itemId);
    }

    public static void toggleFavorite(String itemId) {
        if (!FAVORITES.add(itemId)) {
            FAVORITES.remove(itemId);
        }
    }

    public static Set<String> getFavorites() {
        return FAVORITES;
    }

    public static void pushViewed(String itemId) {
        pushUnique(RECENT_VIEWED, itemId, MAX_RECENT_VIEWED);
    }

    public static void pushTraded(String itemId) {
        pushUnique(RECENT_TRADED, itemId, MAX_RECENT_TRADED);
    }

    public static Deque<String> getRecentViewed() {
        return RECENT_VIEWED;
    }

    public static Deque<String> getRecentTraded() {
        return RECENT_TRADED;
    }

    private static void pushUnique(Deque<String> deque, String itemId, int maxSize) {
        deque.remove(itemId);
        deque.addFirst(itemId);
        while (deque.size() > maxSize) {
            deque.removeLast();
        }
    }
}