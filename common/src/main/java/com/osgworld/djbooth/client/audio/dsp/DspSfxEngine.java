package com.osgworld.djbooth.client.audio.dsp;

import com.osgworld.djbooth.mixer.ChannelSettings;

import org.watermedia.api.media.engines.ALEngine;
import org.watermedia.api.media.engines.SFXEngine;

import java.nio.ByteBuffer;

/**
 * A {@link SFXEngine} that inserts a DJ EQ + colour filter + effects into WaterMedia's audio path.
 * It wraps a real {@link ALEngine} (OpenAL) and forwards every call to it, except
 * {@link #upload(ByteBuffer)}: there it intercepts the decoded PCM, runs it through a
 * {@link ChannelStrip}, then hands the processed PCM to the inner engine.
 *
 * <p>This is why real EQ/filter/FX is possible without any native DSP: {@code FFMediaPlayer} only
 * ever talks to the {@code SFXEngine} interface, so a custom engine can transform the audio in
 * between. Handles S16 and FLT sample formats (what FFmpeg negotiates in practice); any other format
 * is passed through untouched so nothing breaks.
 *
 * <p>Deliberately thin. WaterMedia is {@code compileOnly}, so this class cannot be loaded in a test;
 * the chain lives in {@link ChannelStrip} and the once-per-block bookkeeping in {@link BlockRelay},
 * both of which can.
 */
public final class DspSfxEngine extends SFXEngine {
    private final ALEngine inner = ALEngine.buildDefault();
    private final ChannelStrip strip = new ChannelStrip();
    private final BlockRelay relay = new BlockRelay();
    private boolean supported; // true when the negotiated format is one we filter

    /**
     * Set the MASTER TEMPO correction: the pitch ratio that cancels the tempo fader.
     *
     * <p>Separate from the mixer settings because it belongs to the deck, not the channel.
     */
    public void setKeyCorrection(double ratio) {
        strip.setKeyCorrection(ratio);
    }

    /** Point the whole DSP chain at the mixer's current settings. */
    public void setParams(ChannelSettings cfg) {
        strip.setSettings(cfg);
    }

    /** Loudest sample of the last block on each side, 0..1, for drawing the channel meters. */
    public float peakLeft() { return strip.peakLeft(); }
    public float peakRight() { return strip.peakRight(); }

    @Override
    public long[] supportedChannels() {
        return inner.supportedChannels();
    }

    @Override
    public SampleType[] supportedTypes() {
        return inner.supportedTypes();
    }

    @Override
    public boolean setAudioFormat(SampleType type, int channels, int sampleRate) {
        // Take the flag down first: upload() reads it to decide whether the chain exists, and a
        // format change must never leave it true while the chain is half-built.
        this.supported = false;
        this.sampleType = type;
        this.channels = channels;
        this.sampleRate = sampleRate;
        relay.reset();
        boolean canFilter = (type == SampleType.S16 || type == SampleType.FLT) && channels > 0;
        if (canFilter) {
            strip.setup(sampleRate, channels, type == SampleType.S16);
            this.supported = strip.isReady();
        }
        return inner.setAudioFormat(type, channels, sampleRate);
    }

    @Override
    public boolean upload(ByteBuffer buf) {
        if (!supported || buf == null) {
            return inner.upload(buf);
        }
        return relay.relay(buf, strip, inner::upload);
    }

    @Override
    protected int genSource() {
        return inner.source(); // never called by FFMediaPlayer; the inner engine owns the AL source
    }

    @Override public void pause() { inner.pause(); }
    @Override public void play() { inner.play(); }
    @Override public void speed(float speed) { inner.speed(speed); }
    @Override public void volume(float volume) { inner.volume(volume); }
    @Override public void flush() {
        if (supported) {
            strip.reset();
            relay.reset();
        }
        inner.flush();
    }
    @Override public long pendingMs() { return inner.pendingMs(); }
    @Override public long playbackMs() { return inner.playbackMs(); }
    @Override public void release() { inner.release(); }
}
