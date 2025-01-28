package com.refoler.backend.commons.consts;

public class RecordConst {
    public final static String ERROR_DATA_DEVICE_FILE_INFO_NOT_FOUND = "Device File List Data is not found";
    public final static String ERROR_DATA_DEVICE_INFO_NOT_AVAILABLE = "Device Information is not available";
    public final static String ERROR_DATA_DB_IO_FAILED_READ = "IO Failed while reading from stored db";
    public final static String ERROR_DATA_DB_IO_FAILED_WRITE = "IO Failed while writing to persistent db";

    public final static String SERVICE_TYPE_DEVICE_REGISTRATION = "type_device_registration";
    public final static String SERVICE_TYPE_DEVICE_FILE_LIST = "type_device_file_list";
    public final static String SERVICE_TYPE_FILE_SEARCH = "type_file_search";
    public final static String SERVICE_TYPE_TRANSFER_FILE = "type_transfer_file";

    public final static String SERVICE_ACTION_TYPE_GET = "action_get_data";
    public final static String SERVICE_ACTION_TYPE_POST = "action_post_data";
    public final static String SERVICE_ACTION_TYPE_REMOVE = "action_remove_data";

    public final static String FILE_PREFIX_DEVICE_FILE_LIST = "$FileList.json";
    public final static String FILE_PREFIX_DEVICE_METADATA = "$Metadata.json";
    public final static String FILE_PREFIX_DEVICE_BLOB_LIST = "$BlobList.json";
}
