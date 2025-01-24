package com.refoler.backend.llm;

import com.refoler.Refoler;
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
import dev.langchain4j.model.localai.LocalAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class LlmPacketProcess implements PacketProcessModel {

    private static final String LogTAG = "LlmPacketProcess";
    private final HashMap<String, MapObjLocker<UserSession>> userSessionHashMap;

    public final StreamingChatLanguageModel bigModel;
    public final StreamingChatLanguageModel littleModel;

    public LlmPacketProcess() throws IOException {
        userSessionHashMap = new HashMap<>();
        Argument argument = Service.getInstance().getArgument();
        String openAiKey = IOUtils.readFrom(new File(argument.openAiTokenKey));

        if (argument.llmServerEndpoint == null || argument.llmServerEndpoint.isEmpty()) {
            bigModel = OpenAiStreamingChatModel.builder()
                    .apiKey(openAiKey)
                    .modelName(argument.bigModelName)
                    .build();

            littleModel = OpenAiStreamingChatModel.builder()
                    .apiKey(openAiKey)
                    .modelName(argument.littleModelName)
                    .build();
        } else {
            bigModel = LocalAiStreamingChatModel.builder()
                    .baseUrl(argument.llmServerEndpoint)
                    .modelName(argument.bigModelName)
                    .build();

            littleModel = LocalAiStreamingChatModel.builder()
                    .baseUrl(argument.llmServerEndpoint)
                    .modelName(argument.littleModelName)
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
    public void onWebSocketSessionConnected(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) throws Exception {
        WebSocketUtil.registerOnDataIncomeSocket(socketServerSession, data -> {
            String message = new String(data);
            Log.printDebug(LogTAG,  message);
            WebSocketUtil.replyWebSocket(socketServerSession, "You said: %s".formatted(message));
        });
    }
}
