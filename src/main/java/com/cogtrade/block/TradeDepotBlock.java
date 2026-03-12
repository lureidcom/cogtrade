package com.cogtrade.block;

import com.cogtrade.CogTrade;
import com.cogtrade.market.PlayerShopManager;
import com.cogtrade.network.OpenDepotPacket;
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

public class TradeDepotBlock extends Block implements BlockEntityProvider {

    public static final TradeDepotBlock INSTANCE = new TradeDepotBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.OAK_TAN)
                    .requiresTool()
                    .strength(3.0f, 4.0f)
                    .sounds(BlockSoundGroup.WOOD)
    );

    public static final BlockItem BLOCK_ITEM =
            new BlockItem(INSTANCE, new Item.Settings());

    public static BlockEntityType<TradeDepotBlockEntity> TYPE;

    public TradeDepotBlock(Settings settings) {
        super(settings);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new TradeDepotBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        String uuid = sp.getUuid().toString();

        // Bu blok bu oyuncunun depotuysa aç
        if (!PlayerShopManager.hasDepot(uuid)) {
            sp.sendMessage(Text.literal("§cBu depot sana ait değil veya kayıtlı değil."));
            return ActionResult.FAIL;
        }

        // Depot bu blokta mı? (başkasının depotuna tıklamayı engelle)
        // Basit kontrol: bloğun konumunu kayıtlı depotla karşılaştır
        // (Gelişmiş: BlockEntity'de owner sakla)
        if (world.getBlockEntity(pos) instanceof TradeDepotBlockEntity depot) {
            if (!uuid.equals(depot.getOwnerUuid())) {
                sp.sendMessage(Text.literal("§cBu depot sana ait değil."));
                return ActionResult.FAIL;
            }
        }

        OpenDepotPacket.send(sp, uuid);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         LivingEntity placer, ItemStack itemStack) {
        if (world.isClient() || !(placer instanceof ServerPlayerEntity sp)) return;

        String uuid = sp.getUuid().toString();
        List<PlayerShopManager.ChestPos> pending = CogTrade.pendingChests.remove(uuid);

        if (pending == null || pending.isEmpty()) {
            // onPlaced() vanilla decrement'ten ÖNCE çağrılır.
            // Bir sonraki tick'te veririz → vanilla düşürür, sonra biz geri veririz → ne duplicate ne kayıp.
            world.breakBlock(pos, false);
            if (!sp.isCreative()) {
                sp.getServer().execute(() ->
                        sp.getInventory().offerOrDrop(new ItemStack(TradeDepotBlock.BLOCK_ITEM)));
            }
            sp.sendMessage(Text.literal("§cÖnce sandıklara sağ tıklayarak Trade Depot'a bağlanacak sandıkları seç!"));
            return;
        }

        // BlockEntity'e sahibi yaz
        if (world.getBlockEntity(pos) instanceof TradeDepotBlockEntity depot) {
            depot.setOwner(uuid, sp.getName().getString());
        }

        String worldId = world.getRegistryKey().getValue().toString();
        PlayerShopManager.registerDepot(uuid, sp.getName().getString(), worldId, pos, pending);
        sp.sendMessage(Text.literal("§a✓ Trade Depot yerleştirildi! " + pending.size()
                + " sandık bağlandı. Şimdi Trade Post koyabilirsin."));
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient() && world.getBlockEntity(pos) instanceof TradeDepotBlockEntity depot) {
            String ownerUuid = depot.getOwnerUuid();
            if (ownerUuid != null && ownerUuid.equals(player.getUuid().toString())) {
                PlayerShopManager.removeDepot(ownerUuid, pos);
                boolean hasMore = PlayerShopManager.hasDepot(ownerUuid);
                player.sendMessage(Text.literal(hasMore
                        ? "§eDepot kaldırıldı. Diğer depot(lar)ın hâlâ aktif."
                        : "§eDepot kaldırıldı. Tüm depotlar ve Trade Post bağlantısı temizlendi."));
            }
        }
        super.onBreak(world, pos, state, player);
    }
}