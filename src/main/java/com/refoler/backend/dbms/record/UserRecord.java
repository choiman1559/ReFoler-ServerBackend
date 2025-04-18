package com.refoler.backend.dbms.record;

import com.refoler.Refoler;
import com.refoler.backend.commons.service.GCollectTask;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.IOUtils;
import com.refoler.backend.dbms.DbPacketProcess;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.consts.RecordConst;
import io.ktor.server.application.ApplicationCall;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

public class UserRecord extends GCollectTask<String> {

    private static final String LogTAG = "UserRecord";
    private final String UID;

    private final File userRecordDirectory;
    public ConcurrentHashMap<String, DeviceRecord> deviceMap;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public UserRecord(String uid) {
        super();
        this.UID = uid;
        this.deviceMap = new ConcurrentHashMap<>();
        userRecordDirectory = new File(Service.getInstance().getArgument().recordDirectoryPath, uid);

        if (!userRecordDirectory.exists()) {
            userRecordDirectory.mkdirs();
        }

        for (File deviceDir : Objects.requireNonNullElse(userRecordDirectory.listFiles(), new File[0])) {
            deviceMap.put(deviceDir.getName(), new DeviceRecord(this, deviceDir.getName()));
        }
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
            String[] deviceListArray = fetchDeviceFileListFromDb(requestPacket);
            for(String deviceListObject : deviceListArray) {
                if(deviceListObject == null || deviceListObject.isEmpty()) {
                    isFailed = true;
                    break;
                }
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

    public String[] fetchDeviceFileListFromDb(Refoler.RequestPacket requestPacket) throws IOException {
        String[] deviceListArray = new String[requestPacket.getDeviceCount()];
        for (int i = 0; i < requestPacket.getDeviceCount(); i += 1) {
            DeviceRecord deviceRecord = getDeviceRecordById(requestPacket.getDevice(i).getDeviceId());
            deviceListArray[i] = deviceRecord == null ? "" : deviceRecord.getFileList();
        }
        return deviceListArray;
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

    public void removeDeviceFileList(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws IOException {
        if (DbPacketProcess.checkIfRequestDeviceBlank(applicationCall, requestPacket)) {
            return;
        }

        DeviceRecord deviceRecord = getDeviceRecordById(requestPacket.getDevice(0).getDeviceId());
        if (deviceRecord.removeFileList()) {
            Service.replyPacket(applicationCall, PacketWrapper.makePacket(""));
        } else {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(RecordConst.ERROR_DATA_DB_IO_FAILED_WRITE));
        }
    }

    public void removeDeviceRegistration(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws IOException {
        if (DbPacketProcess.checkIfRequestDeviceBlank(applicationCall, requestPacket)) {
            return;
        }

        String deviceId = requestPacket.getDevice(0).getDeviceId();
        DeviceRecord deviceRecord = getDeviceRecordById(deviceId);

        if (IOUtils.deleteRecursively(deviceRecord.deviceRecordDirectory)) {
            deviceMap.remove(deviceId);
            Service.replyPacket(applicationCall, PacketWrapper.makePacket(""));
        } else {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(RecordConst.ERROR_DATA_DB_IO_FAILED_WRITE));
        }
    }

    public void getDeviceRegistrationList(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws IOException {
        try {
            String[] deviceMetadataArray;
            boolean isFailed = false;

            if (!requestPacket.getDeviceList().isEmpty()) {
                deviceMetadataArray = new String[requestPacket.getDeviceCount()];
                for (int i = 0; i < requestPacket.getDeviceCount(); i += 1) {
                    DeviceRecord deviceRecord = getDeviceRecordById(requestPacket.getDevice(i).getDeviceId());
                    deviceMetadataArray[i] = deviceRecord == null ? "" : deviceRecord.getDeviceMetadata();
                    if(deviceMetadataArray[i] == null) isFailed = true;
                }
            } else {
                Log.printDebug("ddd", String.valueOf(deviceMap));
                deviceMetadataArray = new String[deviceMap.size()];
                int deviceListIndex = 0;
                for (String deviceIdKey : deviceMap.keySet()) {
                    DeviceRecord deviceRecord = getDeviceRecordById(deviceIdKey);
                    deviceMetadataArray[deviceListIndex] = deviceRecord == null ? "" : deviceRecord.getDeviceMetadata();
                    if(deviceMetadataArray[deviceListIndex] == null) isFailed = true;
                    deviceListIndex += 1;
                }
            }

            if(isFailed || deviceMetadataArray.length == 0) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(RecordConst.ERROR_DATA_DEVICE_INFO_NOT_AVAILABLE, deviceMetadataArray));
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

    @Override
    public long requireGcIgniteInterval() {
        return Service.getInstance().getArgument().recordGcInterval;
    }

    @Override
    public @NonNull Set<String> requireKeySet() {
        return deviceMap.keySet();
    }

    @Override
    public GCollectable requireCollectableFromKey(String key) {
        return getDeviceRecordById(key);
    }

    @Override
    public void onGCollected(int cleanedCacheCount) {
        if(cleanedCacheCount > 0) {
            Log.print(LogTAG, "GC Triggered! UID: %s, Cleaned-Up %d in-memory cache(s).".formatted(UID, cleanedCacheCount));
        }
    }

    @Override
    public void onGCollectPerform() {
        super.onGCollectPerform();
    }
}
