package com.refoler.backend.llm.role;

import com.refoler.Refoler;
import com.refoler.backend.commons.service.Argument;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.JsonRequest;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.llm.DeAsyncJob;
import com.refoler.backend.llm.LlmPacketProcess;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolExecution;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FinderAct {

    private final String LogTag = "FinderAct";
    private final FinderAssistant finderAssistant;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public FinderAct(String UID) {
        this.finderAssistant = AiServices.builder(FinderAssistant.class)
                .streamingChatLanguageModel(LlmPacketProcess.littleModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(3))
                .tools(new FinderTools(UID))
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

    public DeAsyncJob<String> performAnalysisTask(String deviceId, String command) {
        if (isRunning.get()) {
            return new DeAsyncJob<>((job -> job.setResult("Error: Another analysis is already processing. Abort.")));
        }

        String message = ("Retrieves full device file list from device id: \"%s\", And then %s.")
                .formatted(String.join(", ", deviceId), command);

        isRunning.set(true);
        DeAsyncJob.AsyncRunnable<String> chatProcess = (job) -> finderAssistant.chat(message)
                .onNext((_) -> { /* Nothing To Do */ })
                .onToolExecuted((ToolExecution toolExecution) -> Log.printDebug(LogTag, "ToolExecuted: %s".formatted(toolExecution.toString())))
                .onComplete((Response<AiMessage> response) -> {
                    job.setResult(response.content().text());
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
