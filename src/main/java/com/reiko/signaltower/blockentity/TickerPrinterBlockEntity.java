package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.SignalTower;
import com.reiko.signaltower.block.TickerPrinterBlock;
import com.reiko.signaltower.item.PagerItem;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.FrequencyNames;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * A Ticker Tape Printer's block entity (v0.63.0). It holds one book: a Book and Quill it prints
 * into, or the signed logbook it became once full. Every {@link #CHECK_TICKS} it looks at the
 * messages the network remembers on its frequency that could be read where it stands
 * ({@link SignalNetwork#messagesAt}) and prints the ones newer than the last it printed: the day
 * and time, who sent it and the text ("(encrypted)" on someone else's encrypted frequency). It
 * reads the network's memory rather than being handed messages, so what came while it was unloaded,
 * or had no book, is printed later (as long as the network still remembers it: the last
 * {@link SignalNetwork#MAX_MESSAGES} per frequency). A message that doesn't fit in the book's
 * {@link #PAGES} pages signs it ("2026 log", by the Ticker Tape Printer) and waits for the next book.
 * A new printer, or one re-tuned, starts from the messages after that.
 */
public class TickerPrinterBlockEntity extends RadioBlockEntity {
    /** Pages in a logbook. */
    public static final int PAGES = 50;
    /** How many lines of a book's page an entry may use, and how many characters a line holds (kept short, as the font varies). */
    public static final int LINES_PER_PAGE = 13;
    public static final int CHARS_PER_LINE = 18;
    /** How often it looks for new messages, and how long its tape runs after printing. */
    public static final int CHECK_TICKS = 10;
    public static final int PRINT_TICKS = 24;
    private static final String KEY = "message." + SignalTower.MOD_ID + ".";

    private ItemStack book = ItemStack.EMPTY;
    /** The game time of the last message printed (or of when it started), and how many from that tick are printed. */
    private long seenTime = Long.MIN_VALUE;
    private int seenCount;
    private int printed;
    private String lastText = "";
    private String lastFrom = "";
    private long printingUntil;
    private final Slot slot = new Slot();

    public TickerPrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TICKER_PRINTER_BE.get(), pos, state);
    }

    // ---- The book ----

    public static boolean isBookAndQuill(ItemStack stack) {
        return stack.is(Items.WRITABLE_BOOK);
    }

    public ItemStack getBook() {
        return book;
    }

    public boolean hasBook() {
        return !book.isEmpty();
    }

    /** True once the book is a signed logbook (nothing more goes in it). */
    public boolean isFull() {
        return book.is(Items.WRITTEN_BOOK);
    }

    /** Pages printed in the book (0 with none). */
    public int pages() {
        if (book.is(Items.WRITABLE_BOOK)) {
            WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
            return content == null ? 0 : content.pages().size();
        }
        if (book.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
            return content == null ? 0 : content.pages().size();
        }
        return 0;
    }

    /** Puts one Book and Quill in from {@code stack} (not used up here). False if it has a book already, or that isn't one. */
    public boolean load(ItemStack stack) {
        if (hasBook() || !isBookAndQuill(stack)) {
            return false;
        }
        book = stack.copyWithCount(1);
        changed();
        return true;
    }

    /** Takes the book out (empty if none). */
    public ItemStack unload() {
        ItemStack out = book;
        book = ItemStack.EMPTY;
        if (!out.isEmpty()) {
            changed();
        }
        return out;
    }

    /** 0 with no book; 1 to 14 as the Book and Quill fills; 15 for a full logbook. */
    public int comparatorOutput() {
        if (!hasBook()) return 0;
        if (isFull()) return 15;
        return 1 + Math.min(13, pages() * 13 / PAGES);
    }

    public IItemHandler items() {
        return slot;
    }

    public int printedCount() {
        return printed;
    }

    public String lastText() {
        return lastText;
    }

    public String lastFrom() {
        return lastFrom;
    }

    /** Messages that reached it and aren't printed yet (waiting for a book, or a new one). */
    public int waiting(ServerLevel level) {
        return unprinted(level).size();
    }

    // ---- Printing ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, TickerPrinterBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long now = level.getGameTime();
        if (be.seenTime == Long.MIN_VALUE) {
            be.startFrom(serverLevel, now);
        }
        if (Math.floorMod(now + pos.hashCode(), CHECK_TICKS) == 0) {
            be.printNew(serverLevel);
        }
        if (state.getValue(TickerPrinterBlock.PRINTING) && now >= be.printingUntil) {
            level.setBlock(pos, state.setValue(TickerPrinterBlock.PRINTING, false), Block.UPDATE_CLIENTS);
        }
    }

    /** From now on: what's already been sent on its frequency isn't printed. */
    private void startFrom(ServerLevel level, long now) {
        seenTime = now;
        seenCount = 0;
        for (SignalNetwork.Message message : SignalNetwork.get(level).messagesAt(getFrequency(), worldPosition)) {
            if (message.gameTime() == now) seenCount++;
        }
        setChanged();
    }

    /** The messages that reached it after the last one it printed, oldest first. */
    private List<SignalNetwork.Message> unprinted(ServerLevel level) {
        List<SignalNetwork.Message> out = new ArrayList<>();
        int sameTick = 0;
        for (SignalNetwork.Message message : SignalNetwork.get(level).messagesAt(getFrequency(), worldPosition)) {
            if (message.gameTime() > seenTime) {
                out.add(message);
            } else if (message.gameTime() == seenTime && ++sameTick > seenCount) {
                out.add(message);
            }
        }
        return out;
    }

    /** Prints what's new into the book, as far as it goes; signs the book if a message doesn't fit. Returns how many it printed. */
    public int printNew(ServerLevel level) {
        if (seenTime == Long.MIN_VALUE || !book.is(Items.WRITABLE_BOOK)) {
            return 0;
        }
        List<SignalNetwork.Message> waiting = unprinted(level);
        if (waiting.isEmpty()) {
            return 0;
        }
        boolean mayRead = FrequencyClaims.mayHear(level, getFrequency(), getTunedBy());
        WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
        List<String> pages = new ArrayList<>();
        if (content != null) {
            content.pages().forEach(page -> pages.add(page.raw()));
        }
        int done = 0;
        boolean signed = false;
        for (SignalNetwork.Message message : waiting) {
            String text = mayRead ? message.text() : Component.translatable(KEY + "printer_encrypted").getString();
            String entry = entry(message.dayTime(), message.name(), text);
            if (!append(pages, entry)) {
                signed = true;
                break;
            }
            done++;
            printed++;
            lastText = text;
            lastFrom = message.name();
            if (message.gameTime() == seenTime) {
                seenCount++;
            } else {
                seenTime = message.gameTime();
                seenCount = 1;
            }
        }
        if (done > 0) {
            book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages.stream().map(Filterable::passThrough).toList()));
            printingUntil = level.getGameTime() + PRINT_TICKS;
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof TickerPrinterBlock && !state.getValue(TickerPrinterBlock.PRINTING)) {
                level.setBlock(worldPosition, state.setValue(TickerPrinterBlock.PRINTING, true), Block.UPDATE_CLIENTS);
            }
            level.playSound(null, worldPosition, com.reiko.signaltower.registry.ModSounds.PRINTER_PRINT.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        if (signed) {
            sign(level, pages);
            level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        changed();
        return done;
    }

    /** "Day 4, 08:30, Dev:" and the text under it. */
    public static String entry(long dayTime, String from, String text) {
        Object[] when = PagerItem.dayAndTime(dayTime);
        return Component.translatable(KEY + "printer_entry", when[0], when[1], from).getString() + "\n" + text;
    }

    /** Adds {@code entry} on the last page if it fits there, else on a new page; false if the book has no room. */
    static boolean append(List<String> pages, String entry) {
        int needed = lines(entry);
        if (!pages.isEmpty()) {
            String last = pages.get(pages.size() - 1);
            if (lines(last) + 1 + needed <= LINES_PER_PAGE) {
                pages.set(pages.size() - 1, last + "\n\n" + entry);
                return true;
            }
        }
        if (pages.size() >= PAGES) {
            return false;
        }
        pages.add(entry.length() > WritableBookContent.PAGE_EDIT_LENGTH ? entry.substring(0, WritableBookContent.PAGE_EDIT_LENGTH) : entry);
        return true;
    }

    /** About how many lines of a book's page {@code text} takes (each line wraps every {@link #CHARS_PER_LINE} characters). */
    static int lines(String text) {
        int lines = 0;
        for (String line : text.split("\n", -1)) {
            lines += Math.max(1, (line.length() + CHARS_PER_LINE - 1) / CHARS_PER_LINE);
        }
        return lines;
    }

    /** The Book and Quill becomes a signed logbook: "2026 log" (with the frequency's name), by the Ticker Tape Printer. */
    private void sign(ServerLevel level, List<String> pages) {
        String title = Component.translatable(KEY + "printer_title", FrequencyNames.label(level, getFrequency())).getString();
        if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
            title = title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH).strip();
        }
        String author = Component.translatable("block." + SignalTower.MOD_ID + ".ticker_printer").getString();
        ItemStack signed = book.transmuteCopy(Items.WRITTEN_BOOK, 1);
        signed.remove(DataComponents.WRITABLE_BOOK_CONTENT);
        signed.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0,
                pages.stream().map(page -> Filterable.<Component>passThrough(Component.literal(page))).toList(), true));
        book = signed;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.getBlock() instanceof TickerPrinterBlock && state.getValue(TickerPrinterBlock.BOOK) != hasBook()) {
                level.setBlock(worldPosition, state.setValue(TickerPrinterBlock.BOOK, hasBook()), Block.UPDATE_CLIENTS);
            }
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void onFrequencyChanged() {
        seenTime = Long.MIN_VALUE; // a new frequency: from its next message on
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hasBook()) tag.put("printer_book", book.save(registries));
        if (seenTime != Long.MIN_VALUE) {
            tag.putLong("printer_seen_time", seenTime);
            tag.putInt("printer_seen_count", seenCount);
        }
        tag.putInt("printer_printed", printed);
        if (!lastText.isEmpty()) {
            tag.putString("printer_last_text", lastText);
            tag.putString("printer_last_from", lastFrom);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        book = tag.contains("printer_book") ? ItemStack.parseOptional(registries, tag.getCompound("printer_book")) : ItemStack.EMPTY;
        seenTime = tag.contains("printer_seen_time") ? tag.getLong("printer_seen_time") : Long.MIN_VALUE;
        seenCount = tag.getInt("printer_seen_count");
        printed = tag.getInt("printer_printed");
        lastText = tag.getString("printer_last_text");
        lastFrom = tag.getString("printer_last_from");
    }

    /**
     * Its one slot, for hoppers and pipes: a Book and Quill goes in when it's empty, and only a
     * finished logbook comes out (a book being printed into stays).
     */
    private final class Slot implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            return index == 0 ? book : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
            if (index != 0 || stack.isEmpty() || hasBook() || !isBookAndQuill(stack)) {
                return stack;
            }
            if (!simulate) {
                load(stack);
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int index, int amount, boolean simulate) {
            if (index != 0 || amount <= 0 || !isFull()) {
                return ItemStack.EMPTY;
            }
            ItemStack out = book.copy();
            if (!simulate) {
                unload();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int index) {
            return 1;
        }

        @Override
        public boolean isItemValid(int index, ItemStack stack) {
            return index == 0 && isBookAndQuill(stack);
        }
    }
}
