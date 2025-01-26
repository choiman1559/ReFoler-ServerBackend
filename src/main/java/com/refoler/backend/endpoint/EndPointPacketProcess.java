package com.refoler.backend.endpoint;

import com.google.protobuf.InvalidProtocolBufferException;
import com.refoler.Refoler;
import com.refoler.backend.commons.utils.WebSocketRequest;
import com.refoler.backend.commons.utils.WebSocketUtil;
import com.refoler.backend.commons.consts.PacketConst;
import com.refoler.backend.commons.packet.PacketProcessModel;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.Argument;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.JsonRequest;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.consts.EndPointConst;
import com.refoler.backend.commons.consts.RecordConst;
import com.refoler.backend.endpoint.provider.FirebaseHelper;
import io.ktor.http.HttpStatusCode;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import io.ktor.websocket.CloseReason;

import java.io.IOException;
import java.util.Objects;

public class EndPointPacketProcess implements PacketProcessModel {

    private static final String LogTAG = "EndPointPacketProcess";

    public EndPointPacketProcess() {
        try {
            FirebaseHelper.init(Service.getInstance().getArgument().authCredentialPath);
        } catch (IOException e) {
            Log.print(LogTAG, "Failed to fetch credential file from storage: %s".formatted(Service.getInstance().getArgument().authCredentialPath));
        }
    }

    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception {
        Refoler.RequestPacket requestPacket = PacketWrapper.parseRequestPacket(rawData);

        if (Service.getInstance().getArgument().useAuthentication) {
            String uid = requestPacket.getUid();
            final String bearerPrefix = "Bearer ";
            final String idToken = applicationCall.getRequest().getHeaders().get(EndPointConst.KEY_AUTHENTICATION);

            if (idToken == null || uid.isEmpty()) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(PacketConst.ERROR_ILLEGAL_ARGUMENT, HttpStatusCode.Companion.getUnauthorized()));
                return;
            } else if (!idToken.startsWith(bearerPrefix)) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(PacketConst.ERROR_ILLEGAL_ARGUMENT, HttpStatusCode.Companion.getUnauthorized()));
                return;
            } else if (!FirebaseHelper.verifyToken(idToken.replace(bearerPrefix, "").trim(), uid)) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(EndPointConst.ERROR_ILLEGAL_AUTHENTICATION, HttpStatusCode.Companion.getUnauthorized()));
                return;
            }
        }

        switch (serviceType) {
            case EndPointConst.SERVICE_TYPE_LLM -> handleLLmRoute(applicationCall, requestPacket);
            case EndPointConst.SERVICE_TYPE_CHECK_ALIVE ->
                    Service.replyPacket(applicationCall, PacketWrapper.makePacket(Service.getInstance().getArgument().version));
            case EndPointConst.SERVICE_TYPE_FCM_POST -> handleFcmPostRequest(applicationCall, requestPacket);
            default -> handleDefaultRoute(applicationCall, serviceType, requestPacket);
        }
    }

    @Override
    public void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) {
        if (Service.getInstance().getArgument().useAuthentication) {
            String uid = applicationCall.getRequest().getHeaders().get(EndPointConst.KET_UID);
            final String bearerPrefix = "Bearer ";
            final String idToken = applicationCall.getRequest().getHeaders().get(EndPointConst.KEY_AUTHENTICATION);

            if (idToken == null || Objects.requireNonNullElse(uid, "").isEmpty()) {
                WebSocketUtil.closeWebSocket(socketServerSession, CloseReason.Codes.CANNOT_ACCEPT, PacketConst.ERROR_ILLEGAL_ARGUMENT);
                return;
            } else if (!idToken.startsWith(bearerPrefix)) {
                WebSocketUtil.closeWebSocket(socketServerSession, CloseReason.Codes.PROTOCOL_ERROR, PacketConst.ERROR_ILLEGAL_ARGUMENT);
                return;
            } else if (!FirebaseHelper.verifyToken(idToken.replace(bearerPrefix, "").trim(), uid)) {
                WebSocketUtil.closeWebSocket(socketServerSession, CloseReason.Codes.CANNOT_ACCEPT, EndPointConst.ERROR_ILLEGAL_AUTHENTICATION);
                return;
            }
        }

        Argument argument = Service.getInstance().getArgument();
        switch (serviceType) {
            case EndPointConst.SERVICE_TYPE_LLM -> WebSocketRequest.handleWebSocketProxy(socketServerSession,
                    argument.llmNodeHost, argument.llmNodePort,
                    PacketConst.API_ROUTE_SCHEMA.replace("{version}", "v1").replace("{service_type}", serviceType));

            case RecordConst.SERVICE_TYPE_TRANSFER_FILE -> WebSocketRequest.handleWebSocketProxy(socketServerSession,
                    argument.recordNodeHost, argument.recordNodePort,
                    PacketConst.API_ROUTE_SCHEMA.replace("{version}", "v1").replace("{service_type}", serviceType));

            default ->
                    WebSocketUtil.closeWebSocket(socketServerSession, CloseReason.Codes.VIOLATED_POLICY, PacketConst.ERROR_SERVICE_NOT_FOUND);
        }
    }

    private void handleFcmPostRequest(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws InvalidProtocolBufferException {
        PacketWrapper packetWrapper;
        String result = FirebaseHelper.postFcmMessage(requestPacket);
        if (result != null && !result.isEmpty()) {
            packetWrapper = PacketWrapper.makePacket(result);
        } else {
            packetWrapper = PacketWrapper.makeErrorPacket(PacketConst.ERROR_INTERNAL_ERROR);
        }

        Service.replyPacket(applicationCall, packetWrapper);
    }

    private void handleLLmRoute(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) {
        Argument argument = Service.getInstance().getArgument();
        JsonRequest.postRequestPacket(JsonRequest.buildIpcUrl(argument.llmNodeHost, argument.llmNodePort, EndPointConst.SERVICE_TYPE_LLM), requestPacket,
                receivedPacket -> {
                    try {
                        Service.replyPacket(applicationCall, receivedPacket);
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void handleDefaultRoute(ApplicationCall applicationCall, String serviceType, Refoler.RequestPacket requestPacket) {
        Argument argument = Service.getInstance().getArgument();
        JsonRequest.postRequestPacket(JsonRequest.buildIpcUrl(argument.recordNodeHost, argument.recordNodePort, serviceType), requestPacket,
                receivedPacket -> {
                    try {
                        Service.replyPacket(applicationCall, receivedPacket);
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
