package com.reiko.signaltower.compat.jade;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.RadioBlock;
import com.reiko.signaltower.block.RadioMastBlock;
import com.reiko.signaltower.block.RadioReceiverBlock;
import com.reiko.signaltower.compat.RadioInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.JadeIds;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade integration: looking at a transmitter, receiver, repeater or mast shows its frequency,
 * signal and reach (see {@link RadioInfo}). Jade finds this class by its annotation and is the only
 * thing that loads it, so the mod doesn't need Jade installed. Covers every {@link RadioBlock}
 * (transmitter, both receivers, repeater) and {@link RadioMastBlock}. Can be switched off in Jade's
 * plugin settings.
 */
@WailaPlugin(SignalTower.MOD_ID)
public final class SignalTowerJadePlugin implements IWailaPlugin {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SignalTower.MOD_ID, "radio");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(RadioProvider.INSTANCE, RadioBlock.class);
        registration.registerBlockDataProvider(RadioProvider.INSTANCE, RadioMastBlock.class);
        registration.registerBlockDataProvider(RadioProvider.INSTANCE, com.reiko.signaltower.block.StatusBoardBlock.class);
        registration.registerBlockDataProvider(RadioProvider.INSTANCE, com.reiko.signaltower.block.AviationBeaconBlock.class);
        registration.registerBlockDataProvider(RadioProvider.INSTANCE, com.reiko.signaltower.block.RadioBatteryBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(RadioProvider.INSTANCE, RadioBlock.class);
        registration.registerBlockComponent(RadioProvider.INSTANCE, RadioMastBlock.class);
        registration.registerBlockComponent(RadioProvider.INSTANCE, com.reiko.signaltower.block.StatusBoardBlock.class);
        registration.registerBlockComponent(RadioProvider.INSTANCE, com.reiko.signaltower.block.AviationBeaconBlock.class);
        registration.registerBlockComponent(RadioProvider.INSTANCE, com.reiko.signaltower.block.RadioBatteryBlock.class);
    }

    /** Sends the server's data along, and draws the lines on the client. */
    enum RadioProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        /** After Jade's own body lines (priority 0), so its generic redstone line can be replaced. */
        @Override
        public int getDefaultPriority() {
            return 100;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getLevel() instanceof ServerLevel level) {
                CompoundTag info = RadioInfo.read(level, accessor.getPosition(), accessor.getPlayer());
                if (info != null) {
                    data.put(RadioInfo.KEY, info);
                }
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag server = accessor.getServerData();
            CompoundTag info = server.contains(RadioInfo.KEY) ? server.getCompound(RadioInfo.KEY) : null;
            if (accessor.getBlock() instanceof RadioReceiverBlock) {
                // Jade's redstone line would say "Power: 15" too; "Output: 15" / "Output: off" replaces it.
                tooltip.remove(JadeIds.MC_REDSTONE);
            }
            RadioInfo.lines(accessor.getBlockState(), info).forEach(tooltip::add);
        }
    }
}
