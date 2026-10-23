package test

import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BigDecimalSerializerTest {
    private fun car(price: String) = CarDto(1, 1, "Audi", "A3", "AB-12-CD", price.toBigDecimal(), 1, 1, null, true)

    @Test
    fun encodesExactUnquotedDecimals() {
        for (price in listOf("0.10", "8.55", "12345678.90", "100")) {
            val primitive = Json.parseToJsonElement(Json.encodeToString(car(price))).jsonObject["pricePerHour"]!!.jsonPrimitive
            assertFalse(primitive.isString)
            assertEquals(price, primitive.content)
        }
    }

    @Test
    fun decodesExactDecimalsAndRoundTrips() {
        assertEquals("0.10".toBigDecimal(), Json.decodeFromString<UpdateCarDto>("""{"pricePerHour":0.10}""").pricePerHour)
        assertEquals("19.99".toBigDecimal(), Json.decodeFromString<UpdateCarDto>("""{"pricePerHour":19.99}""").pricePerHour)
        assertNull(Json.decodeFromString<UpdateCarDto>("""{"pricePerHour":null}""").pricePerHour)
        val original = car("8.55")
        assertEquals(original, Json.decodeFromString<CarDto>(Json.encodeToString(original)))
    }

    @Test
    fun rejectsNonNumericValues() {
        for (value in listOf("\"abc\"", "\"8.55\"", "true", "{}", "[]")) {
            assertFailsWith<SerializationException>(value) {
                Json.decodeFromString<UpdateCarDto>("""{"pricePerHour":$value}""")
            }
        }
    }
}
