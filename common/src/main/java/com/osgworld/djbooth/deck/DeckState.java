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

    public static final int HOT_CUES = 4;
    private final long[] hotCues = { -1, -1, -1, -1 }; // ms, -1 = unset

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

    /** Set the cue point at the current position. */
    public void setCueHere(long now) {
        this.cuePointMs = positionMsAt(now);
    }

    /**
     * CUE button. Playing -&gt; jump back to the cue point and pause there. Parked -&gt; play a
     * preview from the cue point (a second press, which is now "playing", parks it back).
     */
    public void cue(long now) {
        if (playState == PlayState.PLAY) {
            offsetMs = cuePointMs;
            playState = PlayState.CUE;
        } else {
            offsetMs = cuePointMs;
            startEpochMs = now;
            playState = PlayState.PLAY;
        }
    }

    /** Jump to {@code posMs} without changing the play state (needle / beat jump). */
    public void jumpTo(long posMs, long now) {
        offsetMs = Math.max(0, posMs);
        startEpochMs = now;
    }

    // --- Hot cues ---

    public long getHotCue(int i) { return (i >= 0 && i < HOT_CUES) ? hotCues[i] : -1; }
    public boolean hasHotCue(int i) { return getHotCue(i) >= 0; }

    public void setHotCue(int i, long now) {
        if (i >= 0 && i < HOT_CUES) hotCues[i] = positionMsAt(now);
    }

    public void clearHotCue(int i) {
        if (i >= 0 && i < HOT_CUES) hotCues[i] = -1;
    }

    /** Jump onto a hot cue, keeping the current play state. */
    public void jumpHotCue(int i, long now) {
        if (hasHotCue(i)) jumpTo(hotCues[i], now);
    }

    /** Raw hot-cue setter for NBT load. */
    public void loadHotCue(int i, long ms) {
        if (i >= 0 && i < HOT_CUES) hotCues[i] = ms;
    }

    // --- Loop control ---

    /** Arm the loop-in point at the current position. */
    public void loopIn(long now) {
        loopInMs = positionMsAt(now);
    }

    /** Set the loop-out point at the current position and start looping. */
    public void loopOut(long now) {
        loopOutMs = positionMsAt(now);
        loopOn = loopOutMs > loopInMs;
    }

    /** Leave the loop but remember its bounds for a reloop. */
    public void loopExit() {
        loopOn = false;
    }

    /** Re-enter the stored loop and jump to its start. */
    public void reloop(long now) {
        if (loopOutMs > loopInMs) {
            loopOn = true;
            jumpTo(loopInMs, now);
        }
    }

    /** Halve / double the loop length (keeps the in point). */
    public void resizeLoop(double factor) {
        long span = loopOutMs - loopInMs;
        if (span <= 0) return;
        long next = Math.max(50, Math.round(span * factor));
        loopOutMs = loopInMs + next;
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
