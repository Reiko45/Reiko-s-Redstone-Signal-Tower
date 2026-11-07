package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.MotionSensorBlockEntity;
import com.reiko.signaltower.blockentity.RadioTransmitterBlockEntity;
import com.reiko.signaltower.network.OpenSensorScreenPayload;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Radio Motion Sensor (v0.39.0): a button-sized eye on a wall, floor or ceiling that broadcasts
 * full strength on its frequency while it sees a player nearby, over a compact transmitter's short
 * fixed reach. It ignores the redstone around it. Its owner (whoever placed it) right-clicks it to
 * set its range and whether it reacts to everyone or only strangers; anyone else just gets a
 * readout. The detection is in {@link MotionSensorBlockEntity}.
 */
public class RadioMotionSensorBlock extends CompactRadioTransmitterBlock {
    public static final MapCodec<RadioMotionSensorBlock> CODEC = simpleCodec(RadioMotionSensorBlock::new);

    public RadioMotionSensorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(POWERED, false); // it starts off whatever redstone is around
    }

    /** Whoever places it owns it (and is its tuner, which {@link RadioBlock} sets). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor) {
            sensor.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
    }

    // ---- Using it (an empty hand; with an item in hand the item is used, as on other radio blocks) ----

    /** Its owner (or an operator) gets its settings screen; anyone else the one-line readout. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof MotionSensorBlockEntity sensor) {
            Component quiet = RadioBlock.quietNotice(serverLevel, pos);
            if (sensor.mayConfigure(player) && player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, OpenSensorScreenPayload.of(serverLevel, sensor, quiet != null));
            } else {
                player.displayClientMessage(quiet != null ? quiet : describe(serverLevel, pos, state), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** What anyone sees: whose it is and its frequency (its range and mode are the owner's business). */
    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        MotionSensorBlockEntity sensor = level.getBlockEntity(pos) instanceof MotionSensorBlockEntity found ? found : null;
        int frequency = sensor != null ? sensor.getFrequency() : 0;
        return Component.translatable("message." + SignalTower.MOD_ID + ".sensor_status",
                FrequencyNames.label(level, frequency),
                sensor != null ? sensor.ownerLabel() : Component.literal("?"),
                RadioTransmitterBlockEntity.reachOf(level, pos, state));
    }

    // ---- Redstone: it doesn't read any, so dust doesn't point at it ----

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    // ---- Block entity ----

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MotionSensorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModRegistry.MOTION_SENSOR_BE.get(), MotionSensorBlockEntity::sensorTick);
    }

    // ---- Visuals ----

    /** A purple glint from its eye now and then while it sees someone. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED) && random.nextInt(4) == 0) {
            Direction out = pointing(state);
            double x = pos.getX() + 0.5 - out.getStepX() * 0.3;
            double y = pos.getY() + 0.5 - out.getStepY() * 0.3;
            double z = pos.getZ() + 0.5 - out.getStepZ() * 0.3;
            level.addParticle(new DustParticleOptions(new org.joml.Vector3f(0.75F, 0.3F, 1.0F), 0.8F), x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
