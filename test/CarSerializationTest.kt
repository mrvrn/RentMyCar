package test

import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.CreateCarDto
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CarSerializationTest {
    @Test
    fun carResponseSerializesExactPriceWithDefaultJsonConfiguration() = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing {
                get("/cars/1") {
                    call.respond(CarDto(1, 1, "Ford", "Puma", "TEST-PUMA",
                        "12.50".toBigDecimal(), 1, 1, emptyList(), true))
                }
            }
        }
        val response = client.get("/cars/1")
        assertEquals(HttpStatusCode.OK, response.status)
        val price = Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("pricePerHour").jsonPrimitive
        assertTrue(price.isString)
        assertEquals("12.50", price.content)
    }

    @Test
    fun createCarPriceRoundTripsWithoutPrecisionLoss() {
        val car = CreateCarDto(1, "Ford", "Puma", "TEST-PUMA",
            "99999999.99".toBigDecimal(), 1, 1)
        assertEquals(car, Json.decodeFromString<CreateCarDto>(Json.encodeToString(car)))
    }
}
