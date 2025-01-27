package com.refoler.backend.llm.role.query;

import com.refoler.FileSearch;
import com.refoler.Refoler;
import com.refoler.backend.commons.consts.QueryConditions;
import com.refoler.backend.commons.consts.RecordConst;
import com.refoler.backend.llm.DeAsyncJob;
import com.refoler.backend.llm.role.FinderAct;
import com.refoler.backend.llm.role.MasterAct;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public record MasterTools(MasterAct masterAct) {
    @Tool("Retrieves a list of all devices registered to the service. " +
            "Note that element \"last_queried_time\" is unix time, " +
            "therefore you should convert it to human-readable format before print.")
    public List<String> getRegisteredDeviceList() {
        Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
        requestPacket.setUid(masterAct.getUid());
        requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);

        DeAsyncJob<List<String>> getJob = FinderAct.requestRecordQuery(requestPacket.build(), RecordConst.SERVICE_TYPE_DEVICE_REGISTRATION);
        return getJob.runAndWait();
    }

    @Tool("Searches for files and folders on a device specified by a given keyword. " +
            "Note that data key \"$isSkipped\" is true when \"$isFile\" is false " +
            "and this directory is excluded from indexing during file list synchronization. " +
            "Returns empty object if no matching results.")
    public String searchFileByKeyword(@P("List of device id to find. It can be empty list if you want to search among all devices") List<String> deviceIdList,
                                      @P("Keyword to search. This parameter must not be empty.") String keyword) {
        Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
        requestPacket.setUid(masterAct().getUid());
        requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);

        if (!deviceIdList.isEmpty()) {
            for (String deviceId : deviceIdList) {
                requestPacket.addDevice(Refoler.Device.newBuilder().setDeviceId(deviceId).build());
            }
        }

        return requestQueryInternal(keyword, requestPacket);
    }

    @Tool("Get all metadata of single file, including size (in bytes), last modified date (in unix time). " +
            "Returns empty object if theres no such file.")
    public String getSingleFileInfo(@P("Device id where file locates") String deviceId,
                                    @P("Exact Name, or Path of file to retrieve metadata. This parameter must not be empty.") String filePath) {
        Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
        requestPacket.setUid(masterAct.getUid());
        requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);
        requestPacket.addDevice(Refoler.Device.newBuilder().setDeviceId(deviceId).build());

        return requestQueryInternal(filePath, requestPacket);
    }

    @Tool("Performs analysis of a given command on the entire list of files on the device. " +
            "Returns the analyzed results within each device id. " +
            "Note that data size format and time format is raw bytes and unix time format, " +
            "therefore you should convert it to human-readable format before finally print it to user.")
    public Map<String, String> analyzeWholeDeviceFileList(@P("List of device id to analysis. It cannot be empty list.") List<String> deviceIdList,
                                                          @P("A command statement with clear instructions to perform analysis. This parameter must not be empty." +
                                                                  "Note That every file size value and time value format inside command must be raw bytes and unix time format.") String command) {
        HashMap<String, String> resultMap = new HashMap<>();
        for (String deviceId : deviceIdList) {
            resultMap.put(deviceId, masterAct.getFinderActById(deviceId).performAnalysisTask(deviceId, command).runAndWait());
        }
        return resultMap;
    }

    private String requestQueryInternal(String keyword, Refoler.RequestPacket.Builder requestPacket) {
        FileSearch.Query.Builder queryBuilder = FileSearch.Query.newBuilder();
        queryBuilder.setKeywordQuery(FileSearch.KeywordQuery.newBuilder()
                .setKeyword(keyword)
                .setIgnoreCase(true)
                .setKeywordCondition(QueryConditions.CASE_KEYWORD_CONTAINS)
                .build());
        queryBuilder.setIndexQuery(FileSearch.IndexQuery
                .newBuilder().setIsKeywordFullPath(true).build());
        requestPacket.setFileQuery(queryBuilder.build());

        DeAsyncJob<List<String>> getJob = FinderAct.requestRecordQuery(requestPacket.build(), RecordConst.SERVICE_TYPE_FILE_SEARCH);
        List<String> result = getJob.runAndWait();
        return result.isEmpty() ? "" : result.getFirst();
    }
}
