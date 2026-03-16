package com.cogtrade.market;

/**
 * Büyü Marketi'nde satılan bir efekt tanımı.
 *
 * @param effectId        Minecraft namespace ID  (örn. "minecraft:speed")
 * @param displayName     Türkçe görünen ad
 * @param description     Kısa Türkçe açıklama
 * @param maxAmplifier    0 → sadece Seviye I,  1 → Seviye I ve II mevcut
 * @param basePricePerMin Seviye I, 1 dakika için temel fiyat (coin)
 */
public record EffectMarketEntry(
        String effectId,
        String displayName,
        String description,
        int    maxAmplifier,
        int    basePricePerMin
) {}
