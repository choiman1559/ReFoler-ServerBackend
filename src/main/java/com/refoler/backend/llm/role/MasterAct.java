package com.refoler.backend.llm.role;

import com.refoler.Refoler;
import com.refoler.backend.commons.consts.LlmConst;
import com.refoler.backend.commons.consts.PacketConst;
import com.refoler.backend.commons.service.Argument;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.JsonRequest;
import com.refoler.backend.commons.consts.RecordConst;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.utils.WebSocketUtil;
import com.refoler.backend.llm.DeAsyncJob;
import com.refoler.backend.llm.LlmPacketProcess;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.*;
import dev.langchain4j.service.tool.ToolExecution;
import io.ktor.server.websocket.DefaultWebSocketServerSession;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MasterAct {

    private final UserSession currentUserSession;
    private final Refoler.Device requestedDevice;
    private final MasterAssistant masterAssistant;
    public final AtomicBoolean isRunning = new AtomicBoolean(false);
    private DefaultWebSocketServerSession webSocketServerSession;
    private String cachedMessages;

    public MasterAct(UserSession userSession, Refoler.Device requestedDevice) {
        this.currentUserSession = userSession;
        this.requestedDevice = requestedDevice;
        this.masterAssistant = AiServices.builder(MasterAssistant.class)
                .streamingChatLanguageModel(LlmPacketProcess.bigModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .tools(new MasterTools(currentUserSession.uid))
                .build();
    }

    public interface MasterAssistant {
        @SuppressWarnings("UnusedReturnValue")
        @SystemMessage("You are a useful assistant in managing the user's files across devices.")
        TokenStream chat(/*@MemoryId int uniqueId, */@UserMessage String message);
    }

    @SuppressWarnings({"HttpUrlsUsage", "unused"})
    private record MasterTools(String UID) {
        @Tool("Retrieves a list of all devices registered to the service. " +
                "Note that element \"last_queried_time\" is unix time, " +
                "therefore you should convert it to human-readable format before print.")
        public List<String> getRegisteredDeviceList() {
            DeAsyncJob.AsyncRunnable<List<String>> getRunnable = (job) -> {
                Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
                requestPacket.setUid(UID);
                requestPacket.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);

                Argument argument = Service.getInstance().getArgument();
                String url = "http://%s:%s%s".formatted(argument.recordNodeHost, argument.recordNodePort,
                        PacketConst.API_ROUTE_SCHEMA
                                .replace("{version}", "v1")
                                .replace("{service_type}", RecordConst.SERVICE_TYPE_DEVICE_REGISTRATION));

                JsonRequest.postRequestPacket(url, requestPacket.build(),
                        receivedPacket -> job.setResult(receivedPacket.getRefolerPacket().getExtraDataList().stream().toList()));
            };

            DeAsyncJob<List<String>> getJob = new DeAsyncJob<>(getRunnable);
            return getJob.runAndWait();
        }

        @Tool("Converts unix time value to human-readable format")
        public String unixTimeToHumanReadable(@P("unix time value to convert") long unixTime) {
            return new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new Date(unixTime));
        }
    }

    public void setWebSocketServerSession(DefaultWebSocketServerSession socketServerSession) {
        this.webSocketServerSession = socketServerSession;
        if(!isRunning.get() && !cachedMessages.isEmpty()) {
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
        if(isSocketAlive()) {
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
                    if(Service.getInstance().getArgument().isDebug) {
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
