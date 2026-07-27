package com.osgworld.djbooth.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every packet the panel sends, encoded and decoded again.
 *
 * <p>A stream codec lists its fields in an order that has to match the record's constructor. Swap
 * two of the same type and nothing fails to compile, nothing throws, and nothing looks wrong in
 * single player, because the client that wrote the packet is the one that reads it back. It only
 * breaks on a real server, where a knob on deck A moves something on deck B. Round-tripping every
 * payload with values that are all different from each other is what catches that.
 */
class PayloadRoundTripTest {

    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.EMPTY;
    }

    /** Write a payload to a buffer and read it back, exactly as the network layer would. */
    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        codec.encode(buf, value);
        int written = buf.readableBytes();
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(),
                "decoding left " + buf.readableBytes() + " of " + written
                        + " bytes unread: the codec reads a different shape than it writes");
        return back;
    }

    /** Positions that exercise the sign and range of every component separately. */
    private static BlockPos[] positions() {
        return new BlockPos[] {
            new BlockPos(0, 0, 0),
            new BlockPos(1, 2, 3),
            new BlockPos(-1, -2, -3),
            new BlockPos(29999999, 319, -29999999),
            new BlockPos(-29999999, -64, 29999999),
        };
    }

    @Test
    void transportPayloadSurvivesEveryAction() {
        for (BlockPos pos : positions()) {
            for (int action = 0; action <= TransportPayload.KEY_SYNC; action++) {
                TransportPayload sent = new TransportPayload(pos, action);
                assertEquals(sent, roundTrip(TransportPayload.CODEC, sent),
                        "transport action " + action + " at " + pos);
            }
        }
    }

    @Test
    void mixerPayloadSurvivesEveryChannel() {
        // Distinct values per field, so a swapped pair cannot coincidentally match.
        for (BlockPos pos : positions()) {
            for (int channel = 0; channel <= MixerPayload.CROSSFADER_CURVE; channel++) {
                for (float value : new float[] {0f, 0.25f, 0.5f, 0.751f, 1f, -1f, 12345.5f}) {
                    MixerPayload sent = new MixerPayload(pos, channel, value);
                    MixerPayload back = roundTrip(MixerPayload.CODEC, sent);
                    assertEquals(sent, back, "mixer channel " + channel + " value " + value);
                }
            }
        }
    }

    @Test
    void hotCuePayloadKeepsIndexAndActionApart() {
        // Two ints in a row: the classic pair to get the wrong way round.
        for (int index = 0; index < 8; index++) {
            for (int action = 0; action < 3; action++) {
                HotCuePayload sent = new HotCuePayload(new BlockPos(7, 8, 9), index, action);
                HotCuePayload back = roundTrip(HotCuePayload.CODEC, sent);
                assertEquals(index, back.index(), "hot cue index came back as the action");
                assertEquals(action, back.action(), "hot cue action came back as the index");
            }
        }
    }

    @Test
    void loadTrackPayloadSurvivesRealUrls() {
        String[] urls = {
            "",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://example.com/path?a=1&b=2#frag",
            "canción de prueba con acentos y ñ",
            "日本語のトラック名",
            "a track name with  double  spaces and \ttab",
        };
        for (String url : urls) {
            LoadTrackPayload sent = new LoadTrackPayload(new BlockPos(1, 2, 3), url);
            assertEquals(sent, roundTrip(LoadTrackPayload.CODEC, sent), "url [" + url + "]");
        }
    }

    @Test
    void loadTrackPayloadHandlesAnAbsurdlyLongSearch() {
        // The URL box is free text: someone will paste a wall of it. A codec with a length cap
        // throws on encode, which on the client is a disconnect rather than a rejected search.
        String longText = "x".repeat(2000);
        LoadTrackPayload sent = new LoadTrackPayload(new BlockPos(0, 0, 0), longText);
        assertEquals(sent, roundTrip(LoadTrackPayload.CODEC, sent));
    }

    @Test
    void jogNudgePayloadKeepsRateAndPositionApart() {
        double[] rates = {0.0, 1.0, -1.0, 0.84, 1.16, 1e-6, -3.5};
        long[] targets = {0L, 1L, -1L, 1_000L, 7_200_000L, Long.MAX_VALUE / 4};
        for (double rate : rates) {
            for (long target : targets) {
                JogNudgePayload sent = new JogNudgePayload(new BlockPos(-4, 5, -6), rate, target);
                JogNudgePayload back = roundTrip(JogNudgePayload.CODEC, sent);
                assertEquals(rate, back.rate(), 0.0, "jog rate " + rate);
                assertEquals(target, back.scrubToMs(), "jog scrub target " + target);
            }
        }
    }

    @Test
    void deckStatePayloadSurvivesEveryField() {
        // Six fields, three of them numbers of the same broad shape. All distinct on purpose.
        long[] epochs = {0L, 1L, System.currentTimeMillis(), 4_102_444_800_000L};
        for (long epoch : epochs) {
            DeckStatePayload sent = new DeckStatePayload(
                    new BlockPos(11, 22, 33), 2, 1.0625, 123_456L, epoch, "https://x/y");
            DeckStatePayload back = roundTrip(DeckStatePayload.CODEC, sent);
            assertEquals(sent, back, "deck state at epoch " + epoch);
            assertEquals(123_456L, back.offsetMs(), "offset came back as the epoch");
            assertEquals(epoch, back.startEpochMs(), "epoch came back as the offset");
        }
    }

    @Test
    void deckStatePayloadSurvivesATrackStartedBeforeTheEpoch() {
        // Negative offsets are reachable: nudge a deck backwards past its own start.
        DeckStatePayload sent = new DeckStatePayload(
                BlockPos.ZERO, 1, 0.94, -5_000L, 1L, "");
        assertEquals(sent, roundTrip(DeckStatePayload.CODEC, sent));
    }

    @Test
    void deckBpmPayloadKeepsTheKeyIntact() {
        for (int root = 0; root < 12; root++) {
            for (boolean minor : new boolean[] {false, true}) {
                for (float bpm : new float[] {0f, 40f, 128f, 174.5f, 200f}) {
                    DeckBpmPayload sent =
                            new DeckBpmPayload(new BlockPos(3, 3, 3), bpm, root, minor);
                    DeckBpmPayload back = roundTrip(DeckBpmPayload.CODEC, sent);
                    assertEquals(sent, back, "bpm " + bpm + " root " + root + " minor " + minor);
                }
            }
        }
    }

    @Test
    void everyPayloadHasItsOwnIdentity() {
        // Two payloads sharing a type id would silently route to the wrong handler.
        var ids = new java.util.HashSet<String>();
        var types = new Object[] {
            TransportPayload.TYPE, MixerPayload.TYPE, HotCuePayload.TYPE,
            LoadTrackPayload.TYPE, JogNudgePayload.TYPE, DeckStatePayload.TYPE,
            DeckBpmPayload.TYPE,
        };
        for (Object t : types) {
            String id = t.toString();
            assertTrue(ids.add(id), "two payloads share the type id " + id);
        }
        assertEquals(types.length, ids.size());
    }
}
