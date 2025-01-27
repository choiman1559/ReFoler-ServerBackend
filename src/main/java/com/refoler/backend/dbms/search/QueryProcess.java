package com.refoler.backend.dbms.search;

import com.refoler.FileSearch;
import com.refoler.backend.commons.consts.QueryConditions;

import java.net.URLConnection;

public class QueryProcess {

    private final FileSearch.Query queryCondition;

    public QueryProcess(FileSearch.Query queryCondition) {
        this.queryCondition = queryCondition;
    }

    private boolean processKeyword(String keywordToCompare) {
        FileSearch.KeywordQuery keywordQuery = queryCondition.getKeywordQuery();
        return switch (keywordQuery.getKeywordCondition()) {
            case QueryConditions.CASE_EQUALS -> keywordQuery.getIgnoreCase()
                    ? keywordToCompare.equalsIgnoreCase(keywordQuery.getKeyword())
                    : keywordToCompare.equals(keywordQuery.getKeyword());
            case QueryConditions.CASE_KEYWORD_CONTAINS -> keywordToCompare.contains(keywordQuery.getKeyword());
            case QueryConditions.CASE_KEYWORD_START_WITH -> keywordToCompare.startsWith(keywordQuery.getKeyword());
            case QueryConditions.CASE_KEYWORD_END_WITH -> keywordToCompare.endsWith(keywordQuery.getKeyword());
            default ->
                    throw new IllegalStateException("Unexpected value: %s".formatted(keywordQuery.getKeywordCondition()));
        };
    }

    private boolean processDate(long dateToCompare) {
        FileSearch.DateQuery dateQuery = queryCondition.getDateQuery();
        return switch (dateQuery.getDateCondition()) {
            case QueryConditions.CASE_EQUALS -> dateToCompare == dateQuery.getDate(0);
            case QueryConditions.CASE_DATE_BEFORE -> dateToCompare < dateQuery.getDate(0);
            case QueryConditions.CASE_DATE_AFTER -> dateToCompare > dateQuery.getDate(0);
            case QueryConditions.CASE_DATE_BETWEEN ->
                    dateToCompare >= dateQuery.getDate(0) && dateToCompare <= dateQuery.getDate(1);
            default -> throw new IllegalStateException("Unexpected value: %s".formatted(dateQuery.getDateCondition()));
        };
    }

    private boolean processSize(long sizeToCompare) {
        FileSearch.SizeQuery sizeQuery = queryCondition.getSizeQuery();
        return switch (sizeQuery.getSizeCondition()) {
            case QueryConditions.CASE_EQUALS -> sizeToCompare == sizeQuery.getSize(0);
            case QueryConditions.CASE_SIZE_SMALLER -> sizeToCompare < sizeQuery.getSize(0);
            case QueryConditions.CASE_SIZE_BIGGER -> sizeToCompare > sizeQuery.getSize(0);
            case QueryConditions.CASE_SIZE_BETWEEN ->
                    sizeToCompare >= sizeQuery.getSize(0) && sizeToCompare <= sizeQuery.getSize(1);
            default -> throw new IllegalStateException("Unexpected value: %s".formatted(sizeQuery.getSizeCondition()));
        };
    }

    private boolean processIndex(FileElement fileElement) {
        boolean result = true;
        FileSearch.IndexQuery indexQuery = queryCondition.getIndexQuery();
        String scopeValue = indexQuery.hasSearchScope() ? indexQuery.getSearchScope() : QueryConditions.SCOPE_ALL;

        if (fileElement.isFile) {
            if (scopeValue.equals(QueryConditions.SCOPE_FOLDER_ONLY)) {
                result = false;
            } else if (indexQuery.hasMimeQuery()) {
                String mime = URLConnection.guessContentTypeFromName(fileElement.getName());
                result &= mime != null && !mime.isEmpty() && processKeyword(mime);
            }
        } else {
            if (scopeValue.equals(QueryConditions.SCOPE_FILE_ONLY)) {
                result = false;
            } else if (indexQuery.hasExcludeSkippedDir() && indexQuery.getExcludeSkippedDir() && fileElement.isSkipped) {
                result = false;
            }
        }
        return result;
    }

    public boolean matchCondition(FileElement fileElement) {
        boolean result = true;

        if (queryCondition.hasIndexQuery()) {
            result &= processIndex(fileElement);
        }

        keywordQuery : if (queryCondition.hasKeywordQuery()) {
            if(queryCondition.hasIndexQuery() && queryCondition.getIndexQuery().getIsKeywordFullPath()) {
                result &= processKeyword(fileElement.path);
                break keywordQuery;
            }
            result &= processKeyword(fileElement.getName());
        }

        if (queryCondition.hasDateQuery()) {
            result &= processDate(fileElement.lastModified);
        }

        if (fileElement.isFile && queryCondition.hasSizeQuery()) {
            result &= processSize(fileElement.size);
        }

        return result;
    }
}
