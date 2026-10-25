package test

import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.generateMigration
import com.rentmycar.database.migrateDatabase
import org.jetbrains.exposed.v1.core.Table
import org.flywaydb.core.Flyway
import org.testcontainers.mysql.MySQLContainer
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationsTest {
    @Test
    fun `rentals migrate after the existing main schema without losing car data`() = withDatabase { settings ->
        val baseline = Flyway.configure()
            .dataSource(settings.url, settings.user, settings.password)
            .locations("classpath:db/migration")
            .target("20261025010000000")
            .load()
            .migrate()
        assertEquals(10, baseline.migrationsExecuted)
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.metaData.getTables(connection.catalog, null, "rentals", arrayOf("TABLE")).use { rows ->
                assertTrue(!rows.next())
            }
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO users (id, name, email, phone, address, password, role) VALUES (1, 'Owner', 'upgrade@example.com', '1', 'A', 'hash', 'OWNER')")
                statement.executeUpdate("INSERT INTO locations (id, streetName, houseNumber, postalCode, city, country, userId) VALUES (1, 'Street', '1', '1234AB', 'City', 'NL', 1)")
                statement.executeUpdate("INSERT INTO cars (id, ownerId, brand, type, licensePlate, pricePerHour, pickupId, dropOffId) VALUES (1, 1, 'Test', 'Test', 'UPGRADE-1', 19.99, 1, 1)")
            }
        }

        assertEquals(2, migrateDatabase(settings).migrationsExecuted)
        assertEquals(0, migrateDatabase(settings).migrationsExecuted)
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT ownerId, licensePlate, pricePerHour FROM cars WHERE id = 1").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(1L, rows.getLong("ownerId"))
                    assertEquals("UPGRADE-1", rows.getString("licensePlate"))
                    assertEquals("19.99".toBigDecimal(), rows.getBigDecimal("pricePerHour"))
                }
                statement.executeUpdate("INSERT INTO rentals (carId, renterId, startAt, endAt, status, totalPrice) VALUES (1, 1, '2100-11-01 10:00:00', '2100-11-01 11:00:00', 'PENDING', 19.99)")
                statement.executeQuery("SELECT COUNT(*) FROM rentals").use { rows ->
                    assertTrue(rows.next())
                    assertEquals(1, rows.getInt(1))
                }
            }
            val foreignKeys = mutableMapOf<String, String>()
            connection.metaData.getImportedKeys(connection.catalog, null, "rentals").use { rows ->
                while (rows.next()) foreignKeys[rows.getString("FKCOLUMN_NAME")] = rows.getString("PKTABLE_NAME")
            }
            assertEquals("cars", foreignKeys["carId"])
            assertEquals("users", foreignKeys["renterId"])
        }
    }

    @Test
    fun roleMigrationPreservesOwnersAndNormalizesLegacyValues() = withDatabase { settings ->
        Flyway.configure()
            .dataSource(settings.url, settings.user, settings.password)
            .locations("classpath:db/migration")
            .target("20261013131505919")
            .load()
            .migrate()
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO users (id, name, email, phone, address, password) VALUES (1, 'Owner', 'owner-migration@example.com', '1', 'A', 'hash')")
                statement.executeUpdate("INSERT INTO users (id, name, email, phone, address, password) VALUES (2, 'Renter', 'renter-migration@example.com', '2', 'B', 'hash')")
                statement.executeUpdate("INSERT INTO locations (id, streetName, houseNumber, postalCode, city, country, userId) VALUES (1, 'Street', '1', '1234AB', 'City', 'NL', 1)")
                statement.executeUpdate("INSERT INTO cars (id, ownerId, brand, type, licensePlate, pricePerDay, pickupId, dropOffId) VALUES (1, 1, 'Test', 'Test', 'MIG-1', 19.99, 1, 1)")
            }
        }

        val pendingMigrations = kotlin.io.path.Path("resources", "db", "migration")
            .listDirectoryEntries("V*__*.sql")
            .count { it.fileName.toString().removePrefix("V").substringBefore("__") > "20261013131505919" }
        assertEquals(pendingMigrations, migrateDatabase(settings).migrationsExecuted)
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT role FROM users ORDER BY id").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("OWNER", rows.getString(1))
                    assertTrue(rows.next())
                    assertEquals("RENTER", rows.getString(1))
                    assertTrue(!rows.next())
                }
            }
        }
        assertEquals(0, migrateDatabase(settings).migrationsExecuted)
    }

    @Test
    fun priceMigrationUpgradesExistingCarsAndStoresExactDecimals() = withDatabase { settings ->
        Flyway.configure()
            .dataSource(settings.url, settings.user, settings.password)
            .locations("classpath:db/migration")
            .target("20261013131505919")
            .load()
            .migrate()
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO users (id, name, email, phone, address, password) VALUES (1, 'Test', 'test@example.com', '123', 'Test address', 'test hash')")
                statement.executeUpdate("INSERT INTO locations (id, streetName, houseNumber, postalCode, city, country, userId) VALUES (1, 'Test street', '1', '1234AB', 'Test city', 'NL', 1)")
                statement.executeUpdate("INSERT INTO cars (id, ownerId, brand, type, licensePlate, pricePerDay, pickupId, dropOffId) VALUES (1, 1, 'Test', 'Test', 'TEST-1', 19.99, 1, 1)")
            }
        }
        val pendingMigrations = kotlin.io.path.Path("resources", "db", "migration")
            .listDirectoryEntries("V*__*.sql")
            .count { it.fileName.toString().removePrefix("V").substringBefore("__") > "20261013131505919" }
        assertEquals(pendingMigrations, migrateDatabase(settings).migrationsExecuted)
        assertEquals(0, migrateDatabase(settings).migrationsExecuted)
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT pricePerHour FROM cars WHERE id = 1").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("19.99".toBigDecimal(), rows.getBigDecimal(1))
                    assertEquals("DECIMAL", rows.metaData.getColumnTypeName(1))
                    assertEquals(10, rows.metaData.getPrecision(1))
                    assertEquals(2, rows.metaData.getScale(1))
                }
            }
            connection.prepareStatement("UPDATE cars SET pricePerHour = ? WHERE id = 1").use { statement ->
                statement.setBigDecimal(1, "0.10".toBigDecimal())
                assertEquals(1, statement.executeUpdate())
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT pricePerHour FROM cars WHERE id = 1").use { rows ->
                    assertTrue(rows.next())
                    assertEquals("0.10".toBigDecimal(), rows.getBigDecimal(1))
                }
            }
        }
        assertEquals(0, migrateDatabase(settings).migrationsExecuted)
    }

    @Test
    fun priceRenameMigrationSkipsAlreadyRenamedColumn() = withDatabase { settings ->
        Flyway.configure()
            .dataSource(settings.url, settings.user, settings.password)
            .locations("classpath:db/migration")
            .target("20261013142355397")
            .load()
            .migrate()
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.createStatement().use { it.executeUpdate("ALTER TABLE cars RENAME COLUMN pricePerDay TO pricePerHour") }
        }
        val pendingMigrations = kotlin.io.path.Path("resources", "db", "migration")
            .listDirectoryEntries("V*__*.sql")
            .count { it.fileName.toString().removePrefix("V").substringBefore("__") > "20261013142355397" }
        assertEquals(pendingMigrations, migrateDatabase(settings).migrationsExecuted)
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.metaData.getColumns(connection.catalog, null, "cars", "pricePer%").use { rows ->
                assertTrue(rows.next())
                assertEquals("pricePerHour", rows.getString("COLUMN_NAME"))
                assertTrue(!rows.next())
            }
        }
    }

    @Test
    fun uniqueLicensePlateIndexRejectsDuplicatesAndSkipsExistingIndex() = withDatabase { settings ->
        Flyway.configure()
            .dataSource(settings.url, settings.user, settings.password)
            .locations("classpath:db/migration")
            .target("20261022180000000")
            .load()
            .migrate()
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.createStatement().use { it.executeUpdate("ALTER TABLE cars ADD UNIQUE INDEX idx_unique_license_plate (licensePlate)") }
        }
        val pendingMigrations = kotlin.io.path.Path("resources", "db", "migration")
            .listDirectoryEntries("V*__*.sql")
            .count { it.fileName.toString().removePrefix("V").substringBefore("__") > "20261022180000000" }
        assertEquals(pendingMigrations, migrateDatabase(settings).migrationsExecuted)
        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("INSERT INTO users (id, name, email, phone, address, password) VALUES (1, 'Test', 'test@example.com', '123', 'Test address', 'test hash')")
                statement.executeUpdate("INSERT INTO locations (id, streetName, houseNumber, postalCode, city, country, userId) VALUES (1, 'Test street', '1', '1234AB', 'Test city', 'NL', 1)")
                statement.executeUpdate("INSERT INTO cars (ownerId, brand, type, licensePlate, pricePerHour, pickupId, dropOffId) VALUES (1, 'Test', 'Test', 'DUP-1', 10.00, 1, 1)")
                assertFailsWith<java.sql.SQLIntegrityConstraintViolationException> {
                    statement.executeUpdate("INSERT INTO cars (ownerId, brand, type, licensePlate, pricePerHour, pickupId, dropOffId) VALUES (1, 'Test', 'Test', 'DUP-1', 12.00, 1, 1)")
                }
            }
        }
    }

    private class Notes(withSummary: Boolean = false) : Table("test_notes") {
        val id = long("id").autoIncrement()
        val text = varchar("text", 100)
        val summary = if (withSummary) varchar("summary", 100).nullable() else null
        override val primaryKey = PrimaryKey(id)
    }

    private object Tags : Table("test_tags") {
        val id = long("id").autoIncrement()
        val name = varchar("name", 100)
        override val primaryKey = PrimaryKey(id)
    }

    @Test
    fun `committed migrations apply once and create foreign keys`() = withDatabase { settings ->
        val count = kotlin.io.path.Path("resources", "db", "migration")
            .listDirectoryEntries("V*__*.sql").size
        assertTrue(count > 0)
        assertEquals(count, migrateDatabase(settings).migrationsExecuted)
        assertEquals(0, migrateDatabase(settings).migrationsExecuted)

        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            val tables = mutableSetOf<String>()
            connection.metaData.getTables(connection.catalog, null, "%", arrayOf("TABLE")).use { rows ->
                while (rows.next()) tables += rows.getString("TABLE_NAME")
            }
            assertTrue(tables.containsAll(listOf("users", "locations", "cars", "flyway_schema_history")))
            val foreignKeys = mutableMapOf<String, String>()
            connection.metaData.getImportedKeys(connection.catalog, null, "cars").use { rows ->
                while (rows.next()) foreignKeys[rows.getString("FKCOLUMN_NAME")] = rows.getString("PKTABLE_NAME")
            }
            assertEquals("users", foreignKeys["ownerId"])
            assertEquals("locations", foreignKeys["pickupId"])
            assertEquals("locations", foreignKeys["dropOffId"])
        }
    }

    @Test
    fun `generation does not apply sql and subsequent changes preserve data`() = withDatabase { settings ->
        val directory = createTempDirectory("rentmycar-migrations-")
        try {
            val database = settings.connect()
            val notes = Notes()
            val tables = arrayOf<Table>(notes, Tags)
            assertFailsWith<IllegalArgumentException> {
                generateMigration(database, tables, directory, "Invalid description")
            }
            assertTrue(directory.listDirectoryEntries().isEmpty())
            val first = assertNotNull(generateMigration(database, tables, directory, "create_notes_and_tags", "20261013000000001"))
            assertTrue(first.readText().contains("test_notes"))
            assertTrue(first.readText().contains("test_tags"))
            assertEquals("V20261013000000001__create_notes_and_tags.sql", first.fileName.toString())

            // Generation only writes files: tables must not exist before Flyway runs.
            DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
                connection.metaData.getTables(connection.catalog, null, "test_notes", arrayOf("TABLE")).use {
                    assertTrue(!it.next())
                }
            }
            val originalSql = first.readText()
            assertFailsWith<IllegalArgumentException> {
                generateMigration(database, tables, directory, "another_change", "20261013000000001")
            }
            assertEquals(originalSql, first.readText())

            // Without application, another generation repeats the missing schema.
            val pendingDirectory = createTempDirectory("rentmycar-pending-")
            try {
                val repeated = assertNotNull(generateMigration(database, tables, pendingDirectory, "still_pending"))
                assertTrue(repeated.fileName.toString().matches(Regex("V[0-9]{17}__still_pending\\.sql")))
                assertEquals(originalSql, repeated.readText())
            } finally {
                pendingDirectory.toFile().deleteRecursively()
            }

            val location = "filesystem:${directory.toAbsolutePath()}"
            assertEquals(1, migrateDatabase(settings, location).migrationsExecuted)
            assertNull(generateMigration(database, tables, directory, "no_changes", "20261013000000002"))
            assertEquals(1, directory.listDirectoryEntries().size)

            DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
                connection.createStatement().use { it.executeUpdate("INSERT INTO test_notes (`text`) VALUES ('keep me')") }
            }
            val updatedNotes = Notes(withSummary = true)
            val second = assertNotNull(generateMigration(database, arrayOf(updatedNotes, Tags), directory, "add_note_summary", "20261013000000002"))
            assertTrue(second.readText().contains("ALTER TABLE", ignoreCase = true))
            assertTrue(second.readText().contains("summary"))
            assertEquals(1, migrateDatabase(settings, location).migrationsExecuted)
            assertEquals(0, migrateDatabase(settings, location).migrationsExecuted)
            DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT `text`, summary FROM test_notes").use { rows ->
                        assertTrue(rows.next())
                        assertEquals("keep me", rows.getString("text"))
                        assertNull(rows.getString("summary"))
                        assertTrue(!rows.next())
                    }
                }
            }
            assertNull(generateMigration(database, arrayOf(updatedNotes, Tags), directory, "no_changes", "20261013000000003"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun withDatabase(test: (DatabaseSettings) -> Unit) {
        MySQLContainer("mysql:8.1").use { mysql ->
            mysql.start()
            test(DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password))
        }
    }
}
