package com.refoler.backend.llm.role.query;

import com.refoler.FileAction;
import com.refoler.FileSearch;
import com.refoler.Refoler;
import com.refoler.backend.commons.consts.DirectActionConst;
import com.refoler.backend.commons.consts.ReFileConst;
import com.refoler.backend.commons.consts.RecordConst;
import com.refoler.backend.llm.DeAsyncJob;
import com.refoler.backend.llm.role.FinderAct;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@SuppressWarnings("unused")
public record FinderTools(String UID, String agentUUID) {

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

    @Tool("Retrieves entire file/folder list of device. " +
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

    @Tool("Post file action request. " +
            "Returns true if request is sent successfully, otherwise false.")
    public boolean requestFileAction(@P("Device id to retrieve file hash.") String deviceId,
                                    @P("File action information to request") ActionWrapper.ActionRequestImpl action) {
        FileAction.ActionRequest.Builder actionRequest = buildActionRequest(deviceId, action);
        Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
        requestBuilder.addDevice(getAgentDevice());
        requestBuilder.addDevice(Refoler.Device.newBuilder().setDeviceId(deviceId).build());
        requestBuilder.setFileAction(actionRequest);

        DeAsyncJob<Boolean> worker = FinderAct.requestFcmPost(requestBuilder.build(), DirectActionConst.SERVICE_TYPE_FILE_ACTION);
        if(worker == null) {
            return false;
        } else {
            return worker.runAndWait();
        }
    }

    private FileAction.ActionRequest.Builder buildActionRequest(String deviceId, ActionWrapper.ActionRequestImpl actionWrapper) {
        FileAction.ActionRequest.Builder actionBuilder = FileAction.ActionRequest.newBuilder();
        actionBuilder.setOverrideExists(true);

        actionBuilder.setActionType(switch (actionWrapper.actionType) {
            case OP_DELETE -> FileAction.ActionType.OP_DELETE;
            case OP_NEW_FILE -> FileAction.ActionType.OP_NEW_FILE;
            case OP_MAKE_DIR -> FileAction.ActionType.OP_MAKE_DIR;
            case OP_COPY -> FileAction.ActionType.OP_COPY;
            case OP_CUT -> FileAction.ActionType.OP_CUT;
            case OP_RENAME -> FileAction.ActionType.OP_RENAME;
        });

        if(actionWrapper.queryWrapper != null) {
            actionBuilder.setQueryScope(buildQueryWrapper(actionWrapper.queryWrapper));
        }

        if(actionWrapper.targetFiles != null && actionWrapper.targetFiles.isEmpty()) {
            actionBuilder.addAllTargetFiles(actionWrapper.targetFiles);
        }

        if(actionWrapper.destinationDirectory != null && !actionWrapper.destinationDirectory.isBlank()) {
            actionBuilder.setDestDir(actionWrapper.destinationDirectory);
        }

        setChallengeCode(deviceId, actionBuilder);
        return actionBuilder;
    }

    private Refoler.Device getAgentDevice() {
        Refoler.Device.Builder builder = Refoler.Device.newBuilder();
        builder.setDeviceId(agentUUID);
        builder.setDeviceType(Refoler.DeviceType.DEVICE_TYPE_AGENT);
        return builder.build();
    }

    private static void setChallengeCode(String deviceId, FileAction.ActionRequest.Builder action) {
        if(Objects.requireNonNullElse(action.getChallengeCode(), "").isBlank()) {
            action.setChallengeCode(String.format(Locale.getDefault(),
                    "%s_%s_%d", deviceId, action.getActionType().getDescriptorForType().getName(), System.currentTimeMillis()));
        }
    }

    private FileSearch.Query buildQueryWrapper(QueryWrapper queryWrapper) {
        FileSearch.Query.Builder queryBuilder = FileSearch.Query.newBuilder();

        if (queryWrapper.metadataQuery != null) {
            int requiredPermission = ReFileConst.PERMISSION_NONE;
            if (queryWrapper.metadataQuery.permissionCondition == null) {
                requiredPermission = ReFileConst.PERMISSION_UNKNOWN;
            } else {
                if (queryWrapper.metadataQuery.permissionCondition.canExecute) {
                    requiredPermission |= ReFileConst.PERMISSION_EXECUTABLE;
                }

                if (queryWrapper.metadataQuery.permissionCondition.canWrite) {
                    requiredPermission |= ReFileConst.PERMISSION_WRITABLE;
                }

                if (queryWrapper.metadataQuery.permissionCondition.canRead) {
                    requiredPermission |= ReFileConst.PERMISSION_READABLE;
                }
            }

            queryBuilder.setIndexQuery(FileSearch.IndexQuery.newBuilder()
                    .setIsKeywordFullPath(queryWrapper.metadataQuery.isKeywordFullPath)
                    .setExcludeSkippedDir(queryWrapper.metadataQuery.excludeSkippedDir)
                    .setSearchScope(queryWrapper.metadataQuery.searchScope)
                    .setPermissionCondition(requiredPermission)
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
