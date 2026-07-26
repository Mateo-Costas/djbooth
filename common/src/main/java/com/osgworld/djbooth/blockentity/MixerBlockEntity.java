package com.osgworld.djbooth.blockentity;

import com.osgworld.djbooth.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Mixer state stub for Plan 01: holds fader/crossfader/master values and persists them.
 * Deck binding and mixing math land in Plan 03.
 */
public class MixerBlockEntity extends BlockEntity {
    private float faderA = 1.0f;
    private float faderB = 1.0f;
    private float crossfader = 0.5f; // 0 = full A, 1 = full B
    private float master = 1.0f;

    // Per-channel EQ + colour filter + echo + trim, all 0..1. The EQ bands and the COLOR filter
    // rest flat at centre, echo 0 = off, trim 0.5 = unity gain.
    private float eqLowA = 0.5f, eqMidA = 0.5f, eqHiA = 0.5f, filterA = 0.5f, echoA = 0f, gainA = 0.5f;
    private float eqLowB = 0.5f, eqMidB = 0.5f, eqHiB = 0.5f, filterB = 0.5f, echoB = 0f, gainB = 0.5f;
    // Global switches, like the real DJM. isolator: EQ knobs kill to -inf vs -26 dB.
    // faderSharp: steep channel-fader curve vs linear.
    private boolean isolator = false;
    // Fader curves, as the three icons printed by each switch: 0 = slow rise, 1 = linear,
    // 2 = sharp (near-silent until the top of the throw, for cutting).
    public static final int CURVE_SLOW = 0;
    public static final int CURVE_LINEAR = 1;
    public static final int CURVE_SHARP = 2;
    public static final String[] CURVE_NAMES = {"SLOW", "LIN", "SHARP"};
    private int chFaderCurve = CURVE_LINEAR;
    private int crossFaderCurve = CURVE_LINEAR;

    private float balance = 0.5f;  // master BALANCE: 0 = hard left, 1 = hard right
    private float booth = 1.0f;    // BOOTH MONITOR level, heard by whoever is at the booth
    // CUE per channel: the DJ's headphone preview. Only the player working the booth hears it.
    private boolean cueA = false;
    private boolean cueB = false;

    /** CROSS FADER ASSIGN positions, as printed on the switch under each channel fader. */
    public static final int XF_A = 0;
    public static final int XF_THRU = 1;
    public static final int XF_B = 2;
    // Channel 1 defaults to the A side and channel 2 to the B side, the usual club setup.
    private int xfAssignA = XF_A;
    private int xfAssignB = XF_B;

    // SOUND COLOR FX: one mode shared by every channel (the six buttons on the left of the DJM),
    // plus the PARAMETER knob that scales how strong it is. The per-channel COLOR knobs above
    // drive it. FILTER is the mode a mixer ships in.
    private int colorMode = com.osgworld.djbooth.mixer.ColorFxModes.FILTER;
    private float colorParam = 0.5f;

    // BEAT FX: the tempo-locked effect on the right of the panel. One effect at a time, patched
    // across one channel or the master, timed off the BPM and the selected beat fraction.
    private int beatFxType = com.osgworld.djbooth.mixer.BeatFxTypes.DELAY;
    private int beatFxBeat = com.osgworld.djbooth.mixer.BeatFxTypes.DEFAULT_BEAT;
    private int beatFxBands = com.osgworld.djbooth.mixer.BeatFxTypes.BANDS_ALL;
    private int beatFxChannel = com.osgworld.djbooth.mixer.BeatFxTypes.CH_MASTER;
    private float beatFxDepth = 0.5f;
    private boolean beatFxOn = false;
    private float bpm = 128.0f; // set by TAP, or by a deck's measured tempo

