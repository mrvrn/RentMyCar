package test

import com.rentmycar.configureRouting
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.*

class UnitTests {
    @Test
    fun testExample() = testApplication {
        // Test the route without starting the production database integration.
        application {
            configureRouting()
        }
        // verify server root returns 200
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }
}
