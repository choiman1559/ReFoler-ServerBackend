package com.refoler.backend.llm;

import com.refoler.backend.commons.packet.PacketProcessModel;
import io.ktor.server.application.ApplicationCall;

public class LlmPacketProcess implements PacketProcessModel {
    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) {

    }
}
