# Docker, tables, and migrations

Run all commands in PowerShell from the project root: the folder containing `compose.yaml`, `module.yaml`, and `kotlin.bat`.
Each teammate has their own local database. Share Kotlin definitions and SQL migrations through Git; records are not shared automatically.

## Quick workflow: generation and application are separate

Start Docker once with `docker compose up -d --wait` and leave it running. Docker must be running before generation because the generator connects to MySQL.

For every separate schema change:

1. Apply any pending migrations by running `.\kotlin.bat run`, then stop Ktor after successful startup.
2. Add or edit the domain table definition and register new tables in `database/Tables.kt`.
3. Generate SQL with `.\kotlin.bat run --main-class=com.rentmycar.database.MigrationGeneratorKt -- description_of_change`.
4. Review the generated SQL file.
5. Run `.\kotlin.bat run` to apply it. Confirm successful startup, then stop Ktor with Ctrl+C before the next change.

**Generating SQL does not create tables. Starting Docker does not apply migrations. Starting Ktor applies migrations through Flyway.** Do not generate another separate migration until the previous one has been applied: otherwise the same missing tables or constraints can appear again. To group several table changes, make them together and generate one file.

## 1. Start Docker and configure your environment

### Passwords on a fresh database

The password migration is intended for initial database setup, before any users
exist, and adds `password VARCHAR(100) NOT NULL` directly. Registration must hash
the supplied password before storing it; the migration cannot hash passwords
that have not been supplied. Authentication and hashing are not implemented yet.
If this migration is later used to upgrade a populated database, it will need a
staged backfill or explicit password setup state before enforcing `NOT NULL`.

Install and start Docker Desktop with Linux containers enabled. On first setup:

```powershell
Copy-Item .env.example .env
```

If `.env` already exists, keep it instead of overwriting it. Edit database credentials and ports before first startup if needed. `.env` is ignored by Git. Both Compose and Kotlin load it from the project root; system environment variables override its values.

Start MySQL and phpMyAdmin in the background and wait for MySQL to be healthy:

```powershell
docker compose up -d --wait
docker compose ps
```

`docker compose up -d` runs the containers in the background, but its completion does not guarantee MySQL is ready for connections. Adding `--wait` waits until services are running or healthy; this project's MySQL health check verifies a database connection. Prefer `docker compose up -d --wait` before running the generator or Ktor. Keep spaces between `-d` and `--wait`; both commands require `up`.

