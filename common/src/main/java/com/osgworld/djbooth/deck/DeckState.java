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

    /** DIRECTION switch: forward, reverse, or reverse-with-slip. */
    public static final int DIR_FWD = 0;
    public static final int DIR_REV = 1;
    public static final int DIR_SLIP_REV = 2;
    private int direction = DIR_FWD;

    /** JOG MODE: CDJ (the jog only bends) or VINYL (touching it stops the platter). */
    public static final int JOG_CDJ = 0;
    public static final int JOG_VINYL = 1;
    private int jogMode = JOG_VINYL;

    private boolean slip = false;    // SLIP: the track keeps running underneath a loop or scratch
    private boolean quantize = true; // QUANTIZE: cues and loops snap to the beat grid
    private double bpm = 0;          // measured tempo of the loaded track, 0 = unknown

    // Where the track would be if nothing had been done to it: what SLIP returns to.
    private long slipOffsetMs = 0;
    private long slipStartMs = 0;
    private boolean slipping = false;

    /** Cue points saved with MEMORY, recalled with CUE/LOOP CALL. */
    public static final int MAX_MEMORY = 8;
    private final java.util.List<Long> memoryCues = new java.util.ArrayList<>();
    private int memoryIndex = 0;

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
    /** Quantised where QUANTIZE is lit, so a cue lands on the beat rather than between two. */
    public void setCueHere(long now) {
        this.cuePointMs = quantise(positionMsAt(now));
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

    /** Hot cues land on the beat too when QUANTIZE is lit. */
    public void setHotCue(int i, long now) {
        if (i >= 0 && i < HOT_CUES) hotCues[i] = quantise(positionMsAt(now));
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
        loopInMs = quantise(positionMsAt(now));
    }

    /** Set the loop-out point at the current position and start looping. */
    public void loopOut(long now) {
        loopOutMs = quantise(positionMsAt(now));
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

    public int getDirection() { return direction; }
    public void setDirection(int d) { this.direction = Math.floorMod(d, 3); }
    /** True while the DIRECTION switch is asking for backwards playback. */
    public boolean isReverse() { return direction != DIR_FWD; }

    public int getJogMode() { return jogMode; }
    public void setJogMode(int m) { this.jogMode = Math.floorMod(m, 2); }

    public boolean isSlip() { return slip; }
    public void setSlip(boolean v) { this.slip = v; }
    public boolean isQuantize() { return quantize; }
    public void setQuantize(boolean v) { this.quantize = v; }

    public double getBpm() { return bpm; }
    public void setBpm(double v) { this.bpm = v > 0 ? Math.max(40, Math.min(300, v)) : 0; }

    public java.util.List<Long> getMemoryCues() { return java.util.List.copyOf(memoryCues); }

    /** Save the current position as a memory cue, as the MEMORY button does. */
    public void memorise(long now) {
        long pos = positionMsAt(now);
        if (memoryCues.stream().anyMatch(m -> Math.abs(m - pos) < 100)) {
            return; // already have this spot
        }
        memoryCues.add(pos);
        java.util.Collections.sort(memoryCues);
        while (memoryCues.size() > MAX_MEMORY) {
            memoryCues.remove(memoryCues.size() - 1);
        }
    }

    /** Step through the saved cues and jump to one, as CUE/LOOP CALL does. */
    public void callMemory(int step, long now) {
        if (memoryCues.isEmpty()) {
            return;
        }
        memoryIndex = Math.floorMod(memoryIndex + step, memoryCues.size());
        long target = memoryCues.get(memoryIndex);
        cuePointMs = target;
        jumpTo(target, now);
    }

    /** Forget the memory cue nearest the playhead, as DELETE does. */
    public void deleteMemory(long now) {
        if (memoryCues.isEmpty()) {
            return;
        }
        long pos = positionMsAt(now);
        memoryCues.stream()
                .min(java.util.Comparator.comparingLong(m -> Math.abs(m - pos)))
                .ifPresent(memoryCues::remove);
        memoryIndex = 0;
    }

    public void loadMemoryCues(long[] cues) {
        memoryCues.clear();
        for (long c : cues) {
            if (c >= 0 && memoryCues.size() < MAX_MEMORY) {
                memoryCues.add(c);
            }
        }
        java.util.Collections.sort(memoryCues);
    }

    /** Round a position to the nearest beat when QUANTIZE is lit and the tempo is known. */
    public long quantise(long ms) {
        if (!quantize || bpm <= 0) {
            return ms;
        }
        double beatMs = 60000.0 / bpm;
        return Math.max(0, Math.round(Math.round(ms / beatMs) * beatMs));
    }

    /**
     * Start slipping: remember where the track would have carried on to.
     *
     * <p>SLIP is the one CDJ feature that needs a second clock. While it's engaged the audible
     * position can be looped, scratched or reversed, but this shadow timeline keeps advancing as
     * if nothing had happened, so letting go drops you back exactly where the track should be.
     */
    public void beginSlip(long now) {
        if (!slip || slipping) {
            return;
        }
        slipOffsetMs = positionMsAt(now);
        slipStartMs = now;
        slipping = true;
    }

    /** Stop slipping and jump to where the untouched track would be by now. */
    public void endSlip(long now) {
        if (!slipping) {
            return;
        }
        // Read the shadow timeline before clearing the flag: once slipping is false,
        // slipUnderlyingMs reports the audible position instead of the untouched one.
        long underlying = slipUnderlyingMs(now);
        slipping = false;
        jumpTo(underlying, now);
    }

    public boolean isSlipping() { return slipping; }

    /** Where the track would be if the slip hadn't happened. */
    public long slipUnderlyingMs(long now) {
        return slipping
                ? Math.max(0, slipOffsetMs + Math.round((now - slipStartMs) * rate))
                : positionMsAt(now);
    }

    /** TEMPO RESET: back to the track's own speed. */
    public void resetTempo(long now) {
        setRateAt(1.0, now);
    }

    /**
     * BEAT SYNC: match another deck's tempo.
     *
     * <p>Only possible when both decks have a tempo to compare, which here means both have been
     * tapped or analysed. Returns false when there's nothing to sync to, so the GUI can say so.
     */
    public boolean syncTo(double otherBpm, long now) {
        if (bpm <= 0 || otherBpm <= 0) {
            return false;
        }
        setRateAt(otherBpm / bpm, now);
        return true;
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
        // Reverse runs the clock the other way; the track still loops the same way round.
        double effectiveRate = isReverse() ? -rate : rate;
        long raw = (playState == PlayState.PLAY)
                ? offsetMs + Math.round((nowMs - startEpochMs) * effectiveRate)
                : offsetMs;
        if (loopOn && raw >= loopOutMs) {
            long span = loopOutMs - loopInMs;
            raw = loopInMs + Math.floorMod(raw - loopInMs, span);
        }
        return Math.max(0, raw);
    }
}
