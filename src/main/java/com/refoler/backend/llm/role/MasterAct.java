package com.refoler.backend.llm.role;

import com.refoler.Refoler;
import com.refoler.backend.commons.consts.PacketConst;
import com.refoler.backend.commons.service.Argument;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.JsonRequest;
import com.refoler.backend.commons.consts.RecordConst;
import com.refoler.backend.llm.DeAsyncJob;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import java.util.List;

public class MasterAct {
    public interface MasterAssistant {
        @SystemMessage("You are a useful assistant in managing the user's files across devices.")
        String chat(@MemoryId int uniqueId, @UserMessage String message);
    }

    @SuppressWarnings("HttpUrlsUsage")
    public static class MasterTools {
        @Tool("Retrieves a list of all devices registered to the service.")
        public static List<String> getRegisteredDeviceList() {
            DeAsyncJob.AsyncRunnable<List<String>> getRunnable = (job) -> {
                Refoler.RequestPacket.Builder requestPacket = Refoler.RequestPacket.newBuilder();
                requestPacket.setUid("test_uid01"); //TODO: STUB!!
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
    }
}
