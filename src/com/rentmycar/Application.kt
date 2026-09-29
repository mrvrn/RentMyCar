package com.rentmycar

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import com.rentmycar.server.carRoutes
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation


fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    configureRouting()

    routing {
        carRoutes()
    }
}