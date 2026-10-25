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
import kotlin.test.assertFalse

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
        assertFalse(price.isString)
        assertEquals("12.50", price.content)
    }

    @Test
    fun createCarPriceRoundTripsWithoutPrecisionLoss() {
        val car = CreateCarDto(1, "Ford", "Puma", "TEST-PUMA",
            "12345678901234567890.123456789".toBigDecimal(), 1, 1)
        assertEquals(car, Json.decodeFromString<CreateCarDto>(Json.encodeToString(car)))
    }

    @Test
    fun createCarAcceptsNumericPrice() {
        val car = Json.decodeFromString<CreateCarDto>(
            """{"ownerId":1,"brand":"Ford","type":"Puma","licensePlate":"TEST-PUMA","pricePerHour":12.50,"pickupId":1,"dropOffId":1}"""
        )
        assertEquals("12.50".toBigDecimal(), car.pricePerHour)
    }
}
