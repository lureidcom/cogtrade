package com.cogtrade.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Bakiye geçmişi verilerini client-tarafında tutan singleton.
 */
public class ClientTransactionData {

    private static List<TransactionEntry> entries = new ArrayList<>();
    private static boolean                loaded  = false;

    public static void update(List<TransactionEntry> list) {
        entries = new ArrayList<>(list);
        loaded  = true;
    }

    public static List<TransactionEntry> get()      { return entries; }
    public static boolean                isLoaded() { return loaded;  }

    public static void reset() {
        entries = new ArrayList<>();
        loaded  = false;
    }
}
