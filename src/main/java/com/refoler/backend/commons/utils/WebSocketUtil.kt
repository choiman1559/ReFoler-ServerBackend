package com.refoler.backend.commons.utils

import io.ktor.server.websocket.*
import io.ktor.util.collections.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.jetbrains.annotations.Nullable

class WebSocketUtil {

    fun interface OnSocketFrameIncomeListener {
        fun onIncoming(data: ByteArray)
    }

    fun interface OnSocketDisconnectListener {
        fun onDisconnect()
    }

    companion object {
        private var onSocketFrameIncomeListener: ConcurrentMap<DefaultWebSocketServerSession, OnSocketFrameIncomeListener> =
            ConcurrentMap()
        private var onSocketDisconnectListener: ConcurrentMap<DefaultWebSocketServerSession, OnSocketDisconnectListener> =
            ConcurrentMap()

        fun getSocketFrameIncomeListener(webSocketServerSession: DefaultWebSocketServerSession): OnSocketFrameIncomeListener? {
            return this.onSocketFrameIncomeListener[webSocketServerSession]
        }

        fun getSocketDisconnectListener(webSocketServerSession: DefaultWebSocketServerSession): OnSocketDisconnectListener? {
            return this.onSocketDisconnectListener[webSocketServerSession]
        }

        @JvmStatic
        fun registerOnDataIncomeSocket(
            webSocketServerSession: DefaultWebSocketServerSession,
            listener: OnSocketFrameIncomeListener
        ) {
            this.onSocketFrameIncomeListener[webSocketServerSession] = listener
        }

        @JvmStatic
        fun registerOnDisconnectSocket(
            webSocketServerSession: DefaultWebSocketServerSession,
            listener: OnSocketDisconnectListener
        ) {
            this.onSocketDisconnectListener[webSocketServerSession] = listener
        }

        @JvmStatic
        fun removeOnDataIncomeSocket(
            webSocketServerSession: DefaultWebSocketServerSession,
        ) {
            if (this.onSocketFrameIncomeListener.containsKey(webSocketServerSession)) {
                this.onSocketFrameIncomeListener.remove(webSocketServerSession)
            }
        }

        @JvmStatic
        fun replyWebSocket(socketServerSession: DefaultWebSocketServerSession, data: String) {
            replyWebSocket(socketServerSession, data.toByteArray())
        }

        @JvmStatic
        fun replyWebSocket(socketServerSession: DefaultWebSocketServerSession, data: ByteArray) {
            runBlocking {
                socketServerSession.send(data)
            }
        }

        @JvmStatic
        fun closeWebSocket(
            socketServerSession: DefaultWebSocketServerSession,
            closeReason: CloseReason.Codes,
            message: String
        ) {
            runBlocking {
                removeOnDataIncomeSocket(socketServerSession)
                socketServerSession.close(CloseReason(closeReason, message))
            }
        }

        @JvmStatic
        fun isSocketActive(@Nullable socketServerSession: DefaultWebSocketServerSession): Boolean {
            return socketServerSession.isActive
        }
    }
}