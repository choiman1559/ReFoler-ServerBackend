package com.refoler.backend.llm.role;

import com.refoler.FileSearch;
import com.refoler.Refoler;
import com.refoler.backend.commons.consts.RecordConst;
import com.refoler.backend.llm.DeAsyncJob;
import com.refoler.backend.llm.role.query.QueryWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;

@SuppressWarnings("unused")
public record FinderTools(String UID) {

    @Tool("Queries items in the device's file/folder list based on given conditions. " +
            "File name/path, size, and modification time can be provided as individual conditions, and these conditions can be applied simultaneously." +
            "Note that data size format and time format must be raw bytes and unix time format.")
    public String queryComplicatedConditions(@P("Device id to retrieve entire file list.") String deviceId,
                                             @P("Conditions to query") QueryWrapper query) {
        Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
        requestPacket.setUid(UID);
        requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);
        requestPacket.addDevice(Refoler.Device.newBuilder().setDeviceId(deviceId).build());
        requestPacket.setFileQuery(buildQueryWrapper(query));

        DeAsyncJob<List<String>> getJob = FinderAct.requestRecordQuery(requestPacket.build(), RecordConst.SERVICE_TYPE_FILE_SEARCH);
        List<String> result = getJob.runAndWait();
        return result.isEmpty() ? "" : result.getFirst();
    }

    @Tool("Retrieves entire file/folder list of device." +
            "WARING: Avoid using this function unless absolutely necessary. " +
            "Always prefer \"queryComplicatedConditions\" tool and only use this function if you cannot get satisfactory results.")
    public String getEntireDeviceFileList(@P("Device id to retrieve entire file list.") String deviceId) {
        Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
        requestPacket.setUid(UID);
        requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);
        requestPacket.addDevice(Refoler.Device.newBuilder().setDeviceId(deviceId).build());

        String result = FinderAct.requestRecordQuery(requestPacket.build(), RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST).runAndWait().getFirst();
        return result.isEmpty() ? "Device hasn't uploaded file list yet. Abort." : result;
    }

    private FileSearch.Query buildQueryWrapper(QueryWrapper queryWrapper) {
        FileSearch.Query.Builder queryBuilder = FileSearch.Query.newBuilder();

        if (queryWrapper.metadataQuery != null) {
            queryBuilder.setIndexQuery(FileSearch.IndexQuery.newBuilder()
                    .setIsKeywordFullPath(queryWrapper.metadataQuery.isKeywordFullPath)
                    .setExcludeSkippedDir(queryWrapper.metadataQuery.excludeSkippedDir)
                    .setSearchScope(queryWrapper.metadataQuery.searchScope)
                    .setMimeQuery(FileSearch.KeywordQuery.newBuilder()
                            .setIgnoreCase(queryWrapper.metadataQuery.mimeCondition.ignoreCase)
                            .setKeywordCondition(queryWrapper.metadataQuery.mimeCondition.keywordCondition)
                            .setKeyword(queryWrapper.metadataQuery.mimeCondition.keyword)
                            .build())
                    .build());
        }

        if (queryWrapper.dateQuery != null) {
            queryBuilder.setDateQuery(FileSearch.DateQuery.newBuilder()
                    .addAllDate(queryWrapper.dateQuery.date)
                    .setDateCondition(queryWrapper.dateQuery.dateCondition)
                    .build());
        }

        if (queryWrapper.sizeQuery != null) {
            queryBuilder.setSizeQuery(FileSearch.SizeQuery.newBuilder()
                    .addAllSize(queryWrapper.sizeQuery.size)
                    .setSizeCondition(queryWrapper.sizeQuery.sizeCondition)
                    .build());
        }

        if (queryWrapper.nameQuery != null) {
            queryBuilder.setKeywordQuery(FileSearch.KeywordQuery.newBuilder()
                    .setKeyword(queryWrapper.nameQuery.keyword)
                    .setKeywordCondition(queryWrapper.nameQuery.keywordCondition)
                    .setIgnoreCase(queryWrapper.nameQuery.ignoreCase)
                    .build());
        }

        return queryBuilder.build();
    }
}
