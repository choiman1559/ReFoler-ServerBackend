package com.refoler.backend.llm.role;

import com.refoler.Refoler;
import com.refoler.backend.commons.consts.LlmConst;
import com.refoler.backend.commons.service.GCollectTask;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.utils.WebSocketUtil;
import com.refoler.backend.llm.LlmPacketProcess;

import com.refoler.backend.llm.role.query.CommonTools;
import com.refoler.backend.llm.role.query.MasterTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.*;
import dev.langchain4j.service.tool.ToolExecution;

import io.ktor.server.websocket.DefaultWebSocketServerSession;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class MasterAct implements GCollectTask.GCollectable {

    private final UserSession currentUserSession;
    private final Refoler.Device requestedDevice;
    private final MasterAssistant masterAssistant;
    public final AtomicBoolean isRunning = new AtomicBoolean(false);
    public final HashMap<String, FinderAct> finderActHashMap = new HashMap<>();
    private DefaultWebSocketServerSession webSocketServerSession;
    private String cachedMessages;
    private volatile long lastStartedTime;

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
        @SystemMessage("You are a useful assistant in managing the user's files across devices. " +
                "Note that You must check the device list in advance before performing any file operations. " +
                "Keep in mind that you should not expose device id to user directly since its very sensitive information. " +
                "Additionally, When you indicating a path, you must wrap exact path with markdown link with device id, (seperated by \"" + LlmConst.RAW_DATA_PATH_TOKEN + "\") " +
                "For example, [/some/location/file.txt](/some/location/file.txt" + LlmConst.RAW_DATA_PATH_TOKEN + "some_device_id)")
        TokenStream chat(/*@MemoryId int uniqueId,*/ @UserMessage String message);
    }

    public Refoler.Device getRequestedDevice() {
        return requestedDevice;
    }

    public String getUid() {
        return this.currentUserSession.uid;
    }

    public FinderAct getFinderActById(String deviceId) {
        FinderAct finderAct = this.finderActHashMap.get(deviceId);
        if (finderAct == null) {
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
        lastStartedTime = System.currentTimeMillis();

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
                .onPartialResponse((String token) -> {
                    token = token
                            .replace(System.lineSeparator(), LlmConst.RAW_DATA_LINE_SEPARATION)
                            .replace(" ", LlmConst.RAW_DATA_SPACE);

                    if (token.isEmpty()) {
                        token = LlmConst.RAW_DATA_SPACE;
                    }

                    if (isSocketAlive()) {
                        WebSocketUtil.replyWebSocket(webSocketServerSession, "%s%s".formatted(cachedMessages, token).trim());
                        cachedMessages = "";
                    } else {
                        cachedMessages += "%s".formatted(token);
                    }
                })
                .onCompleteResponse(chatResponse -> {
                    Log.printDebug("MasterAct_Completed", chatResponse.aiMessage().text());
                    cleanUpChat(message);
                    if (isSocketAlive()) {
                        WebSocketUtil.replyWebSocket(webSocketServerSession, LlmConst.RAW_DATA_END_OF_CONVERSATION);
                    }
                });

        tokenStream.start();
        isRunning.set(true);
    }

    @Override
    public boolean cleanUpCache() {
        if (!isRunning.get() && !isSocketAlive() &&
                cachedMessages != null && !cachedMessages.isEmpty() &&
                (Service.getInstance().getArgument().recordHotRecordLifetime + lastStartedTime) < System.currentTimeMillis()) {
            currentUserSession.messageQueryHashMap.remove(requestedDevice.getDeviceId());
            cachedMessages = "";
            return true;
        }
        return false;
    }
}
