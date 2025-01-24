package com.refoler.backend.commons.modules

import com.refoler.backend.commons.consts.PacketConst
import com.refoler.backend.commons.service.Service
import com.refoler.backend.commons.utils.Log
import com.refoler.backend.commons.utils.WebSocketUtil

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

const val LOG_TAG = "rawSocket"
val WEBSOCKET_ENABLED = Service.getInstance().argument.webSocketEnabled

@Suppress("unused")
fun Application.configureSockets() {
    if (!WEBSOCKET_ENABLED) {
        return
    }

    Log.print("WebSocket", "Websocket Enabled!!!")
    install(WebSockets) {
        pingPeriod = 20.toDuration(DurationUnit.SECONDS)
        timeout = 30.toDuration(DurationUnit.SECONDS)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        webSocket(PacketConst.API_ROUTE_SCHEMA) {
            if (call.parameters["version"] == "v1") {
                val serviceType: String = call.parameters["service_type"].toString()
                val socketSession = this

                CoroutineScope(Dispatchers.IO).launch {
                    Log.printDebug(LOG_TAG, "Connected: $socketSession")
                    Service.invokeProcessWebSocketPacket(call, serviceType, socketSession)
                }

                for(frame in incoming) {
                    Log.printDebug(LOG_TAG, String(frame.readBytes()))
                    val listener = WebSocketUtil.getSocketFrameIncomeListener(socketSession)
                    listener?.onIncoming(frame.readBytes())
                }
            } else {
                WebSocketUtil.closeWebSocket(this, CloseReason.Codes.PROTOCOL_ERROR, PacketConst.ERROR_ILLEGAL_ARGUMENT)
            }
        }
    }
}