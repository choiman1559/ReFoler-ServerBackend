package com.refoler.backend.endpoint.search;

import com.google.protobuf.InvalidProtocolBufferException;
import com.refoler.Refoler;
import com.refoler.backend.commons.packet.PacketConst;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.Argument;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.JsonRequest;
import com.refoler.backend.dbms.RecordConst;
import io.ktor.server.application.ApplicationCall;
import org.json.JSONObject;

public class SearchProcess {
    public static void handleSearchRequest(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) {
        String searchKeyword = requestPacket.getExtraData();
        requestDeviceList(requestPacket, (receivedDeviceList) -> {
            JSONObject resultObject = new JSONObject();
            for (int i = 0; i < requestPacket.getDeviceCount(); i++) {
                FileSearchJob searchJob = new FileSearchJob(receivedDeviceList.getRefolerPacket().getExtraData(i), searchKeyword);
                String deviceId = requestPacket.getDevice(i).getDeviceId();

                if (searchJob.searchFor()) {
                    resultObject.put(deviceId, searchJob.printResult());
                } else {
                    resultObject.put(deviceId, "");
                }
            }

            try {
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(resultObject.toString()));
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @SuppressWarnings("HttpUrlsUsage")
    private static void requestDeviceList(Refoler.RequestPacket wantedPacket, JsonRequest.OnReceivedCompleteListener onReceivedCompleteListener) {
        Argument argument = Service.getInstance().getArgument();
        String url = "http://%s:%s%s".formatted(argument.recordNodeHost, argument.recordNodePort,
                PacketConst.API_ROUTE_SCHEMA.replace("{version}", "v1").replace("{service_type}", RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST));

        Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
        requestPacket.setUid(wantedPacket.getUid());
        requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);
        requestPacket.addAllDevice(wantedPacket.getDeviceList());

        JsonRequest.postRequestPacket(url, requestPacket.build(), onReceivedCompleteListener);
    }
}
