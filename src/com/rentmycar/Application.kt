package com.rentmycar

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import com.rentmycar.database.configureDatabase
import com.rentmycar.server.auth.configureSecurity
import io.github.cdimascio.dotenv.dotenv
import com.rentmycar.server.auth.configureSecurity

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
    configureSecurity()

    install(ContentNegotiation) {
        json()
    }

    configureSecurity()
    configureRouting()
}
