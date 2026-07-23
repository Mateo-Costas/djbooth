package com.osgworld.djbooth.client.audio;

import com.osgworld.djbooth.blockentity.CdjBlockEntity;
import com.osgworld.djbooth.blockentity.MixerBlockEntity;
import com.osgworld.djbooth.booth.BoothRefs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

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
    private static final Map<BlockPos, DeckAudio> AUDIO = new HashMap<>();
    private static Boolean waterMediaPresent;

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
            waterMediaPresent = ModList.get().isLoaded("watermedia");
        }
        return waterMediaPresent;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
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
            if (dist > RANGE) {
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

            float attenuation = (float) (1.0 - dist / RANGE);
            float mixerVol = mixerVolumeFor(mc.level, deck);
            audio.syncTo(deck.state(), now, mixerVol * attenuation);
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

    /** Volume for this deck's channel: the mixer's fader/crossfader/master, or full if no mixer. */
    private static float mixerVolumeFor(Level level, CdjBlockEntity deck) {
        BoothRefs refs = BoothRefs.scan(level, deck.getBlockPos());
        if (refs.mixer() == null) {
            return 1.0f;
        }
        if (!(level.getBlockEntity(refs.mixer()) instanceof MixerBlockEntity mixer)) {
            return 1.0f;
        }
        boolean isA = deck.getBlockPos().equals(refs.deckA());
        return mixer.volumeForDeck(isA);
    }

    private static void releaseAll() {
        AUDIO.values().forEach(DeckAudio::release);
        AUDIO.clear();
    }
}
