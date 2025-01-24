package com.refoler.backend.commons.packet;

import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;

public interface PacketProcessModel {
    void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception;
    void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) throws Exception;
}
