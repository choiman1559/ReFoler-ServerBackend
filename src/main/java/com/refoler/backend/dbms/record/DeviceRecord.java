package com.refoler.backend.dbms.record;

import com.google.protobuf.util.JsonFormat;
import com.refoler.Refoler;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.packet.PacketConst;
import com.refoler.backend.commons.utils.IOUtils;
import com.refoler.backend.dbms.RecordConst;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class DeviceRecord {
    @SuppressWarnings("unused")
    ArrayList<BlobRecord> downloadableLinkList; // Not Used For Now

    Refoler.Device deviceMetadata;
    volatile File deviceRecordDirectory;
    volatile String deviceRecordFileListCache;
    volatile long deviceRecordFileListCacheTimeInMillis;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public DeviceRecord(UserRecord userRecord, String deviceId) {
        deviceRecordDirectory = new File(userRecord.getUserRecordDirectory(), deviceId);
        if (!deviceRecordDirectory.exists()) {
            deviceRecordDirectory.mkdirs();
        }
    }

    public boolean publishFileList(String fileListData) {
        try {
            this.deviceRecordFileListCache = fileListData;
            this.deviceMetadata = this.deviceMetadata.toBuilder().setLastQueriedTime(System.currentTimeMillis()).build();
            updateMetaData();

            IOUtils.writeTo(new File(deviceRecordDirectory, RecordConst.FILE_PREFIX_DEVICE_FILE_LIST), deviceRecordFileListCache, true);
            refreshCacheThreshold();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    @Nullable
    public String getFileList() throws IOException {
        refreshCacheThreshold();
        File deviceListFile = new File(deviceRecordDirectory, RecordConst.FILE_PREFIX_DEVICE_FILE_LIST);

        if(deviceListFile.exists()) {
            return deviceRecordFileListCache == null ? IOUtils.readFrom(deviceListFile) : deviceRecordFileListCache;
        } else return null;
    }

    private void refreshCacheThreshold() {
        this.deviceRecordFileListCacheTimeInMillis = System.currentTimeMillis();
    }

    public boolean registerMetaData(Refoler.Device deviceMetadata) {
        try {
            this.deviceMetadata = deviceMetadata;
            updateMetaData();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public void updateMetaData() throws IOException {
        IOUtils.writeTo(new File(deviceRecordDirectory, RecordConst.FILE_PREFIX_DEVICE_METADATA), JsonFormat.printer().print(deviceMetadata), true);
    }

    @Nullable
    public String getDeviceMetadata() {
        try {
            if (deviceMetadata == null) {
                Refoler.Device.Builder deviceBuilder = Refoler.Device.newBuilder();
                JsonFormat.parser().merge(IOUtils.readFrom(new File(deviceRecordDirectory, RecordConst.FILE_PREFIX_DEVICE_METADATA)), deviceBuilder);
                this.deviceMetadata = deviceBuilder.build();
            }
            return JsonFormat.printer().print(deviceMetadata);
        } catch (IOException e) {
            return null;
        }
    }

    public boolean cleanUpCache() {
        if(deviceRecordFileListCache != null &&
                (Service.getInstance().getArgument().recordHotRecordLifetime + deviceRecordFileListCacheTimeInMillis) > System.currentTimeMillis()) {
            deviceRecordFileListCache = null;
            return true;
        }
        return false;
    }
}
