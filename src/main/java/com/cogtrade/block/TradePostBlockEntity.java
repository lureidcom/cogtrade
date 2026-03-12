package com.cogtrade.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class TradePostBlockEntity extends BlockEntity {

    public static BlockEntityType<TradePostBlockEntity> TYPE;

    private String ownerUuid = "";
    private String ownerName = "";

    public TradePostBlockEntity(BlockPos pos, BlockState state) {
        super(TradePostBlock.TYPE, pos, state);
    }

    public String getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }

    public void setOwner(String uuid, String name) {
        this.ownerUuid = uuid;
        this.ownerName = name;
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        ownerUuid = nbt.getString("ownerUuid");
        ownerName = nbt.getString("ownerName");
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("ownerUuid", ownerUuid != null ? ownerUuid : "");
        nbt.putString("ownerName", ownerName != null ? ownerName : "");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
