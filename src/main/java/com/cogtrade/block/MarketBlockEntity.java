package com.cogtrade.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;

public class MarketBlockEntity extends BlockEntity {

    /** CogTrade.java'da Registry.register() ile atanır. */
    public static BlockEntityType<MarketBlockEntity> TYPE;

    public MarketBlockEntity(BlockPos pos, BlockState state) {
        super(TYPE, pos, state);
    }

    /** Sabit sunucu sahibi. */
    public String getOwner() {
        return "Sunucu";
    }
}
