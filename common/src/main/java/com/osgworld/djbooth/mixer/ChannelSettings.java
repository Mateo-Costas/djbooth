package com.osgworld.djbooth.mixer;

/**
 * Everything the mixer tells one deck's audio to do, in one value.
 *
 * <p>These travel from the mixer block entity to the DSP every client tick. Passing them as a
 * record rather than a dozen positional floats keeps the call sites readable and makes it
 * impossible to swap two arguments of the same type by accident.
 *
 * @param eqLow      LOW band gain, 0..1 with 0.5 flat
 * @param eqMid      MID band gain, 0..1 with 0.5 flat
 * @param eqHigh     HI band gain, 0..1 with 0.5 flat
 * @param colour     COLOR knob, 0..1 with 0.5 the centre detent
 * @param echo       legacy per-channel echo send, 0 = off
 * @param trim       channel TRIM, 0..1 with 0.5 unity
 * @param isolator   true when EQ CURVE is set to ISOLATOR
 * @param colourMode which SOUND COLOR FX the COLOR knob drives
 * @param colourParam PARAMETER knob, 0..1
 * @param beatType   which BEAT FX is selected
 * @param beatOn     whether the BEAT FX reaches this channel
 * @param beatSeconds effect time, already derived from BPM and beat fraction
 * @param beatDepth  LEVEL/DEPTH, 0..1
 * @param beatBands  FX FREQUENCY mask
 * @param balance    master BALANCE, 0 = hard left, 0.5 = centre, 1 = hard right
 */
public record ChannelSettings(
        float eqLow, float eqMid, float eqHigh, float colour, float echo, float trim,
        boolean isolator, int colourMode, float colourParam,
        int beatType, boolean beatOn, float beatSeconds, float beatDepth, int beatBands,
        float balance) {

    /** Everything flat: what a deck uses when it can't find a mixer. */
    public static ChannelSettings flat() {
        return new ChannelSettings(0.5f, 0.5f, 0.5f, 0.5f, 0f, 0.5f, false,
                ColorFxModes.FILTER, 0.5f,
                BeatFxTypes.DELAY, false, 0.5f, 0.5f, BeatFxTypes.BANDS_ALL, 0.5f);
    }
}
