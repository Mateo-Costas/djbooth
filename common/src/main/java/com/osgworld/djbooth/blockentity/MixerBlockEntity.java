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
 * The mixer's state: faders, EQ, colour and beat effects, switches. Server-authoritative; persisted
 * to NBT and synced to clients like the decks. The audio itself is mixed client-side from these
 * values (see {@link com.osgworld.djbooth.mixer.MixLevels} and {@code DeckAudioManager}).
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
    public static final int CURVE_SLOW = com.osgworld.djbooth.mixer.MixLevels.CURVE_SLOW;
    public static final int CURVE_LINEAR = com.osgworld.djbooth.mixer.MixLevels.CURVE_LINEAR;
    public static final int CURVE_SHARP = com.osgworld.djbooth.mixer.MixLevels.CURVE_SHARP;
    public static final String[] CURVE_NAMES = com.osgworld.djbooth.mixer.MixLevels.CURVE_NAMES;
    // The crossfader has its own three shapes: smooth (constant power), medium and fast (cut).
    public static final int XF_CURVE_SLOW = com.osgworld.djbooth.mixer.MixLevels.XF_CURVE_SLOW;
    public static final int XF_CURVE_MEDIUM = com.osgworld.djbooth.mixer.MixLevels.XF_CURVE_MEDIUM;
    public static final int XF_CURVE_FAST = com.osgworld.djbooth.mixer.MixLevels.XF_CURVE_FAST;
    public static final String[] XF_CURVE_NAMES = com.osgworld.djbooth.mixer.MixLevels.XF_CURVE_NAMES;
    private int chFaderCurve = CURVE_LINEAR;
    // Smooth by default: with both decks open at the centre, a cut curve would add them up.
    private int crossFaderCurve = XF_CURVE_SLOW;

    private float balance = 0.5f;  // master BALANCE: 0 = hard left, 1 = hard right
    private float booth = 1.0f;    // BOOTH MONITOR level, heard by whoever is at the booth
    // CUE per channel: the DJ's headphone preview. Only the player working the booth hears it.
    private boolean cueA = false;
    private boolean cueB = false;

    /** CROSS FADER ASSIGN positions, as printed on the switch under each channel fader. */
    public static final int XF_A = com.osgworld.djbooth.mixer.MixLevels.XF_A;
    public static final int XF_THRU = com.osgworld.djbooth.mixer.MixLevels.XF_THRU;
    public static final int XF_B = com.osgworld.djbooth.mixer.MixLevels.XF_B;
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
        return com.osgworld.djbooth.mixer.MixLevels.clamp01(v);
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
    public void setBpm(float v) {
        if (Float.isFinite(v)) {
            this.bpm = Math.max(40f, Math.min(300f, v));
        }
    }

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

    // The crossfader used to share the channel fader's three curves (sqrt, linear, cube), which made
    // its "sharp" setting the opposite of a cut: both sides quiet in the middle. The shapes changed,
    // so saves carry a version to say which meaning a stored index has.
    private static final String XF_CURVE_VERSION_KEY = "XfCurveV";
    private static final int XF_CURVE_VERSION = 2;

    /** The stored crossfader curve, translated from the old shapes if the save predates the change. */
    static int readCrossFaderCurve(CompoundTag tag) {
        if (!tag.contains("CrossFaderCurve")) {
            return XF_CURVE_SLOW;
        }
        int stored = tag.getInt("CrossFaderCurve");
        if (tag.getInt(XF_CURVE_VERSION_KEY) >= XF_CURVE_VERSION) {
            return stored;
        }
        // Old sqrt and linear were both gentle blends; the old cube was the cutting one.
        return stored == 2 ? XF_CURVE_FAST : XF_CURVE_SLOW;
    }

    private static int clampAssign(int v) {
        return v < XF_A ? XF_A : (v > XF_B ? XF_B : v);
    }

    /**
     * The two gains one deck's audio runs through, for a listener out on the floor or one stood at
     * the booth: the part ahead of its BEAT FX and the part after. Crossfader 0 = full A, 1 = full B.
     *
     * <p>A real desk feeds the booth monitors from their own knob, and the DJ hears whatever is
     * cued on top of that. Here the "booth" is simply the blocks right around the mixer: stand
     * there and you hear the BOOTH MONITOR level, and cueing a channel previews it for you alone,
     * which is as close to headphones as a shared world gets.
     */
    public com.osgworld.djbooth.mixer.MixLevels.Gains gainsForDeck(boolean deckA, boolean atBooth) {
        return com.osgworld.djbooth.mixer.MixLevels.gains(
                beatFxChannel == com.osgworld.djbooth.mixer.BeatFxTypes.CH_MASTER,
                deckA ? faderA : faderB, chFaderCurve,
                deckA ? xfAssignA : xfAssignB, crossfader, crossFaderCurve,
                master,
                atBooth, booth, anyCue(), isCued(deckA));
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
        tag.putInt(XF_CURVE_VERSION_KEY, XF_CURVE_VERSION);
        tag.putFloat("Balance", balance);
        tag.putFloat("Booth", booth);
        tag.putBoolean("CueA", cueA);
        tag.putBoolean("CueB", cueB);
    }

    /** A 0..1 float from the tag: the default if it is missing, or if what is stored is not a number. */
    private static float unit(CompoundTag tag, String key, float fallback) {
        if (!tag.contains(key)) {
            return fallback;
        }
        float v = tag.getFloat(key);
        return Float.isFinite(v) ? clamp01(v) : fallback;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        faderA = unit(tag, "FaderA", 1.0f);
        faderB = unit(tag, "FaderB", 1.0f);
        crossfader = unit(tag, "Crossfader", 0.5f);
        master = unit(tag, "Master", 1.0f);
        eqLowA = unit(tag, "EqLowA", 0.5f);
        eqMidA = unit(tag, "EqMidA", 0.5f);
        eqHiA = unit(tag, "EqHiA", 0.5f);
        filterA = unit(tag, "FilterA", 0.5f);
        eqLowB = unit(tag, "EqLowB", 0.5f);
        eqMidB = unit(tag, "EqMidB", 0.5f);
        eqHiB = unit(tag, "EqHiB", 0.5f);
        filterB = unit(tag, "FilterB", 0.5f);
        echoA = unit(tag, "EchoA", 0f);
        echoB = unit(tag, "EchoB", 0f);
        gainA = unit(tag, "GainA", 0.5f);
        gainB = unit(tag, "GainB", 0.5f);
        setColorMode(tag.contains("ColorMode") ? tag.getInt("ColorMode")
                : com.osgworld.djbooth.mixer.ColorFxModes.FILTER);
        colorParam = unit(tag, "ColorParam", 0.5f);
        setBeatFxType(tag.contains("BeatFxType") ? tag.getInt("BeatFxType")
                : com.osgworld.djbooth.mixer.BeatFxTypes.DELAY);
        setBeatFxBeat(tag.contains("BeatFxBeat") ? tag.getInt("BeatFxBeat")
                : com.osgworld.djbooth.mixer.BeatFxTypes.DEFAULT_BEAT);
        setBeatFxBands(tag.contains("BeatFxBands") ? tag.getInt("BeatFxBands")
                : com.osgworld.djbooth.mixer.BeatFxTypes.BANDS_ALL);
        setBeatFxChannel(tag.contains("BeatFxChannel") ? tag.getInt("BeatFxChannel")
                : com.osgworld.djbooth.mixer.BeatFxTypes.CH_MASTER);
        beatFxDepth = unit(tag, "BeatFxDepth", 0.5f);
        beatFxOn = tag.contains("BeatFxOn") && tag.getBoolean("BeatFxOn");
        setBpm(tag.contains("Bpm") ? tag.getFloat("Bpm") : 128.0f);
        xfAssignA = clampAssign(tag.contains("XfAssignA") ? tag.getInt("XfAssignA") : XF_A);
        xfAssignB = clampAssign(tag.contains("XfAssignB") ? tag.getInt("XfAssignB") : XF_B);
        isolator = tag.contains("Isolator") && tag.getBoolean("Isolator");
        // Older worlds stored the channel fader curve as a sharp/linear flag.
        setChFaderCurve(tag.contains("ChFaderCurve") ? tag.getInt("ChFaderCurve")
                : (tag.getBoolean("FaderSharp") ? CURVE_SHARP : CURVE_LINEAR));
        setCrossFaderCurve(readCrossFaderCurve(tag));
        balance = unit(tag, "Balance", 0.5f);
        booth = unit(tag, "Booth", 1.0f);
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
