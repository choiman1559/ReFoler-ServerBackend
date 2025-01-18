package com.refoler.backend.commons

import com.noti.server.module.configureMonitoring
import com.refoler.backend.commons.modules.configureStatus
import com.refoler.backend.commons.modules.configureHTTP
import com.refoler.backend.commons.modules.configureRouting
import com.refoler.backend.commons.service.Argument
import com.refoler.backend.commons.service.Service

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    val argObj: Argument = Argument.buildFrom(args.toList())
    Service.configureServiceInstance(argObj)

    val server = embeddedServer(Netty, port = argObj.port, host = argObj.host, module = Application::module)
        .start(wait = false)
    Runtime.getRuntime().addShutdownHook(Thread {
        Service.getInstance().serviceStatusHandler.onServiceDead()
        server.stop(1, 5, TimeUnit.SECONDS)
    })

    Service.getInstance().serviceStatusHandler.onServiceStart()
    Thread.currentThread().join()
}

fun Application.module() {
    configureHTTP()
    configureStatus()
    configureMonitoring()
    //configureSerialization()
    configureRouting()
}
