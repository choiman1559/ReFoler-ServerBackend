package com.refoler.backend.commons.consts;

public class ReFileConst {
    public static final String DATA_TYPE_PATH = "$path";
    public static final String DATA_TYPE_IS_FILE = "$isFile";
    public static final String DATA_TYPE_LAST_MODIFIED = "$lastModified";
    public static final String DATA_TYPE_IS_SKIPPED = "$isSkipped";
    public static final String DATA_TYPE_SIZE = "$size";
    public static final String DATA_TYPE_PERMISSION = "$permission";

    public static final Integer PERMISSION_UNKNOWN = -1;
    public static final Integer PERMISSION_NONE = 0;
    public static final Integer PERMISSION_READABLE = 4;
    public static final Integer PERMISSION_WRITABLE = 2;
    public static final Integer PERMISSION_EXECUTABLE = 1;
}
