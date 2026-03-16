package com.cogtrade.economy;

import com.cogtrade.client.TransactionEntry;
import com.cogtrade.config.CogTradeConfig;
import com.cogtrade.database.DatabaseManager;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerEconomy {

    public static void initPlayer(UUID uuid, String username) {
        String sql = "INSERT OR IGNORE INTO players (uuid, username, balance) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.setDouble(3, CogTradeConfig.get().startingBalance);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static double getBalance(UUID uuid) {
        String sql = "SELECT balance FROM players WHERE uuid = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public static boolean setBalance(UUID uuid, double amount) {
        String sql = "UPDATE players SET balance = ? WHERE uuid = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Not içermeyen transfer (geriye dönük uyumluluk). */
    public static boolean transfer(UUID from, UUID to, double amount) {
        return transfer(from, to, amount, null);
    }

    /**
     * Oyuncular arası para transferi.
     *
     * @param note Opsiyonel açıklama (max 100 karakter); {@code null} veya boş ise kaydedilmez.
     */
    public static boolean transfer(UUID from, UUID to, double amount, String note) {
        if (amount <= 0) return false;
        if (getBalance(from) < amount) return false;

        // Notu güvenli hale getir: null → null, boş → null, uzunsa kırp
        String safeNote = (note == null || note.isBlank()) ? null
                        : (note.length() > 100 ? note.substring(0, 100) : note);

        Connection conn = DatabaseManager.getConnection();
        try {
            conn.setAutoCommit(false);

            setBalance(from, getBalance(from) - amount);
            setBalance(to,   getBalance(to)   + amount);

            String logSql = "INSERT INTO transactions " +
                    "(sender_uuid, receiver_uuid, amount, reason, note, timestamp) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(logSql)) {
                stmt.setString(1, from.toString());
                stmt.setString(2, to.toString());
                stmt.setDouble(3, amount);
                stmt.setString(4, "transfer");
                stmt.setString(5, safeNote);        // NULL olabilir
                stmt.setLong(6, System.currentTimeMillis());
                stmt.executeUpdate();
            }

            conn.commit();
            conn.setAutoCommit(true);
            return true;
        } catch (SQLException e) {
            try { conn.rollback(); conn.setAutoCommit(true); } catch (SQLException ex) { ex.printStackTrace(); }
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Büyü Marketi satın alımını {@code transactions} tablosuna kaydeder.
     *
     * @param uuid       Satın alan oyuncunun UUID'si
     * @param price      Ödenen coin miktarı
     * @param effectNote Görünen açıklama (örn. "Hız III - 15 dk")
     */
    public static void recordEffectPurchase(UUID uuid, long price, String effectNote) {
        String sql = "INSERT INTO transactions (sender_uuid, receiver_uuid, amount, reason, note, timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, "00000000-0000-0000-0000-000000000000"); // sistem/sunucu
            stmt.setDouble(3, price);
            stmt.setString(4, "effect_purchase");
            stmt.setString(5, effectNote);
            stmt.setLong(6, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void recordDailyEarned(UUID uuid, double amount) {
        String date = java.time.LocalDate.now().toString();
        String sql = """
        INSERT INTO daily_stats (uuid, date, earned, spent)
        VALUES (?, ?, ?, 0)
        ON CONFLICT(uuid, date) DO UPDATE SET earned = earned + ?
    """;
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, date);
            stmt.setDouble(3, amount);
            stmt.setDouble(4, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void recordDailySpent(UUID uuid, double amount) {
        String date = java.time.LocalDate.now().toString();
        String sql = """
        INSERT INTO daily_stats (uuid, date, earned, spent)
        VALUES (?, ?, 0, ?)
        ON CONFLICT(uuid, date) DO UPDATE SET spent = spent + ?
    """;
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, date);
            stmt.setDouble(3, amount);
            stmt.setDouble(4, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static double getDailyEarned(UUID uuid) {
        String date = java.time.LocalDate.now().toString();
        String sql = "SELECT earned FROM daily_stats WHERE uuid = ? AND date = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, date);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("earned");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public static double getDailySpent(UUID uuid) {
        String date = java.time.LocalDate.now().toString();
        String sql = "SELECT spent FROM daily_stats WHERE uuid = ? AND date = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, date);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("spent");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    /**
     * Oyuncunun tüm coin işlemlerini birleştirerek döner (en yenisi önce).
     * Para transferleri, market alım/satım işlemleri ve doğrudan takaslar dahildir.
     *
     * @param uuid  Sorgulanacak oyuncu UUID'si
     * @param limit Maksimum kayıt sayısı
     */
    public static List<TransactionEntry> getTransactionHistory(UUID uuid, int limit) {
        List<TransactionEntry> list = new ArrayList<>();
        Connection conn = DatabaseManager.getConnection();
        if (conn == null) return list;
        String uuidStr = uuid.toString();

        // ── 1. Para transferleri ──────────────────────────────────────────
        try {
            String sql = """
                SELECT t.id,
                       CASE WHEN t.sender_uuid = ? THEN 'TRANSFER_OUT' ELSE 'TRANSFER_IN' END AS tx_type,
                       t.amount,
                       CASE WHEN t.sender_uuid = ?
                            THEN COALESCE(p2.username, 'Bilinmiyor')
                            ELSE COALESCE(p1.username, 'Bilinmiyor') END AS party,
                       t.timestamp,
                       COALESCE(t.note, '') AS note
                FROM transactions t
                LEFT JOIN players p1 ON p1.uuid = t.sender_uuid
                LEFT JOIN players p2 ON p2.uuid = t.receiver_uuid
                WHERE (t.sender_uuid = ? OR t.receiver_uuid = ?)
                  AND (t.reason IS NULL OR t.reason = 'transfer')
                ORDER BY t.timestamp DESC
                LIMIT ?
            """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuidStr);
                stmt.setString(2, uuidStr);
                stmt.setString(3, uuidStr);
                stmt.setString(4, uuidStr);
                stmt.setInt(5, limit);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    int    id     = rs.getInt("id");
                    String type   = rs.getString("tx_type");
                    double amount = rs.getDouble("amount");
                    String party  = rs.getString("party");
                    long   ts     = rs.getLong("timestamp");
                    String note   = rs.getString("note");
                    double signed = "TRANSFER_IN".equals(type) ? amount : -amount;
                    list.add(new TransactionEntry(id, type, signed, party, ts, note, ""));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }

        // ── 2. Market alım/satım işlemleri ───────────────────────────────
        try {
            String sql = """
                SELECT mt.id,
                       CASE WHEN mt.type = 'BUY' THEN 'MARKET_BUY' ELSE 'MARKET_SELL' END AS tx_type,
                       mt.total_price,
                       COALESCE(mi.display_name, mt.item_id) AS item_name,
                       mt.amount AS qty,
                       mt.price_per_unit,
                       mt.timestamp
                FROM market_transactions mt
                LEFT JOIN market_items mi ON mi.item_id = mt.item_id
                WHERE mt.player_uuid = ?
                ORDER BY mt.timestamp DESC
                LIMIT ?
            """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuidStr);
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    int    id     = 10000 + rs.getInt("id");
                    String type   = rs.getString("tx_type");
                    double total  = rs.getDouble("total_price");
                    String item   = rs.getString("item_name");
                    int    qty    = rs.getInt("qty");
                    double ppu    = rs.getDouble("price_per_unit");
                    long   ts     = rs.getLong("timestamp");
                    double signed = "MARKET_BUY".equals(type) ? -total : total;
                    String detail = "Adet: " + qty + " | Birim: "
                            + String.format("%.0f", ppu) + " coin";
                    list.add(new TransactionEntry(id, type, signed, item, ts, "", detail));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }

        // ── 3. Doğrudan takaslar ─────────────────────────────────────────
        try {
            String sql = """
                SELECT dt.id,
                       CASE WHEN dt.initiator_uuid = ? THEN dt.target_name    ELSE dt.initiator_name  END AS partner,
                       CASE WHEN dt.initiator_uuid = ? THEN dt.initiator_coins ELSE dt.target_coins    END AS given_coins,
                       CASE WHEN dt.initiator_uuid = ? THEN dt.target_coins    ELSE dt.initiator_coins END AS recv_coins,
                       CASE WHEN dt.initiator_uuid = ? THEN dt.initiator_items ELSE dt.target_items    END AS given_items,
                       CASE WHEN dt.initiator_uuid = ? THEN dt.target_items    ELSE dt.initiator_items END AS recv_items,
                       dt.completed_at
                FROM direct_trades dt
                WHERE (dt.initiator_uuid = ? OR dt.target_uuid = ?) AND dt.status = 'COMPLETED'
                ORDER BY dt.completed_at DESC
                LIMIT ?
            """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 1; i <= 7; i++) stmt.setString(i, uuidStr);
                stmt.setInt(8, limit);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    int    id         = 20000 + rs.getInt("id");
                    String partner    = rs.getString("partner");
                    double givenCoins = rs.getDouble("given_coins");
                    double recvCoins  = rs.getDouble("recv_coins");
                    int    givenItems = rs.getInt("given_items");
                    int    recvItems  = rs.getInt("recv_items");
                    long   ts         = rs.getLong("completed_at");
                    double signed     = recvCoins - givenCoins;
                    String detail     = "Verilen: " + String.format("%.0f", givenCoins)
                            + " coin, " + givenItems + " eşya\n"
                            + "Alınan:  " + String.format("%.0f", recvCoins)
                            + " coin, " + recvItems + " eşya";
                    list.add(new TransactionEntry(id, "TRADE", signed, partner, ts, "", detail));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }

        // ── 4. Büyü Marketi satın alımları ───────────────────────────────
        try {
            String sql = """
                SELECT t.id,
                       t.amount,
                       COALESCE(t.note, 'Büyü') AS effect_note,
                       t.timestamp
                FROM transactions t
                WHERE t.sender_uuid = ? AND t.reason = 'effect_purchase'
                ORDER BY t.timestamp DESC
                LIMIT ?
            """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, uuidStr);
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    int    id   = 30000 + rs.getInt("id");
                    double amt  = rs.getDouble("amount");
                    String note = rs.getString("effect_note");
                    long   ts   = rs.getLong("timestamp");
                    list.add(new TransactionEntry(id, "EFFECT_BUY", -amt, note, ts, "", ""));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }

        // En yenisi önce sırala, limitle
        list.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }
}