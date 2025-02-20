package com.refoler.backend.llm.role;

import com.refoler.Refoler;
import com.refoler.backend.commons.consts.LlmConst;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.GCollectTask;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.utils.WebSocketUtil;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import io.ktor.websocket.CloseReason;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class UserSession extends GCollectTask {

    private static final String LogTAG = "LLM_UserSession";
    public final String uid;
    private final ConcurrentHashMap<String, MasterAct> masterActConcurrentHashMap;
    public ConcurrentHashMap<String, String> messageQueryHashMap;

    public UserSession(String uid) {
        super();
        this.uid = uid;
        this.masterActConcurrentHashMap = new ConcurrentHashMap<>();
        this.messageQueryHashMap = new ConcurrentHashMap<>();
    }

    public void igniteConversation(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws IOException {
        String message = requestPacket.getExtraData();
        String deviceId = requestPacket.getDevice(0).getDeviceId();

        MasterAct masterAct = masterActConcurrentHashMap.get(deviceId);
        if (masterAct == null) {
            masterAct = new MasterAct(this, requestPacket.getDevice(0));
            masterActConcurrentHashMap.put(deviceId, masterAct);
        }

        if (Objects.requireNonNullElse(messageQueryHashMap.get(deviceId), "").equals(Integer.toString(message.hashCode()))) {
            if(!masterAct.cleanUpCache()) {
                 Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(LlmConst.ERROR_CONVERSATION_ALREADY_PROCESSED));
                 return;
            }
        }

        messageQueryHashMap.put(deviceId, Integer.toString(message.hashCode()));
        if (masterAct.isRunning.get()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(LlmConst.ERROR_ANOTHER_CONVERSATION_PROCESSING));
        } else {
            masterAct.performChat(message);
            Service.replyPacket(applicationCall, PacketWrapper.makePacket(""));
        }
    }

    public void transferConversation(Refoler.RequestPacket requestPacket, DefaultWebSocketServerSession socketServerSession) {
        String deviceId = requestPacket.getDevice(0).getDeviceId();
        if (messageQueryHashMap.containsKey(deviceId) && messageQueryHashMap.get(deviceId).equals(requestPacket.getExtraData())) {
            masterActConcurrentHashMap.get(deviceId).setWebSocketServerSession(socketServerSession);
        } else {
            WebSocketUtil.closeWebSocket(socketServerSession, CloseReason.Codes.CANNOT_ACCEPT, LlmConst.ERROR_CONVERSATION_MESSAGE_NOT_REGISTERED);
        }
    }

    @Override
    public void performIntervalGc() {
        int cleanedCacheCount = 0;
        for (String deviceRecordKey : masterActConcurrentHashMap.keySet()) {
            MasterAct masterAct = masterActConcurrentHashMap.get(deviceRecordKey);
            if(masterAct.cleanUpCache()) {
                cleanedCacheCount += 1;
            }
        }

        if(cleanedCacheCount > 0) {
            Log.print(LogTAG, "GC Triggered! UID: %s, Cleaned-Up %d in-memory cache(s).".formatted(uid, cleanedCacheCount));
        }
    }
}
