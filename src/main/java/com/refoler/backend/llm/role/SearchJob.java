package com.refoler.backend.llm.role;

import com.refoler.backend.dbms.search.ReFileConst;
import org.json.JSONObject;

public class SearchJob {
    public static class SearchResult {
        public String path;
        public String name;
        public boolean isFile;

        public SearchResult(JSONObject result) {
            String fullPath = result.getString(ReFileConst.DATA_TYPE_PATH);
            isFile = result.getBoolean(ReFileConst.DATA_TYPE_IS_FILE);
            name = getName(fullPath);
            path = fullPath.replace(name, "");
        }

        private String getName(String path) {
            String[] pathArrays = path.split("/");
            return pathArrays[pathArrays.length - 1];
        }
    }
}
