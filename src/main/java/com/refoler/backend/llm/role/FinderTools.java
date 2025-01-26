package com.refoler.backend.llm.role;

import com.refoler.Refoler;
import com.refoler.backend.commons.consts.RecordConst;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

@SuppressWarnings("unused")
public record FinderTools(String UID) {
    @Tool("Retrieves entire file/folder list of device.")
    public String getEntireDeviceFileList(@P("Device id to retrieve entire file list.") String deviceId) {
        Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
        requestPacket.setUid(UID);
        requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);
        requestPacket.addDevice(Refoler.Device.newBuilder().setDeviceId(deviceId).build());

        String result = FinderAct.requestRecordQuery(requestPacket.build(), RecordConst.SERVICE_TYPE_DEVICE_FILE_LIST).runAndWait().getFirst();
        return result.isEmpty() ? "Device hasn't uploaded file list yet. Abort." : result;
    }
}
