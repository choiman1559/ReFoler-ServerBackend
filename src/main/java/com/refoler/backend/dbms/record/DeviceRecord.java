package com.refoler.backend.dbms.record;

import com.google.protobuf.util.JsonFormat;
import com.refoler.Refoler;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.IOUtils;
import com.refoler.backend.commons.consts.RecordConst;
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
            if (loadDeviceMetadata()) {
                this.deviceRecordFileListCache = fileListData;
                this.deviceMetadata = this.deviceMetadata.toBuilder().setLastQueriedTime(System.currentTimeMillis()).build();
                updateMetaData();

                IOUtils.writeTo(new File(deviceRecordDirectory, RecordConst.FILE_PREFIX_DEVICE_FILE_LIST), deviceRecordFileListCache, true);
                refreshCacheThreshold();
            } else throw new IOException();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public boolean removeFileList() {
        try {
            if (loadDeviceMetadata()) {
                this.deviceRecordFileListCache = "";
                this.deviceMetadata = this.deviceMetadata.toBuilder().setLastQueriedTime(0).build();
                updateMetaData();

                IOUtils.deleteRecursively(new File(deviceRecordDirectory, RecordConst.FILE_PREFIX_DEVICE_FILE_LIST));
                refreshCacheThreshold();
            } else throw new IOException();
        } catch (IOException | SecurityException e) {
            return false;
        }
        return true;
    }

    @Nullable
    public String getFileList() throws IOException {
        refreshCacheThreshold();
        File deviceListFile = new File(deviceRecordDirectory, RecordConst.FILE_PREFIX_DEVICE_FILE_LIST);

        if (deviceListFile.exists()) {
            if(deviceRecordFileListCache == null) {
                deviceRecordFileListCache = IOUtils.readFrom(deviceListFile);
            }
            return deviceRecordFileListCache;
        } else return null;
    }

    private void refreshCacheThreshold() {
        this.deviceRecordFileListCacheTimeInMillis = System.currentTimeMillis();
    }

    public boolean registerMetaData(Refoler.Device deviceMetadata) {
        try {
            if(!deviceMetadata.hasLastQueriedTime() && loadDeviceMetadata()) {
               deviceMetadata = deviceMetadata.toBuilder().setLastQueriedTime(this.deviceMetadata.getLastQueriedTime()).build();
            }
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

    public boolean loadDeviceMetadata() throws IOException {
        if (deviceMetadata == null) {
            File deviceMetadataFile = new File(deviceRecordDirectory, RecordConst.FILE_PREFIX_DEVICE_METADATA);
            if (deviceMetadataFile.exists()) {
                Refoler.Device.Builder deviceBuilder = Refoler.Device.newBuilder();
                JsonFormat.parser().merge(IOUtils.readFrom(deviceMetadataFile), deviceBuilder);
                this.deviceMetadata = deviceBuilder.build();
            } else return false;
        }
        return true;
    }

    @Nullable
    public String getDeviceMetadata() {
        try {
            if (loadDeviceMetadata()) {
                return JsonFormat.printer().print(deviceMetadata);
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    public boolean cleanUpCache() {
        if (deviceRecordFileListCache != null &&
                (Service.getInstance().getArgument().recordHotRecordLifetime + deviceRecordFileListCacheTimeInMillis) < System.currentTimeMillis()) {
            deviceRecordFileListCache = null;
            return true;
        }
        return false;
    }
}
