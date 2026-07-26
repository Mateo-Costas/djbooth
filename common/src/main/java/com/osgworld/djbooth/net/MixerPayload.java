package com.osgworld.djbooth.net;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: set one mixer channel value.
 * channel: 0 = fader A, 1 = fader B, 2 = crossfader, 3 = master.
 */
public record MixerPayload(BlockPos pos, int channel, float value) implements CustomPacketPayload {

    public static final int FADER_A = 0;
    public static final int FADER_B = 1;
    public static final int CROSSFADER = 2;
    public static final int MASTER = 3;
    // EQ + colour filter per deck (0..1, 0.5 = flat).
    public static final int EQ_LOW_A = 4;
    public static final int EQ_MID_A = 5;
    public static final int EQ_HI_A = 6;
    public static final int FILTER_A = 7;
    public static final int EQ_LOW_B = 8;
    public static final int EQ_MID_B = 9;
    public static final int EQ_HI_B = 10;
    public static final int FILTER_B = 11;
    public static final int FX_ECHO_A = 12;
    public static final int FX_ECHO_B = 13;
    // Global switches sent as 0/1: isolator EQ curve, steep fader curve.
    public static final int ISOLATOR = 14;
    public static final int FADER_CURVE = 15;
    // Channel trim/gain, the knob at the top of each strip on a real DJM (0.5 = unity).
    public static final int GAIN_A = 16;
    public static final int GAIN_B = 17;
    // CROSS FADER ASSIGN switch per channel, sent as the position index (0 = A, 1 = THRU, 2 = B).
    public static final int XF_ASSIGN_A = 18;
    public static final int XF_ASSIGN_B = 19;
    // SOUND COLOR FX mode (sent as the mode index) and its PARAMETER knob.
    public static final int COLOR_MODE = 20;
    public static final int COLOR_PARAM = 21;
    // BEAT FX: effect selector, beat fraction, LEVEL/DEPTH, FX FREQUENCY mask, channel, ON/OFF, BPM.
    public static final int BEATFX_TYPE = 22;
    public static final int BEATFX_BEAT = 23;
    public static final int BEATFX_DEPTH = 24;
    public static final int BEATFX_BANDS = 25;
    public static final int BEATFX_CHANNEL = 26;
    public static final int BEATFX_ON = 27;
    public static final int BPM = 28;
    // Master section: BALANCE, BOOTH MONITOR, headphone CUE per channel, and the two fader curves
    // (sent as a curve index, since each switch has three positions).
    public static final int BALANCE = 29;
    public static final int BOOTH = 30;
    public static final int CUE_A = 31;
    public static final int CUE_B = 32;
    public static final int CROSSFADER_CURVE = 33;

    public static final Type<MixerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "mixer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MixerPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, MixerPayload::pos,
                    ByteBufCodecs.VAR_INT, MixerPayload::channel,
                    ByteBufCodecs.FLOAT, MixerPayload::value,
                    MixerPayload::new);

    @Override
    public Type<MixerPayload> type() {
        return TYPE;
    }
}
