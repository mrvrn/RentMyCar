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

/**
 * Command-line entry point for generating a Flyway migration from the Exposed table definitions.
 * Pass one lowercase, underscore-separated description, for example `add_car_status`.
 * The generated SQL must be reviewed; Flyway applies it when the application starts.
 */
@OptIn(ExperimentalTime::class)
fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: MigrationGenerator <description>" }
    val description = args.single()

    // Load environment overrides before resolving the database connection settings.
    loadEnvironment()

    // Migration generation only needs database settings, not the server's JWT secret.
    val config = requireNotNull(YamlConfig("migration.yaml")) { "Missing migration.yaml" }
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

/**
 * Compares [tables] with the current schema in [database] and writes the SQL needed to align them.
 * This generates a script without executing the proposed schema changes.
 *
 * Returns the generated file, or `null` when the schema already matches the table definitions.
 * [version] can be supplied explicitly for reproducible filenames, such as in tests.
 */
@OptIn(ExperimentalTime::class)
fun generateMigration(
    database: Database,
    tables: Array<Table>,
    directory: java.nio.file.Path,
    description: String,
    // Convert the UTC instant to a sortable yyyyMMddHHmmssSSS version (millisecond precision).
    version: String = Clock.System.now().toString()
        .replace(Regex("[-:TZ.]"), "")
        .take(17)
        .padEnd(17, '0'),
): java.nio.file.Path? {
    // Keep the description filename-safe and the version consistent with our migration naming.
    require(description.matches(Regex("[a-z][a-z0-9_]*"))) {
        "Description must use lowercase letters, digits, and underscores"
    }

    require(version.matches(Regex("[0-9]{17}"))) { "Version must be a 17-digit UTC timestamp" }
    directory.createDirectories()

    // Exposed needs a transaction to inspect the live database and calculate the schema difference.
    val statements = transaction(database) {
        // Flyway changes the schema outside Exposed; compare fresh metadata.
        database.dialectMetadata.resetCaches()
        MigrationUtils.statementsRequiredForDatabaseMigration(*tables, withLogs = false)
    }

    // Avoid creating an empty migration when there is nothing for Flyway to apply.
    if (statements.isEmpty()) {
        return null
    }

    // Flyway versions must be unique, even when two scripts have different descriptions.
    require(directory.listDirectoryEntries().none { it.name.startsWith("V${version}__") }) {
        "A migration with timestamp $version already exists; run the generator again"
    }

    // Flyway discovers versioned scripts using the V<version>__<description>.sql convention.
    val output = directory.resolve("V${version}__${description}.sql")

    // Create a new file (failing if it exists), separate statements, and normalize their terminators.
    output.createFile().writeText(
        statements.joinToString("\n\n", postfix = "\n") { it.trimEnd().removeSuffix(";") + ";" },
    )

    return output
}
