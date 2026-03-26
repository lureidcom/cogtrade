package com.cogtrade.client;

import com.cogtrade.network.AllShopListingsPacket;
import java.util.ArrayList;
import java.util.List;

/**
 * L tuşu Book GUI'sinin PAZAR sayfası için sunucu market verilerini bellekte tutar.
 * OpenMarketPacket ve AllShopListingsPacket handler'larından güncellenir.
 */
public final class ClientMarketData {

    private ClientMarketData() {}

    public static List<ClientMarketItem>           items             = new ArrayList<>();
    public static List<AllShopListingsPacket.Entry> shopListings      = new ArrayList<>();
    public static boolean                          canBuy            = false;
    public static double                           sellPriceMultiplier = 1.0;
    public static boolean                          loaded            = false;

    public static void updateMarket(List<ClientMarketItem> i, boolean buy, double mult) {
        items               = new ArrayList<>(i);
        canBuy              = buy;
        sellPriceMultiplier = mult;
        loaded              = true;
    }

    public static void updateShopListings(List<AllShopListingsPacket.Entry> e) {
        shopListings = new ArrayList<>(e);
    }

    public static void reset() {
        items        = new ArrayList<>();
        shopListings = new ArrayList<>();
        loaded       = false;
    }
}
