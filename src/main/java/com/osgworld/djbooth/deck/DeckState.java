package com.osgworld.djbooth.deck;

/**
 * Pure transport model for one deck. No Minecraft dependencies so it can be unit tested.
 * All times are milliseconds. Position is derived from wall-clock time, an anchor point,
 * and the tempo rate, so any client can reconstruct the expected playback position.
 */
public final class DeckState {
    private PlayState playState = PlayState.STOP;
    private long startEpochMs = 0;   // wall-clock time when PLAY began
    private long offsetMs = 0;       // position at startEpochMs (or frozen position)
    private double rate = 1.0;       // tempo multiplier
    private long cuePointMs = 0;
    private boolean loopOn = false;
    private long loopInMs = 0;
    private long loopOutMs = 0;
    private String trackUrl = "";

    public PlayState getPlayState() { return playState; }
    public void setPlayState(PlayState s) { this.playState = s; }

    public double getRate() { return rate; }

    /** Raw rate setter (use for NBT load only; does not re-anchor position). */
    public void setRate(double r) { this.rate = Math.max(0.01, r); }

    /**
     * Change tempo without teleporting: freeze the current position at the old rate,
     * then apply the new rate anchored at {@code now}. Safe to call in any play state.
     */
    public void setRateAt(double r, long now) {
        long current = positionMsAt(now);
        this.offsetMs = current;
        this.startEpochMs = now;
        this.rate = Math.max(0.01, r);
    }

    public long getCuePointMs() { return cuePointMs; }
    public void setCuePointMs(long ms) { this.cuePointMs = Math.max(0, ms); }

    public long getStartEpochMs() { return startEpochMs; }
    public void setStartEpochMs(long ms) { this.startEpochMs = ms; }

    public long getOffsetMs() { return offsetMs; }
    public void setOffsetMs(long ms) { this.offsetMs = ms; }

    public boolean isLoopOn() { return loopOn; }
    public long getLoopInMs() { return loopInMs; }
    public long getLoopOutMs() { return loopOutMs; }

    public String getTrackUrl() { return trackUrl; }
    public void setTrackUrl(String u) { this.trackUrl = u == null ? "" : u; }

    public void setLoop(long inMs, long outMs, boolean on) {
        this.loopInMs = Math.min(inMs, outMs);
        this.loopOutMs = Math.max(inMs, outMs);
        this.loopOn = on && this.loopOutMs > this.loopInMs;
    }

    /** Transition to {@code next}, re-anchoring the position math to {@code now}. */
    public void press(PlayState next, long now) {
        long current = positionMsAt(now);
        switch (next) {
            case PLAY -> { offsetMs = current; startEpochMs = now; }
            case PAUSE -> offsetMs = current;
            case CUE -> offsetMs = cuePointMs;
            case STOP -> offsetMs = 0;
        }
        playState = next;
    }

    /** Expected playback position at wall-clock {@code nowMs}. */
    public long positionMsAt(long nowMs) {
        long raw = (playState == PlayState.PLAY)
                ? offsetMs + Math.round((nowMs - startEpochMs) * rate)
                : offsetMs;
        if (loopOn && raw >= loopOutMs) {
            long span = loopOutMs - loopInMs;
            raw = loopInMs + Math.floorMod(raw - loopInMs, span);
        }
        return Math.max(0, raw);
    }
}
