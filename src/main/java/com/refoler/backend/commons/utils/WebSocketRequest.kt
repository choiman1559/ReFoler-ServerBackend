package com.refoler.backend.commons.utils

import com.refoler.backend.commons.utils.WebSocketUtil.Companion.registerOnDataIncomeSocket
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*

import java.util.concurrent.atomic.AtomicReference

class WebSocketRequest {
    companion object {
        private const val PING_INTERVAL: Long = 20_000

        @JvmStatic
        fun handleWebSocketProxy(
            socketServerSession: DefaultWebSocketServerSession,
            host: String,
            port: Int,
            path: String
        ) {
            runBlocking {
                val client = HttpClient(CIO) {
                    install(WebSockets) {
                        pingIntervalMillis = PING_INTERVAL
                    }
                }

                val sessionAtomic = AtomicReference<WebSocketSession>()
                launch {
                    registerOnDataIncomeSocket(socketServerSession,
                        (WebSocketUtil.OnSocketFrameIncomeListener { data: ByteArray ->
                            runBlocking {
                                if (sessionAtomic.get() != null) {
                                    sessionAtomic.get().send(data)
                                }
                            }
                        })
                    )
                }

                CoroutineScope(Dispatchers.IO).launch {
                    while(socketServerSession.isActive) {
                        delay(PING_INTERVAL)
                    }

                    val clientSessionAtomic = sessionAtomic.get()
                    if(clientSessionAtomic != null && clientSessionAtomic.isActive) {
                        clientSessionAtomic.close()
                    }
                }

                client.webSocket (
                    method = HttpMethod.Get,
                    host = host, port = port, path = path
                ) {
                    val clientSession = this
                    sessionAtomic.set(clientSession)

                    for (received in clientSession.incoming) {
                        socketServerSession.send(received.readBytes())
                    }
                }
            }
        }
    }
}