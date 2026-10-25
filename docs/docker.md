# Local MySQL and phpMyAdmin

For the full teammate workflow, including domain tables, registration, generation,
and applying migrations, see [Docker, tables, and migrations](migrations.md).

Install Docker Desktop with Linux containers enabled. Run these commands from
the project root:

```powershell
docker compose up -d --wait
```

Compose runs MySQL 8.1 and phpMyAdmin 5.2.3 using fixed version tags.
phpMyAdmin connects to the `db` service: it manages the same MySQL database that
your local Ktor application can connect to, rather than having its own database.
Keep running Ktor from IntelliJ on port 8080.

Open http://localhost:8081 and sign in with username `rentmycar` and password
`rentmycar_dev`. The `rentmycar` database is created automatically on first start.
These credentials are for local development only; services listen on localhost.

## Connecting Ktor

The default connection settings are:

| Setting | Value |
| --- | --- |
| JDBC URL | `jdbc:mysql://127.0.0.1:3306/rentmycar` |
| Username | `rentmycar` |
| Password | `rentmycar_dev` |
| JDBC driver | `com.mysql.cj.jdbc.Driver` (MySQL Connector/J) |

Ktor runs Flyway migrations from `resources/db/migration` at startup, then connects
Exposed. Startup fails if the database is unavailable or migration validation fails.
If Ktor is containerized later, use `db:3306` instead of `127.0.0.1:3306`.

## Optional local settings

```powershell
Copy-Item .env.example .env
```

Edit `.env` to change credentials or host ports. For example, set
`MYSQL_PORT=3307` if another MySQL installation uses port 3306. Kotlin derives the
local JDBC URL from that setting unless `DATABASE_URL` is explicitly set.
`.env` is ignored by Git; `.env.example` is shared.

Compose and the Kotlin application both read `.env` when run from the project root.
Kotlin uses `MYSQL_USER` and `MYSQL_PASSWORD` for credentials, and `MYSQL_DATABASE`
and `MYSQL_PORT` for the local JDBC URL. Set `DATABASE_URL` to override that URL
entirely. System environment variables take precedence over `.env` values.
In IntelliJ, set the run configuration's working directory to the project root.

Set `DEMO_SEED=true` before starting Ktor to load local demo users, locations, and
cars. Leave it as `false` for a schema-only database; demo data is never loaded by
the normal migration location.

MySQL only uses the initialization credentials and database name when its data
volume is empty. Changing `.env` after initialization does not change existing
database users or passwords; update those through SQL or recreate the volume
if you intend to discard the local data.

## Managing the environment

```powershell
docker compose ps
docker compose logs db
docker compose down
```

`docker compose down` preserves database data in the named `mysql81_data` volume.
To deliberately delete all local database data and start fresh:

```powershell
docker compose down -v
docker compose up -d --wait
```

Each teammate has a separate local database. Shared image versions do not
synchronize tables or records; add shared migrations when implementing the schema.

Official image references: [MySQL](https://hub.docker.com/_/mysql) and
[phpMyAdmin](https://hub.docker.com/_/phpmyadmin).
