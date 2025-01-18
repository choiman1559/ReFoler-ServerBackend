package com.refoler.backend.dbms.record;

import com.refoler.Refoler;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.dbms.DbPacketProcess;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.dbms.RecordConst;
import io.ktor.server.application.ApplicationCall;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;

public class UserRecord {

    private static final String LogTAG = "UserRecord";
    private final String UID;

    private final File userRecordDirectory;
    public ConcurrentHashMap<String, DeviceRecord> deviceMap;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public UserRecord(String uid) {
        this.UID = uid;
        this.deviceMap = new ConcurrentHashMap<>();
        userRecordDirectory = new File(Service.getInstance().getArgument().recordDirectoryPath, uid);

        if (!userRecordDirectory.exists()) {
            userRecordDirectory.mkdirs();
        }

        for (File deviceDir : Objects.requireNonNullElse(userRecordDirectory.listFiles(), new File[0])) {
            deviceMap.put(deviceDir.getName(), new DeviceRecord(this, deviceDir.getName()));
        }

        igniteIntervalGcThread();
    }

    public File getUserRecordDirectory() {
        return userRecordDirectory;
    }

    public void getDeviceFileList(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws IOException {
        if (DbPacketProcess.checkIfRequestDeviceBlank(applicationCall, requestPacket)) {
            return;
        }

        try {
            boolean isFailed = false;
            String[] deviceListArray = new String[requestPacket.getDeviceCount()];
            for (int i = 0; i < requestPacket.getDeviceCount(); i += 1) {
                DeviceRecord deviceRecord = getDeviceRecordById(requestPacket.getDevice(i).getDeviceId());
                deviceListArray[i] = deviceRecord == null ? "" : deviceRecord.getFileList();
                if(deviceListArray[i] == null) isFailed = true;
            }

            if(isFailed) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(RecordConst.ERROR_DATA_DEVICE_FILE_INFO_NOT_FOUND, deviceListArray));
            } else {
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(deviceListArray));
            }
        } catch (IOException e) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(RecordConst.ERROR_DATA_DB_IO_FAILED_READ));
        }
    }

    public void uploadDeviceList(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws IOException {
        if (DbPacketProcess.checkIfRequestDeviceBlank(applicationCall, requestPacket)) {
            return;
        }

        DeviceRecord deviceRecord = getDeviceRecordById(requestPacket.getDevice(0).getDeviceId());
        if (deviceRecord.publishFileList(requestPacket.getExtraData())) {
            Service.replyPacket(applicationCall, PacketWrapper.makePacket(""));
        } else {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(RecordConst.ERROR_DATA_DB_IO_FAILED_WRITE));
        }
    }

    public void getDeviceList(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws IOException {
        try {
            String[] deviceMetadataArray;
            boolean isFailed = false;

            if (requestPacket.getDeviceCount() > 0) {
                deviceMetadataArray = new String[requestPacket.getDeviceCount()];
                for (int i = 0; i < requestPacket.getDeviceCount(); i += 1) {
                    DeviceRecord deviceRecord = getDeviceRecordById(requestPacket.getDevice(i).getDeviceId());
                    deviceMetadataArray[i] = deviceRecord == null ? "" : deviceRecord.getDeviceMetadata();
                    if(deviceMetadataArray[i] == null) isFailed = true;
                }
            } else {
                deviceMetadataArray = new String[deviceMap.size()];
                int i = 0;
                for (String deviceIdKey : deviceMap.keySet()) {
                    DeviceRecord deviceRecord = getDeviceRecordById(deviceIdKey);
                    deviceMetadataArray[i += 1] = deviceRecord == null ? "" : deviceRecord.getDeviceMetadata();
                    if(deviceMetadataArray[i] == null) isFailed = true;
                }
            }

            if(isFailed || deviceMetadataArray.length == 0) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(RecordConst.ERROR_DATA_DEVICE_INFO_NOT_AVAILABLE));
            } else {
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(deviceMetadataArray));
            }
        } catch (IOException e) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(RecordConst.ERROR_DATA_DB_IO_FAILED_READ));
        }
    }

    public void registerDeviceList(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws IOException {
        if (DbPacketProcess.checkIfRequestDeviceBlank(applicationCall, requestPacket)) {
            return;
        }

        Refoler.Device device = requestPacket.getDevice(0);
        DeviceRecord deviceRecord = getDeviceRecordById(device.getDeviceId());
        if (deviceRecord.registerMetaData(device)) {
            Service.replyPacket(applicationCall, PacketWrapper.makePacket(""));
        } else {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(RecordConst.ERROR_DATA_DB_IO_FAILED_WRITE));
        }
    }

    private DeviceRecord getDeviceRecordById(String key) {
        if(!deviceMap.containsKey(key)){
            DeviceRecord deviceRecord = new DeviceRecord(this, key);
            deviceMap.put(key, deviceRecord);
        }
        return deviceMap.get(key);
    }

    private void igniteIntervalGcThread() {
        final long gcInterval = Service.getInstance().getArgument().recordGcInterval;
        try (ScheduledExecutorService executor = Executors.newScheduledThreadPool(1)){
            executor.scheduleAtFixedRate(this::performIntervalGc, gcInterval, gcInterval, TimeUnit.MILLISECONDS);
        }
    }

    private void performIntervalGc() {
        Log.printDebug(LogTAG, "GC_Tick");
        int cleanedCacheCount = 0;
        for (String deviceRecordKey : deviceMap.keySet()) {
             DeviceRecord deviceRecord = getDeviceRecordById(deviceRecordKey);
             if(deviceRecord.cleanUpCache()) {
                 cleanedCacheCount += 1;
             }
        }

        if(cleanedCacheCount > 0) {
            Log.print(LogTAG, "GC Triggered! UID: %s, Cleaned-Up %d in-memory cache(s).".formatted(UID, cleanedCacheCount));
        }
    }
}
