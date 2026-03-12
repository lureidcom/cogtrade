package com.cogtrade.market;

import com.cogtrade.database.DatabaseManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.sql.*;
import java.util.*;

public class PlayerShopManager {

    public record ChestPos(String world, int x, int y, int z) {
        public BlockPos toBlockPos() { return new BlockPos(x, y, z); }

        public static ChestPos of(String worldId, BlockPos pos) {
            return new ChestPos(worldId, pos.getX(), pos.getY(), pos.getZ());
        }

        public String serialize() { return world + "@" + x + ":" + y + ":" + z; }

        public static ChestPos deserialize(String s) {
            int at = s.indexOf('@');
            if (at < 0) return null;
            String worldPart = s.substring(0, at);
            String[] coords = s.substring(at + 1).split(":");
            if (coords.length != 3) return null;
            try {
                return new ChestPos(worldPart, Integer.parseInt(coords[0]),
                        Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public record ShopListing(int id, String ownerUuid, String itemId,
                              String displayName, String category, double price, boolean enabled) {}

    // ── Depot kayıt / güncelleme ───────────────────────────────────────────

    public static void registerDepot(String ownerUuid, String ownerName,
                                     String worldId, BlockPos pos,
                                     List<ChestPos> chests) {
        String chestData = serializeChests(chests);
        String sql = """
            INSERT INTO player_shops (owner_uuid, owner_name, depot_world, depot_x, depot_y, depot_z, chest_positions)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(owner_uuid) DO UPDATE SET
                owner_name = excluded.owner_name,
                depot_world = excluded.depot_world,
                depot_x = excluded.depot_x,
                depot_y = excluded.depot_y,
                depot_z = excluded.depot_z,
                chest_positions = excluded.chest_positions
        """;
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            stmt.setString(2, ownerName);
            stmt.setString(3, worldId);
            stmt.setInt(4, pos.getX());
            stmt.setInt(5, pos.getY());
            stmt.setInt(6, pos.getZ());
            stmt.setString(7, chestData);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void removeDepot(String ownerUuid) {
        String sql = "DELETE FROM player_shops WHERE owner_uuid = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Listings da temizle
        removeAllListings(ownerUuid);
    }

    public static void registerPost(String ownerUuid, String worldId, BlockPos pos) {
        String sql = "UPDATE player_shops SET post_world = ?, post_x = ?, post_y = ?, post_z = ? WHERE owner_uuid = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, worldId);
            stmt.setInt(2, pos.getX());
            stmt.setInt(3, pos.getY());
            stmt.setInt(4, pos.getZ());
            stmt.setString(5, ownerUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void removePost(String ownerUuid) {
        String sql = "UPDATE player_shops SET post_world = NULL, post_x = NULL, post_y = NULL, post_z = NULL WHERE owner_uuid = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean hasDepot(String ownerUuid) {
        String sql = "SELECT 1 FROM player_shops WHERE owner_uuid = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<ChestPos> getChests(String ownerUuid) {
        String sql = "SELECT chest_positions FROM player_shops WHERE owner_uuid = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return deserializeChests(rs.getString("chest_positions"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static String getOwnerName(String ownerUuid) {
        String sql = "SELECT owner_name FROM player_shops WHERE owner_uuid = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("owner_name");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "?";
    }

    // ── Sandıklardan item tarama ───────────────────────────────────────────

    /**
     * Bağlı tüm sandıklardaki itemleri toplar.
     * key = itemId (minecraft:...), value = toplam adet
     */
    public static Map<String, Integer> scanChestContents(String ownerUuid, ServerWorld world) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<ChestPos> chests = getChests(ownerUuid);

        for (ChestPos cp : chests) {
            if (!cp.world().equals(world.getRegistryKey().getValue().toString())) continue;
            BlockPos pos = cp.toBlockPos();
            if (!(world.getBlockEntity(pos) instanceof net.minecraft.block.entity.ChestBlockEntity chest)) continue;

            for (int i = 0; i < chest.size(); i++) {
                ItemStack stack = chest.getStack(i);
                if (stack.isEmpty()) continue;
                String id = Registries.ITEM.getId(stack.getItem()).toString();
                counts.merge(id, stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /**
     * Sandıklardan belirli bir item'den istenen miktarı çeker.
     * Başarılı olursa true döner.
     */
    public static boolean withdrawFromChests(String ownerUuid, ServerWorld world,
                                             String itemId, int amount) {
        List<ChestPos> chests = getChests(ownerUuid);
        net.minecraft.item.Item mcItem;
        try {
            mcItem = Registries.ITEM.get(new Identifier(itemId));
        } catch (Exception e) {
            return false;
        }

        int remaining = amount;
        for (ChestPos cp : chests) {
            if (remaining <= 0) break;
            if (!cp.world().equals(world.getRegistryKey().getValue().toString())) continue;
            BlockPos pos = cp.toBlockPos();
            if (!(world.getBlockEntity(pos) instanceof net.minecraft.block.entity.ChestBlockEntity chest)) continue;

            for (int i = 0; i < chest.size() && remaining > 0; i++) {
                ItemStack stack = chest.getStack(i);
                if (stack.isOf(mcItem)) {
                    int take = Math.min(stack.getCount(), remaining);
                    stack.decrement(take);
                    remaining -= take;
                }
            }
            chest.markDirty();
        }
        return remaining <= 0;
    }

    // ── Listings ──────────────────────────────────────────────────────────

    public static boolean addListing(String ownerUuid, String itemId,
                                     String displayName, String category, double price) {
        // Zaten varsa güncelle
        String checkSql = "SELECT id FROM player_shop_listings WHERE owner_uuid = ? AND item_id = ?";
        try (PreparedStatement check = DatabaseManager.getConnection().prepareStatement(checkSql)) {
            check.setString(1, ownerUuid);
            check.setString(2, itemId);
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                String updateSql = "UPDATE player_shop_listings SET price = ?, enabled = 1 WHERE id = ?";
                try (PreparedStatement upd = DatabaseManager.getConnection().prepareStatement(updateSql)) {
                    upd.setDouble(1, price);
                    upd.setInt(2, id);
                    upd.executeUpdate();
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        String sql = """
            INSERT INTO player_shop_listings (owner_uuid, item_id, display_name, category, price, enabled)
            VALUES (?, ?, ?, ?, ?, 1)
        """;
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            stmt.setString(2, itemId);
            stmt.setString(3, displayName);
            stmt.setString(4, category);
            stmt.setDouble(5, price);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean removeListing(String ownerUuid, String itemId) {
        String sql = "DELETE FROM player_shop_listings WHERE owner_uuid = ? AND item_id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            stmt.setString(2, itemId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void removeAllListings(String ownerUuid) {
        String sql = "DELETE FROM player_shop_listings WHERE owner_uuid = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<ShopListing> getListings(String ownerUuid) {
        List<ShopListing> list = new ArrayList<>();
        String sql = "SELECT * FROM player_shop_listings WHERE owner_uuid = ? AND enabled = 1";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, ownerUuid);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new ShopListing(
                        rs.getInt("id"),
                        rs.getString("owner_uuid"),
                        rs.getString("item_id"),
                        rs.getString("display_name"),
                        rs.getString("category"),
                        rs.getDouble("price"),
                        rs.getInt("enabled") == 1
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // ── Yardımcılar ──────────────────────────────────────────────────────

    private static String serializeChests(List<ChestPos> chests) {
        if (chests == null || chests.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chests.size(); i++) {
            if (i > 0) sb.append("|");
            sb.append(chests.get(i).serialize());
        }
        return sb.toString();
    }

    private static List<ChestPos> deserializeChests(String data) {
        List<ChestPos> list = new ArrayList<>();
        if (data == null || data.isBlank()) return list;
        for (String part : data.split("\\|")) {
            ChestPos cp = ChestPos.deserialize(part);
            if (cp != null) list.add(cp);
        }
        return list;
    }
}