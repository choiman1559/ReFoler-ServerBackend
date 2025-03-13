package com.refoler.backend.llm;

import com.refoler.Refoler;
import com.refoler.backend.commons.consts.LlmConst;
import com.refoler.backend.commons.consts.PacketConst;
import com.refoler.backend.commons.packet.PacketProcessModel;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.Argument;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.IOUtils;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.utils.MapObjLocker;
import com.refoler.backend.commons.utils.WebSocketUtil;
import com.refoler.backend.llm.role.UserSession;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import io.ktor.websocket.CloseReason;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class LlmPacketProcess implements PacketProcessModel {

    private static final String LogTAG = "LlmPacketProcess";
    private final HashMap<String, MapObjLocker<UserSession>> userSessionHashMap;

    public static StreamingChatLanguageModel bigModel;
    public static StreamingChatLanguageModel littleModel;

    public LlmPacketProcess() throws IOException {
        userSessionHashMap = new HashMap<>();
        Argument argument = Service.getInstance().getArgument();

        if (argument.llmServerEndpoint == null || argument.llmServerEndpoint.isEmpty()) {
            String openAiKey = IOUtils.readFrom(new File(argument.openAiTokenKey));
            bigModel = OpenAiStreamingChatModel.builder()
                    .apiKey(openAiKey)
                    .modelName(argument.bigModelName)
                    .strictTools(true)
                    .build();

            littleModel = OpenAiStreamingChatModel.builder()
                    .apiKey(openAiKey)
                    .modelName(argument.littleModelName)
                    .strictTools(true)
                    .build();
        } else {
            bigModel = OpenAiStreamingChatModel.builder()
                    .apiKey("Stub!")
                    .baseUrl(argument.llmServerEndpoint)
                    .modelName(argument.bigModelName)
                    .strictTools(false)
                    .build();

            littleModel = OpenAiStreamingChatModel.builder()
                    .apiKey("Stub!")
                    .baseUrl(argument.llmServerEndpoint)
                    .modelName(argument.littleModelName)
                    .strictTools(false)
                    .build();
        }
    }

    @Nullable
    public UserSession getUserRecordMap(String Uid) {
        MapObjLocker<UserSession> userSessionLocker = userSessionHashMap.get(Uid);
        return userSessionLocker == null ? null : userSessionLocker.getLockedObject();
    }

    @Override
    public void onPacketReceived(ApplicationCall applicationCall, String serviceType, String rawData) throws IOException {
        Refoler.RequestPacket requestPacket = PacketWrapper.parseRequestPacket(rawData);
        String message = requestPacket.getExtraData();

        if (message.isEmpty() || requestPacket.getDeviceCount() < 1) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket(PacketConst.ERROR_ILLEGAL_ARGUMENT));
            return;
        }

        UserSession userSession = getUserRecordMap(requestPacket.getUid());
        if (userSession == null) {
            Log.print(LogTAG, "Created New User Session: %s".formatted(requestPacket.getUid()));
            userSession = new UserSession(requestPacket.getUid());
            userSessionHashMap.put(requestPacket.getUid(), new MapObjLocker<UserSession>().setLockedObject(userSession));
        }

        userSession.igniteConversation(applicationCall, requestPacket);
    }

    @Override
    public void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) {
        WebSocketUtil.registerOnDataIncomeSocket(socketServerSession, data -> {
            try {
                Refoler.RequestPacket requestPacket = PacketWrapper.parseRequestPacket(new String(data));
                UserSession userSession = getUserRecordMap(requestPacket.getUid());
                if (userSession != null) {
                    userSession.transferConversation(requestPacket, socketServerSession);
                } else {
                    WebSocketUtil.closeWebSocket(socketServerSession, CloseReason.Codes.CANNOT_ACCEPT, LlmConst.ERROR_CONVERSATION_SESSION_NOT_INITIALIZED);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
