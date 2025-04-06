package com.refoler.backend.commons.service;

import com.google.protobuf.InvalidProtocolBufferException;
import com.refoler.backend.commons.packet.PacketProcessModel;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.dbms.DbPacketProcess;
import com.refoler.backend.endpoint.EndPointPacketProcess;
import com.refoler.backend.llm.LlmPacketProcess;
import io.ktor.http.HttpStatusCode;
import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;

public class Service {
    private volatile static Service instance;
    private final Argument argument;
    public final ServiceStatusHandler serviceStatusHandler;
    public PacketProcessModel packetProcessModel;

    public interface onPacketProcessReplyReceiver {
        void onPacketReply(ApplicationCall call, HttpStatusCode code, String data);
    }

    public onPacketProcessReplyReceiver mOnPacketProcessReplyReceiver;

    private Service(Argument argument) {
        this.argument = argument;
        this.serviceStatusHandler = new ServiceStatusHandler();
    }

    public static synchronized void configureServiceInstance(Argument argument) throws ReflectiveOperationException {
        instance = new Service(argument);
        Class<?> packetProcessCls = switch (argument.operationMode) {
            case Argument.OPERATION_MODE_ENDPOINT -> EndPointPacketProcess.class;
            case Argument.OPERATION_MODE_DBMS -> DbPacketProcess.class;
            case Argument.OPERATION_MODE_LLM -> LlmPacketProcess.class;
            default -> throw new IllegalArgumentException("Operation Mode %d is not valid value.".formatted(argument.operationMode));
        };
        instance.packetProcessModel = (PacketProcessModel) packetProcessCls.getDeclaredConstructor().newInstance();
    }

    public static synchronized Service getInstance() {
        if (instance == null) {
            throw new NullPointerException("Service Instance is not initialized!");
        }
        return instance;
    }

    public Argument getArgument() {
        return argument;
    }

    public static void invokeProcessPacket(ApplicationCall applicationCall, String serviceType, String rawData) throws Exception {
        if (rawData == null || rawData.isEmpty()) {
            Service.replyPacket(applicationCall, PacketWrapper.makeErrorPacket("HTTP Request body is null", HttpStatusCode.Companion.getNoContent()));
        } else if (instance.packetProcessModel != null) {
            instance.packetProcessModel.onPacketReceived(applicationCall, serviceType, rawData);
        }
    }

    public static void replyPacket(ApplicationCall call, PacketWrapper data) throws InvalidProtocolBufferException {
        Service mInstance = Service.getInstance();
        if (mInstance != null && mInstance.mOnPacketProcessReplyReceiver != null) {
            mInstance.mOnPacketProcessReplyReceiver.onPacketReply(call, data.getStatusCode(), data.getSerializedData());
        }
    }

    public static void invokeProcessWebSocketPacket(ApplicationCall applicationCall, String serviceType, DefaultWebSocketServerSession socketServerSession) throws Exception {
        if (instance.packetProcessModel != null) {
            instance.packetProcessModel.onWebSocketSessionConnected(applicationCall, serviceType, socketServerSession);
        }
    }
}
