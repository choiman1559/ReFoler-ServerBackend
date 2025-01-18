package com.refoler.backend.dbms.record;

import java.io.File;

public class BlobRecord {

    UserRecord userRecord;
    DeviceRecord deviceRecord;
    String fileName;

    long registrationTimeInMillis;
    long expireTimeInMillis;

    public File getFile() {
        return new File(userRecord.getUserRecordDirectory(), fileName);
    }

    boolean isExpired() {
        return (registrationTimeInMillis + expireTimeInMillis) > System.currentTimeMillis();
    }
}
