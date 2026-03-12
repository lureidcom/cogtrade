package com.cogtrade.database;

import com.cogtrade.CogTrade;
import java.sql.*;
import java.nio.file.Path;

public class DatabaseManager {

    private static Connection connection;


    public static void initialize(Path dataDir) {
        try {
            // Önceki açık bağlantı varsa kapat (tek oyunculu tekrar giriş senaryosu)
            if (connection != null) {
                try {
                    if (!connection.isClosed()) {
                        connection.close();
                        CogTrade.LOGGER.info("Önceki veritabanı bağlantısı kapatıldı.");
                    }
                } catch (SQLException ignored) {}
                connection = null;
            }

            // Klasör yoksa oluştur
            if (!dataDir.toFile().exists()) {
                boolean created = dataDir.toFile().mkdirs();
                if (created) CogTrade.LOGGER.info("CogTrade veri klasörü oluşturuldu.");
            }
            String url = "jdbc:sqlite:" + dataDir.resolve("cogtrade.db");
            connection = DriverManager.getConnection(url);
            createTables();
            CogTrade.LOGGER.info("CogTrade veritabanı başlatıldı.");
        } catch (SQLException e) {
            CogTrade.LOGGER.error("Veritabanı başlatılamadı: " + e.getMessage());
        }
    }

    private static void createTables() throws SQLException {
        String playersTable = """
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY,
                username TEXT NOT NULL,
                balance REAL NOT NULL DEFAULT 0
            );
        """;

        String transactionsTable = """
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_uuid TEXT NOT NULL,
                receiver_uuid TEXT NOT NULL,
                amount REAL NOT NULL,
                reason TEXT,
                timestamp INTEGER NOT NULL
            );
        """;

        String dailyStatsTable = """
            CREATE TABLE IF NOT EXISTS daily_stats (
                uuid TEXT NOT NULL,
                date TEXT NOT NULL,
                earned REAL NOT NULL DEFAULT 0,
                spent REAL NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, date)
            );
        """;

        String marketItemsTable = """
            CREATE TABLE IF NOT EXISTS market_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                item_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                category TEXT NOT NULL,
                price REAL NOT NULL,
                stock INTEGER NOT NULL DEFAULT 0,
                max_stock INTEGER NOT NULL DEFAULT 1000,
                enabled INTEGER NOT NULL DEFAULT 1
            );
        """;

        String marketTransactionsTable = """
                CREATE TABLE IF NOT EXISTS market_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    price_per_unit REAL NOT NULL,
                    total_price REAL NOT NULL,
                    type TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                );
            """;

        // Trade Depot / Trade Post sistemi
        String playerShopsTable = """
                CREATE TABLE IF NOT EXISTS player_shops (
                    owner_uuid TEXT PRIMARY KEY,
                    owner_name TEXT NOT NULL,
                    depot_world TEXT NOT NULL,
                    depot_x INTEGER NOT NULL,
                    depot_y INTEGER NOT NULL,
                    depot_z INTEGER NOT NULL,
                    post_world TEXT,
                    post_x INTEGER,
                    post_y INTEGER,
                    post_z INTEGER,
                    chest_positions TEXT NOT NULL DEFAULT ''
                );
            """;

        // item_id, fiyat, stok sandıktan okunur (stok sanal değil)
        String playerShopListingsTable = """
                CREATE TABLE IF NOT EXISTS player_shop_listings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    price REAL NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1
                );
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(playersTable);
            stmt.execute(transactionsTable);
            stmt.execute(dailyStatsTable);
            stmt.execute(marketItemsTable);
            stmt.execute(marketTransactionsTable);
            stmt.execute(playerShopsTable);
            stmt.execute(playerShopListingsTable);
        }
    }

    public static Connection getConnection() {
        return connection;
    }

    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                CogTrade.LOGGER.info("Veritabanı bağlantısı kapatıldı.");
            }
        } catch (SQLException e) {
            CogTrade.LOGGER.error("Veritabanı kapatılamadı: " + e.getMessage());
        }
    }
}