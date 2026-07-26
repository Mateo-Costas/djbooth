package com.osgworld.djbooth.client.audio;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.blockentity.MixerBlockEntity;
import com.osgworld.djbooth.booth.BoothRefs;
import com.osgworld.djbooth.mixer.ChannelSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import dev.architectury.platform.Platform;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Drives per-deck {@link DeckAudio} from the loaded decks each client tick: matches every
 * deck's audio to its synced state, folds in the mixer's volume for that channel, and fades
 * with distance so a booth is heard from nearby but not across the map.
 *
 * <p>Everything here is gated on WaterMedia actually being installed. Until the gate passes we
 * never touch {@link DeckAudio}, so its WaterMedia class links are never resolved on clients
 * without the mod.
 */
public final class DeckAudioManager {
    private DeckAudioManager() {}

    private static final double RANGE = 24.0; // blocks; audio fully faded beyond this
    // Beyond RANGE we keep the player alive but silent out to KEEPALIVE, so walking away and back
    // doesn't tear down + re-stream the track (which restarts it from 0). Only release past this.
    private static final double KEEPALIVE = 80.0;
    // Stand this close and you are "at the booth": you hear the booth monitor feed, cue included.
    private static final double BOOTH_RANGE = 4.0;
    private static final Map<BlockPos, DeckAudio> AUDIO = new HashMap<>();
    private static Boolean waterMediaPresent;

    /** Search YouTube for {@code query} and hand the top hit's URL back on the client thread.
     *  Runs off-thread (network). No-op without WaterMedia. */
    public static void searchTop(String query, java.util.function.Consumer<String> onUrl) {
        if (!available() || query == null || query.isBlank()) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                var yt = new org.watermedia.api.platform.web.YouTubePlatform();
                var results = yt.search(query, 1);
                if (results != null && !results.isEmpty() && results.get(0).url() != null) {
                    String url = results.get(0).url().toString();
                    Minecraft.getInstance().execute(() -> onUrl.accept(url));
                }
            } catch (Throwable e) {
                com.osgworld.djbooth.DJBooth.LOGGER.warn("YouTube search failed for '{}'", query, e);
            }
        }, "djbooth-yt-search");
        t.setDaemon(true);
        t.start();
    }

    /** Track length in ms for a deck's audio, or 0 if unknown / no WaterMedia. */
    public static long durationMs(BlockPos pos) {
        if (!available()) {
            return 0;
        }
        DeckAudio audio = AUDIO.get(pos);
        return audio != null ? audio.durationMs() : 0;
    }

    /** Ride the pitch on one deck's audio (jog bend). No-op without WaterMedia or an active deck. */
    public static void nudgeBend(BlockPos pos, double factor) {
        if (!available()) {
            return;
        }
        DeckAudio audio = AUDIO.get(pos);
        if (audio != null) {
            audio.nudgeBend(factor);
        }
    }

    private static boolean available() {
        if (waterMediaPresent == null) {
            waterMediaPresent = Platform.isModLoaded("watermedia");
        }
        return waterMediaPresent;
    }

    public static void onClientTick() {
        if (!available()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!AUDIO.isEmpty()) {
                releaseAll();
            }
            return;
        }

        long now = mc.level.getGameTime() * 50L;

        java.util.Set<BlockPos> live = new java.util.HashSet<>();
        for (CdjBlockEntity deck : CdjBlockEntity.CLIENT_DECKS) {
            if (deck.isRemoved() || deck.getLevel() != mc.level) {
                continue;
            }
            BlockPos pos = deck.getBlockPos();
            double dist = Math.sqrt(mc.player.distanceToSqr(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));

            DeckAudio audio = AUDIO.get(pos);
            if (dist > KEEPALIVE) {
                if (audio != null) {
                    audio.release();
                    AUDIO.remove(pos);
                }
                continue;
            }
            live.add(pos);
            if (audio == null) {
                audio = new DeckAudio();
                AUDIO.put(pos, audio);
            }

            // Audible with distance falloff inside RANGE; silent (but still streaming + synced)
            // between RANGE and KEEPALIVE so returning resumes in place instead of restarting.
            float attenuation = dist < RANGE ? (float) (1.0 - dist / RANGE) : 0f;
            // Scan for the booth once and reuse it: the scan walks several hundred block
            // positions, and doing it separately for the volume and the DSP doubled that for
            // every deck, every tick.
            BoothRefs refs = BoothRefs.scan(mc.level, pos);
            MixerBlockEntity mixer =
                    refs.mixer() != null
                            && mc.level.getBlockEntity(refs.mixer()) instanceof MixerBlockEntity m
                            ? m : null;
            boolean isA = pos.equals(refs.deckA());
            audio.setDsp(mixer != null ? mixer.settingsForDeck(isA) : ChannelSettings.flat());
            audio.syncTo(deck.state(), now, mixerVolume(mixer, isA, dist) * attenuation);
        }

        // Release any audio whose deck is no longer in range/loaded.
        Iterator<Map.Entry<BlockPos, DeckAudio>> it = AUDIO.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, DeckAudio> e = it.next();
            if (!live.contains(e.getKey())) {
                e.getValue().release();
                it.remove();
            }
        }
    }

    /**
     * Volume for this deck's channel.
     *
     * <p>Out on the floor that is the mixer's fader/crossfader/master chain. Stand at the booth —
     * within {@link #BOOTH_RANGE} of the deck — and you get the BOOTH MONITOR feed instead, which
     * is what lets CUE preview a channel for the person working the booth without the whole
     * server hearing it.
     */
    private static float mixerVolume(MixerBlockEntity mixer, boolean deckA, double listenerDist) {
        if (mixer == null) {
            return 1.0f;
        }
        return listenerDist <= BOOTH_RANGE
                ? mixer.boothVolumeForDeck(deckA)
                : mixer.volumeForDeck(deckA);
    }

    /** Loudest sample of a deck's last audio block, 0..1, for the panel meters. */
    public static float peakLevel(BlockPos pos) {
        if (!available()) {
            return 0f;
        }
        DeckAudio audio = AUDIO.get(pos);
        return audio != null ? audio.peakLevel() : 0f;
    }

    private static void releaseAll() {
        AUDIO.values().forEach(DeckAudio::release);
        AUDIO.clear();
    }
}
