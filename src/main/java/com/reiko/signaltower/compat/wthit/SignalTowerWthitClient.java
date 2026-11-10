package com.reiko.signaltower.compat.wthit;

import com.reiko.signaltower.compat.RadioInfo;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import net.minecraft.nbt.CompoundTag;

/**
 * v0.63.0: WTHIT's client side: the lines under the block's name, from the server's data (or the
 * block state alone, without it), worded as in the Jade tooltip ({@link RadioInfo#lines}).
 */
public final class SignalTowerWthitClient implements IWailaClientPlugin {
    /** After WTHIT's own body lines. */
    static final int PRIORITY = 1100;

    @Override
    public void register(IClientRegistrar registrar) {
        for (Class<?> block : WthitBlocks.COVERED) {
            registrar.body(Lines.INSTANCE, block, PRIORITY);
            WthitBlocks.registered("client", block);
        }
        registrar.body(DroneLines.INSTANCE, com.reiko.signaltower.entity.RadioDroneEntity.class, PRIORITY); // v0.67.0
        WthitBlocks.registered("client", com.reiko.signaltower.entity.RadioDroneEntity.class);
    }

    enum DroneLines implements mcp.mobius.waila.api.IEntityComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, mcp.mobius.waila.api.IEntityAccessor accessor, IPluginConfig config) {
            if (accessor.getEntity() instanceof com.reiko.signaltower.entity.RadioDroneEntity drone) {
                CompoundTag server = accessor.getData().raw();
                RadioInfo.droneLines(drone, server.contains(RadioInfo.KEY) ? server.getCompound(RadioInfo.KEY) : null).forEach(tooltip::addLine);
            }
        }
    }

    enum Lines implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            CompoundTag server = accessor.getData().raw();
            CompoundTag info = server.contains(RadioInfo.KEY) ? server.getCompound(RadioInfo.KEY) : null;
            RadioInfo.lines(accessor.getBlockState(), info).forEach(tooltip::addLine);
        }
    }
}
