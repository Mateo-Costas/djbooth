package com.osgworld.djbooth.registry;

import com.osgworld.djbooth.net.HotCuePayload;
import com.osgworld.djbooth.net.JogNudgePayload;
import com.osgworld.djbooth.net.LoadTrackPayload;
import com.osgworld.djbooth.net.MixerPayload;
import com.osgworld.djbooth.net.TransportPayload;
import com.osgworld.djbooth.net.handler.ServerHotCueHandler;
import com.osgworld.djbooth.net.handler.ServerJogHandler;
import com.osgworld.djbooth.net.handler.ServerLoadTrackHandler;
import com.osgworld.djbooth.net.handler.ServerMixerHandler;
import com.osgworld.djbooth.net.handler.ServerTransportHandler;
import dev.architectury.networking.NetworkManager;

/** Registers all C2S packets through Architectury's cross-loader network manager. */
public final class ModPayloads {
    private ModPayloads() {}

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                TransportPayload.TYPE, TransportPayload.CODEC, ServerTransportHandler::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                JogNudgePayload.TYPE, JogNudgePayload.CODEC, ServerJogHandler::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                MixerPayload.TYPE, MixerPayload.CODEC, ServerMixerHandler::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                LoadTrackPayload.TYPE, LoadTrackPayload.CODEC, ServerLoadTrackHandler::handle);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,
                HotCuePayload.TYPE, HotCuePayload.CODEC, ServerHotCueHandler::handle);
    }
}
