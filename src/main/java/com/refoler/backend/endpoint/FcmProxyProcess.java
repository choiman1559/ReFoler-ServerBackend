package com.refoler.backend.endpoint;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.protobuf.InvalidProtocolBufferException;
import com.refoler.Refoler;
import com.refoler.backend.commons.consts.EndPointConst;
import com.refoler.backend.commons.consts.LlmConst;
import com.refoler.backend.commons.consts.PacketConst;
import com.refoler.backend.commons.consts.RecordConst;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.GCollectTask;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.MapObjLocker;
import com.refoler.backend.commons.utils.WebSocketUtil;
import com.refoler.backend.endpoint.provider.FirebaseHelper;

import io.ktor.server.application.ApplicationCall;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import io.ktor.websocket.CloseReason;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FcmProxyProcess extends GCollectTask<String> {

    private static FcmProxyProcess instance;
    private final HashMap<String, MapObjLocker<FcmProxyRecord>> fcmProxyRecordHashMap = new HashMap<>();

    public static FcmProxyProcess getInstance() {
        if (instance == null) {
            instance = new FcmProxyProcess();
        }
        return instance;
    }

    public record FcmDataHolder(
            Refoler.Device fromDevice,
            List<Refoler.Device> targetDevices,
            boolean isAgent,
            long registeredDate,
            String message
    ) implements Comparable<FcmDataHolder> {
        public static FcmDataHolder createFrom(Refoler.RequestPacket requestPacket) {
            ArrayList<Refoler.Device> arrayList = new ArrayList<>();
            final long registeredDate = System.currentTimeMillis();
            final Refoler.Device fromDevice = requestPacket.getDevice(0);

            for (int i = 1; i < requestPacket.getDeviceCount(); i += 1) {
                arrayList.add(requestPacket.getDevice(i));
            }

            return new FcmDataHolder(
                    fromDevice, arrayList, isLlmAgentDevice(requestPacket.getDevice(0)),
                    registeredDate, requestPacket.getExtraData()
            );
        }

        public String getIdentifier() {
            JSONObject jsonObject = new JSONObject();
            JSONArray jsonArray = new JSONArray();

            for (Refoler.Device device : targetDevices) {
                jsonArray.put(device.getDeviceId());
            }

            jsonObject.put(EndPointConst.FCM_KEY_SENT_DEVICE, fromDevice.getDeviceId());
            jsonObject.put(EndPointConst.FCM_KEY_DATA_HASH, message.hashCode());
            jsonObject.put(EndPointConst.FCM_KEY_SENT_DATE, registeredDate);
            jsonObject.put(EndPointConst.FCM_KEY_LIST_TARGETS, jsonArray);
            jsonObject.put(EndPointConst.FCM_KEY_IS_AGENT, isAgent);

            return String.format(EndPointConst.PREFIX_FCM_PROXY, jsonObject);
        }

        public boolean isReceiversMine(Refoler.RequestPacket requestPacket) {
            try {
                JSONObject jsonObject = new JSONObject(requestPacket.getExtraData().split("=")[1]);
                if (jsonObject.getString(EndPointConst.FCM_KEY_SENT_DEVICE).equals(fromDevice.getDeviceId())) {
                    return findDeviceIndex(requestPacket.getDevice(0)) >= 0 &&
                            jsonObject.getInt(EndPointConst.FCM_KEY_DATA_HASH) == message.hashCode() &&
                            jsonObject.getLong(EndPointConst.FCM_KEY_SENT_DATE) == registeredDate;
                }
            } catch (Exception e) {
                return false;
            }
            return false;
        }

        public int findDeviceIndex(Refoler.Device deviceToFind) {
            for (int i = 0; i < targetDevices.size(); i += 1) {
                if (targetDevices.get(i).getDeviceId().equals(deviceToFind.getDeviceId())) {
                    return i;
                }
            }
            return -1;
        }

        public void removeDevice(Refoler.Device deviceToRemove) {
            int deviceIndex = findDeviceIndex(deviceToRemove);
            if (deviceIndex >= 0) {
                targetDevices.remove(deviceIndex);
            }
        }

        @Override
        public int compareTo(@NotNull FcmProxyProcess.FcmDataHolder o) {
            return Long.compare(this.registeredDate, o.registeredDate);
        }
    }

    public static boolean isLlmAgentDevice(Refoler.Device device) {
        return device.hasDeviceType() && device.getDeviceType().equals(Refoler.DeviceType.DEVICE_TYPE_AGENT);
    }

    public static class FcmProxyRecord implements GCollectable {
        ConcurrentHashMultiset<FcmDataHolder> fcmMessageHashMap = ConcurrentHashMultiset.create();
        ConcurrentHashMap<String, DefaultWebSocketServerSession> fcmSocketSessionMap = new ConcurrentHashMap<>();

        public Refoler.RequestPacket registerMessage(Refoler.RequestPacket requestPacket) {
            Refoler.RequestPacket.Builder newRequestPacket = Refoler.RequestPacket.newBuilder();
            newRequestPacket.setUid(requestPacket.getUid());
            newRequestPacket.setExtraData(issueMessage(requestPacket));
            return newRequestPacket.build();
        }

        private String issueMessage(Refoler.RequestPacket requestPacket) {
            FcmDataHolder dataHolder = FcmDataHolder.createFrom(requestPacket);

            for (Refoler.Device device : dataHolder.targetDevices()) {
                DefaultWebSocketServerSession socketServerSession = getSocketSessionByDevice(device);
                if (socketServerSession != null && WebSocketUtil.isSocketActive(socketServerSession)) {
                    WebSocketUtil.replyWebSocket(socketServerSession, dataHolder.message());
                    clearDeviceFrom(dataHolder, device);
                }
            }

            if (!dataHolder.targetDevices().isEmpty()) {
                fcmMessageHashMap.add(dataHolder);
            }
            return dataHolder.getIdentifier();
        }

        public void clearDeviceFrom(FcmDataHolder holder, Refoler.Device device) {
            holder.removeDevice(device);
            if (holder.targetDevices().isEmpty()) {
                fcmMessageHashMap.remove(holder);
            }
        }

        public String getMessage(Refoler.RequestPacket requestPacket) {
            FcmDataHolder holder = null;
            for (FcmDataHolder dataHolder : fcmMessageHashMap) {
                if (dataHolder.isReceiversMine(requestPacket)) {
                    holder = dataHolder;
                    break;
                }
            }

            if (holder != null) {
                String message = holder.message();
                clearDeviceFrom(holder, requestPacket.getDevice(0));
                return Objects.requireNonNullElse(message, "");
            }
            return "";
        }

        public List<String> getAllPendingMessages(Refoler.Device device) {
            ArrayList<String> messages = new ArrayList<>();
            ArrayList<FcmDataHolder> keys = new ArrayList<>(fcmMessageHashMap);
            Collections.sort(keys);

            for (FcmDataHolder dataHolder : keys) {
                if (dataHolder.findDeviceIndex(device) >= 0) {
                    messages.add(dataHolder.message());
                    clearDeviceFrom(dataHolder, device);
                }
            }
            return messages;
        }

        public void registerWebSocketSession(Refoler.RequestPacket requestPacket, DefaultWebSocketServerSession serverSession) {
            final Refoler.Device device = requestPacket.getDevice(0);
            DefaultWebSocketServerSession obsoleteSession = getSocketSessionByDevice(device);
            if (obsoleteSession != null && WebSocketUtil.isSocketActive(obsoleteSession)) {
                WebSocketUtil.closeWebSocket(obsoleteSession, CloseReason.Codes.SERVICE_RESTART, EndPointConst.ERROR_RAW_ANOTHER_SESSION_STARTED);
            }

            fcmSocketSessionMap.put(device.getDeviceId(), serverSession);
            List<String> pendingMessages = getAllPendingMessages(device);
            if (!pendingMessages.isEmpty()) {
                for (String message : pendingMessages) {
                    WebSocketUtil.replyWebSocket(serverSession, message);
                }
            }
        }

        public DefaultWebSocketServerSession getSocketSessionByDevice(Refoler.Device device) {
            return fcmSocketSessionMap.get(device.getDeviceId());
        }

        @Override
        public boolean cleanUpCache() {
            final long lifetime = Service.getInstance().getArgument().recordHotRecordLifetime;
            boolean hasRemove = false;

            for (FcmDataHolder holder : fcmMessageHashMap) {
                if (holder.targetDevices().isEmpty() || System.currentTimeMillis() - holder.registeredDate() >= lifetime) {
                    fcmMessageHashMap.remove(holder);
                    hasRemove = true;
                }
            }

            for (String deviceId : fcmSocketSessionMap.keySet()) {
                if (!WebSocketUtil.isSocketActive(fcmSocketSessionMap.get(deviceId))) {
                    fcmSocketSessionMap.remove(deviceId);
                }
            }
            return hasRemove;
        }
    }

    public FcmProxyRecord getFcmRecordByUserId(Refoler.RequestPacket requestPacket) {
        FcmProxyRecord fcmProxyRecord;
        if (fcmProxyRecordHashMap.containsKey(requestPacket.getUid())) {
            fcmProxyRecord = fcmProxyRecordHashMap.get(requestPacket.getUid()).getLockedObject();
        } else {
            fcmProxyRecord = new FcmProxyRecord();
            fcmProxyRecordHashMap.put(requestPacket.getUid(), new MapObjLocker<FcmProxyRecord>().setLockedObject(fcmProxyRecord));
        }
        return fcmProxyRecord;
    }

    public void publishFcmPacket(ApplicationCall applicationCall, Refoler.RequestPacket requestPacket) throws InvalidProtocolBufferException {
        FcmProxyRecord fcmProxyRecord = getFcmRecordByUserId(requestPacket);

        switch (requestPacket.getActionName()) {
            case RecordConst.SERVICE_ACTION_TYPE_GET -> {
                String message = fcmProxyRecord.getMessage(requestPacket);
                PacketWrapper packetWrapper;

                if (message == null || message.isEmpty()) {
                    packetWrapper = PacketWrapper.makeErrorPacket(EndPointConst.ERROR_FCM_CODE_NOT_AVAILABLE);
                } else {
                    packetWrapper = PacketWrapper.makePacket(message);
                }

                Service.replyPacket(applicationCall, packetWrapper);
            }

            case RecordConst.SERVICE_ACTION_TYPE_POST -> {
                String message = requestPacket.getExtraData();
                String fcmResponse;

                if (message.length() >= 2048) {
                    fcmResponse = FirebaseHelper.postFcmMessage(fcmProxyRecord.registerMessage(requestPacket));
                } else {
                    fcmResponse = FirebaseHelper.postFcmMessage(requestPacket);
                }

                PacketWrapper packetWrapper;
                if (fcmResponse != null && !fcmResponse.isEmpty()) {
                    packetWrapper = PacketWrapper.makePacket(fcmResponse);
                } else {
                    packetWrapper = PacketWrapper.makeErrorPacket(PacketConst.ERROR_INTERNAL_ERROR);
                }
                Service.replyPacket(applicationCall, packetWrapper);
            }
        }
    }

    public void handleWebSocketRoute(DefaultWebSocketServerSession socketServerSession) {
        WebSocketUtil.registerOnDataIncomeSocket(socketServerSession, data -> {
            try {
                Refoler.RequestPacket requestPacket = PacketWrapper.parseRequestPacket(new String(data));
                FcmProxyRecord fcmProxyRecord = getFcmRecordByUserId(requestPacket);

                if (fcmProxyRecord != null) {
                    fcmProxyRecord.registerWebSocketSession(requestPacket, socketServerSession);
                } else {
                    WebSocketUtil.closeWebSocket(socketServerSession, CloseReason.Codes.CANNOT_ACCEPT, LlmConst.ERROR_CONVERSATION_SESSION_NOT_INITIALIZED);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public FcmProxyProcess() {
        super();
    }

    @Override
    public long requireGcIgniteInterval() {
        return Service.getInstance().getArgument().recordGcInterval;
    }

    @Override
    public GCollectable requireCollectableFromKey(String key) {
        return fcmProxyRecordHashMap.get(key).getLockedObject();
    }

    @Override
    public @NonNull Set<String> requireKeySet() {
        return fcmProxyRecordHashMap.keySet();
    }
}
