package com.refoler.backend.commons.packet;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.refoler.Refoler;
import com.refoler.backend.commons.consts.PacketConst;
import io.ktor.http.HttpStatusCode;

import java.io.IOException;
import java.util.List;

public class PacketWrapper {
    private HttpStatusCode statusCode;
    private Refoler.ResponsePacket refolerPacket;

    public void setStatusCode(HttpStatusCode statusCode) {
        this.statusCode = statusCode;
    }

    public void setRefolerPacket(Refoler.ResponsePacket refolerPacket) {
        this.refolerPacket = refolerPacket;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public Refoler.ResponsePacket getRefolerPacket() {
        return refolerPacket;
    }

    public String getSerializedData() throws InvalidProtocolBufferException {
        return JsonFormat.printer().print(getRefolerPacket());
    }

    public static PacketWrapper makePacket(String... extra_data) {
        PacketWrapper packetWrapper = new PacketWrapper();
        Refoler.ResponsePacket.Builder refolerData = Refoler.ResponsePacket.newBuilder();

        refolerData.setStatus(PacketConst.STATUS_OK);
        refolerData.setErrorCause(PacketConst.ERROR_NONE);

        if(extra_data.length > 0) {
            refolerData.addAllExtraData(List.of(extra_data));
        }

        packetWrapper.setStatusCode(HttpStatusCode.Companion.getOK());
        packetWrapper.setRefolerPacket(refolerData.build());
        return packetWrapper;
    }

    public static PacketWrapper makeErrorPacket(String message) {
        return makeErrorPacket(message, "");
    }

    public static PacketWrapper makeErrorPacket(String message, String... extraDescription) {
        return makeErrorPacket(message, HttpStatusCode.Companion.getInternalServerError(), extraDescription);
    }

    public static PacketWrapper makeErrorPacket(String message, HttpStatusCode statusCode, String... extraDescription) {
        PacketWrapper packetWrapper = new PacketWrapper();
        Refoler.ResponsePacket.Builder refolerData = Refoler.ResponsePacket.newBuilder();

        refolerData.setStatus(PacketConst.STATUS_ERROR);
        refolerData.setErrorCause(message);

        if(extraDescription.length > 0) {
            refolerData.addAllExtraData(List.of(extraDescription));
        }

        packetWrapper.setStatusCode(statusCode);
        packetWrapper.setRefolerPacket(refolerData.build());
        return packetWrapper;
    }

    public static Refoler.ResponsePacket parseResponsePacket(String rawData) throws IOException {
        Refoler.ResponsePacket.Builder responsePacketBuilder = Refoler.ResponsePacket.newBuilder();
        JsonFormat.parser().merge(rawData, responsePacketBuilder);
        return responsePacketBuilder.build();
    }

    public static Refoler.RequestPacket parseRequestPacket(String rawData) throws IOException {
        Refoler.RequestPacket.Builder requestPacketBuilder = Refoler.RequestPacket.newBuilder();
        JsonFormat.parser().merge(rawData, requestPacketBuilder);
        return requestPacketBuilder.build();
    }
}
