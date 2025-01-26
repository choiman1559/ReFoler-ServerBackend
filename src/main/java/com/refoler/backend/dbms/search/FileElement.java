package com.refoler.backend.dbms.search;

import org.json.JSONObject;

public class FileElement {
    public String path;
    public boolean isFile;

    public long lastModified;
    public long size;
    public boolean isSkipped = false;

    public String getName() {
        String[] pathArrays = path.split("/");
        return pathArrays[pathArrays.length - 1];
    }

    public boolean includeName(String toFind) {
        return getName().toLowerCase().contains(toFind.toLowerCase());
    }

    public JSONObject toJsonObject() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(ReFileConst.DATA_TYPE_PATH, this.path);
        jsonObject.put(ReFileConst.DATA_TYPE_IS_FILE, isFile);
        jsonObject.put(ReFileConst.DATA_TYPE_LAST_MODIFIED, lastModified);
        jsonObject.put(ReFileConst.DATA_TYPE_SIZE, size);
        jsonObject.put(ReFileConst.DATA_TYPE_IS_SKIPPED, isSkipped);
        return jsonObject;
    }
}
