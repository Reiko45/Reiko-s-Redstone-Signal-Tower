package com.reiko.signaltower.blockentity;

import com.reiko.signaltower.radio.Frequency;
import com.reiko.signaltower.radio.FrequencyClaims;
import com.reiko.signaltower.radio.SignalNetwork;
import com.reiko.signaltower.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A Radio Status Board's rows (v0.43.0): up to {@link #ROWS} frequencies (0: an empty row) and,
 * worked out on the server like a receiver's (when a frequency's revision moves, or once a
 * second), the strength each is heard at here and whether the board is deaf to it (someone else's
 * encrypted frequency, judged by whoever placed or last set the board). Those go to clients in
 * the block entity's update packet, for the renderer and Jade. The frequencies are saved and kept
 * on the item; the strengths aren't saved.
 */
public class StatusBoardBlockEntity extends BlockEntity {
    public static final int ROWS = 4;
    private static final String FREQUENCIES_TAG = "board_frequencies";
    private static final String STRENGTHS_TAG = "board_strengths";
    private static final String DEAF_TAG = "board_deaf";
    private static final String MESSAGES_TAG = "board_messages";
    private static final String TUNED_BY_TAG = "tuned_by";
    private static final int RECHECK_INTERVAL = 20;

    private final int[] frequencies = new int[ROWS];
    private final int[] strengths = new int[ROWS];
    private final boolean[] deaf = new boolean[ROWS];
    /** v0.51.0: each row's last telegraph message ("Dev: hello"), for {@link com.reiko.signaltower.radio.Telegraph#BOARD_TICKS}; "" for none. */
    private final String[] messages = {"", "", "", ""};
    private final long[] seenRevisions = new long[ROWS];
    private @Nullable UUID tunedBy;
    private int timer;
    private boolean dirtyRows = true;

    public StatusBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.STATUS_BOARD_BE.get(), pos, state);
        Arrays.fill(seenRevisions, -1);
    }

    /** Row {@code row}'s frequency, 0 if it's empty. */
    public int getFrequency(int row) {
        return frequencies[row];
    }

    /** The strength row {@code row}'s frequency is heard at here (0 if off, empty or deaf). */
    public int getStrength(int row) {
        return strengths[row];
    }

    public boolean isDeaf(int row) {
        return deaf[row];
    }

    /** Row {@code row}'s last telegraph message as shown ("Dev: hello"), or "" (v0.51.0). */
    public String getMessage(int row) {
        return messages[row];
    }

    public List<Integer> frequencyList() {
        List<Integer> list = new ArrayList<>(ROWS);
        for (int frequency : frequencies) {
            list.add(frequency);
        }
        return list;
    }

    public @Nullable UUID getTunedBy() {
        return tunedBy;
    }

    public void setTunedBy(@Nullable UUID tunedBy) {
        this.tunedBy = tunedBy;
        dirtyRows = true;
        setChanged();
    }

    /** Sets the rows (anything that isn't a frequency becomes an empty row; missing rows are empty). */
    public void setFrequencies(List<Integer> rows) {
        for (int i = 0; i < ROWS; i++) {
            int frequency = i < rows.size() ? rows.get(i) : 0;
            frequencies[i] = frequency >= Frequency.MIN && frequency <= Frequency.MAX ? frequency : 0;
        }
        Arrays.fill(seenRevisions, -1);
        dirtyRows = true;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StatusBoardBlockEntity board) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SignalNetwork network = SignalNetwork.get(serverLevel);
        boolean changed = board.dirtyRows || board.timer-- <= 0;
        for (int i = 0; i < ROWS; i++) {
            if (board.frequencies[i] > 0 && network.revision(board.frequencies[i]) != board.seenRevisions[i]) {
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        board.timer = RECHECK_INTERVAL;
        boolean updated = board.dirtyRows;
        board.dirtyRows = false;
        for (int i = 0; i < ROWS; i++) {
            int frequency = board.frequencies[i];
            int strength = 0;
            boolean deafHere = false;
            if (frequency > 0) {
                board.seenRevisions[i] = network.revision(frequency);
                deafHere = !FrequencyClaims.mayHear(serverLevel, frequency, board.tunedBy);
                strength = deafHere ? 0 : network.listen(serverLevel, frequency, pos).power();
            }
            String message = "";
            if (frequency > 0 && !deafHere) {
                SignalNetwork.Message last = network.lastMessageAt(frequency, pos,
                        serverLevel.getGameTime() - com.reiko.signaltower.radio.Telegraph.BOARD_TICKS);
                if (last != null) {
                    message = last.name() + ": " + last.text();
                }
            }
            if (strength != board.strengths[i] || deafHere != board.deaf[i] || !message.equals(board.messages[i])) {
                board.strengths[i] = strength;
                board.deaf[i] = deafHere;
                board.messages[i] = message;
                updated = true;
            }
        }
        if (updated) {
            serverLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- Saving and syncing ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray(FREQUENCIES_TAG, frequencies);
        if (tunedBy != null) {
            tag.putUUID(TUNED_BY_TAG, tunedBy);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] saved = tag.getIntArray(FREQUENCIES_TAG);
        for (int i = 0; i < ROWS; i++) {
            int frequency = i < saved.length ? saved[i] : 0;
            frequencies[i] = frequency >= Frequency.MIN && frequency <= Frequency.MAX ? frequency : 0;
        }
        tunedBy = tag.hasUUID(TUNED_BY_TAG) ? tag.getUUID(TUNED_BY_TAG) : null;
        // Only the update packet carries these (the client's copy).
        if (tag.contains(STRENGTHS_TAG)) {
            int[] heard = tag.getIntArray(STRENGTHS_TAG);
            byte[] deafRows = tag.getByteArray(DEAF_TAG);
            net.minecraft.nbt.ListTag shown = tag.getList(MESSAGES_TAG, net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < ROWS; i++) {
                strengths[i] = i < heard.length ? heard[i] : 0;
                deaf[i] = i < deafRows.length && deafRows[i] != 0;
                messages[i] = i < shown.size() ? shown.getString(i) : "";
            }
        }
        Arrays.fill(seenRevisions, -1);
        dirtyRows = true;
    }

    /** What clients get: the rows, the strengths, the deaf rows and the messages (not who set it). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray(FREQUENCIES_TAG, frequencies);
        tag.putIntArray(STRENGTHS_TAG, strengths);
        byte[] deafRows = new byte[ROWS];
        for (int i = 0; i < ROWS; i++) {
            deafRows[i] = (byte) (deaf[i] ? 1 : 0);
        }
        tag.putByteArray(DEAF_TAG, deafRows);
        net.minecraft.nbt.ListTag shown = new net.minecraft.nbt.ListTag();
        for (String message : messages) {
            shown.add(net.minecraft.nbt.StringTag.valueOf(message));
        }
        tag.put(MESSAGES_TAG, shown);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---- Item <-> block: it keeps its rows ----

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        List<Integer> rows = componentInput.get(ModRegistry.BOARD_FREQUENCIES.get());
        if (rows != null) {
            setFrequencies(rows);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModRegistry.BOARD_FREQUENCIES.get(), frequencyList());
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(FREQUENCIES_TAG);
    }
}
