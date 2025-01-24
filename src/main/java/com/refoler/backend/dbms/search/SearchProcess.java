package com.refoler.backend.dbms.search;

import com.google.protobuf.InvalidProtocolBufferException;
import com.refoler.Refoler;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.dbms.record.UserRecord;
import io.ktor.server.application.ApplicationCall;
import org.json.JSONObject;

import java.io.IOException;

public class SearchProcess {
    public static void handleSearchRequest(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket, UserRecord userRecord) throws IOException {
        String searchKeyword = requestPacket.getExtraData();
        String[] deviceFileList = userRecord.fetchDeviceFileListFromDb(requestPacket);
        JSONObject resultObject = new JSONObject();

        for (int i = 0; i < requestPacket.getDeviceCount(); i++) {
            FileSearchJob searchJob = new FileSearchJob(deviceFileList[i], searchKeyword);
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
    }
}
