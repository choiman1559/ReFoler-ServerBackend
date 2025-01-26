package com.refoler.backend.commons.utils

import com.refoler.Refoler
import com.refoler.backend.commons.consts.PacketConst
import com.refoler.backend.commons.packet.PacketWrapper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking

class JsonRequest {

    interface OnReceivedCompleteListener {
        fun onFinished(receivedPacket: PacketWrapper)
    }

    companion object {
        @Suppress("HttpUrlsUsage")
        @JvmStatic
        fun buildIpcUrl(host: String, port: Int, serviceType: String): String {
            return "http://%s:%s%s".format(
                host, port,
                PacketConst.API_ROUTE_SCHEMA.replace("{version}", "v1").replace("{service_type}", serviceType)
            )
        }

        @JvmStatic
        fun postRequestPacket(url: String, requestPacket: Refoler.RequestPacket, event: OnReceivedCompleteListener) {
            postRequest(url, com.google.protobuf.util.JsonFormat.printer().print(requestPacket), event)
        }

        @JvmStatic
        fun postRequest(url: String, body: String, event: OnReceivedCompleteListener) {
            runBlocking {
                val responsePacket = PacketWrapper()
                val client = HttpClient().post(url) {
                    setBody(body)
                }

                responsePacket.refolerPacket = PacketWrapper.parseResponsePacket(client.call.body())
                responsePacket.statusCode = client.status
                event.onFinished(responsePacket)
            }
        }
    }
}