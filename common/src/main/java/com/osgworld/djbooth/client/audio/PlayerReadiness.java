package com.osgworld.djbooth.client.audio;

/**
 * Where a WaterMedia player is in opening its media, read from the name of its status so this can be
 * tested without WaterMedia (it is compile-only here).
 *
 * <p>The player opens the media on its own thread. WaterMedia waits for that with a wait on the
 * clock's LOADING state, and only afterwards starts the audio decoder, and only if the codec exists by
 * then. Resuming or seeking the player while it is still opening changes that state, which ends the
 * wait early: the codec is not there yet, the decoder is never started, and the deck is silent for
 * good while its clock keeps running as if nothing were wrong. (Measured: the demux thread parked on a
 * full packet queue and no decoder thread at all.) So a deck must not touch its player until
 * {@link Stage#UP}.
 *
 * <p>Why the status alone is enough: WaterMedia's clock is born WAITING and its transition table lets
 * WAITING go to LOADING only, so no other status can be seen before the pipeline has started. The wait
 * in {@code lifecycle()} has no time limit, so a slow link does not cut it short either.
 */
final class PlayerReadiness {
    enum Stage {
        /** Still opening the media: do not resume, seek or pause it. */
        OPENING,
        /** Open and running its own threads: safe to drive. */
        UP,
        /** Gave up (bad link, refused by the host, no audio stream). */
        FAILED
    }

    private PlayerReadiness() {
    }

    /** The stage a player is in, from {@code MediaPlayer.Status.name()}. Unknown or missing counts as opening. */
    static Stage of(String status) {
        if (status == null) {
            return Stage.OPENING;
        }
        switch (status) {
            case "WAITING":
            case "LOADING":
                return Stage.OPENING;
            case "ERROR":
                return Stage.FAILED;
            default:
                return Stage.UP;
        }
    }
}
