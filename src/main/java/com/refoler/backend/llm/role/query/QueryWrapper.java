package com.refoler.backend.llm.role.query;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@SuppressWarnings("unused")
@Description("Specifies query information. " +
        "Each of the \"nameQuery\", \"sizeQuery\", \"dateQuery\", and \"metadataQuery\" options can be null if not used, " +
        "but at least one of them must have a value. If there are multiple conditions, the value that satisfies all of them is returned.")
public class QueryWrapper {
    public static class NameCondition {
        @Description("Specifies the case to query. Possible cases are \"equals\", \"contains\", \"start_with\", and \"end_with\".")
        public String keywordCondition;
        @Description("Keyword to search. To select all items, specify an empty string value.")
        public String keyword;
        @Description("Specifies whether to ignore uppercase and lowercase letters. The default is false.")
        public boolean ignoreCase = false;
    }

    public static class DateCondition {
        @Description("Specifies the case to query. Possible cases are \"date_before\", \"date_after\", \"date_between\".")
        public String dateCondition;
        @Description("The date values to compare. " +
                "If \"dateCondition\" is \"date_between\", use a list where the smaller value is at index 0 and the larger value is at index 1. " +
                "Otherwise, just put the data at index 0.")
        public List<Long> date;
    }

    public static class SizeCondition {
        @Description("Specifies the case to query. Possible cases are \"size_smaller\", \"size_bigger\", \"size_between\".")
        public String sizeCondition;
        @Description("The size values to compare. " +
                "If \"sizeCondition\" is \"size_between\", use a list where the smaller value is at index 0 and the larger value is at index 1. " +
                "Otherwise, just put the data at index 0.")
        public List<Long> size;
    }

    public static class MetadataCondition {
        @Description("Use this option when you want to search for a specific MIME.")
        public NameCondition mimeCondition;
        @Description("Compares with the entire path when searching for keywords. Default is false.")
        public boolean isKeywordFullPath = false;
        @Description("Folders that are excluded from indexing during file list synchronization are excluded from searches. The default is false.")
        public boolean excludeSkippedDir = false;
        @Description("Determines the type of items to search for. Possible values are \"file_only\", \"folder_only\", and \"all\".")
        public String searchScope;
    }

    @Description("Search by file path or name")
    public NameCondition nameQuery;

    @Description("Search by file size")
    public SizeCondition sizeQuery;

    @Description("Search by date")
    public DateCondition dateQuery;

    @Description("Settings query options and additional information")
    public MetadataCondition metadataQuery;
}
