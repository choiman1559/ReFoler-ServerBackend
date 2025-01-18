package com.refoler.backend.endpoint;

import com.google.protobuf.InvalidProtocolBufferException;
import com.refoler.Refoler;
import com.refoler.backend.commons.packet.PacketConst;
import com.refoler.backend.commons.packet.PacketProcessModel;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.Argument;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.JsonRequest;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.endpoint.provider.FirebaseHelper;
import com.refoler.backend.endpoint.search.SearchProcess;
import io.ktor.http.HttpStatusCode;
import io.ktor.server.application.ApplicationCall;
import java.io.IOException;

@SuppressWarnings("HttpUrlsUsage")
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

        if(Service.getInstance().getArgument().useAuthentication) {
            String uid = requestPacket.getUid();
            final String bearerPrefix = "Bearer ";
            final String idToken = applicationCall.getRequest().getHeaders().get(EndPointConst.KEY_AUTHENTICATION);

            if(idToken == null || uid.isEmpty()) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(PacketConst.ERROR_ILLEGAL_ARGUMENT, HttpStatusCode.Companion.getUnauthorized()));
                return;
            } else if(!idToken.startsWith(bearerPrefix)) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(PacketConst.ERROR_ILLEGAL_ARGUMENT, HttpStatusCode.Companion.getUnauthorized()));
                return;
            } else if(!FirebaseHelper.verifyToken(idToken.replace(bearerPrefix, "").trim(), uid)) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(EndPointConst.ERROR_ILLEGAL_AUTHENTICATION, HttpStatusCode.Companion.getUnauthorized()));
                return;
            }
        }

        switch (serviceType) {
            case EndPointConst.SERVICE_TYPE_LLM -> {
                //TODO: Have To Connect with 192.168.50.194:18035 -> (LM-Studio) 192.168.50.13:18037
            }
            case EndPointConst.SERVICE_TYPE_FILE_SEARCH -> SearchProcess.handleSearchRequest(applicationCall, requestPacket);
            case EndPointConst.SERVICE_TYPE_CHECK_ALIVE -> Service.replyPacket(applicationCall, PacketWrapper.makePacket(Service.getInstance().getArgument().version));
            case EndPointConst.SERVICE_TYPE_FCM_POST -> handleFcmPostRequest(applicationCall, requestPacket);
            default -> handleDefaultRoute(applicationCall, serviceType, requestPacket);
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

    private void handleDefaultRoute(ApplicationCall applicationCall, String serviceType, Refoler.RequestPacket requestPacket) {
        Argument argument = Service.getInstance().getArgument();
        String url = "http://%s:%s%s".formatted(argument.recordNodeHost, argument.recordNodePort,
                PacketConst.API_ROUTE_SCHEMA.replace("{version}", "v1").replace("{service_type}", serviceType));
        JsonRequest.postRequestPacket(url, requestPacket, receivedPacket -> {
            try {
                Service.replyPacket(applicationCall, receivedPacket);
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
