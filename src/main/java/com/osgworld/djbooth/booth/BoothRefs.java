package com.osgworld.djbooth.booth;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.blockentity.MixerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The set of blocks that make up one booth: an optional mixer plus up to two decks.
 * Discovered by scanning around the block the player clicked, so decks and mixer
 * don't need to be manually linked — placing them near each other is enough.
 */
public record BoothRefs(@Nullable BlockPos mixer,
                        @Nullable BlockPos deckA,
                        @Nullable BlockPos deckB) {

    private static final int RADIUS_XZ = 6;
    private static final int RADIUS_Y = 2;

    /** Scan the neighbourhood of {@code anchor} and collect the booth blocks. */
    public static BoothRefs scan(Level level, BlockPos anchor) {
        BlockPos mixer = null;
        List<BlockPos> decks = new ArrayList<>();

        for (int dx = -RADIUS_XZ; dx <= RADIUS_XZ; dx++) {
            for (int dz = -RADIUS_XZ; dz <= RADIUS_XZ; dz++) {
                for (int dy = -RADIUS_Y; dy <= RADIUS_Y; dy++) {
                    BlockPos p = anchor.offset(dx, dy, dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    var be = level.getBlockEntity(p);
                    if (be instanceof MixerBlockEntity && mixer == null) {
                        mixer = p.immutable();
                    } else if (be instanceof CdjBlockEntity) {
                        decks.add(p.immutable());
                    }
                }
            }
        }

        // Stable A/B assignment: sort by position so both decks keep their side.
        decks.sort(Comparator.<BlockPos>comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getY));

        BlockPos a = decks.isEmpty() ? null : decks.get(0);
        BlockPos b = decks.size() < 2 ? null : decks.get(1);
        return new BoothRefs(mixer, a, b);
    }

    public void write(FriendlyByteBuf buf) {
        writeOpt(buf, mixer);
        writeOpt(buf, deckA);
        writeOpt(buf, deckB);
    }

    public static BoothRefs read(FriendlyByteBuf buf) {
        return new BoothRefs(readOpt(buf), readOpt(buf), readOpt(buf));
    }

    private static void writeOpt(FriendlyByteBuf buf, @Nullable BlockPos pos) {
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeBlockPos(pos);
        }
    }

    @Nullable
    private static BlockPos readOpt(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockPos() : null;
    }

    /** Any block usable as the interaction anchor (for range checks). */
    @Nullable
    public BlockPos anchor() {
        if (mixer != null) return mixer;
        if (deckA != null) return deckA;
        return deckB;
    }
}
