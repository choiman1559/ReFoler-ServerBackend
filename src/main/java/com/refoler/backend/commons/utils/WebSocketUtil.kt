package com.refoler.backend.commons.utils

import io.ktor.server.websocket.*
import io.ktor.util.collections.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking

class WebSocketUtil {

    fun interface OnSocketFrameIncomeListener {
        fun onIncoming(data: ByteArray)
    }

    companion object {
        private var onSocketFrameIncomeListener : ConcurrentMap<DefaultWebSocketServerSession, OnSocketFrameIncomeListener> = ConcurrentMap()

        fun getSocketFrameIncomeListener(webSocketServerSession: DefaultWebSocketServerSession) : OnSocketFrameIncomeListener? {
            return this.onSocketFrameIncomeListener[webSocketServerSession]
        }

        @JvmStatic
        fun registerOnDataIncomeSocket(webSocketServerSession: DefaultWebSocketServerSession, listener: OnSocketFrameIncomeListener) {
            this.onSocketFrameIncomeListener[webSocketServerSession] = listener
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
                socketServerSession.close(CloseReason(closeReason, message))
            }
            this.onSocketFrameIncomeListener.remove(socketServerSession)
        }
    }
}