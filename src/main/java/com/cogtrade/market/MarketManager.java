package com.cogtrade.market;

import com.cogtrade.database.DatabaseManager;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.sql.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MarketManager {

    // ── Fiyat tablosu: item path → kesin fiyat ───────────────────────────
    private static final Map<String, Double> PRICE_TABLE = new HashMap<>();
    static {
        // Değerli mineraller
        PRICE_TABLE.put("diamond",                  90.0);
        PRICE_TABLE.put("diamond_block",           810.0);  // 9 × 90
        PRICE_TABLE.put("diamond_ore",             180.0);
        PRICE_TABLE.put("deepslate_diamond_ore",   200.0);
        PRICE_TABLE.put("netherite_ingot",         450.0);
        PRICE_TABLE.put("netherite_scrap",         130.0);
        PRICE_TABLE.put("netherite_block",        4050.0);  // 9 × 450
        PRICE_TABLE.put("ancient_debris",          350.0);
        PRICE_TABLE.put("emerald",                  60.0);
        PRICE_TABLE.put("emerald_block",           540.0);  // 9 × 60
        PRICE_TABLE.put("emerald_ore",             120.0);
        PRICE_TABLE.put("deepslate_emerald_ore",   140.0);
        PRICE_TABLE.put("iron_ingot",                8.0);
        PRICE_TABLE.put("iron_nugget",               1.0);
        PRICE_TABLE.put("iron_block",               72.0);  // 9 × 8
        PRICE_TABLE.put("iron_ore",                 15.0);
        PRICE_TABLE.put("deepslate_iron_ore",       18.0);
        PRICE_TABLE.put("raw_iron",                  7.0);
        PRICE_TABLE.put("raw_iron_block",           63.0);
        PRICE_TABLE.put("gold_ingot",               15.0);
        PRICE_TABLE.put("gold_nugget",               2.0);
        PRICE_TABLE.put("gold_block",              135.0);  // 9 × 15
        PRICE_TABLE.put("gold_ore",                 25.0);
        PRICE_TABLE.put("deepslate_gold_ore",       28.0);
        PRICE_TABLE.put("nether_gold_ore",          20.0);
        PRICE_TABLE.put("raw_gold",                 14.0);
        PRICE_TABLE.put("raw_gold_block",          126.0);
        PRICE_TABLE.put("coal",                      3.0);
        PRICE_TABLE.put("coal_block",               27.0);  // 9 × 3
        PRICE_TABLE.put("coal_ore",                  5.0);
        PRICE_TABLE.put("deepslate_coal_ore",        6.0);
        PRICE_TABLE.put("copper_ingot",              4.0);
        PRICE_TABLE.put("copper_block",             36.0);  // 9 × 4
        PRICE_TABLE.put("copper_ore",                6.0);
        PRICE_TABLE.put("deepslate_copper_ore",      7.0);
        PRICE_TABLE.put("raw_copper",                4.0);
        PRICE_TABLE.put("raw_copper_block",         31.0);
        PRICE_TABLE.put("lapis_lazuli",              5.0);
        PRICE_TABLE.put("lapis_block",              45.0);  // 9 × 5
        PRICE_TABLE.put("lapis_ore",                20.0);
        PRICE_TABLE.put("deepslate_lapis_ore",      22.0);
        PRICE_TABLE.put("redstone",                  4.0);
        PRICE_TABLE.put("redstone_block",           36.0);  // 9 × 4
        PRICE_TABLE.put("redstone_ore",             18.0);
        PRICE_TABLE.put("deepslate_redstone_ore",   20.0);
        PRICE_TABLE.put("quartz",                    6.0);
        PRICE_TABLE.put("quartz_block",             18.0);
        PRICE_TABLE.put("nether_quartz_ore",        14.0);
        PRICE_TABLE.put("amethyst_shard",           15.0);
        PRICE_TABLE.put("amethyst_block",           45.0);
        PRICE_TABLE.put("budding_amethyst",        200.0);
        // Nether & End
        PRICE_TABLE.put("nether_star",            2000.0);
        PRICE_TABLE.put("beacon",                 5000.0);
        PRICE_TABLE.put("elytra",                 3000.0);
        PRICE_TABLE.put("totem_of_undying",       2500.0);
        PRICE_TABLE.put("enchanted_golden_apple", 5000.0);
        PRICE_TABLE.put("golden_apple",            300.0);
        PRICE_TABLE.put("end_crystal",             200.0);
        PRICE_TABLE.put("dragon_egg",            10000.0);
        PRICE_TABLE.put("dragon_breath",            80.0);
        PRICE_TABLE.put("ender_pearl",              25.0);
        PRICE_TABLE.put("eye_of_ender",             60.0);
        PRICE_TABLE.put("end_stone",                 8.0);
        PRICE_TABLE.put("end_stone_bricks",         10.0);
        PRICE_TABLE.put("purpur_block",             12.0);
        PRICE_TABLE.put("shulker_shell",           400.0);
        PRICE_TABLE.put("shulker_box",             900.0);
        PRICE_TABLE.put("blaze_rod",                20.0);
        PRICE_TABLE.put("blaze_powder",             12.0);
        PRICE_TABLE.put("ghast_tear",               50.0);
        PRICE_TABLE.put("magma_cream",              18.0);
        PRICE_TABLE.put("magma_block",              25.0);
        PRICE_TABLE.put("wither_skeleton_skull",   800.0);
        PRICE_TABLE.put("skeleton_skull",          200.0);
        PRICE_TABLE.put("zombie_head",             200.0);
        PRICE_TABLE.put("creeper_head",            300.0);
        PRICE_TABLE.put("nether_wart",               8.0);
        PRICE_TABLE.put("soul_sand",                 5.0);
        PRICE_TABLE.put("soul_soil",                 5.0);
        PRICE_TABLE.put("netherrack",                2.0);
        PRICE_TABLE.put("nether_brick",              3.0);
        PRICE_TABLE.put("nether_bricks",            15.0);
        PRICE_TABLE.put("crimson_stem",              6.0);
        PRICE_TABLE.put("warped_stem",               6.0);
        PRICE_TABLE.put("glowstone",                12.0);
        PRICE_TABLE.put("glowstone_dust",            4.0);
        PRICE_TABLE.put("crying_obsidian",          30.0);
        PRICE_TABLE.put("respawn_anchor",          200.0);
        PRICE_TABLE.put("lodestone",                80.0);
        // Obsidian
        PRICE_TABLE.put("obsidian",                 25.0);
        // Odunlar
        PRICE_TABLE.put("oak_log",                   5.0);
        PRICE_TABLE.put("spruce_log",                5.0);
        PRICE_TABLE.put("birch_log",                 5.0);
        PRICE_TABLE.put("jungle_log",                6.0);
        PRICE_TABLE.put("acacia_log",                5.0);
        PRICE_TABLE.put("dark_oak_log",              5.0);
        PRICE_TABLE.put("mangrove_log",              6.0);
        PRICE_TABLE.put("cherry_log",                7.0);
        PRICE_TABLE.put("bamboo_block",              4.0);
        PRICE_TABLE.put("oak_planks",                2.0);
        PRICE_TABLE.put("spruce_planks",             2.0);
        PRICE_TABLE.put("birch_planks",              2.0);
        PRICE_TABLE.put("jungle_planks",             2.0);
        PRICE_TABLE.put("acacia_planks",             2.0);
        PRICE_TABLE.put("dark_oak_planks",           2.0);
        PRICE_TABLE.put("mangrove_planks",           2.0);
        PRICE_TABLE.put("cherry_planks",             2.5);
        // Taşlar
        PRICE_TABLE.put("stone",                     2.0);
        PRICE_TABLE.put("cobblestone",               1.0);
        PRICE_TABLE.put("smooth_stone",              3.0);
        PRICE_TABLE.put("stone_bricks",              4.0);
        PRICE_TABLE.put("granite",                   2.0);
        PRICE_TABLE.put("diorite",                   2.0);
        PRICE_TABLE.put("andesite",                  2.0);
        PRICE_TABLE.put("deepslate",                 3.0);
        PRICE_TABLE.put("cobbled_deepslate",         2.0);
        PRICE_TABLE.put("deepslate_bricks",          5.0);
        PRICE_TABLE.put("deepslate_tiles",           6.0);
        PRICE_TABLE.put("tuff",                      2.0);
        PRICE_TABLE.put("calcite",                   2.0);
        PRICE_TABLE.put("basalt",                    2.0);
        PRICE_TABLE.put("blackstone",                2.0);
        PRICE_TABLE.put("dirt",                      1.0);
        PRICE_TABLE.put("sand",                      1.0);
        PRICE_TABLE.put("red_sand",                  2.0);
        PRICE_TABLE.put("gravel",                    1.0);
        PRICE_TABLE.put("sandstone",                 3.0);
        PRICE_TABLE.put("red_sandstone",             3.0);
        PRICE_TABLE.put("mud",                       1.0);
        PRICE_TABLE.put("packed_mud",                2.0);
        PRICE_TABLE.put("mud_bricks",                3.0);
        PRICE_TABLE.put("ice",                       3.0);
        PRICE_TABLE.put("packed_ice",               10.0);
        PRICE_TABLE.put("blue_ice",                 25.0);
        PRICE_TABLE.put("snow_block",                4.0);
        PRICE_TABLE.put("snowball",                  1.0);
        // Kılıçlar
        PRICE_TABLE.put("wooden_sword",             10.0);
        PRICE_TABLE.put("stone_sword",              15.0);
        PRICE_TABLE.put("iron_sword",               80.0);
        PRICE_TABLE.put("golden_sword",             30.0);
        PRICE_TABLE.put("diamond_sword",           450.0);
        PRICE_TABLE.put("netherite_sword",        1200.0);
        // Baltalar
        PRICE_TABLE.put("wooden_axe",              10.0);
        PRICE_TABLE.put("stone_axe",               15.0);
        PRICE_TABLE.put("iron_axe",                80.0);
        PRICE_TABLE.put("golden_axe",              30.0);
        PRICE_TABLE.put("diamond_axe",            450.0);
        PRICE_TABLE.put("netherite_axe",         1200.0);
        // Kazma
        PRICE_TABLE.put("wooden_pickaxe",          12.0);
        PRICE_TABLE.put("stone_pickaxe",           18.0);
        PRICE_TABLE.put("iron_pickaxe",           100.0);
        PRICE_TABLE.put("golden_pickaxe",          35.0);
        PRICE_TABLE.put("diamond_pickaxe",        400.0);
        PRICE_TABLE.put("netherite_pickaxe",     1200.0);
        // Kürek
        PRICE_TABLE.put("wooden_shovel",           10.0);
        PRICE_TABLE.put("stone_shovel",            15.0);
        PRICE_TABLE.put("iron_shovel",             70.0);
        PRICE_TABLE.put("golden_shovel",           25.0);
        PRICE_TABLE.put("diamond_shovel",         350.0);
        PRICE_TABLE.put("netherite_shovel",      1100.0);
        // Çapa
        PRICE_TABLE.put("wooden_hoe",              10.0);
        PRICE_TABLE.put("stone_hoe",               15.0);
        PRICE_TABLE.put("iron_hoe",                80.0);
        PRICE_TABLE.put("golden_hoe",              30.0);
        PRICE_TABLE.put("diamond_hoe",            400.0);
        PRICE_TABLE.put("netherite_hoe",         1200.0);
        // Zırh
        PRICE_TABLE.put("leather_helmet",          20.0);
        PRICE_TABLE.put("leather_chestplate",      40.0);
        PRICE_TABLE.put("leather_leggings",        35.0);
        PRICE_TABLE.put("leather_boots",           25.0);
        PRICE_TABLE.put("chainmail_helmet",        50.0);
        PRICE_TABLE.put("chainmail_chestplate",   100.0);
        PRICE_TABLE.put("chainmail_leggings",      80.0);
        PRICE_TABLE.put("chainmail_boots",         55.0);
        PRICE_TABLE.put("iron_helmet",             60.0);
        PRICE_TABLE.put("iron_chestplate",        120.0);
        PRICE_TABLE.put("iron_leggings",          100.0);
        PRICE_TABLE.put("iron_boots",              65.0);
        PRICE_TABLE.put("golden_helmet",           40.0);
        PRICE_TABLE.put("golden_chestplate",       80.0);
        PRICE_TABLE.put("golden_leggings",         70.0);
        PRICE_TABLE.put("golden_boots",            45.0);
        PRICE_TABLE.put("diamond_helmet",         500.0);
        PRICE_TABLE.put("diamond_chestplate",     900.0);
        PRICE_TABLE.put("diamond_leggings",       800.0);
        PRICE_TABLE.put("diamond_boots",          550.0);
        PRICE_TABLE.put("netherite_helmet",      1500.0);
        PRICE_TABLE.put("netherite_chestplate",  2500.0);
        PRICE_TABLE.put("netherite_leggings",    2200.0);
        PRICE_TABLE.put("netherite_boots",       1700.0);
        PRICE_TABLE.put("turtle_helmet",          400.0);
        PRICE_TABLE.put("shield",                  50.0);
        PRICE_TABLE.put("bow",                     60.0);
        PRICE_TABLE.put("crossbow",                90.0);
        PRICE_TABLE.put("trident",                600.0);
        // Yiyecek
        PRICE_TABLE.put("bread",                    6.0);
        PRICE_TABLE.put("apple",                    5.0);
        PRICE_TABLE.put("cooked_beef",             10.0);
        PRICE_TABLE.put("beef",                     7.0);
        PRICE_TABLE.put("cooked_porkchop",         10.0);
        PRICE_TABLE.put("porkchop",                 7.0);
        PRICE_TABLE.put("cooked_chicken",           8.0);
        PRICE_TABLE.put("chicken",                  5.0);
        PRICE_TABLE.put("cooked_salmon",            9.0);
        PRICE_TABLE.put("salmon",                   6.0);
        PRICE_TABLE.put("cooked_cod",               8.0);
        PRICE_TABLE.put("cod",                      5.0);
        PRICE_TABLE.put("cooked_mutton",            9.0);
        PRICE_TABLE.put("mutton",                   6.0);
        PRICE_TABLE.put("cooked_rabbit",            9.0);
        PRICE_TABLE.put("rabbit",                   6.0);
        PRICE_TABLE.put("cake",                    30.0);
        PRICE_TABLE.put("cookie",                   2.0);
        PRICE_TABLE.put("pumpkin_pie",             15.0);
        PRICE_TABLE.put("carrot",                   3.0);
        PRICE_TABLE.put("potato",                   2.0);
        PRICE_TABLE.put("baked_potato",             4.0);
        PRICE_TABLE.put("beetroot",                 3.0);
        PRICE_TABLE.put("melon_slice",              3.0);
        PRICE_TABLE.put("sweet_berries",            4.0);
        PRICE_TABLE.put("glow_berries",             6.0);
        PRICE_TABLE.put("suspicious_stew",         20.0);
        PRICE_TABLE.put("mushroom_stew",           12.0);
        PRICE_TABLE.put("rabbit_stew",             18.0);
        PRICE_TABLE.put("beetroot_soup",           12.0);
        PRICE_TABLE.put("milk_bucket",             10.0);
        PRICE_TABLE.put("honey_bottle",            15.0);
        // Malzemeler & araçlar
        PRICE_TABLE.put("stick",                    1.0);
        PRICE_TABLE.put("paper",                    2.0);
        PRICE_TABLE.put("book",                     6.0);
        PRICE_TABLE.put("name_tag",                80.0);
        PRICE_TABLE.put("saddle",                 150.0);
        PRICE_TABLE.put("lead",                    15.0);
        PRICE_TABLE.put("compass",                 30.0);
        PRICE_TABLE.put("clock",                   40.0);
        PRICE_TABLE.put("spyglass",                60.0);
        PRICE_TABLE.put("map",                     10.0);
        PRICE_TABLE.put("filled_map",              15.0);
        PRICE_TABLE.put("fire_charge",             15.0);
        PRICE_TABLE.put("tnt",                     25.0);
        PRICE_TABLE.put("flint_and_steel",         30.0);
        PRICE_TABLE.put("fishing_rod",             20.0);
        PRICE_TABLE.put("carrot_on_a_stick",       25.0);
        PRICE_TABLE.put("warped_fungus_on_a_stick",25.0);
        PRICE_TABLE.put("experience_bottle",       50.0);
        PRICE_TABLE.put("glass_bottle",             2.0);
        PRICE_TABLE.put("bucket",                  15.0);
        PRICE_TABLE.put("water_bucket",            15.0);
        PRICE_TABLE.put("lava_bucket",             20.0);
        PRICE_TABLE.put("powder_snow_bucket",      20.0);
        PRICE_TABLE.put("axolotl_bucket",          50.0);
        PRICE_TABLE.put("phantom_membrane",        25.0);
        PRICE_TABLE.put("string",                   3.0);
        PRICE_TABLE.put("spider_eye",               8.0);
        PRICE_TABLE.put("fermented_spider_eye",    20.0);
        PRICE_TABLE.put("gunpowder",               10.0);
        PRICE_TABLE.put("sugar",                    3.0);
        PRICE_TABLE.put("slime_ball",              12.0);
        PRICE_TABLE.put("slime_block",            108.0);  // 9 × 12
        PRICE_TABLE.put("honey_bottle",            15.0);
        PRICE_TABLE.put("honeycomb",               10.0);
        PRICE_TABLE.put("honeycomb_block",         50.0);
        PRICE_TABLE.put("leather",                 12.0);
        PRICE_TABLE.put("rabbit_hide",              5.0);
        PRICE_TABLE.put("rabbit_foot",             30.0);
        PRICE_TABLE.put("feather",                  3.0);
        PRICE_TABLE.put("arrow",                    2.0);
        PRICE_TABLE.put("spectral_arrow",           4.0);
        PRICE_TABLE.put("tipped_arrow",             8.0);
        PRICE_TABLE.put("ink_sac",                  4.0);
        PRICE_TABLE.put("glow_ink_sac",            20.0);
        PRICE_TABLE.put("prismarine_shard",        12.0);
        PRICE_TABLE.put("prismarine_crystals",     15.0);
        PRICE_TABLE.put("nautilus_shell",          60.0);
        PRICE_TABLE.put("heart_of_the_sea",       500.0);
        PRICE_TABLE.put("conduit",               1200.0);
        PRICE_TABLE.put("sponge",                  80.0);
        PRICE_TABLE.put("wet_sponge",              85.0);
        PRICE_TABLE.put("bone",                     4.0);
        PRICE_TABLE.put("bone_block",              30.0);
        PRICE_TABLE.put("bone_meal",                2.0);
        PRICE_TABLE.put("flint",                    3.0);
        PRICE_TABLE.put("clay",                     3.0);
        PRICE_TABLE.put("clay_ball",                1.0);
        PRICE_TABLE.put("brick",                    4.0);
        PRICE_TABLE.put("bricks",                  12.0);
        // İşlevsel bloklar
        PRICE_TABLE.put("crafting_table",          10.0);
        PRICE_TABLE.put("furnace",                 15.0);
        PRICE_TABLE.put("blast_furnace",           60.0);
        PRICE_TABLE.put("smoker",                  40.0);
        PRICE_TABLE.put("anvil",                  200.0);
        PRICE_TABLE.put("chipped_anvil",          150.0);
        PRICE_TABLE.put("damaged_anvil",          100.0);
        PRICE_TABLE.put("enchanting_table",       500.0);
        PRICE_TABLE.put("ender_chest",            400.0);
        PRICE_TABLE.put("chest",                   15.0);
        PRICE_TABLE.put("trapped_chest",           18.0);
        PRICE_TABLE.put("barrel",                  15.0);
        PRICE_TABLE.put("hopper",                  60.0);
        PRICE_TABLE.put("dispenser",               40.0);
        PRICE_TABLE.put("dropper",                 30.0);
        PRICE_TABLE.put("brewing_stand",           80.0);
        PRICE_TABLE.put("cartography_table",       20.0);
        PRICE_TABLE.put("smithing_table",          30.0);
        PRICE_TABLE.put("stonecutter",             20.0);
        PRICE_TABLE.put("loom",                    20.0);
        PRICE_TABLE.put("grindstone",              30.0);
        PRICE_TABLE.put("lectern",                 30.0);
        PRICE_TABLE.put("composter",               15.0);
        PRICE_TABLE.put("cauldron",                20.0);
        PRICE_TABLE.put("bell",                   200.0);
        PRICE_TABLE.put("jukebox",                100.0);
        PRICE_TABLE.put("note_block",              25.0);
        PRICE_TABLE.put("chiseled_bookshelf",      30.0);
        PRICE_TABLE.put("bookshelf",               20.0);
        PRICE_TABLE.put("flower_pot",               5.0);
        PRICE_TABLE.put("painting",                10.0);
        PRICE_TABLE.put("item_frame",              10.0);
        PRICE_TABLE.put("glow_item_frame",         30.0);
        PRICE_TABLE.put("armor_stand",             15.0);
        // İksirler (tür NBT'de, base fiyat)
        PRICE_TABLE.put("potion",                  15.0);
        PRICE_TABLE.put("splash_potion",           20.0);
        PRICE_TABLE.put("lingering_potion",        30.0);
    }

    // ── Büyü fiyat tablosu: enchantment path → level başına fiyat ────────
    private static final Map<String, Double> ENCHANT_PRICE = new HashMap<>();
    static {
        ENCHANT_PRICE.put("mending",              500.0);
        ENCHANT_PRICE.put("silk_touch",           400.0);
        ENCHANT_PRICE.put("fortune",              100.0);
        ENCHANT_PRICE.put("efficiency",            60.0);
        ENCHANT_PRICE.put("unbreaking",            70.0);
        ENCHANT_PRICE.put("sharpness",             70.0);
        ENCHANT_PRICE.put("smite",                 50.0);
        ENCHANT_PRICE.put("bane_of_arthropods",    50.0);
        ENCHANT_PRICE.put("knockback",             60.0);
        ENCHANT_PRICE.put("fire_aspect",           80.0);
        ENCHANT_PRICE.put("looting",              100.0);
        ENCHANT_PRICE.put("sweeping",              70.0);
        ENCHANT_PRICE.put("power",                 60.0);
        ENCHANT_PRICE.put("punch",                 70.0);
        ENCHANT_PRICE.put("flame",                 90.0);
        ENCHANT_PRICE.put("infinity",             350.0);
        ENCHANT_PRICE.put("luck_of_the_sea",       80.0);
        ENCHANT_PRICE.put("lure",                  70.0);
        ENCHANT_PRICE.put("channeling",           200.0);
        ENCHANT_PRICE.put("loyalty",               60.0);
        ENCHANT_PRICE.put("impaling",              60.0);
        ENCHANT_PRICE.put("riptide",               80.0);
        ENCHANT_PRICE.put("piercing",              60.0);
        ENCHANT_PRICE.put("multishot",            200.0);
        ENCHANT_PRICE.put("quick_charge",          60.0);
        ENCHANT_PRICE.put("protection",            70.0);
        ENCHANT_PRICE.put("fire_protection",       60.0);
        ENCHANT_PRICE.put("blast_protection",      60.0);
        ENCHANT_PRICE.put("projectile_protection", 60.0);
        ENCHANT_PRICE.put("feather_falling",       70.0);
        ENCHANT_PRICE.put("respiration",           80.0);
        ENCHANT_PRICE.put("aqua_affinity",        120.0);
        ENCHANT_PRICE.put("depth_strider",         90.0);
        ENCHANT_PRICE.put("frost_walker",          90.0);
        ENCHANT_PRICE.put("soul_speed",           100.0);
        ENCHANT_PRICE.put("swift_sneak",          100.0);
        ENCHANT_PRICE.put("thorns",                80.0);
        ENCHANT_PRICE.put("binding_curse",         30.0);
        ENCHANT_PRICE.put("vanishing_curse",       30.0);
    }

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
            if (path.equals("enchanted_book")) continue; // Büyüler ayrı eklenir
            // İksirler NBT bazlı ayrı eklenir (3 temel item yerine türler halinde)
            if (path.equals("potion") || path.equals("splash_potion") || path.equals("lingering_potion")) continue;

            String itemId = id.toString();
            String displayName = item.getDefaultStack().getName().getString();
            if (displayName == null || displayName.isBlank()) continue;

            String category = resolveCategory(item);
            double price = estimatePrice(item, path);
            int maxStock = estimateMaxStock(price);
            int stock = estimateInitialStock(maxStock);

            addItem(itemId, displayName, category, price, stock, maxStock);
        }

        seedPotions();
        seedEnchantedBooks();
    }

    public static void reseedMarket() {
        String sql = "DELETE FROM market_items";
        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        seedDefaultItemsIfEmpty();
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
        if (path.equals("potion") || path.equals("splash_potion") || path.equals("lingering_potion")) {
            return "Potions";
        }
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
        // Önce kesin fiyat tablosuna bak
        Double explicit = PRICE_TABLE.get(path);
        if (explicit != null) return explicit;

        // Formül tabanlı fallback (tabloda olmayan itemler için)
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

    private static int estimateMaxStock(double price) {
        if (price >= 5000) return 5;
        if (price >= 2000) return 10;
        if (price >= 1000) return 25;
        if (price >= 500)  return 50;
        if (price >= 200)  return 100;
        if (price >= 100)  return 200;
        if (price >= 50)   return 500;
        if (price >= 20)   return 1000;
        if (price >= 10)   return 2000;
        return 4000;
    }

    private static int estimateInitialStock(int maxStock) {
        return Math.max(1, maxStock / 2);
    }

    private static void addPotion(String typeId, String displayName, double price) {
        int maxStock = 100, stock = 50;
        addItem("minecraft:potion|"        + typeId, displayName,                "Potions", price,        stock, maxStock);
        addItem("minecraft:splash_potion|" + typeId, "Atılabilir " + displayName, "Potions", price * 1.3, stock, maxStock);
    }

    private static void seedPotions() {
        addPotion("night_vision",         "Gece Görüşü İksiri",                12.0);
        addPotion("long_night_vision",    "Uzun Gece Görüşü İksiri",           18.0);
        addPotion("invisibility",         "Görünmezlik İksiri",                20.0);
        addPotion("long_invisibility",    "Uzun Görünmezlik İksiri",           28.0);
        addPotion("leaping",              "Zıplama İksiri",                    12.0);
        addPotion("long_leaping",         "Uzun Zıplama İksiri",               18.0);
        addPotion("strong_leaping",       "Güçlü Zıplama İksiri",              22.0);
        addPotion("fire_resistance",      "Ateş Direnci İksiri",               15.0);
        addPotion("long_fire_resistance", "Uzun Ateş Direnci İksiri",          22.0);
        addPotion("swiftness",            "Sürat İksiri",                      12.0);
        addPotion("long_swiftness",       "Uzun Sürat İksiri",                 18.0);
        addPotion("strong_swiftness",     "Güçlü Sürat İksiri",                24.0);
        addPotion("slowness",             "Yavaşlama İksiri",                  10.0);
        addPotion("long_slowness",        "Uzun Yavaşlama İksiri",             15.0);
        addPotion("strong_slowness",      "Güçlü Yavaşlama İksiri",            18.0);
        addPotion("water_breathing",      "Su Soluma İksiri",                  18.0);
        addPotion("long_water_breathing", "Uzun Su Soluma İksiri",             25.0);
        addPotion("healing",              "Anında İyileşme İksiri",            18.0);
        addPotion("strong_healing",       "Güçlü Anında İyileşme İksiri",      30.0);
        addPotion("harming",              "Anında Hasar İksiri",               15.0);
        addPotion("strong_harming",       "Güçlü Anında Hasar İksiri",         22.0);
        addPotion("poison",               "Zehir İksiri",                      15.0);
        addPotion("long_poison",          "Uzun Zehir İksiri",                 20.0);
        addPotion("strong_poison",        "Güçlü Zehir İksiri",                25.0);
        addPotion("regeneration",         "Yenilenme İksiri",                  20.0);
        addPotion("long_regeneration",    "Uzun Yenilenme İksiri",             28.0);
        addPotion("strong_regeneration",  "Güçlü Yenilenme İksiri",            38.0);
        addPotion("strength",             "Güç İksiri",                        18.0);
        addPotion("long_strength",        "Uzun Güç İksiri",                   25.0);
        addPotion("strong_strength",      "Güçlü Güç İksiri",                  35.0);
        addPotion("weakness",             "Zayıflık İksiri",                   10.0);
        addPotion("long_weakness",        "Uzun Zayıflık İksiri",              15.0);
        addPotion("slow_falling",         "Yavaş Düşüş İksiri",               15.0);
        addPotion("long_slow_falling",    "Uzun Yavaş Düşüş İksiri",           22.0);
        addPotion("turtle_master",        "Kaplumbağa Ustası İksiri",          25.0);
        addPotion("long_turtle_master",   "Uzun Kaplumbağa Ustası İksiri",     35.0);
        addPotion("strong_turtle_master", "Güçlü Kaplumbağa Ustası İksiri",    45.0);
        addPotion("luck",                 "Şans İksiri",                       30.0);
    }

    private static void seedEnchantedBooks() {
        for (Enchantment enchant : Registries.ENCHANTMENT) {
            Identifier enchId = Registries.ENCHANTMENT.getId(enchant);
            if (enchId == null || !"minecraft".equals(enchId.getNamespace())) continue;

            String enchPath = enchId.getPath();
            double basePrice = ENCHANT_PRICE.getOrDefault(enchPath, 50.0);

            for (int level = 1; level <= enchant.getMaxLevel(); level++) {
                String itemId = "minecraft:enchanted_book|" + enchPath + "|" + level;
                String displayName = enchant.getName(level).getString() + " Büyü Kitabı";
                double price = basePrice * level;
                int maxStock = basePrice >= 300 ? 10 : (basePrice >= 100 ? 20 : 32);
                int stock = maxStock / 2;
                addItem(itemId, displayName, "Enchantments", price, stock, maxStock);
            }
        }
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