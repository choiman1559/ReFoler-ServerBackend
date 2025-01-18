package com.refoler.backend.commons.modules

import com.refoler.backend.commons.service.Service
import com.refoler.backend.commons.packet.PacketConst
import com.refoler.backend.commons.packet.PacketWrapper
import com.refoler.backend.commons.utils.Log
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

suspend fun doProcessPacket(call: ApplicationCall) {
    if (call.parameters["version"] == "v1") {
        val serviceType: String = call.parameters["service_type"].toString()
        Service.invokeProcessPacket(call, serviceType, call.receiveText())
    } else {
        Service.replyPacket(call, PacketWrapper.makeErrorPacket(
            PacketConst.ERROR_ILLEGAL_ARGUMENT))
    }
}

fun Application.configureRouting() {
    val service: Service = Service.getInstance()
    service.mOnPacketProcessReplyReceiver = Service.onPacketProcessReplyReceiver { call, code, data ->
        runBlocking {
            call.respond(code, data)
            Log.printDebug("routingProcess", String.format("RESPONSE %s", data))
        }
    }

    routing {
        post(PacketConst.API_ROUTE_SCHEMA) {
            doProcessPacket(call)
        }

        get(PacketConst.API_ROUTE_SCHEMA) {
            doProcessPacket(call)
        }

        delete(PacketConst.API_ROUTE_SCHEMA) {
            doProcessPacket(call)
        }

        put(PacketConst.API_ROUTE_SCHEMA) {
            doProcessPacket(call)
        }
    }
}
