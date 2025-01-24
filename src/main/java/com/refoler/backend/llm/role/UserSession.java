package com.refoler.backend.llm.role;

import com.refoler.Refoler;
import com.refoler.backend.commons.consts.LlmConst;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.WebSocketUtil;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import io.ktor.websocket.CloseReason;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class UserSession {
    public final String uid;
    private final ConcurrentHashMap<String, MasterAct> masterActConcurrentHashMap;
    public ConcurrentHashMap<String, String> messageQueryHashMap;

    public UserSession(String uid) {
        this.uid = uid;
        this.masterActConcurrentHashMap = new ConcurrentHashMap<>();
        this.messageQueryHashMap = new ConcurrentHashMap<>();
    }

    public void igniteConversation(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws IOException {
        String message = requestPacket.getExtraData();
        String deviceId = requestPacket.getDevice(0).getDeviceId();

        if (Objects.requireNonNullElse(messageQueryHashMap.get(deviceId), "").equals(Integer.toString(message.hashCode()))) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(LlmConst.ERROR_CONVERSATION_ALREADY_PROCESSED));
        } else {
            messageQueryHashMap.put(deviceId, Integer.toString(message.hashCode()));
            MasterAct masterAct = masterActConcurrentHashMap.get(deviceId);
            if (masterAct == null) {
                masterAct = new MasterAct(this, requestPacket.getDevice(0));
                masterActConcurrentHashMap.put(deviceId, masterAct);
            }

            if (masterAct.isRunning.get()) {
                Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(LlmConst.ERROR_ANOTHER_CONVERSATION_PROCESSING));
            } else {
                masterAct.performChat(message);
                Service.replyPacket(applicationCall, PacketWrapper.makePacket(""));
            }
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
}
