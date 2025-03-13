package com.refoler.backend.llm.role;

import com.google.protobuf.util.JsonFormat;
import com.refoler.Refoler;
import com.refoler.backend.commons.consts.DirectActionConst;
import com.refoler.backend.commons.consts.EndPointConst;
import com.refoler.backend.commons.consts.PacketConst;
import com.refoler.backend.commons.consts.RecordConst;
import com.refoler.backend.commons.service.Argument;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.JsonRequest;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.llm.DeAsyncJob;
import com.refoler.backend.llm.LlmPacketProcess;
import com.refoler.backend.llm.role.query.FinderTools;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecution;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class FinderAct {

    private final String LogTag = "FinderAct";
    private final FinderAssistant finderAssistant;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public FinderAct(String UID) {
        final String agentUUID = UUID.randomUUID().toString();
        this.finderAssistant = AiServices.builder(FinderAssistant.class)
                .streamingChatLanguageModel(LlmPacketProcess.littleModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(3))
                .tools(new FinderTools(UID, agentUUID))
                .build();
    }

    public interface FinderAssistant {
        @SuppressWarnings("UnusedReturnValue")
        @SystemMessage("You are a useful assistant in perform command based on given device's file and folder list data.")
        TokenStream chat(@UserMessage String message);
    }

    public static DeAsyncJob<List<String>> requestRecordQuery(Refoler.RequestPacket requestPacket, String actionType) {
        Argument argument = Service.getInstance().getArgument();
        DeAsyncJob.AsyncRunnable<List<String>> getRunnable = (job) -> JsonRequest.postRequestPacket(
                JsonRequest.buildIpcUrl(argument.recordNodeHost, argument.recordNodePort, actionType),
                requestPacket,
                receivedPacket -> job.setResult(receivedPacket.getRefolerPacket().getExtraDataList().stream().toList()));
        return new DeAsyncJob<>(getRunnable);
    }

    public static DeAsyncJob<Boolean> requestFcmPost(Refoler.RequestPacket requestPacket, String actionType) {
        Argument argument = Service.getInstance().getArgument();
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(DirectActionConst.KEY_ACTION_TYPE, actionType);
            jsonObject.put(DirectActionConst.KEY_ACTION_SIDE, DirectActionConst.ACTION_SIDE_REQUESTER);
            jsonObject.put(DirectActionConst.KEY_REQUEST_PACKET, JsonFormat.printer().print(requestPacket));

            Refoler.RequestPacket.Builder requestWrapper = Refoler.RequestPacket.newBuilder();
            requestWrapper.setActionName(RecordConst.SERVICE_ACTION_TYPE_POST);
            requestWrapper.addDevice(requestPacket.getDevice(0));
            requestWrapper.addDevice(requestPacket.getDevice(1));
            requestWrapper.setExtraData(jsonObject.toString());

            DeAsyncJob.AsyncRunnable<Boolean> getRunnable = (job) -> JsonRequest.postRequestPacket(
                    JsonRequest.buildIpcUrl(argument.endpointNodeHost, argument.endpointNodePort, EndPointConst.SERVICE_TYPE_FCM_POST),
                    requestWrapper.build(),
                    receivedPacket -> job.setResult(receivedPacket.getRefolerPacket().getStatus().equals(PacketConst.STATUS_OK)));
            return new DeAsyncJob<>(getRunnable);
        } catch (Exception e) {
            if (argument.isDebug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public DeAsyncJob<String> performAnalysisTask(String deviceId, String command) {
        if (isRunning.get()) {
            return new DeAsyncJob<>((job -> job.setResult("Error: Another analysis is already processing. Abort.")));
        }

        String message = ("Retrieves full device file list from device id: \"%s\", And then %s.")
                .formatted(String.join(", ", deviceId), command);

        isRunning.set(true);
        DeAsyncJob.AsyncRunnable<String> chatProcess = (job) -> finderAssistant.chat(message)
                .onPartialResponse((_) -> { /* Nothing To Do */ })
                .onToolExecuted((ToolExecution toolExecution) -> Log.printDebug(LogTag, "ToolExecuted: %s".formatted(toolExecution.toString())))
                .onCompleteResponse((chatResponse) -> {
                    job.setResult(chatResponse.aiMessage().text());
                    isRunning.set(false);
                })
                .onError((Throwable throwable) -> {
                    Log.printDebug(LogTag, "Finder Throws Exception: %s".formatted(throwable.toString()));
                    isRunning.set(false);
                    job.setResult("Error while processing analysis. Abort.");
                }).start();
        return new DeAsyncJob<>(chatProcess);
    }
}