Open [phpMyAdmin](http://localhost:8081). Log in with `MYSQL_USER` and `MYSQL_PASSWORD` from `.env`; the unchanged example uses `rentmycar` and `rentmycar_dev`. The server is `db`. If you changed `PHPMYADMIN_PORT`, use that port in the URL.

## 2. Apply existing migrations before changing tables

Start Ktor:

```powershell
.\kotlin.bat run
```

Alternatively, run `ApplicationKt` in IntelliJ with the project root as its working directory.
Ktor runs Flyway on startup, applies pending SQL from `resources/db/migration`, then connects Exposed and starts the routes. Flyway records applied versions in `flyway_schema_history`.

After successful startup, stop Ktor with Ctrl+C or IntelliJ's Stop button. Leave Docker running. Your database now contains the existing migrations and provides the baseline for generation.

## 3. Define the table in its domain package

Create the table in its relevant domain package, then register it separately. For example, `src/com/rentmycar/domain/user/UsersTable.kt`:

```kotlin
package com.rentmycar.domain.user

import org.jetbrains.exposed.v1.core.Table

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    val email = varchar("email", 100).uniqueIndex()
    val phone = varchar("phone", 100)
    val address = varchar("address", 100)
    val bonusPoints = long("bonusPoints").nullable()
    val password = varchar("password", 100) // Stores a password hash.

    override val primaryKey = PrimaryKey(id)
}
```

This user table already exists in the project; use it as an example rather than defining a second `Users` object. The filename is `UsersTable.kt`, but the object is named `Users`. Imports and registration use the object name.

## 4. Import and register the table in Tables.kt

In `src/com/rentmycar/database/Tables.kt`, import the table object and add it to `applicationTables`:

```kotlin
package com.rentmycar.database

import com.rentmycar.domain.car.Cars
import com.rentmycar.domain.location.Locations
import com.rentmycar.domain.user.Users
import org.jetbrains.exposed.v1.core.Table

val applicationTables: Array<Table> = arrayOf(Users, Locations, Cars)
```

Keep existing tables registered. For each new table, first create its definition in its domain package, then import and add the object to this array. A new table that is not registered is not included in generation.

## 5. Generate a migration

With Docker running and the table definitions ready:

```powershell
.\kotlin.bat run --main-class=com.rentmycar.database.MigrationGeneratorKt -- create_users
```

Replace `create_users` with a description starting with a lowercase letter and containing only lowercase letters, digits, and underscores. The generator automatically supplies a UTC timestamp version with millisecond precision, for example:

```text
resources/db/migration/V20261013101530123__create_users.sql
```

Exposed compares every table registered in `applicationTables` with the actual MySQL schema. The generator writes the required SQL without executing it. If no schema differences exist, it writes nothing. The description names the file; it does not select the tables to compare.

### Multiple tables in the same migration

If you create and register multiple tables before running the command, their changes go into one migration file.

For example, create `UsersTable.kt` in `domain/user` and `RentalsTable.kt` in `domain/rental`. Import both objects into `Tables.kt`, include them in `applicationTables`, then run the generator once:

```powershell
.\kotlin.bat run --main-class=com.rentmycar.database.MigrationGeneratorKt -- create_users_and_rentals
```

If both tables are missing from the database, the resulting file contains SQL for both. Any other pending differences in registered tables are included too. One migration can contain several table changes.

To generate separate migration files, generate, review, and apply the first change before making and generating the next one. Running generation twice without applying the first file can duplicate SQL: the generator compares the database, not pending migration files, and the database has not changed yet.

## 6. Review, apply, and share the migration

Open the generated SQL and check that it matches your intended changes. Review drops, type changes, and new required columns on tables containing records. Adjust SQL if existing records need defaults or a backfill.

After reviewing it, start Ktor again:

```powershell
.\kotlin.bat run
```

Flyway executes pending migrations in version order and records them in `flyway_schema_history`. Refresh phpMyAdmin to inspect the result. Defining or registering a Kotlin table alone does not create it in MySQL.

Commit the domain table files, changes to `database/Tables.kt`, and generated SQL together. Do not commit `.env`. Teammates pull the changes, start Docker, and start Ktor to apply your migrations to their databases. They should not regenerate the same migration.

## Changing existing tables and migration files

For another schema change, repeat the sequence: apply existing migrations, stop Ktor, edit the registered table definition, generate SQL, review it, and restart Ktor.

### Example: adding a password column to an existing users table

Suppose the `users` table already exists in MySQL but does not yet have a password column:

1. Start Ktor to apply all existing migrations, then stop it. Leave Docker running.
2. Open the existing `src/com/rentmycar/domain/user/UsersTable.kt` file and add the column inside the existing `Users` object:

   ```kotlin
   val password = varchar("password", 100) // Stores a password hash.
   ```

   Update the existing table definition; do not create a second users table or replace its original migration. Since `Users` is already registered in `database/Tables.kt`, no additional registration is needed.

3. Generate a new migration describing this change:

   ```powershell
   .\kotlin.bat run --main-class=com.rentmycar.database.MigrationGeneratorKt -- add_user_password
   ```

4. Review the new `resources/db/migration/V<UTC_timestamp>__add_user_password.sql` file. It should contain the SQL needed to add the column to the existing table, rather than recreate the table. If users already exist, plan how to populate their password hashes; adding a required column may need a nullable column first, a backfill, and a later migration to make it required.
5. Start Ktor again to apply the new migration. Commit the updated `UsersTable.kt` and the new SQL file together.

The Kotlin table file describes the current schema. The SQL migration files preserve the history of changes needed to reach that schema. Keep the previous migrations and add a new one for each subsequent change. The project's current `Users` definition already includes `password`, so this example applies when introducing a column that is not yet defined and migrated.

You can edit SQL before it has been applied or shared. Once a migration has been applied or shared with teammates, leave it unchanged and create a new migration for corrections. Flyway checks applied files' checksums and can reject startup if they have changed.

Apply teammates' migrations before generating your own. Timestamps reduce filename conflicts, but overlapping changes still need review. Avoid manually changing tables in phpMyAdmin: that changes the generator's baseline without creating a migration for teammates.

## Stopping containers and resetting local data

Stop Docker containers while keeping data:

```powershell
docker compose down
```

Restart with `docker compose up -d --wait`. The named `mysql81_data` volume preserves records, tables, and migration history.

To deliberately delete all local database data and rebuild from committed migrations:

```powershell
docker compose down -v
docker compose up -d --wait
.\kotlin.bat run
```

This deletes records and migration history. MySQL reads initialization credentials from `.env` only when the volume is empty. Editing `.env` alone does not change an existing user's password.

For startup problems, inspect `docker compose ps` and `docker compose logs db`. See [Docker configuration details](docker.md) for connection settings.

## Running migration tests

Start Docker Desktop, then run from the project root:

```powershell
.\kotlin.bat test
```

`MigrationsTest` uses Testcontainers to start disposable MySQL 8.1 databases on
automatically allocated ports. It does not use your Compose database or `.env`
credentials, and writes generated SQL only to temporary folders. Containers and
temporary SQL are cleaned up after the tests. The first run may download images.

The tests verify committed SQL applies successfully and is skipped on repeat runs,
car foreign keys reference the expected tables, generation does not execute SQL,
multiple tables can share one migration, duplicate versions cannot overwrite files,
unchanged schemas produce no new file, and adding a column preserves existing data.
They also demonstrate that generating again before application repeats the pending
schema changes; they do not add a guard against that behavior to the generator.
