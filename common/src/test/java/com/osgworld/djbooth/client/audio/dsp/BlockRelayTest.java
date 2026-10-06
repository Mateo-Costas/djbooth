package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.BeatFxTypes;
import com.osgworld.djbooth.mixer.ChannelSettings;
import com.osgworld.djbooth.mixer.ColorFxModes;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The output engine turns a block down whenever it has no free buffer, and the player then offers
 * the identical block again. These tests pin down that the DSP chain still runs once per block.
 *
 * <p>The chain is stateful, so "ran twice" is not a harmless no-op: it advances every filter, delay
 * line and the limiter, and in the original design it also fed its own output back in as input.
 */
class BlockRelayTest {

    private static final int FS = 48000;
    private static final int FRAMES = 1024;

    /** Settings that make every stage do something, so a repeated pass cannot hide. */
    private static ChannelSettings busy() {
        return new ChannelSettings(0.15f, 0.5f, 1.0f, 0.3f, 0.4f, 0.8f, false,
                ColorFxModes.FILTER, 0.5f, BeatFxTypes.DELAY, false, 0.5f, 0.5f,
                BeatFxTypes.BANDS_ALL, 0.5f);
    }

    private static ChannelStrip strip() {
        ChannelStrip s = new ChannelStrip();
        s.setup(FS, 2, false);
        s.setSettings(busy());
        return s;
    }

    /** Block {@code n} of a continuous two-tone stereo signal, as raw interleaved float bytes. */
    private static byte[] block(int n) {
        ByteBuffer b = ByteBuffer.allocate(FRAMES * 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < FRAMES; i++) {
            long t = (long) n * FRAMES + i;
            b.putFloat((float) (0.5 * Math.sin(2 * Math.PI * 220.0 * t / FS)));
            b.putFloat((float) (0.5 * Math.sin(2 * Math.PI * 3300.0 * t / FS)));
        }
        return b.array();
    }

    /** What the player hands over: a direct buffer over the frame's own bytes. */
    private static ByteBuffer asPlayerWouldGiveIt(byte[] bytes) {
        ByteBuffer b = ByteBuffer.allocateDirect(bytes.length);
        b.put(bytes);
        b.clear();
        return b;
    }

    private static byte[] bytesOf(ByteBuffer b) {
        byte[] out = new byte[b.remaining()];
        b.duplicate().get(out);
        return out;
    }

    /** A sink that refuses the first {@code refusals} offers of every block, then takes it. */
    private static final class FussySink implements BlockRelay.Sink {
        final int refusals;
        int seenThisBlock;
        int offers;
        final List<byte[]> taken = new ArrayList<>();

        FussySink(int refusals) {
            this.refusals = refusals;
        }

        @Override
        public boolean accept(ByteBuffer block) {
            offers++;
            if (seenThisBlock++ < refusals) {
                return false;
            }
            seenThisBlock = 0;
            taken.add(bytesOf(block));
            return true;
        }
    }

    /** Push blocks 0..count-1 through, re-offering each one until the sink takes it. */
    private static FussySink run(int refusals, int count) {
        ChannelStrip strip = strip();
        BlockRelay relay = new BlockRelay();
        FussySink sink = new FussySink(refusals);
        for (int n = 0; n < count; n++) {
            byte[] raw = block(n);
            while (!relay.relay(asPlayerWouldGiveIt(raw), strip, sink)) {
                // the player offers the same block again
            }
        }
        return sink;
    }

    @Test
    void aRefusedBlockIsProcessedOnceNoMatterHowManyTimesItIsOffered() {
        FussySink straight = run(0, 4);
        FussySink fussy = run(7, 4);

        assertEquals(4, fussy.taken.size());
        assertEquals(4 * 8, fussy.offers, "each block should have been offered 8 times");
        for (int n = 0; n < 4; n++) {
            assertArrayEquals(straight.taken.get(n), fussy.taken.get(n),
                    "block " + n + " came out different once the sink had refused it");
        }
    }

    @Test
    void theBlockHandedInIsNeverModified() {
        ChannelStrip strip = strip();
        BlockRelay relay = new BlockRelay();
        byte[] raw = block(0);
        ByteBuffer in = asPlayerWouldGiveIt(raw);

        relay.relay(in, strip, b -> false);
        relay.relay(in, strip, b -> true);

        assertArrayEquals(raw, bytesOf(in), "the player's own frame must stay exactly as decoded");
        assertEquals(0, in.position(), "the caller's buffer position must not move");
    }

    @Test
    void theProcessedAudioReallyDiffersFromTheInput() {
        // Guards the other tests against passing because the chain did nothing at all.
        FussySink out = run(0, 1);
        assertFalse(java.util.Arrays.equals(block(0), out.taken.get(0)));
    }

    @Test
    void aDifferentBlockAfterARefusalIsNotMistakenForTheOldOne() {
        ChannelStrip strip = strip();
        BlockRelay relay = new BlockRelay();
        List<byte[]> delivered = new ArrayList<>();

        assertFalse(relay.relay(asPlayerWouldGiveIt(block(0)), strip, b -> false));
        assertTrue(relay.relay(asPlayerWouldGiveIt(block(1)), strip, b -> {
            delivered.add(bytesOf(b));
            return true;
        }));

        // Reference: the chain run over block 0 and then block 1, which is what actually happened.
        ChannelStrip ref = strip();
        BlockRelay refRelay = new BlockRelay();
        FussySink refSink = new FussySink(0);
        refRelay.relay(asPlayerWouldGiveIt(block(0)), ref, b -> true);
        refRelay.relay(asPlayerWouldGiveIt(block(1)), ref, refSink);

        assertArrayEquals(refSink.taken.get(0), delivered.get(0));
    }

    @Test
    void resettingForgetsTheHeldBlock() {
        ChannelStrip strip = strip();
        BlockRelay relay = new BlockRelay();
        byte[] raw = block(0);

        List<byte[]> first = new ArrayList<>();
        relay.relay(asPlayerWouldGiveIt(raw), strip, b -> {
            first.add(bytesOf(b));
            return false;
        });
        relay.reset(); // a seek: the held block belongs to audio that no longer exists

        List<byte[]> second = new ArrayList<>();
        relay.relay(asPlayerWouldGiveIt(raw), strip, b -> {
            second.add(bytesOf(b));
            return true;
        });

        // Run through the chain a second time, it comes out differently (the echo has state now).
        assertNotEquals(java.util.Arrays.hashCode(first.get(0)),
                java.util.Arrays.hashCode(second.get(0)));
    }
}