    public MixerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MIXER.get(), pos, blockState);
    }

    public float getFaderA() { return faderA; }
    public float getFaderB() { return faderB; }
    public float getCrossfader() { return crossfader; }
    public float getMaster() { return master; }

    public void setFaderA(float v) { this.faderA = clamp01(v); }
    public void setFaderB(float v) { this.faderB = clamp01(v); }
    public void setCrossfader(float v) { this.crossfader = clamp01(v); }
    public void setMaster(float v) { this.master = clamp01(v); }

    public float getEqLowA() { return eqLowA; }
    public float getEqMidA() { return eqMidA; }
    public float getEqHiA() { return eqHiA; }
    public float getFilterA() { return filterA; }
    public float getEqLowB() { return eqLowB; }
    public float getEqMidB() { return eqMidB; }
    public float getEqHiB() { return eqHiB; }
    public float getFilterB() { return filterB; }

    public void setEqLowA(float v) { this.eqLowA = clamp01(v); }
    public void setEqMidA(float v) { this.eqMidA = clamp01(v); }
    public void setEqHiA(float v) { this.eqHiA = clamp01(v); }
    public void setFilterA(float v) { this.filterA = clamp01(v); }
    public void setEqLowB(float v) { this.eqLowB = clamp01(v); }
    public void setEqMidB(float v) { this.eqMidB = clamp01(v); }
    public void setEqHiB(float v) { this.eqHiB = clamp01(v); }
    public void setFilterB(float v) { this.filterB = clamp01(v); }

    public float getEchoA() { return echoA; }
    public float getEchoB() { return echoB; }
    public void setEchoA(float v) { this.echoA = clamp01(v); }
    public void setEchoB(float v) { this.echoB = clamp01(v); }

    public float getGainA() { return gainA; }
    public float getGainB() { return gainB; }
    public void setGainA(float v) { this.gainA = clamp01(v); }
    public void setGainB(float v) { this.gainB = clamp01(v); }

    public boolean isIsolator() { return isolator; }
    public void setIsolator(boolean v) { this.isolator = v; }

    public int getChFaderCurve() { return chFaderCurve; }
    public int getCrossFaderCurve() { return crossFaderCurve; }
    public void setChFaderCurve(int v) { this.chFaderCurve = Math.floorMod(v, 3); }
    public void setCrossFaderCurve(int v) { this.crossFaderCurve = Math.floorMod(v, 3); }

    public float getBalance() { return balance; }
    public float getBooth() { return booth; }
    public void setBalance(float v) { this.balance = clamp01(v); }
    public void setBooth(float v) { this.booth = clamp01(v); }

    public boolean isCueA() { return cueA; }
    public boolean isCueB() { return cueB; }
    public void setCueA(boolean v) { this.cueA = v; }
    public void setCueB(boolean v) { this.cueB = v; }
    public boolean isCued(boolean deckA) { return deckA ? cueA : cueB; }
    public boolean anyCue() { return cueA || cueB; }

    /** Shape a 0..1 fader position by one of the three printed curves. */
    private static float curve(float v, int shape) {
        return switch (shape) {
            case CURVE_SHARP -> v * v * v;   // stays quiet, then jumps: the cutting curve
            case CURVE_SLOW -> (float) Math.pow(v, 1.0 / 2.0); // opens up early, for long blends
            default -> v;
        };
    }

    /** Everything the DSP needs for one deck's channel, in one value. */
    public com.osgworld.djbooth.mixer.ChannelSettings settingsForDeck(boolean deckA) {
        boolean beatHere = beatFxOn && beatFxAppliesTo(deckA);
        return new com.osgworld.djbooth.mixer.ChannelSettings(
                deckA ? eqLowA : eqLowB,
                deckA ? eqMidA : eqMidB,
                deckA ? eqHiA : eqHiB,
                deckA ? filterA : filterB,
                deckA ? echoA : echoB,
                deckA ? gainA : gainB,
                isolator, colorMode, colorParam,
                beatFxType, beatHere, beatFxSeconds(), beatFxDepth, beatFxBands,
                balance);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    public int getColorMode() { return colorMode; }
    public float getColorParam() { return colorParam; }
    public void setColorMode(int v) {
        this.colorMode = Math.floorMod(v, com.osgworld.djbooth.mixer.ColorFxModes.MODES);
    }
    public void setColorParam(float v) { this.colorParam = clamp01(v); }

    public int getBeatFxType() { return beatFxType; }
    public int getBeatFxBeat() { return beatFxBeat; }
    public int getBeatFxBands() { return beatFxBands; }
    public int getBeatFxChannel() { return beatFxChannel; }
    public float getBeatFxDepth() { return beatFxDepth; }
    public boolean isBeatFxOn() { return beatFxOn; }
    public float getBpm() { return bpm; }

    public void setBeatFxType(int v) {
        this.beatFxType = Math.floorMod(v, com.osgworld.djbooth.mixer.BeatFxTypes.TYPES);
    }
    public void setBeatFxBeat(int v) {
        this.beatFxBeat = Math.floorMod(v, com.osgworld.djbooth.mixer.BeatFxTypes.BEATS.length);
    }
    public void setBeatFxBands(int v) {
        this.beatFxBands = v & com.osgworld.djbooth.mixer.BeatFxTypes.BANDS_ALL;
    }
    public void setBeatFxChannel(int v) {
        this.beatFxChannel = Math.floorMod(v, 3);
    }
    public void setBeatFxDepth(float v) { this.beatFxDepth = clamp01(v); }
    public void setBeatFxOn(boolean v) { this.beatFxOn = v; }
    public void setBpm(float v) { this.bpm = Math.max(40f, Math.min(300f, v)); }

    /** How long one cycle of the selected beat fraction lasts at the current BPM. */
    public float beatFxSeconds() {
        double beat = 60.0 / bpm;
        return (float) (beat * com.osgworld.djbooth.mixer.BeatFxTypes.BEATS[beatFxBeat]);
    }

    /** Whether the BEAT FX is patched across this deck's channel (MASTER hits both). */
    public boolean beatFxAppliesTo(boolean deckA) {
        return switch (beatFxChannel) {
            case com.osgworld.djbooth.mixer.BeatFxTypes.CH_A -> deckA;
            case com.osgworld.djbooth.mixer.BeatFxTypes.CH_B -> !deckA;
            default -> true;
        };
    }

    public int getXfAssignA() { return xfAssignA; }
    public int getXfAssignB() { return xfAssignB; }
    public void setXfAssignA(int v) { this.xfAssignA = clampAssign(v); }
    public void setXfAssignB(int v) { this.xfAssignB = clampAssign(v); }

    private static int clampAssign(int v) {
        return v < XF_A ? XF_A : (v > XF_B ? XF_B : v);
    }

    /**
     * Effective output volume (0..1) for one deck, folding in its channel fader, the
     * crossfader weight and the master. Crossfader 0 = full A, 1 = full B.
     */
    public float volumeForDeck(boolean deckA) {
        float channel = curve(deckA ? faderA : faderB, chFaderCurve);
        return clamp01(channel * crossfaderWeight(deckA ? xfAssignA : xfAssignB) * master);
    }

    /** How much the crossfader lets a channel through, given which side it is assigned to.
     *  THRU takes the channel off the crossfader entirely, exactly like the hardware switch. */
    private float crossfaderWeight(int assign) {
        return switch (assign) {
            case XF_A -> curve(1.0f - crossfader, crossFaderCurve);
            case XF_B -> curve(crossfader, crossFaderCurve);
            default -> 1.0f; // THRU
        };
    }

    /**
     * Level for someone stood at the booth rather than out on the floor.
     *
     * <p>A real desk feeds the booth monitors from their own knob, and the DJ hears whatever is
     * cued on top of that. Here the "booth" is simply the blocks right around the mixer: stand
     * there and you hear the BOOTH MONITOR level, and cueing a channel previews it for you alone,
     * which is as close to headphones as a shared world gets.
     */
    public float boothVolumeForDeck(boolean deckA) {
        if (anyCue()) {
            // Cue overrides: only cued channels are in the DJ's ears, at full level.
            return isCued(deckA) ? booth : 0f;
        }
        return clamp01(volumeForDeck(deckA) * booth);
    }

    public void applyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("FaderA", faderA);
        tag.putFloat("FaderB", faderB);
        tag.putFloat("Crossfader", crossfader);
        tag.putFloat("Master", master);
        tag.putFloat("EqLowA", eqLowA);
        tag.putFloat("EqMidA", eqMidA);
        tag.putFloat("EqHiA", eqHiA);
        tag.putFloat("FilterA", filterA);
        tag.putFloat("EqLowB", eqLowB);
        tag.putFloat("EqMidB", eqMidB);
        tag.putFloat("EqHiB", eqHiB);
        tag.putFloat("FilterB", filterB);
        tag.putFloat("EchoA", echoA);
        tag.putFloat("EchoB", echoB);
        tag.putFloat("GainA", gainA);
        tag.putFloat("GainB", gainB);
        tag.putInt("ColorMode", colorMode);
        tag.putFloat("ColorParam", colorParam);
        tag.putInt("BeatFxType", beatFxType);
        tag.putInt("BeatFxBeat", beatFxBeat);
        tag.putInt("BeatFxBands", beatFxBands);
        tag.putInt("BeatFxChannel", beatFxChannel);
        tag.putFloat("BeatFxDepth", beatFxDepth);
        tag.putBoolean("BeatFxOn", beatFxOn);
        tag.putFloat("Bpm", bpm);
        tag.putInt("XfAssignA", xfAssignA);
        tag.putInt("XfAssignB", xfAssignB);
        tag.putBoolean("Isolator", isolator);
        tag.putInt("ChFaderCurve", chFaderCurve);
        tag.putInt("CrossFaderCurve", crossFaderCurve);
        tag.putFloat("Balance", balance);
        tag.putFloat("Booth", booth);
        tag.putBoolean("CueA", cueA);
        tag.putBoolean("CueB", cueB);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        faderA = tag.contains("FaderA") ? tag.getFloat("FaderA") : 1.0f;
        faderB = tag.contains("FaderB") ? tag.getFloat("FaderB") : 1.0f;
        crossfader = tag.contains("Crossfader") ? tag.getFloat("Crossfader") : 0.5f;
        master = tag.contains("Master") ? tag.getFloat("Master") : 1.0f;
        eqLowA = tag.contains("EqLowA") ? tag.getFloat("EqLowA") : 0.5f;
        eqMidA = tag.contains("EqMidA") ? tag.getFloat("EqMidA") : 0.5f;
        eqHiA = tag.contains("EqHiA") ? tag.getFloat("EqHiA") : 0.5f;
        filterA = tag.contains("FilterA") ? tag.getFloat("FilterA") : 0.5f;
        eqLowB = tag.contains("EqLowB") ? tag.getFloat("EqLowB") : 0.5f;
        eqMidB = tag.contains("EqMidB") ? tag.getFloat("EqMidB") : 0.5f;
        eqHiB = tag.contains("EqHiB") ? tag.getFloat("EqHiB") : 0.5f;
        filterB = tag.contains("FilterB") ? tag.getFloat("FilterB") : 0.5f;
        echoA = tag.contains("EchoA") ? tag.getFloat("EchoA") : 0f;
        echoB = tag.contains("EchoB") ? tag.getFloat("EchoB") : 0f;
        gainA = tag.contains("GainA") ? tag.getFloat("GainA") : 0.5f;
        gainB = tag.contains("GainB") ? tag.getFloat("GainB") : 0.5f;
        setColorMode(tag.contains("ColorMode") ? tag.getInt("ColorMode")
                : com.osgworld.djbooth.mixer.ColorFxModes.FILTER);
        colorParam = tag.contains("ColorParam") ? tag.getFloat("ColorParam") : 0.5f;
        setBeatFxType(tag.contains("BeatFxType") ? tag.getInt("BeatFxType")
                : com.osgworld.djbooth.mixer.BeatFxTypes.DELAY);
        setBeatFxBeat(tag.contains("BeatFxBeat") ? tag.getInt("BeatFxBeat")
                : com.osgworld.djbooth.mixer.BeatFxTypes.DEFAULT_BEAT);
        setBeatFxBands(tag.contains("BeatFxBands") ? tag.getInt("BeatFxBands")
                : com.osgworld.djbooth.mixer.BeatFxTypes.BANDS_ALL);
        setBeatFxChannel(tag.contains("BeatFxChannel") ? tag.getInt("BeatFxChannel")
                : com.osgworld.djbooth.mixer.BeatFxTypes.CH_MASTER);
        beatFxDepth = tag.contains("BeatFxDepth") ? tag.getFloat("BeatFxDepth") : 0.5f;
        beatFxOn = tag.contains("BeatFxOn") && tag.getBoolean("BeatFxOn");
        setBpm(tag.contains("Bpm") ? tag.getFloat("Bpm") : 128.0f);
        xfAssignA = clampAssign(tag.contains("XfAssignA") ? tag.getInt("XfAssignA") : XF_A);
        xfAssignB = clampAssign(tag.contains("XfAssignB") ? tag.getInt("XfAssignB") : XF_B);
        isolator = tag.contains("Isolator") && tag.getBoolean("Isolator");
        // Older worlds stored the channel fader curve as a sharp/linear flag.
        setChFaderCurve(tag.contains("ChFaderCurve") ? tag.getInt("ChFaderCurve")
                : (tag.getBoolean("FaderSharp") ? CURVE_SHARP : CURVE_LINEAR));
        setCrossFaderCurve(tag.contains("CrossFaderCurve")
                ? tag.getInt("CrossFaderCurve") : CURVE_LINEAR);
        balance = tag.contains("Balance") ? tag.getFloat("Balance") : 0.5f;
        booth = tag.contains("Booth") ? tag.getFloat("Booth") : 1.0f;
        cueA = tag.contains("CueA") && tag.getBoolean("CueA");
        cueB = tag.contains("CueB") && tag.getBoolean("CueB");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
