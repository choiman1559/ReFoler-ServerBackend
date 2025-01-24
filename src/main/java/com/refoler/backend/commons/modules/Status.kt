package com.refoler.backend.commons.modules

import com.refoler.backend.commons.service.Service
import com.refoler.backend.commons.consts.PacketConst
import com.refoler.backend.commons.packet.PacketWrapper

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*

fun Application.configureStatus() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, _ ->
            Service.replyPacket(call, PacketWrapper.makeErrorPacket(
                PacketConst.ERROR_NOT_FOUND, HttpStatusCode.NotFound))
        }

        exception<Throwable> { call, cause ->
            val causeMessage: String = PacketConst.ERROR_INTERNAL_ERROR

            val packet: PacketWrapper? = if (Service.getInstance().argument.isDebug) {
                cause.printStackTrace()
                PacketWrapper.makeErrorPacket(causeMessage, cause.message)
            } else {
                PacketWrapper.makeErrorPacket(causeMessage)
            }

            if (packet != null) {
                Service.replyPacket(call, packet)
            }
        }
    }
}