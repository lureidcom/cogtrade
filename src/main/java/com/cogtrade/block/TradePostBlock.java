package com.cogtrade.block;

import com.cogtrade.config.CogTradeConfig;
import com.cogtrade.market.PlayerShopManager;
import com.cogtrade.network.OpenPlayerShopPacket;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class TradePostBlock extends Block implements BlockEntityProvider {

    public static final TradePostBlock INSTANCE = new TradePostBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .requiresTool()
                    .strength(3.5f, 5.0f)
                    .sounds(BlockSoundGroup.METAL)
    );

    public static final BlockItem BLOCK_ITEM =
            new BlockItem(INSTANCE, new Item.Settings());

    public static BlockEntityType<TradePostBlockEntity> TYPE;

    public TradePostBlock(Settings settings) {
        super(settings);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TradePostBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         LivingEntity placer, ItemStack itemStack) {
        if (world.isClient() || !(placer instanceof ServerPlayerEntity sp)) return;

        String uuid = sp.getUuid().toString();

        if (!PlayerShopManager.hasDepot(uuid)) {
            world.breakBlock(pos, false);
            if (!sp.isCreative()) {
                sp.getServer().execute(() ->
                        sp.getInventory().offerOrDrop(new ItemStack(TradePostBlock.BLOCK_ITEM)));
            }
            sp.sendMessage(Text.literal("§cÖnce bir Trade Depot yerleştirmelisin!"));
            return;
        }

        if (world.getBlockEntity(pos) instanceof TradePostBlockEntity post) {
            post.setOwner(uuid, sp.getName().getString());
        }

        String worldId = world.getRegistryKey().getValue().toString();
        PlayerShopManager.registerPost(uuid, worldId, pos);
        sp.sendMessage(Text.literal("§a✓ Trade Post yerleştirildi! Pazar açık: §e"
                + sp.getName().getString() + "'in Pazarı"));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        ServerPlayerEntity sp = (ServerPlayerEntity) player;

        if (!(world.getBlockEntity(pos) instanceof TradePostBlockEntity post)) {
            return ActionResult.FAIL;
        }

        String ownerUuid = post.getOwnerUuid();
        if (ownerUuid == null || ownerUuid.isBlank()) {
            sp.sendMessage(Text.literal("§cBu Trade Post henüz yapılandırılmamış."));
            return ActionResult.FAIL;
        }

        List<PlayerShopManager.ShopListing> listings = PlayerShopManager.getListings(ownerUuid);
        if (listings.isEmpty()) {
            sp.sendMessage(Text.literal("§e" + post.getOwnerName() + "'in pazarı şu an boş."));
            return ActionResult.SUCCESS;
        }

        // Stok kontrolü: sandıkları tara, stoksuz olanları filtrele
        Map<String, Integer> available = new java.util.HashMap<>();
        if (sp.getServerWorld() != null) {
            available = PlayerShopManager.scanChestContents(ownerUuid, sp.getServerWorld());
            final Map<String, Integer> avail = available;
            listings = listings.stream()
                    .filter(l -> avail.getOrDefault(l.itemId(), 0) > 0)
                    .toList();
        }

        OpenPlayerShopPacket.send(sp, ownerUuid, post.getOwnerName(), listings, available,
                CogTradeConfig.get().sellPriceMultiplier);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof TradePostBlockEntity post) {
            String ownerUuid = post.getOwnerUuid();
            if (ownerUuid != null && !ownerUuid.isBlank()) {
                PlayerShopManager.removePost(ownerUuid);
            }
        }
        super.onBreak(world, pos, state, player);
    }
}