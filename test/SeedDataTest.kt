package test

import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.migrateDatabase
import com.rentmycar.domain.auth.PasswordService
import org.testcontainers.mysql.MySQLContainer
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeedDataTest {
    @Test
    fun `seed migration loads demo users and cars without affecting default migrations`() = withDatabase { settings ->
        migrateDatabase(settings)

        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM users WHERE email = ?").use { statement ->
                statement.setString(1, "owner@example.com")
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals(0, rows.getInt(1))
                }
            }
        }

        migrateDatabase(settings, "classpath:db/migration", "classpath:db/seed")

        DriverManager.getConnection(settings.url, settings.user, settings.password).use { connection ->
            connection.prepareStatement("SELECT role, password FROM users WHERE email = ?").use { statement ->
                statement.setString(1, "owner@example.com")
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals("OWNER", rows.getString("role"))
                    assertTrue(PasswordService.verifyPassword("Owner12345", rows.getString("password")))
                }
            }
            connection.prepareStatement("SELECT password FROM users WHERE email = ?").use { statement ->
                statement.setString(1, "renter1@example.com")
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertTrue(PasswordService.verifyPassword("Renter12345", rows.getString("password")))
                }
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM cars WHERE isAvailable = TRUE").use { rows ->
                    assertTrue(rows.next())
                    assertTrue(rows.getInt(1) >= 6)
                }
                statement.executeQuery("SELECT DISTINCT category FROM cars WHERE category IN ('ICE', 'BEV', 'FCEV')").use { rows ->
                    val categories = mutableSetOf<String>()
                    while (rows.next()) categories += rows.getString("category")
                    assertEquals(setOf("ICE", "BEV", "FCEV"), categories)
                }
            }
        }
    }

    private fun withDatabase(test: (DatabaseSettings) -> Unit) {
        MySQLContainer("mysql:8.1").use { mysql ->
            mysql.start()
            test(DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password))
        }
    }
}
