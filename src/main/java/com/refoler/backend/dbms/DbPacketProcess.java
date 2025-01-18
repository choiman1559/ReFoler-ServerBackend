package com.refoler.backend.dbms;

import com.google.protobuf.util.JsonFormat;
import com.refoler.Refoler;
import com.refoler.backend.commons.packet.PacketProcessModel;
import com.refoler.backend.dbms.record.UserRecord;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.packet.PacketConst;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.Service;
import io.ktor.server.application.ApplicationCall;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class DbPacketProcess implements PacketProcessModel {

    private static final String LogTAG = "DbPacketProcess";
    private final ConcurrentHashMap<String, UserRecord> userRecordMap;

    public DbPacketProcess() {
        this.userRecordMap = new ConcurrentHashMap<>();
    }

    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws IOException {
        Refoler.RequestPacket requestPacket = PacketWrapper.parseRequestPacket(rawData);
        UserRecord userRecord = userRecordMap.get(requestPacket.getUid());

        if(userRecord == null) {
            Log.print(LogTAG, "Created New User Record: %s".formatted(requestPacket.getUid()));
            userRecord = new UserRecord(requestPacket.getUid());
            userRecordMap.put(requestPacket.getUid(), userRecord);
        }

       processUserAction(applicationCall, serviceType, requestPacket, userRecord);
    }

    public static void processUserAction(ApplicationCall applicationCall, String serviceType, Refoler.RequestPacket requestPacket, UserRecord userRecord) throws IOException {
        switch (serviceType) {
            case RecordConst.SERVICE_TYPE_DEVICE_REGISTRATION -> {
                if(requestPacket.getActionName().equals(RecordConst.SERVICE_ACTION_TYPE_GET)) {
                    userRecord.getDeviceList(applicationCall, requestPacket);
                } else if (requestPacket.getActionName().equals(RecordConst.SERVICE_ACTION_TYPE_POST)) {
                    userRecord.registerDeviceList(applicationCall, requestPacket);
                }
            }

            case RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST -> {
                if(requestPacket.getActionName().equals(RecordConst.SERVICE_ACTION_TYPE_GET)) {
                    userRecord.getDeviceFileList(applicationCall, requestPacket);
                } else if (requestPacket.getActionName().equals(RecordConst.SERVICE_ACTION_TYPE_POST)) {
                    userRecord.uploadDeviceList(applicationCall, requestPacket);
                }
            }

            case RecordConst.SERVICE_TYPE_TRANSFER_FILE -> // Stub, For further development (About WebSocket?)
                    Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(PacketConst.ERROR_SERVICE_NOT_IMPLEMENTED));
            default -> Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(PacketConst.ERROR_SERVICE_NOT_FOUND));
        }
    }

    public static boolean checkIfRequestDeviceBlank(ApplicationCall call, Refoler.RequestPacket requestPacket) throws IOException {
        if(requestPacket.getDeviceCount() < 1) {
            Service.replyPacket(call, PacketWrapper.makeErrorPacket(PacketConst.ERROR_ILLEGAL_ARGUMENT));
            return true;
        }
        return false;
    }
}
