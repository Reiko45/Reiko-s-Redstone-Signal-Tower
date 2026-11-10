package com.reiko.signaltower.block;

import com.mojang.serialization.MapCodec;
import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.blockentity.TickerPrinterBlockEntity;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Ticker Tape Printer (v0.63.0): a stock-ticker printer under a glass dome. Tune it like a
 * receiver and give it a Book and Quill (by hand or hopper): every message that reaches its
 * frequency is printed into the book ({@link TickerPrinterBlockEntity}), and a full book is signed
 * as a logbook. An empty hand takes the book out; sneak for its status. A comparator reads how full
 * the book is (15: a logbook to take out).
 */
public class TickerPrinterBlock extends RadioBlock {
    public static final MapCodec<TickerPrinterBlock> CODEC = simpleCodec(TickerPrinterBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** A book is in it (shown lying on its base). */
    public static final BooleanProperty BOOK = BooleanProperty.create("book");
    /** It printed a moment ago (the tape runs out further). */
    public static final BooleanProperty PRINTING = BooleanProperty.create("printing");
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(4, 2, 4, 12, 11, 12));
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    public TickerPrinterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BOOK, false).setValue(PRINTING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BOOK, PRINTING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Its tape comes out towards whoever placed it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!TickerPrinterBlockEntity.isBookAndQuill(stack) || !(level.getBlockEntity(pos) instanceof TickerPrinterBlockEntity printer)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!printer.load(stack)) {
            player.displayClientMessage(Component.translatable(KEY + "printer_has_book").withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable(KEY + "printer_loaded",
                FrequencyNames.label((ServerLevel) level, printer.getFrequency())).withStyle(ChatFormatting.GREEN), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof TickerPrinterBlockEntity printer) || !printer.hasBook()) {
            return super.useWithoutItem(state, level, pos, player, hitResult); // the status readout
        }
        if (!level.isClientSide) {
            boolean full = printer.isFull();
            int pages = printer.pages();
            ItemStack book = printer.unload();
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage((full ? Component.translatable(KEY + "printer_took_logbook")
                    : Component.translatable(KEY + (pages == 1 ? "printer_took_one" : "printer_took"), pages)).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected Component describe(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof TickerPrinterBlockEntity printer)) {
            return Component.empty();
        }
        return Component.translatable(KEY + "printer_status", FrequencyNames.label(level, printer.getFrequency()), bookState(printer),
                printer.printedCount());
    }

    /** "Book: 12 / 50 pages", "No book" or "Logbook full: take it out". */
    public static Component bookState(TickerPrinterBlockEntity printer) {
        return !printer.hasBook() ? Component.translatable(KEY + "printer_no_book")
                : printer.isFull() ? Component.translatable(KEY + "printer_full")
                : Component.translatable(KEY + "printer_pages", printer.pages(), TickerPrinterBlockEntity.PAGES);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TickerPrinterBlockEntity printer && printer.hasBook()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), printer.unload());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TickerPrinterBlockEntity printer ? printer.comparatorOutput() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TickerPrinterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.TICKER_PRINTER_BE.get(), TickerPrinterBlockEntity::serverTick);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip." + SignalTower.MOD_ID + ".ticker_printer_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
