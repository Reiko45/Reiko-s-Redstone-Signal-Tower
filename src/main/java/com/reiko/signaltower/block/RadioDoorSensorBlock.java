package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.DoorSensorBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Door Sensor (v0.68.0): a button-sized contact that sticks to a wall, floor or ceiling like a
 * Compact Radio Transmitter and sends full strength on its frequency, over its short reach, while a
 * door, trapdoor, fence gate, barrel or chest touching it is open (see {@link DoorSensorBlockEntity}).
 * It ignores the redstone around it. A Security Console zone on its frequency trips when it opens.
 */
public class RadioDoorSensorBlock extends CompactRadioTransmitterBlock {
    public static final MapCodec<RadioDoorSensorBlock> CODEC = simpleCodec(RadioDoorSensorBlock::new);

    public RadioDoorSensorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(POWERED, false); // redstone around it doesn't matter
    }

    /** "Door Sensor | Frequency 4673 | Oak Door: open | Reach 16 blocks". */
    @Override
    public Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        DoorSensorBlockEntity sensor = level.getBlockEntity(pos) instanceof DoorSensorBlockEntity found ? found : null;
        int frequency = sensor != null ? sensor.getFrequency() : 0;
        String key = "message." + SignalTower.MOD_ID + ".";
        Component watching;
        if (sensor == null || !DoorSensorBlockEntity.watchable(level, sensor.watched())) {
            watching = Component.translatable(key + "door_sensor_nothing");
        } else {
            watching = Component.translatable(key + (sensor.isOpen() ? "door_sensor_open" : "door_sensor_closed"),
                    level.getBlockState(sensor.watched()).getBlock().getName());
        }
        return Component.translatable(key + "door_sensor_status", FrequencyNames.label(level, frequency), watching,
                RadioTransmitterBlockEntity.reachOf(level, pos, state));
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoorSensorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.DOOR_SENSOR_BE.get(), DoorSensorBlockEntity::sensorTick);
    }
}
