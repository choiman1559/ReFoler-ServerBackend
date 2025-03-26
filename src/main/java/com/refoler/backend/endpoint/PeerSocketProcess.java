package com.refoler.backend.endpoint;

import com.refoler.Refoler;
import com.refoler.backend.commons.consts.EndPointConst;
import com.refoler.backend.commons.packet.PacketWrapper;
import com.refoler.backend.commons.service.GCollectTask;
import com.refoler.backend.commons.service.Service;
import com.refoler.backend.commons.utils.Log;
import com.refoler.backend.commons.utils.MapObjLocker;
import com.refoler.backend.commons.utils.WebSocketUtil;
import io.ktor.server.websocket.DefaultWebSocketServerSession;
import io.ktor.websocket.CloseReason;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PeerSocketProcess extends GCollectTask<String> {

    private final static String LogTAG = "PeerSocketProcess";
    private static PeerSocketProcess instance;
    private final HashMap<String, MapObjLocker<PeerSession>> peerSocketMap = new HashMap<>();
    private final ConcurrentLinkedQueue<HeaderWaitingPeer> headerWaitSocket = new ConcurrentLinkedQueue<>();

    public static class SocketPeer {
        protected long encounterTime;
        protected DefaultWebSocketServerSession connectSession;

        public boolean isSessionExpired() {
            final long lifetime = Service.getInstance().getArgument().recordHotRecordLifetime;
            return !WebSocketUtil.isSocketActive(connectSession) || System.currentTimeMillis() - encounterTime >= lifetime;
        }

        public void cleanUp() {
            if (WebSocketUtil.isSocketActive(connectSession)) {
                WebSocketUtil.closeWebSocket(connectSession, CloseReason.Codes.TRY_AGAIN_LATER, callControlPacket(EndPointConst.ERROR_RAW_SESSION_EXPIRED_TIMEOUT));
            }
        }
    }

    public static class PeerInfo extends SocketPeer {
        private Refoler.Device localDevice;
        private Refoler.Device remoteDevice;
        private String challengeCode;

        public static PeerInfo createFrom(Refoler.RequestPacket requestPacket, DefaultWebSocketServerSession connectSession) {
            PeerInfo peerInfo = new PeerInfo();
            peerInfo.localDevice = requestPacket.getDevice(0);
            peerInfo.remoteDevice = requestPacket.getDevice(1);
            peerInfo.challengeCode = requestPacket.getExtraData();
            peerInfo.encounterTime = System.currentTimeMillis();
            peerInfo.connectSession = connectSession;
            return peerInfo;
        }

        public boolean isEncounter(PeerInfo peerInfo) {
            return peerInfo.challengeCode.equals(challengeCode) &&
                    peerInfo.remoteDevice.getDeviceId().equals(localDevice.getDeviceId()) &&
                    peerInfo.localDevice.getDeviceId().equals(remoteDevice.getDeviceId());
        }
    }

    public static class PeerSession implements GCollectable {

        private final ConcurrentLinkedQueue<PeerInfo> peerInfos = new ConcurrentLinkedQueue<>();

        public void registerPeer(PeerInfo peerInfo) {
            Log.printDebug(LogTAG, "Socket Registered: %s => Code: %s, Device: %s".formatted(peerInfo.connectSession.toString(), peerInfo.challengeCode, peerInfo.localDevice.getDeviceId()));
            for (PeerInfo otherPeer : peerInfos) {
                if (otherPeer.isEncounter(peerInfo)) {
                    peerInfos.remove(otherPeer);
                    linkPeer(peerInfo, otherPeer);
                    return;
                }
            }
            peerInfos.add(peerInfo);
        }

        private void linkPeer(PeerInfo peerFoo, PeerInfo peerBar) {
            Log.print(LogTAG, "Socket Peer (code %s) Connected: %s <--> %s".formatted(peerFoo.challengeCode, peerFoo.localDevice.getDeviceId(), peerBar.localDevice.getDeviceId()));
            WebSocketUtil.replyWebSocket(peerFoo.connectSession, PeerSocketProcess.callControlPacket(EndPointConst.FILE_PART_CONTROL_PEER_ACK));
            WebSocketUtil.replyWebSocket(peerBar.connectSession, PeerSocketProcess.callControlPacket(EndPointConst.FILE_PART_CONTROL_PEER_ACK));
            registerOnDisconnect(peerBar, peerFoo);
            registerOnDisconnect(peerFoo, peerBar);
        }

        private void registerOnDisconnect(PeerInfo peerFoo, PeerInfo peerBar) {
            WebSocketUtil.registerOnDataIncomeSocket(peerBar.connectSession, data -> {
                if(WebSocketUtil.isSocketActive(peerFoo.connectSession)) {
                    WebSocketUtil.replyWebSocket(peerFoo.connectSession, data);
                }
            });

            WebSocketUtil.registerOnDisconnectSocket(peerBar.connectSession, () -> {
                Log.printDebug(LogTAG, "Socket (code %s) Disconnected requested by: %s".formatted(peerBar.challengeCode, peerBar.localDevice.getDeviceId()));
                WebSocketUtil.removeOnDataIncomeSocket(peerBar.connectSession);
                WebSocketUtil.removeOnDisconnectSocket(peerBar.connectSession);

                if (WebSocketUtil.isSocketActive(peerFoo.connectSession)) {
                    Log.printDebug(LogTAG, "Trying to disconnect forcefully; Session: %s".formatted(peerFoo.connectSession));
                    WebSocketUtil.closeWebSocket(peerFoo.connectSession, CloseReason.Codes.GOING_AWAY, PeerSocketProcess.callControlPacket(EndPointConst.FILE_PART_CONTROL_PEER_DISCONNECTED));
                }

                peerBar.connectSession = null;
                peerFoo.connectSession = null;
            });
        }

        @Override
        public boolean cleanUpCache() {
            boolean hasCleaned = false;
            for (PeerInfo peerInfo : peerInfos) {
                if (peerInfo.isSessionExpired()) {
                    peerInfo.cleanUp();
                    peerInfos.remove(peerInfo);
                    hasCleaned = true;
                } else for (PeerInfo otherPeer : peerInfos) {
                    if (!otherPeer.equals(peerInfo) && otherPeer.isEncounter(peerInfo)) {
                        peerInfos.remove(peerInfo);
                        peerInfos.remove(otherPeer);
                        linkPeer(peerInfo, otherPeer);
                    }
                }
            }
            return hasCleaned;
        }
    }

    public static class HeaderWaitingPeer extends SocketPeer {

        private OnHeaderEncountered onHeaderEncountered;

        public interface OnHeaderEncountered {
            void onEncounter(String uid, PeerInfo peerInfo);
        }

        public static HeaderWaitingPeer createNewQuery(DefaultWebSocketServerSession webSocketServerSession) {
            HeaderWaitingPeer headerWaitingPeer = new HeaderWaitingPeer();
            headerWaitingPeer.connectSession = webSocketServerSession;
            headerWaitingPeer.encounterTime = System.currentTimeMillis();

            WebSocketUtil.removeOnDataIncomeSocket(webSocketServerSession);
            WebSocketUtil.registerOnDataIncomeSocket(webSocketServerSession, data -> {
                Log.printDebug(LogTAG, "Receiving raw handshake packet of: %s".formatted(webSocketServerSession.toString()));
                String dataString = new String(data);
                if (dataString.startsWith(EndPointConst.FILE_PART_CONTROL_PREFIX) &&
                        dataString.contains(EndPointConst.FILE_PART_CONTROL_HEADER_ACK)) {
                    try {
                        Refoler.RequestPacket requestPacket = PacketWrapper.parseRequestPacket(dataString.split(EndPointConst.FILE_PART_CONTROL_SEPARATOR)[1]);
                        WebSocketUtil.replyWebSocket(headerWaitingPeer.connectSession, PeerSocketProcess.callControlPacket(EndPointConst.FILE_PART_CONTROL_HEADER_OK));
                        headerWaitingPeer.callOnHeaderEncountered(requestPacket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            return headerWaitingPeer;
        }

        protected void callOnHeaderEncountered(Refoler.RequestPacket requestPacket) {
            WebSocketUtil.removeOnDataIncomeSocket(connectSession);
            if (onHeaderEncountered != null) {
                onHeaderEncountered.onEncounter(requestPacket.getUid(), PeerInfo.createFrom(requestPacket, connectSession));
            }
        }

        public void setOnHeaderEncountered(OnHeaderEncountered onHeaderEncountered) {
            this.onHeaderEncountered = onHeaderEncountered;
        }
    }

    public void handleEncounterSocket(DefaultWebSocketServerSession webSocketServerSession) {
        Log.printDebug(LogTAG, "New Socket Encountered: %s".formatted(webSocketServerSession.toString()));
        HeaderWaitingPeer headerWaitingPeer = HeaderWaitingPeer.createNewQuery(webSocketServerSession);
        headerWaitingPeer.setOnHeaderEncountered((uid, peerInfo) -> {
            getPeerSessionByUserId(uid).registerPeer(peerInfo);
            headerWaitSocket.remove(headerWaitingPeer);
        });
        headerWaitSocket.add(headerWaitingPeer);
    }

    public PeerSession getPeerSessionByUserId(String Uid) {
        PeerSession peerSession;
        if (peerSocketMap.containsKey(Uid)) {
            peerSession = peerSocketMap.get(Uid).getLockedObject();
        } else {
            peerSession = new PeerSession();
            peerSocketMap.put(Uid, new MapObjLocker<PeerSession>().setLockedObject(peerSession));
        }
        return peerSession;
    }

    private static String callControlPacket(String message) {
        return "%s%s".formatted(EndPointConst.FILE_PART_CONTROL_PREFIX, message);
    }

    public PeerSocketProcess() {
        super();
    }

    public static PeerSocketProcess getInstance() {
        if (instance == null) {
            instance = new PeerSocketProcess();
        }
        return instance;
    }

    @Override
    public void onGCollectPerform() {
        super.onGCollectPerform();

        for (HeaderWaitingPeer headerWaitingPeer : headerWaitSocket) {
            if (headerWaitingPeer.isSessionExpired()) {
                headerWaitingPeer.cleanUp();
                headerWaitSocket.remove(headerWaitingPeer);
            }
        }
    }

    @Override
    public long requireGcIgniteInterval() {
        return Service.getInstance().getArgument().recordGcInterval;
    }

    @Override
    public GCollectable requireCollectableFromKey(String key) {
        return peerSocketMap.get(key).getLockedObject();
    }

    @Override
    public @NonNull Set<String> requireKeySet() {
        return peerSocketMap.keySet();
    }

    @Override
    public String requireLogTag() {
        return LogTAG;
    }
}