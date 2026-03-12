package com.cogtrade.block;

import com.cogtrade.config.CogTradeConfig;
import com.cogtrade.market.MarketManager;
import com.cogtrade.network.OpenMarketPacket;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MarketBlock extends Block implements BlockEntityProvider {

    /**
     * Ayarlar:
     *  - COPPER ses grubu  → endüstriyel / Create mod teması
     *  - strength(5, 6)    → iron_block ile aynı sertlik
     *  - requiresTool()    → kazma gerektirir
     *  - deprecated copy() kullanılmıyor
     */
    public static final MarketBlock INSTANCE = new MarketBlock(
            AbstractBlock.Settings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .requiresTool()
                    .strength(5.0f, 6.0f)
                    .sounds(BlockSoundGroup.COPPER)
    );

    public static final BlockItem BLOCK_ITEM =
            new BlockItem(INSTANCE, new Item.Settings());

    public MarketBlock(Settings settings) {
        super(settings);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MarketBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient()) {
            ServerPlayerEntity sp = (ServerPlayerEntity) player;
            var items = MarketManager.getAllItems();
            if (items.isEmpty()) {
                sp.sendMessage(Text.literal("§eMarket şu an boş."));
            } else {
                OpenMarketPacket.send(sp, items, true, CogTradeConfig.get().sellPriceMultiplier);
            }
        }
        return ActionResult.SUCCESS;
    }
}
