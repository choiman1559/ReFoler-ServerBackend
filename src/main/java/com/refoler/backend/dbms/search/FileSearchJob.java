package com.refoler.backend.dbms.search;

import com.refoler.FileSearch;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class FileSearchJob {

    private final ArrayList<String> metadataKeyFilter;
    private final JSONObject jsonObject;
    private final QueryProcess queryProcess;
    private ArrayList<FileElement> result;

    public FileSearchJob(String rawData, FileSearch.Query query) {
        this.jsonObject = new JSONObject(rawData);
        this.queryProcess = new QueryProcess(query);

        metadataKeyFilter = new ArrayList<>();
        metadataKeyFilter.add(ReFileConst.DATA_TYPE_IS_FILE);
        metadataKeyFilter.add(ReFileConst.DATA_TYPE_LAST_MODIFIED);
        metadataKeyFilter.add(ReFileConst.DATA_TYPE_IS_SKIPPED);
        metadataKeyFilter.add(ReFileConst.DATA_TYPE_SIZE);
    }

    public boolean searchFor() {
        try {
            ArrayList<FileElement> searchResult = new ArrayList<>();
            for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
                String key = it.next();
                Object obj = jsonObject.get(key);
                ArrayList<FileElement> foundElements = switch (key) {
                    case ReFileConst.DATA_TYPE_LAST_MODIFIED, ReFileConst.DATA_TYPE_IS_FILE -> null;
                    default -> searchForSubElement((JSONObject) obj, key);
                };

                if(foundElements != null && !foundElements.isEmpty()) {
                    searchResult.addAll(foundElements);
                }
            }
            this.result = searchResult;
        } catch (JSONException e) {
            return false;
        }
        return true;
    }

    protected ArrayList<FileElement> searchForSubElement(JSONObject subJsonObject, String basePath) throws JSONException {
        ArrayList<FileElement> searchResult = new ArrayList<>();
        FileElement fileElement = new FileElement();
        fileElement.path = basePath;
        fileElement.lastModified = subJsonObject.getLong(ReFileConst.DATA_TYPE_LAST_MODIFIED);

        if (subJsonObject.getBoolean(ReFileConst.DATA_TYPE_IS_FILE)) {
            fileElement.isFile = true;
            fileElement.size = subJsonObject.getLong(ReFileConst.DATA_TYPE_SIZE);
        } else {
            fileElement.isFile = false;
            fileElement.isSkipped = subJsonObject.getBoolean(ReFileConst.DATA_TYPE_IS_SKIPPED);

            for (Iterator<String> it = subJsonObject.keys(); it.hasNext(); ) {
                String key = it.next();
                if (!metadataKeyFilter.contains(key)) {
                    Object obj = subJsonObject.get(key);
                    searchResult.addAll(searchForSubElement((JSONObject) obj, "%s/%s".formatted(basePath, key)));
                }
            }
        }

        if(queryProcess.matchCondition(fileElement)) {
            searchResult.add(fileElement);
        }
        return searchResult;
    }

    @Nullable
    public JSONArray printResult() {
        if(result != null) {
            JSONArray jsonArray = new JSONArray();
            for(FileElement element : result) {
                jsonArray.put(element.toJsonObject());
            }
            return jsonArray;
        } else return null;
    }
}
