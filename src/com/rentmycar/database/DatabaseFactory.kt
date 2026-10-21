package com.rentmycar.database

import com.rentmycar.appEnvironment
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

data class DatabaseSettings(val url: String, val user: String, val password: String) {
    companion object {
        fun from(config: ApplicationConfig): DatabaseSettings = DatabaseSettings(
            url = appEnvironment["DATABASE_URL"] ?: localDatabaseUrl(config),
            user = appEnvironment["MYSQL_USER"] ?: config.property("database.user").getString(),
            password = appEnvironment["MYSQL_PASSWORD"] ?: config.property("database.password").getString(),
        )

        private fun localDatabaseUrl(config: ApplicationConfig): String {
            val database = appEnvironment["MYSQL_DATABASE"]
            val port = appEnvironment["MYSQL_PORT"]
            return if (database != null || port != null) {
                "jdbc:mysql://127.0.0.1:${port ?: "3306"}/${database ?: "rentmycar"}"
            } else {
                config.property("database.url").getString()
            }
        }
    }

    fun connect(): Database = Database.connect(
        url = url,
        driver = "com.mysql.cj.jdbc.Driver",
        user = user,
        password = password,
    )
}

fun Application.configureDatabase() {
    val settings = DatabaseSettings.from(environment.config)
    migrateDatabase(settings)
    settings.connect()
}

fun migrateDatabase(
    settings: DatabaseSettings,
    location: String = "classpath:db/migration",
): org.flywaydb.core.api.output.MigrateResult = Flyway.configure()
        .dataSource(settings.url, settings.user, settings.password)
        .locations(location)
        .validateMigrationNaming(true)
        .load()
        .migrate()
