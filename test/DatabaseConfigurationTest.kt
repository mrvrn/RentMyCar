package test

import com.rentmycar.loadEnvironment
import io.ktor.server.config.yaml.YamlConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DatabaseConfigurationTest {
    @Test
    fun migrationConfigurationOnlyRequiresDatabaseSettings() {
        val variables = loadEnvironment()
        val previousSecret = System.getProperty("JWT_SECRET")
        System.clearProperty("JWT_SECRET")
        try {
            // The CLI must load its configuration without resolving any JWT settings.
            val config = assertNotNull(YamlConfig("migration.yaml"))
            assertNull(config.propertyOrNull("jwt.secret"))
            assertEquals(variables["MYSQL_USER"] ?: "rentmycar", config.property("database.user").getString())
            assertEquals(variables["MYSQL_PASSWORD"] ?: "rentmycar_dev", config.property("database.password").getString())
            assertEquals(
                variables["DATABASE_URL"] ?: "jdbc:mysql://127.0.0.1:3306/rentmycar",
                config.property("database.url").getString()
            )
        } finally {
            if (previousSecret != null) System.setProperty("JWT_SECRET", previousSecret)
        }
    }

    @Test
    fun yamlResolvesLoadedCredentials() {
        // Load .env values and expose them as JVM system properties for YAML resolution.
        val variables = loadEnvironment()

        // The YAML also requires JWT_SECRET, even though this test checks database settings.
        // Save the existing property before supplying a temporary test-only value.
        val previousSecret = System.getProperty("JWT_SECRET")
        System.setProperty("JWT_SECRET", "database-configuration-test-only-secret")
        try {
            // Load the YAML and fail the test if the configuration file is missing.
            val config = assertNotNull(YamlConfig("application.yaml"))

            // Check that database settings match the environment values or their defaults.
            // The ?: operator uses the default when the environment value is null.
            assertEquals(config.property("database.user").getString(), (variables["MYSQL_USER"] ?: "rentmycar"))
            assertEquals(config.property("database.password").getString(), (variables["MYSQL_PASSWORD"] ?: "rentmycar_dev"))
            assertEquals(
                config.property("database.url").getString(),
                (variables["DATABASE_URL"] ?: "jdbc:mysql://127.0.0.1:3306/rentmycar")
            )
        } finally {
            // Restore the previous property even if loading or an assertion fails,
            // so this temporary secret does not leak into later tests.
            if (previousSecret == null) System.clearProperty("JWT_SECRET")
            else System.setProperty("JWT_SECRET", previousSecret)
        }
    }
}
