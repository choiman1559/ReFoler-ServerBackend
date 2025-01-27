package com.refoler.backend.dbms.search;

import com.google.protobuf.InvalidProtocolBufferException;
import com.refoler.Refoler;
import com.refoler.backend.commons.consts.PacketConst;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.dbms.record.UserRecord;
import io.ktor.server.application.ApplicationCall;
import org.json.JSONObject;

import java.io.IOException;

public class SearchProcess {
    public static void handleSearchRequest(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket, UserRecord userRecord) throws IOException {
        String[] deviceFileList = userRecord.fetchDeviceFileListFromDb(requestPacket);
        JSONObject resultObject = new JSONObject();

        if(!requestPacket.hasFileQuery()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(PacketConst.ERROR_ILLEGAL_ARGUMENT));
            return;
        }

        for (int i = 0; i < requestPacket.getDeviceCount(); i++) {
            String deviceId = requestPacket.getDevice(i).getDeviceId();
            if(deviceFileList[i].isEmpty()) {
                resultObject.put(deviceId, "");
                continue;
            }

            FileSearchJob searchJob = new FileSearchJob(deviceFileList[i], requestPacket.getFileQuery());
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
