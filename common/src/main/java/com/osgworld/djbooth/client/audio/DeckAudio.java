package com.osgworld.djbooth.client.audio;

import com.osgworld.djbooth.DJBooth;
import com.osgworld.djbooth.client.audio.dsp.DspSfxEngine;
import com.osgworld.djbooth.deck.DeckState;
import com.osgworld.djbooth.deck.PlayState;
import org.watermedia.api.media.MRL;
import org.watermedia.api.media.MediaAPI;
import org.watermedia.api.media.engines.ALEngine;
import org.watermedia.api.media.players.FFMediaPlayer;
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
    // We re-seek only on a genuine discontinuity (cue jump, loop, track load), never to shave
    // small drift, because seeking a live FFmpeg stream every tick stutters badly.
    private static final long JUMP_MS = 400;   // server-position jump that counts as a seek
    private static final long BEND_HOLD_MS = 160; // how long a jog nudge keeps bending the pitch

    private String url = "";
    private MRL mrl;
    private volatile boolean mrlReady;
    private MediaPlayer player;
    private DspSfxEngine dsp; // non-null when the FFmpeg path built our EQ engine
    private double lastSpeed = -1.0;
    private int lastVolume = -1;

    // The mixer's settings for this channel, refreshed every client tick.
    private volatile com.osgworld.djbooth.mixer.ChannelSettings settings =
            com.osgworld.djbooth.mixer.ChannelSettings.flat();

    /** Point the deck's DSP at the mixer's current settings for its channel. */
    public void setDsp(com.osgworld.djbooth.mixer.ChannelSettings cfg) {
        this.settings = cfg;
    }

    /** Loudest sample of the last audio block, 0..1, for the panel meters. 0 without a DSP. */
    public float peakLevel() {
        DspSfxEngine d = dsp;
        return d == null ? 0f : Math.max(d.peakLeft(), d.peakRight());
    }

    // Discontinuity tracking: where the server clock said we were last tick.
    private boolean freshPlayer = true;
    private long lastTarget = 0;
    private long lastNow = 0;

    // Jog pitch-bend (client-local, no seek): a transient speed multiplier.
    private volatile double bend = 1.0;
    private volatile long bendUntil = 0;

    /** Nudge the pitch for a moment, like riding a CDJ jog. factor &gt;1 speeds up, &lt;1 slows. */
    public void nudgeBend(double factor) {
        this.bend = Math.max(0.05, Math.min(3.0, factor));
        this.bendUntil = System.currentTimeMillis() + BEND_HOLD_MS;
    }

    public boolean isBending() {
        return System.currentTimeMillis() < bendUntil;
    }

    /** Track length in ms once known, else 0. */
    public long durationMs() {
        try {
            return player != null ? Math.max(0, player.duration()) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

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
                // Decode audio with FFmpeg directly (gfx=null skips video), on the source that
                // carries the audio. This avoids WaterMedia routing a "video" URL (e.g. YouTube)
                // to the VLC/texture player, which we don't want for an audio-only deck.
                int idx = audioSourceIndex(mrl);
                dsp = new DspSfxEngine();
                player = new FFMediaPlayer(mrl, idx, null, dsp);
                player.startPaused();
            } catch (Throwable t) {
                DJBooth.LOGGER.warn("DeckAudio: FFmpeg player failed for {}, trying default", url, t);
                dsp = null; // fallback player owns its own engine; no EQ on this path
                try {
                    player = MediaAPI.createPlayer(mrl, () -> null, ALEngine::buildDefault);
                    if (player == null) {
                        return;
                    }
                    player.startPaused();
                } catch (Throwable t2) {
                    DJBooth.LOGGER.warn("DeckAudio: failed to create player for {}", url, t2);
                    player = null;
                    return;
                }
            }
        }

        // Only the FFmpeg path carries our DSP engine.
        if (dsp != null) {
            dsp.setParams(settings);
        }

        // Volume (0..100).
        int vol = Math.round(Math.max(0f, Math.min(1f, volume)) * 100);
        if (vol != lastVolume) {
            player.volume(vol);
            lastVolume = vol;
        }

        boolean shouldPlay = state.getPlayState() == PlayState.PLAY;
        double rate = state.getRate();
        double effSpeed = rate * (isBending() ? bend : 1.0);
        if (Math.abs(effSpeed - lastSpeed) > 1e-3) {
            player.speed((float) effSpeed);
            lastSpeed = effSpeed;
        }

        long target = state.positionMsAt(nowMs);

        // Seek only when the server position jumps (cue/loop/track/first play), not to trim
        // drift. When bending, the audio deliberately runs off the server clock; leave it.
        if (freshPlayer) {
            player.seek(target);
            freshPlayer = false;
        } else if (!isBending()) {
            long expected = lastTarget + (shouldPlay ? Math.round((nowMs - lastNow) * rate) : 0);
            if (Math.abs(target - expected) > JUMP_MS) {
                player.seek(target);
            }
        }
        lastTarget = target;
        lastNow = nowMs;

        if (shouldPlay) {
            if (player.status() != MediaPlayer.Status.PLAYING) {
                player.resume();
            }
        } else if (player.status() == MediaPlayer.Status.PLAYING) {
            player.pause();
        }
    }

    /** Index of the source carrying audio: prefer a pure audio source, else a video one (FFmpeg
     *  pulls the audio track out of it), else the first source. */
    private static int audioSourceIndex(MRL mrl) {
        java.util.List<MRL.Source> sources = mrl.sources();
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).isAudio()) {
                return i;
            }
        }
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).isVideo()) {
                return i;
            }
        }
        return 0;
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
        dsp = null;
        mrl = null;
        mrlReady = false;
        lastVolume = -1;
        lastSpeed = -1.0;
        freshPlayer = true;
        bend = 1.0;
        bendUntil = 0;
    }
}
