package com.rentmycar.database

import com.rentmycar.loadEnvironment
import io.ktor.server.config.yaml.YamlConfig
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database

@OptIn(ExperimentalTime::class)
fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: MigrationGenerator <description>" }
    val description = args.single()
    loadEnvironment()
    val config = requireNotNull(YamlConfig("application.yaml")) { "Missing application.yaml" }
    val output = generateMigration(
        DatabaseSettings.from(config).connect(),
        applicationTables,
        Path("resources", "db", "migration"),
        description,
    )
    if (output == null) {
        println("No schema changes detected; no migration written.")
    } else {
        println("Generated $output. Review the SQL before starting Ktor to apply it.")
    }
}

@OptIn(ExperimentalTime::class)
fun generateMigration(
    database: Database,
    tables: Array<Table>,
    directory: java.nio.file.Path,
    description: String,
    version: String = Clock.System.now().toString()
        .replace(Regex("[-:TZ.]"), "")
        .take(17)
        .padEnd(17, '0'),
): java.nio.file.Path? {
    require(description.matches(Regex("[a-z][a-z0-9_]*"))) {
        "Description must use lowercase letters, digits, and underscores"
    }
    require(version.matches(Regex("[0-9]{17}"))) { "Version must be a 17-digit UTC timestamp" }
    directory.createDirectories()
    val statements = transaction(database) {
        // Flyway changes the schema outside Exposed; compare fresh metadata.
        database.dialectMetadata.resetCaches()
        MigrationUtils.statementsRequiredForDatabaseMigration(*tables, withLogs = false)
    }
    if (statements.isEmpty()) {
        return null
    }
    require(directory.listDirectoryEntries().none { it.name.startsWith("V${version}__") }) {
        "A migration with timestamp $version already exists; run the generator again"
    }
    val output = directory.resolve("V${version}__${description}.sql")
    output.createFile().writeText(
        statements.joinToString("\n\n", postfix = "\n") { it.trimEnd().removeSuffix(";") + ";" },
    )
    return output
}
