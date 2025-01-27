package com.refoler.backend.llm.role;

import com.refoler.Refoler;
import com.refoler.backend.commons.consts.LlmConst;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.utils.WebSocketUtil;
import com.refoler.backend.llm.LlmPacketProcess;

import com.refoler.backend.llm.role.query.CommonTools;
import com.refoler.backend.llm.role.query.MasterTools;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.*;
import dev.langchain4j.service.tool.ToolExecution;

import io.ktor.server.websocket.DefaultWebSocketServerSession;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MasterAct {

    private final UserSession currentUserSession;
    private final Refoler.Device requestedDevice;
    private final MasterAssistant masterAssistant;
    public final AtomicBoolean isRunning = new AtomicBoolean(false);
    public final HashMap<String, FinderAct> finderActHashMap = new HashMap<>();
    private DefaultWebSocketServerSession webSocketServerSession;
    private String cachedMessages;

    public MasterAct(UserSession userSession, Refoler.Device requestedDevice) {
        this.currentUserSession = userSession;
        this.requestedDevice = requestedDevice;
        this.masterAssistant = AiServices.builder(MasterAssistant.class)
                .streamingChatLanguageModel(LlmPacketProcess.bigModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(new MasterTools(this), new CommonTools())
                .build();
    }

    public interface MasterAssistant {
        @SuppressWarnings("UnusedReturnValue")
        @SystemMessage("You are a useful assistant in managing the user's files across devices.")
        TokenStream chat(/*@MemoryId int uniqueId,*/ @UserMessage String message);
    }

    public String getUid() {
        return this.currentUserSession.uid;
    }

    public FinderAct getFinderActById(String deviceId) {
        FinderAct finderAct = this.finderActHashMap.get(deviceId);
        if(finderAct == null) {
            finderAct = new FinderAct(getUid());
            this.finderActHashMap.put(deviceId, finderAct);
        }
        return finderAct;
    }

    public void setWebSocketServerSession(DefaultWebSocketServerSession socketServerSession) {
        this.webSocketServerSession = socketServerSession;
        if (!isRunning.get() && !cachedMessages.isEmpty()) {
            WebSocketUtil.replyWebSocket(socketServerSession, cachedMessages);
            WebSocketUtil.replyWebSocket(socketServerSession, LlmConst.RAW_DATA_END_OF_CONVERSATION);

            currentUserSession.messageQueryHashMap.remove(requestedDevice.getDeviceId());
            cachedMessages = "";
        }
    }

    private boolean isSocketAlive() {
        return webSocketServerSession != null && WebSocketUtil.isSocketActive(webSocketServerSession);
    }

    private void cleanUpChat(String message) {
        isRunning.set(false);
        if (isSocketAlive()) {
            currentUserSession.messageQueryHashMap.remove(requestedDevice.getDeviceId(), Integer.toString(message.hashCode()));
        }
    }

    public void performChat(String message) {
        cachedMessages = "";
        TokenStream tokenStream = masterAssistant.chat(/*requestedDevice.getDeviceId().hashCode(),*/ message); //TODO - persistent DB memory
        tokenStream
                .onToolExecuted((ToolExecution toolExecution) -> Log.printDebug("LLM_ToolExecuted", toolExecution.toString()))
                .onError((Throwable error) -> {
                    WebSocketUtil.replyWebSocket(webSocketServerSession, "%s: %s".formatted(LlmConst.RAW_DATA_ERROR_THROWN, error.toString()));
                    cleanUpChat(message);
                    if (Service.getInstance().getArgument().isDebug) {
                        error.printStackTrace();
                    }
                })
                .onNext((String token) -> {
                    if (isSocketAlive()) {
                        WebSocketUtil.replyWebSocket(webSocketServerSession, "%s %s".formatted(cachedMessages, token).trim());
                        cachedMessages = "";
                    } else {
                        cachedMessages += " %s".formatted(token);
                    }
                })
                .onComplete((Response<AiMessage> complete) -> {
                    Log.printDebug("MasterAct_Completed", complete.content().text());
                    cleanUpChat(message);
                    if (isSocketAlive()) {
                        WebSocketUtil.replyWebSocket(webSocketServerSession, LlmConst.RAW_DATA_END_OF_CONVERSATION);
                    }
                });

        tokenStream.start();
        isRunning.set(true);
    }
}
