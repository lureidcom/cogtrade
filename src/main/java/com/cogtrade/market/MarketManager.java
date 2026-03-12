package com.cogtrade.market;

import com.cogtrade.database.DatabaseManager;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class MarketManager {

    public static class HistoryEntry {
        public final String displayName;
        public final int    amount;
        public final double totalPrice;
        public final String type;
        public final long   timestamp;

        HistoryEntry(String displayName, int amount, double totalPrice, String type, long timestamp) {
            this.displayName = displayName;
            this.amount      = amount;
            this.totalPrice  = totalPrice;
            this.type        = type;
            this.timestamp   = timestamp;
        }

        public String format() {
            String date = DateTimeFormatter.ofPattern("dd.MM HH:mm")
                    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()));
            String label = "BUY".equals(type) ? "§aSATIN AL" : "§cSAT";
            return String.format("§7[%s] %s §f%s §7x%d — §6⬡%.0f",
                    date, label, displayName, amount, totalPrice);
        }
    }

    /** Oyuncunun son {@code limit} market işlemini döndürür. */
    public static List<HistoryEntry> getPlayerHistory(UUID playerUuid, int limit) {
        List<HistoryEntry> list = new ArrayList<>();
        String sql = """
            SELECT mt.amount, mt.total_price, mt.type, mt.timestamp,
                   COALESCE((SELECT mi.display_name FROM market_items mi
                              WHERE mi.item_id = mt.item_id LIMIT 1), mt.item_id) AS display_name
            FROM market_transactions mt
            WHERE mt.player_uuid = ?
            ORDER BY mt.timestamp DESC
            LIMIT ?
            """;
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new HistoryEntry(
                        rs.getString("display_name"),
                        rs.getInt("amount"),
                        rs.getDouble("total_price"),
                        rs.getString("type"),
                        rs.getLong("timestamp")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static List<MarketItem> getAllItems() {
        List<MarketItem> items = new ArrayList<>();
        String sql = "SELECT * FROM market_items WHERE enabled = 1 ORDER BY category, display_name";
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public static List<String> getCategories() {
        List<MarketItem> items = getAllItems();
        return items.stream()
                .map(MarketItem::getCategory)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public static List<MarketItem> getItemsByCategory(String category) {
        List<MarketItem> items = new ArrayList<>();
        String sql = "SELECT * FROM market_items WHERE category = ? AND enabled = 1 ORDER BY display_name";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, category);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                items.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    public static boolean addItem(String itemId, String displayName, String category,
                                  double price, int stock, int maxStock) {
        String sql = """
            INSERT INTO market_items (item_id, display_name, category, price, stock, max_stock, enabled)
            VALUES (?, ?, ?, ?, ?, ?, 1)
        """;
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, itemId);
            stmt.setString(2, displayName);
            stmt.setString(3, category);
            stmt.setDouble(4, price);
            stmt.setInt(5, stock);
            stmt.setInt(6, maxStock);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean removeItem(int id) {
        String sql = "UPDATE market_items SET enabled = 0 WHERE id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateStock(int id, int newStock) {
        String sql = "UPDATE market_items SET stock = ? WHERE id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, newStock);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updatePrice(int id, double newPrice) {
        String sql = "UPDATE market_items SET price = ? WHERE id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, newPrice);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateItem(int id, double newPrice, int newStock) {
        String sql = "UPDATE market_items SET price = ?, stock = ? WHERE id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setDouble(1, newPrice);
            stmt.setInt(2, newStock);
            stmt.setInt(3, id);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isEmpty() {
        String sql = "SELECT COUNT(*) FROM market_items";
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return !rs.next() || rs.getInt(1) == 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    public static void seedDefaultItemsIfEmpty() {
        if (!isEmpty()) return;

        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) continue;
            if (!"minecraft".equals(id.getNamespace())) continue;
            if (item == Items.AIR) continue;

            String path = id.getPath();
            if (path.contains("debug")) continue;

            String itemId = id.toString();
            String displayName = item.getDefaultStack().getName().getString();
            if (displayName == null || displayName.isBlank()) continue;

            String category = resolveCategory(item);
            double price = estimatePrice(item, path);
            int maxStock = estimateMaxStock(item, path);
            int stock = estimateInitialStock(item, maxStock);

            addItem(itemId, displayName, category, price, stock, maxStock);
        }
    }

    public static MarketItem getItemById(int id) {
        String sql = "SELECT * FROM market_items WHERE id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return fromResultSet(rs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void recordTransaction(String playerUuid, String itemId, int amount,
                                         double pricePerUnit, String type) {
        String sql = """
            INSERT INTO market_transactions
            (player_uuid, item_id, amount, price_per_unit, total_price, type, timestamp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, playerUuid);
            stmt.setString(2, itemId);
            stmt.setInt(3, amount);
            stmt.setDouble(4, pricePerUnit);
            stmt.setDouble(5, pricePerUnit * amount);
            stmt.setString(6, type);
            stmt.setLong(7, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static String resolveCategory(Item item) {
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();

            if (isNaturalBlock(block)) {
                return "Natural Blocks";
            }
            if (isFunctionalBlock(block)) {
                return "Functional Blocks";
            }
            if (isRedstoneBlock(item)) {
                return "Redstone Blocks";
            }
            return "Building Blocks";
        }

        if (item instanceof SwordItem || item instanceof ArmorItem || item instanceof BowItem
                || item instanceof CrossbowItem || item instanceof ShieldItem
                || item instanceof TridentItem) {
            return "Combat";
        }

        if (item instanceof ToolItem || item instanceof HoeItem || item instanceof FishingRodItem
                || item instanceof FlintAndSteelItem || item instanceof ShearsItem) {
            return "Tools";
        }

        if (item.isFood()) {
            return "Food & Drinks";
        }

        String path = Registries.ITEM.getId(item).getPath();
        if (path.contains("spawn_egg")) return "Spawn Eggs";
        if (path.contains("ingot") || path.contains("nugget") || path.contains("gem")
                || path.contains("dust") || path.contains("shard") || path.contains("hide")
                || path.contains("membrane") || path.contains("pearl") || path.contains("rod")
                || path.contains("string") || path.contains("feather") || path.contains("bone")) {
            return "Ingredients";
        }

        return "Misc";
    }

    private static boolean isNaturalBlock(Block block) {
        return block == Blocks.STONE
                || block == Blocks.COBBLESTONE
                || block == Blocks.DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.SAND
                || block == Blocks.RED_SAND
                || block == Blocks.GRAVEL
                || block == Blocks.CLAY
                || block == Blocks.NETHERRACK
                || block == Blocks.END_STONE
                || block == Blocks.DEEPSLATE
                || block == Blocks.TUFF
                || block == Blocks.CALCITE
                || block == Blocks.OBSIDIAN
                || block == Blocks.BASALT
                || block == Blocks.SMOOTH_BASALT
                || block == Blocks.BLACKSTONE
                || block == Blocks.SNOW_BLOCK
                || block == Blocks.ICE
                || block == Blocks.PACKED_ICE
                || block == Blocks.BLUE_ICE
                || block == Blocks.OAK_LOG
                || block == Blocks.SPRUCE_LOG
                || block == Blocks.BIRCH_LOG
                || block == Blocks.JUNGLE_LOG
                || block == Blocks.ACACIA_LOG
                || block == Blocks.DARK_OAK_LOG
                || block == Blocks.MANGROVE_LOG
                || block == Blocks.CHERRY_LOG
                || block == Blocks.BAMBOO_BLOCK;
    }

    private static boolean isFunctionalBlock(Block block) {
        return block == Blocks.CRAFTING_TABLE
                || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER
                || block == Blocks.ANVIL
                || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL
                || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.ENDER_CHEST
                || block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.BARREL
                || block == Blocks.HOPPER
                || block == Blocks.DISPENSER
                || block == Blocks.DROPPER
                || block == Blocks.BREWING_STAND
                || block == Blocks.BEACON
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.STONECUTTER
                || block == Blocks.LOOM
                || block == Blocks.GRINDSTONE;
    }

    private static boolean isRedstoneBlock(Item item) {
        String path = Registries.ITEM.getId(item).getPath();
        return path.contains("redstone")
                || path.contains("comparator")
                || path.contains("repeater")
                || path.contains("observer")
                || path.contains("piston")
                || path.contains("lever")
                || path.contains("button")
                || path.contains("pressure_plate")
                || path.contains("daylight_detector")
                || path.contains("target")
                || path.contains("tripwire")
                || path.contains("hopper")
                || path.contains("dispenser")
                || path.contains("dropper");
    }

    private static double estimatePrice(Item item, String path) {
        double price = 12.0;

        if (item instanceof BlockItem) price = 8.0;
        if (item.getMaxCount() == 16) price *= 1.8;
        if (item.getMaxCount() == 1)  price *= 3.5;
        if (item.isFood())            price *= 1.6;

        if (path.contains("diamond"))      price *= 14.0;
        else if (path.contains("netherite")) price *= 36.0;
        else if (path.contains("emerald"))   price *= 18.0;
        else if (path.contains("gold"))      price *= 7.0;
        else if (path.contains("iron"))      price *= 5.0;
        else if (path.contains("copper"))    price *= 3.0;
        else if (path.contains("lapis"))     price *= 3.0;
        else if (path.contains("redstone"))  price *= 2.4;
        else if (path.contains("coal"))      price *= 1.8;
        else if (path.contains("quartz"))    price *= 2.8;

        if (path.contains("ore"))         price *= 2.2;
        if (path.contains("log"))         price *= 1.2;
        if (path.contains("planks"))      price *= 1.0;
        if (path.contains("stairs"))      price *= 1.15;
        if (path.contains("slab"))        price *= 0.8;
        if (path.contains("fence"))       price *= 1.2;
        if (path.contains("glass"))       price *= 1.4;
        if (path.contains("terracotta"))  price *= 1.7;
        if (path.contains("concrete"))    price *= 2.0;
        if (path.contains("wool"))        price *= 1.6;
        if (path.contains("bed"))         price *= 2.8;
        if (path.contains("shulker"))     price *= 16.0;
        if (path.contains("elytra"))      price *= 120.0;
        if (path.contains("totem"))       price *= 180.0;
        if (path.contains("beacon"))      price *= 220.0;
        if (path.contains("enchanted_golden_apple")) price *= 260.0;
        if (path.contains("spawn_egg"))   price *= 40.0;

        return Math.max(1.0, Math.round(price));
    }

    private static int estimateMaxStock(Item item, String path) {
        int max = 1024;

        if (item instanceof BlockItem) max = 4096;
        if (item.getMaxCount() == 16)  max = 512;
        if (item.getMaxCount() == 1)   max = 128;

        if (path.contains("diamond") || path.contains("netherite") || path.contains("elytra")
                || path.contains("totem") || path.contains("beacon")) {
            max = Math.min(max, 64);
        } else if (path.contains("emerald") || path.contains("spawn_egg")) {
            max = Math.min(max, 128);
        } else if (path.contains("iron") || path.contains("gold")) {
            max = Math.min(max, 256);
        }

        return Math.max(16, max);
    }

    private static int estimateInitialStock(Item item, int maxStock) {
        if (item instanceof BlockItem) return Math.max(128, maxStock / 2);
        if (item.getMaxCount() == 1)   return Math.max(8, maxStock / 4);
        return Math.max(32, maxStock / 3);
    }

    private static MarketItem fromResultSet(ResultSet rs) throws SQLException {
        return new MarketItem(
                rs.getInt("id"),
                rs.getString("item_id"),
                rs.getString("display_name"),
                rs.getString("category"),
                rs.getDouble("price"),
                rs.getInt("stock"),
                rs.getInt("max_stock"),
                rs.getInt("enabled") == 1
        );
    }
}