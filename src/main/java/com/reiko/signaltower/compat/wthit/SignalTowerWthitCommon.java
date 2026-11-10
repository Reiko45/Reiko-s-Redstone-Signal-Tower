package com.reiko.signaltower.compat.wthit;

import com.reiko.signaltower.compat.RadioInfo;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * v0.63.0: WTHIT's server side. WTHIT finds this class through {@code waila_plugins.json} and is
 * the only thing that loads it, so the mod doesn't need WTHIT. For the blocks in
 * {@link WthitBlocks#COVERED} it sends the same data as the Jade plugin ({@link RadioInfo#read}) to
 * whoever looks at one. (WTHIT only asks for blocks with a block entity: a mast shows what its
 * block state says.)
 */
public final class SignalTowerWthitCommon implements IWailaCommonPlugin {
    @Override
    public void register(ICommonRegistrar registrar) {
        for (Class<?> block : WthitBlocks.COVERED) {
            registrar.blockData(Data.INSTANCE, block);
            WthitBlocks.registered("common", block);
        }
        registrar.entityData(DroneData.INSTANCE, com.reiko.signaltower.entity.RadioDroneEntity.class); // v0.67.0
        WthitBlocks.registered("common", com.reiko.signaltower.entity.RadioDroneEntity.class);
    }

    enum DroneData implements IDataProvider<com.reiko.signaltower.entity.RadioDroneEntity> {
        INSTANCE;

        @Override
        public void appendData(IDataWriter data, IServerAccessor<com.reiko.signaltower.entity.RadioDroneEntity> accessor, IPluginConfig config) {
            data.raw().put(RadioInfo.KEY, RadioInfo.readDrone(accessor.getLevel(), accessor.getTarget(), accessor.getPlayer()));
        }
    }

    enum Data implements IDataProvider<BlockEntity> {
        INSTANCE;

        @Override
        public void appendData(IDataWriter data, IServerAccessor<BlockEntity> accessor, IPluginConfig config) {
            BlockEntity target = accessor.getTarget();
            if (target == null) {
                return;
            }
            CompoundTag info = RadioInfo.read(accessor.getLevel(), target.getBlockPos(), accessor.getPlayer());
            if (info != null) {
                data.raw().put(RadioInfo.KEY, info);
            }
        }
    }
}
