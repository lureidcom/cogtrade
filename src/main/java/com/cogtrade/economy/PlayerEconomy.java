package com.cogtrade.economy;

import com.cogtrade.config.CogTradeConfig;
import com.cogtrade.database.DatabaseManager;
import java.sql.*;
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
}