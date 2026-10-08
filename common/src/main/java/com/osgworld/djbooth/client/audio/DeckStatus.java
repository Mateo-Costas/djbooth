package com.osgworld.djbooth.client.audio;

/**
 * What a deck's audio is doing, as far as the panel needs to say.
 *
 * <p>Plain enum with no WaterMedia types in it, so the screen can use it on a client that does not
 * have WaterMedia installed. Before this the deck's screen said nothing in any of these cases: a
 * link that failed to load, one still loading and a client with no audio backend all looked like a
 * deck that was simply quiet.
 */
public enum DeckStatus {
    /** WaterMedia is not installed, so there is no sound to wait for. */
    NO_BACKEND,
    /** Nothing is loaded on the deck. */
    NO_TRACK,
    /** A link is set and the audio is still being resolved or opened. */
    LOADING,
    /** The audio is open and following the deck. */
    READY,
    /** The link could not be opened. */
    FAILED
}
