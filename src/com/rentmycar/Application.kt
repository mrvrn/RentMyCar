package com.rentmycar

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import com.rentmycar.server.routing.carRoutes
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import com.rentmycar.database.configureDatabase
import io.github.cdimascio.dotenv.dotenv

val appEnvironment = dotenv {
    ignoreIfMissing = true
    systemProperties = true
}

fun loadEnvironment() = appEnvironment

fun main(args: Array<String>) {
    loadEnvironment()
    EngineMain.main(args)
}

fun Application.module() {
    configureDatabase()

    install(ContentNegotiation) {
        json()
    }

    configureRouting()
    
    routing {
        carRoutes()
    }
}
