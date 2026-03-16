package com.cogtrade.market;

import java.util.List;

/**
 * Büyü Marketi kataloğu ve fiyat hesaplama mantığı.
 * <p>
 * Hem client (fiyat gösterimi) hem server (satın alma doğrulaması) tarafında kullanılır.
 */
public class EffectMarketRegistry {

    // ── Süre seçenekleri ─────────────────────────────────────────────────────

    /** Süre seçenekleri (tick cinsinden, 20 ticks = 1 saniye). */
    public static final int[]    DURATION_TICKS  = { 1200, 6000, 18000, 36000, 72000 };
    /** Kullanıcıya gösterilen etiketler. */
    public static final String[] DURATION_LABELS = { "1 dk", "5 dk", "15 dk", "30 dk", "1 sa" };

    /**
     * Seviye çarpanı: I=1.0x, II=2.0x, III=3.5x, IV=6.0x, V=10.0x.
     * Her ekstra seviye katlanarak pahalılaşır.
     */
    private static final double[] AMP_MULT     = { 1.0, 2.0, 3.5, 6.0, 10.0 };
    /** Uzun sürelerde iskonto (uzun süre alırsan birim başına daha ucuz). */
    private static final double[] DUR_DISCOUNT = { 1.00, 0.90, 0.85, 0.80, 0.75 };

    // ── Efekt kataloğu ────────────────────────────────────────────────────────

    public static final List<EffectMarketEntry> EFFECTS = List.of(
            // maxAmplifier: 0=sadece I, 1=I-II, 2=I-III, 3=I-IV, 4=I-V
            // basePricePerMin: Seviye I, 1 dakika için coin fiyatı

            // ── Hareket büyüleri ─────────────────────────────────────────
            new EffectMarketEntry("minecraft:speed",
                    "Hız", "Hareket hızını artırır. V'e kadar ekstra seviye!", 4, 300),
            new EffectMarketEntry("minecraft:jump_boost",
                    "Zıplama", "Zıplama yüksekliğini ve mesafesini artırır.", 2, 280),
            new EffectMarketEntry("minecraft:slow_falling",
                    "Yavaş Düşüş", "Düşerken hasar almaz, yavaş inersin.", 0, 200),
            new EffectMarketEntry("minecraft:dolphins_grace",
                    "Balina Zarafeti", "Su içinde hareket hızını ciddi artırır.", 0, 320),

            // ── Madencilik / iş büyüleri ──────────────────────────────────
            new EffectMarketEntry("minecraft:haste",
                    "Acele", "Kazma ve saldırı hızını artırır. IV'e kadar ekstra seviye!", 3, 350),

            // ── Dövüş büyüleri ────────────────────────────────────────────
            new EffectMarketEntry("minecraft:strength",
                    "Güç", "Yakın dövüş hasarını artırır. IV'e kadar ekstra seviye!", 3, 500),
            new EffectMarketEntry("minecraft:resistance",
                    "Dayanıklılık", "Tüm kaynaklardan alınan hasarı azaltır.", 1, 600),
            new EffectMarketEntry("minecraft:regeneration",
                    "Yenilenme", "Canını düzenli aralıklarla yeniler.", 1, 700),

            // ── Hayatta kalma büyüleri ───────────────────────────────────
            new EffectMarketEntry("minecraft:fire_resistance",
                    "Ateş Direnci", "Ateş, lav ve alev hasarından korur.", 0, 300),
            new EffectMarketEntry("minecraft:water_breathing",
                    "Su Nefesi", "Su altında sonsuz nefes sağlar.", 0, 250),

            // ── Görüş / gizlilik büyüleri ────────────────────────────────
            new EffectMarketEntry("minecraft:night_vision",
                    "Gece Görüşü", "Karanlıkta ve suda net görüş sağlar.", 0, 200),
            new EffectMarketEntry("minecraft:invisibility",
                    "Görünmezlik", "Mob ve oyuncular seni göremez.", 0, 750),

            // ── Özel / fayda büyüleri ─────────────────────────────────────
            new EffectMarketEntry("minecraft:saturation",
                    "Doyumluluk", "Açlığı anında doldurur ve can yeniler.", 0, 800),
            new EffectMarketEntry("minecraft:luck",
                    "Şans", "Sandık ve balıkçılık lootunu iyileştirir.", 0, 400),
            new EffectMarketEntry("minecraft:conduit_power",
                    "Kanal Gücü", "Suda hız, gece görüşü ve haste verir.", 0, 650),
            new EffectMarketEntry("minecraft:hero_of_the_village",
                    "Köy Kahramanı", "Köylülerle ticarette indirim sağlar.", 0, 450)
    );

    // ── Fiyat hesaplama ───────────────────────────────────────────────────────

    /**
     * Verilen efekt, seviye ve süre indeksine göre toplam coin fiyatını hesaplar.
     *
     * @param entry         Efekt tanımı
     * @param amplifier     0 = Seviye I,  1 = Seviye II
     * @param durationIndex {@link #DURATION_TICKS} içindeki indeks (0..4)
     * @return Yuvarlanmış coin miktarı (minimum 1)
     */
    public static long calcPrice(EffectMarketEntry entry, int amplifier, int durationIndex) {
        double minutes  = DURATION_TICKS[durationIndex] / 1200.0;
        double ampMult  = AMP_MULT[Math.max(0, Math.min(AMP_MULT.length - 1, amplifier))];
        double discount = DUR_DISCOUNT[Math.max(0, Math.min(4, durationIndex))];
        return Math.max(1, Math.round(entry.basePricePerMin() * minutes * ampMult * discount));
    }

    /** Efekt ID'sine göre kayıtlı efekti döner; bulunamazsa {@code null}. */
    public static EffectMarketEntry find(String effectId) {
        for (EffectMarketEntry e : EFFECTS)
            if (e.effectId().equals(effectId)) return e;
        return null;
    }
}
