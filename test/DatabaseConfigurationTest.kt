package test

import com.rentmycar.loadEnvironment
import io.ktor.server.config.yaml.YamlConfig
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseConfigurationTest {
    @Test
    fun yamlResolvesLoadedCredentials() {
        val variables = loadEnvironment()
        val config = assertNotNull(YamlConfig("application.yaml"))
        assertTrue(config.property("database.user").getString() == (variables["MYSQL_USER"] ?: "rentmycar"))
        assertTrue(config.property("database.password").getString() == (variables["MYSQL_PASSWORD"] ?: "rentmycar_dev"))
        assertTrue(
            config.property("database.url").getString() ==
                (variables["DATABASE_URL"] ?: "jdbc:mysql://127.0.0.1:3306/rentmycar")
        )
    }
}
