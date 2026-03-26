package com.cogtrade.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-side bekleyen takas teklifleri store'u.
 * Sunucu yalnızca bir oyuncuya aynı anda bir teklif gönderebilir,
 * ancak Liste gelecekteki genişlemeye açık tutuldu.
 */
@Environment(EnvType.CLIENT)
public class ClientTradeOffers {

    private static final List<String> offers = new ArrayList<>();

    public static void addOffer(String initiatorName) {
        if (!offers.contains(initiatorName)) {
            offers.add(initiatorName);
        }
    }

    public static void clear() {
        offers.clear();
    }

    public static List<String> getOffers() {
        return Collections.unmodifiableList(offers);
    }

    public static boolean hasOffers() {
        return !offers.isEmpty();
    }

    public static int getCount() {
        return offers.size();
    }
}
