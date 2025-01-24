package com.refoler.backend.dbms;

import com.refoler.Refoler;
import com.refoler.backend.commons.packet.PacketProcessModel;
import com.refoler.backend.commons.utils.MapObjLocker;
import com.refoler.backend.commons.consts.RecordConst;
import com.refoler.backend.dbms.record.UserRecord;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.consts.PacketConst;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.dbms.search.SearchProcess;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;

public class DbPacketProcess implements PacketProcessModel {

    private static final String LogTAG = "DbPacketProcess";
    private final HashMap<String, MapObjLocker<UserRecord>> userRecordMap;

    public DbPacketProcess() {
        this.userRecordMap = new HashMap<>();
    }

    @Nullable
    public UserRecord getUserRecordMap(String Uid) {
        MapObjLocker<UserRecord> userRecordLocker = userRecordMap.get(Uid);
        return userRecordLocker == null ? null : userRecordLocker.getLockedObject();
    }

    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws IOException {
        Refoler.RequestPacket requestPacket = PacketWrapper.parseRequestPacket(rawData);
        UserRecord userRecord = getUserRecordMap(requestPacket.getUid());

        if(userRecord == null) {
            Log.print(LogTAG, "Created New User Record: %s".formatted(requestPacket.getUid()));
            userRecord = new UserRecord(requestPacket.getUid());
            userRecordMap.put(requestPacket.getUid(), new MapObjLocker<UserRecord>().setLockedObject(userRecord));
        }

       processUserAction(applicationCall, serviceType, requestPacket, userRecord);
    }

    @Override
    public void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) throws Exception {

    }

    public static void processUserAction(ApplicationCall applicationCall, String serviceType, Refoler.RequestPacket requestPacket, UserRecord userRecord) throws IOException {
        switch (serviceType) {
            case RecordConst.SERVICE_TYPE_DEVICE_REGISTRATION -> {
                if(requestPacket.getActionName().equals(RecordConst.SERVICE_ACTION_TYPE_GET)) {
                    userRecord.getDeviceRegistrationList(applicationCall, requestPacket);
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

            case RecordConst.SERVICE_TYPE_FILE_SEARCH -> SearchProcess.handleSearchRequest(applicationCall, requestPacket, userRecord);
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
