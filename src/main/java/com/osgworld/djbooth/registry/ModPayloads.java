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
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(TransportPayload.TYPE, TransportPayload.CODEC, ServerTransportHandler::handle);
        registrar.playToServer(JogNudgePayload.TYPE, JogNudgePayload.CODEC, ServerJogHandler::handle);
        registrar.playToServer(MixerPayload.TYPE, MixerPayload.CODEC, ServerMixerHandler::handle);
        registrar.playToServer(LoadTrackPayload.TYPE, LoadTrackPayload.CODEC, ServerLoadTrackHandler::handle);
        registrar.playToServer(HotCuePayload.TYPE, HotCuePayload.CODEC, ServerHotCueHandler::handle);
    }
}
