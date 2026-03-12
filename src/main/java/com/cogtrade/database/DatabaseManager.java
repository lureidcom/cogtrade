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
        // player_shops: sahip kaydı + Trade Post konumu
        String playerShopsTable = """
                CREATE TABLE IF NOT EXISTS player_shops (
                    owner_uuid TEXT PRIMARY KEY,
                    owner_name TEXT NOT NULL,
                    depot_world TEXT NOT NULL DEFAULT 'N/A',
                    depot_x INTEGER NOT NULL DEFAULT 0,
                    depot_y INTEGER NOT NULL DEFAULT 0,
                    depot_z INTEGER NOT NULL DEFAULT 0,
                    post_world TEXT,
                    post_x INTEGER,
                    post_y INTEGER,
                    post_z INTEGER,
                    chest_positions TEXT NOT NULL DEFAULT ''
                );
            """;

        // trade_depots: her Trade Depot bloğu için ayrı kayıt (oyuncu başına çoklu destekli)
        String tradeDepotsTable = """
                CREATE TABLE IF NOT EXISTS trade_depots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    depot_world TEXT NOT NULL,
                    depot_x INTEGER NOT NULL,
                    depot_y INTEGER NOT NULL,
                    depot_z INTEGER NOT NULL,
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

        // Audit log for completed direct player-to-player trades
        String directTradesTable = """
                CREATE TABLE IF NOT EXISTS direct_trades (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT NOT NULL,
                    initiator_uuid TEXT NOT NULL,
                    initiator_name TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    target_name TEXT NOT NULL,
                    initiator_coins REAL NOT NULL DEFAULT 0,
                    target_coins REAL NOT NULL DEFAULT 0,
                    initiator_items INTEGER NOT NULL DEFAULT 0,
                    target_items INTEGER NOT NULL DEFAULT 0,
                    status TEXT NOT NULL DEFAULT 'COMPLETED',
                    completed_at INTEGER NOT NULL
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
            stmt.execute(tradeDepotsTable);
            stmt.execute(directTradesTable);
        }

        // Eski player_shops.chest_positions verisini trade_depots'a taşı (tek seferlik migration)
        migrateDepotsFromPlayerShops();
    }

    /**
     * Eski şemada player_shops'ta saklanan tek depot kaydını
     * yeni trade_depots tablosuna aktarır (her server başlatmada güvenli çalışır).
     */
    private static void migrateDepotsFromPlayerShops() {
        try {
            // Taşınacak kayıt var mı kontrol et
            String migrateSql = """
                INSERT INTO trade_depots (owner_uuid, depot_world, depot_x, depot_y, depot_z, chest_positions)
                SELECT p.owner_uuid, p.depot_world, p.depot_x, p.depot_y, p.depot_z, p.chest_positions
                FROM player_shops p
                WHERE p.depot_world != 'N/A'
                  AND p.chest_positions != ''
                  AND NOT EXISTS (
                      SELECT 1 FROM trade_depots t
                      WHERE t.owner_uuid = p.owner_uuid
                        AND t.depot_x = p.depot_x
                        AND t.depot_y = p.depot_y
                        AND t.depot_z = p.depot_z
                  )
            """;
            connection.createStatement().execute(migrateSql);
        } catch (SQLException e) {
            // Sütun yoksa veya başka bir sorunsa sessizce atla
            CogTrade.LOGGER.info("Depot migration atlandı: " + e.getMessage());
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