package com.refoler.backend.commons.modules

import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*

fun Application.configureHTTP() {
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }
    install(DefaultHeaders) {
        header("X-Engine", "Nginx")
        header("Content-Type", "application/json")
    }
}
