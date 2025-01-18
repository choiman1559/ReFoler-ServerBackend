package com.refoler.backend.commons.packet;

import io.ktor.server.application.ApplicationCall;

public interface PacketProcessModel {
    void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception;
}
