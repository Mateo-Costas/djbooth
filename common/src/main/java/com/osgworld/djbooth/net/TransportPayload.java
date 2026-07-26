package com.osgworld.djbooth.net;

import com.osgworld.djbooth.DJBooth;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C2S: a transport button was pressed on the CDJ at {@code pos}. */
public record TransportPayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final int PLAY = 0, PAUSE = 1, CUE = 2, SET_CUE = 3, LOOP_TOGGLE = 4;
    public static final int LOOP_IN = 5, LOOP_OUT = 6, LOOP_EXIT = 7, RELOOP = 8,
            LOOP_HALVE = 9, LOOP_DOUBLE = 10, JUMP_BACK = 11, JUMP_FWD = 12;
    // CDJ-3000 deck controls.
    public static final int DIRECTION = 13;    // step FWD -> REV -> SLIP REV
    public static final int JOG_MODE = 14;     // toggle VINYL / CDJ
    public static final int SLIP = 15;
    public static final int QUANTIZE = 16;
    public static final int TEMPO_RESET = 17;
    public static final int BEAT_SYNC = 18;
    public static final int TRACK_START = 19;  // TRACK SEARCH: back to the top
    public static final int SEARCH_BACK = 20;  // SEARCH: scan backwards
    public static final int SEARCH_FWD = 21;
    public static final int MEMORY = 22;
    public static final int CALL_PREV = 23;
    public static final int CALL_NEXT = 24;
    public static final int MEMORY_DELETE = 25;

    public static final Type<TransportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DJBooth.MODID, "transport"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransportPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TransportPayload::pos,
            ByteBufCodecs.VAR_INT, TransportPayload::action,
            TransportPayload::new);

    @Override
    public Type<TransportPayload> type() {
        return TYPE;
    }
}
