package com.cogtrade.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

/**
 * config/cogtrade.json dosyasından okunur/yazılır.
 * /ctadmin config get|set komutuyla oyun içinden de değiştirilebilir.
 */
public class CogTradeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("cogtrade.json");

    private static CogTradeConfig instance = new CogTradeConfig();

    // ── Ayarlar ──────────────────────────────────────────────────────────────
    /** Yeni oyuncunun başlangıç bakiyesi */
    public double startingBalance = 100.0;

    /** Satış fiyatı = alış fiyatı × bu çarpan (varsayılan: %60) */
    public double sellPriceMultiplier = 0.6;

    // ── API ──────────────────────────────────────────────────────────────────
    public static CogTradeConfig get() { return instance; }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                CogTradeConfig loaded = GSON.fromJson(reader, CogTradeConfig.class);
                if (loaded != null) instance = loaded;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        save(); // varsayılan değerlerle dosyayı oluştur / eksik alanları tamamla
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
