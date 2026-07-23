package com.osgworld.djbooth.client.audio;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.deck.DeckState;
import com.osgworld.djbooth.deck.PlayState;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.engines.ALEngine;
import org.watermedia.api.media.players.MediaPlayer;

/**
 * One deck's client-side audio player, backed by WaterMedia (FFmpeg + OpenAL). Mirrors the
 * server-authoritative {@link DeckState}: same track, same play/pause, same scrub position,
 * same tempo, with volume folded in by the mixer.
 *
 * <p>This class links WaterMedia directly, so it must only ever be loaded when WaterMedia is
 * present (guarded by {@link DeckAudioManager}). MRLs resolve asynchronously; the actual
 * player is built on the client thread once its MRL is ready.
 */
public class DeckAudio {
    private static final long DRIFT_MS = 250; // re-seek only when audio drifts past this

    private String url = "";
    private MRL mrl;
    private volatile boolean mrlReady;
    private MediaPlayer player;
    private double lastRate = 1.0;
    private int lastVolume = -1;

    /** Point this deck at a track URL, tearing down any previous player. */
    public void ensureUrl(String newUrl) {
        String u = newUrl == null ? "" : newUrl;
        if (u.equals(this.url)) {
            return;
        }
        release();
        this.url = u;
        if (u.isEmpty()) {
            return;
        }
        try {
            mrl = MediaAPI.getMRL(u);
            mrl.subscribe(m -> mrlReady = true);
        } catch (Throwable t) {
            DJBooth.LOGGER.warn("DeckAudio: failed to resolve MRL {}", u, t);
        }
    }

    /** Bring the audio in line with the deck state. Called every client tick on the main thread. */
    public void syncTo(DeckState state, long nowMs, float volume) {
        ensureUrl(state.getTrackUrl());
        if (url.isEmpty()) {
            return;
        }
        // Build the player once its MRL has resolved (must happen on the client thread).
        if (player == null) {
            if (!mrlReady || mrl == null || mrl.exception() != null) {
                return;
            }
            try {
                player = MediaAPI.createPlayer(mrl, () -> null, ALEngine::buildDefault);
                if (player == null) {
                    return;
                }
                player.startPaused();
            } catch (Throwable t) {
                DJBooth.LOGGER.warn("DeckAudio: failed to create player for {}", url, t);
                player = null;
                return;
            }
        }

        // Volume (0..100).
        int vol = Math.round(Math.max(0f, Math.min(1f, volume)) * 100);
        if (vol != lastVolume) {
            player.volume(vol);
            lastVolume = vol;
        }

        // Tempo (playback speed).
        double rate = state.getRate();
        if (Math.abs(rate - lastRate) > 1e-3) {
            player.speed((float) rate);
            lastRate = rate;
        }

        boolean shouldPlay = state.getPlayState() == PlayState.PLAY;
        long target = state.positionMsAt(nowMs);

        if (shouldPlay) {
            if (player.status() != MediaPlayer.Status.PLAYING) {
                player.resume();
            }
            // Correct only real drift so we don't stutter every tick.
            if (Math.abs(player.time() - target) > DRIFT_MS) {
                player.seek(target);
            }
        } else {
            if (player.status() == MediaPlayer.Status.PLAYING) {
                player.pause();
            }
            // Parked (cue/pause/stop): keep the playhead where the deck says it is.
            if (Math.abs(player.time() - target) > DRIFT_MS) {
                player.seek(target);
            }
        }
    }

    /** Silence and free this deck's player. */
    public void release() {
        if (player != null) {
            try {
                player.stop();
            } catch (Throwable ignored) {
            }
            player = null;
        }
        mrl = null;
        mrlReady = false;
        lastVolume = -1;
        lastRate = 1.0;
    }
}
