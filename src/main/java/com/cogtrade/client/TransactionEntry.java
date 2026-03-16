package com.cogtrade.client;

/**
 * Bir bakiye işlemini temsil eden veri modeli.
 * <p>
 * {@code amount} işaretlidir: pozitif = para girişi, negatif = para çıkışı.
 */
public class TransactionEntry {

    public final int    id;
    /** TRANSFER_IN | TRANSFER_OUT | MARKET_BUY | MARKET_SELL | TRADE */
    public final String txType;
    /** Pozitif = kazanılan, negatif = harcanan coin miktarı. */
    public final double amount;
    /** Karşı taraf oyuncu adı, ürün adı vb. */
    public final String party;
    /** Epoch milisaniye. */
    public final long   timestamp;
    /** Opsiyonel not (boş string = yok). */
    public final String note;
    /** Detay sayfası için ek bilgi; satırlar '\n' ile ayrılmış. */
    public final String detail;

    public TransactionEntry(int id, String txType, double amount, String party,
                            long timestamp, String note, String detail) {
        this.id        = id;
        this.txType    = txType  != null ? txType  : "?";
        this.amount    = amount;
        this.party     = party   != null ? party   : "?";
        this.timestamp = timestamp;
        this.note      = note    != null ? note    : "";
        this.detail    = detail  != null ? detail  : "";
    }
}
