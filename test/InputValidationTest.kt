package test

import com.rentmycar.domain.car.CarRepository
import com.rentmycar.domain.car.CarValidation
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import com.rentmycar.domain.user.UserRepository
import com.rentmycar.domain.user.UserValidation
import com.rentmycar.domain.user.dto.CreateUserDto
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InputValidationTest {
    private val car = CreateCarDto("Ford", "Puma", "AB-12-CD", "12.50".toBigDecimal(), 1, 2)
    private val user = CreateUserDto("Test User", "test@example.com", "0612345678", "Street 1", "password123")

    @Test
    fun `car repository rejects invalid create and update before database access`() = runBlocking {
        val invalid = listOf(
            car.copy(brand = " "), car.copy(type = " "), car.copy(licensePlate = " "),
            car.copy(brand = "a".repeat(256)), car.copy(type = "a".repeat(256)), car.copy(licensePlate = "A".repeat(256)),
            car.copy(licensePlate = "ß".repeat(128)), // Uppercasing expands past the database limit.
            car.copy(brand = "Ford\u0000Test"), car.copy(type = "Test\nCar"),
            car.copy(licensePlate = "AB/<script>"), car.copy(licensePlate = "---"),
            car.copy(pricePerHour = "0".toBigDecimal()), car.copy(pricePerHour = "-1".toBigDecimal()),
            car.copy(pricePerHour = "100000000".toBigDecimal()), car.copy(pricePerHour = "1.001".toBigDecimal()),
            car.copy(pickupId = 0), car.copy(dropOffId = -1),
        ) + invalidPictures().map { car.copy(pictures = it) }
        for (input in invalid) {
            val createError = assertFailsWith<IllegalArgumentException> { CarRepository().create(input, 1) }
            val update = UpdateCarDto(input.brand, input.type, input.licensePlate, input.pricePerHour,
                input.pickupId, input.dropOffId, input.pictures, input.isAvailable)
            val updateError = assertFailsWith<IllegalArgumentException> { CarRepository().update(1, update) }
            assertEquals(createError.message, updateError.message)
        }
    }

    @Test
    fun `car normalization supports boundary lengths URLs and clearing pictures`() {
        val longUrl = "https://example.com/".padEnd(2048, 'a')
        val input = car.copy(brand = " ${"a".repeat(255)} ", type = " ${"b".repeat(255)} ",
            licensePlate = " ${"c".repeat(255)} ", pictures = List(20) { " $longUrl " })
        val saved = CarValidation.normalizeAndValidate(input)
        assertEquals(255, saved.brand.length)
        assertEquals(255, saved.type.length)
        assertEquals("C".repeat(255), saved.licensePlate)
        assertEquals(List(20) { longUrl }, saved.pictures)
        assertEquals(emptyList(), CarValidation.normalizeAndValidate(UpdateCarDto(pictures = emptyList())).pictures)
        assertEquals(UpdateCarDto(), CarValidation.normalizeAndValidate(UpdateCarDto()))
        for (plate in listOf("AB-12-CD", "AB12 CDE", "1-ABC-123")) {
            assertEquals(plate, CarValidation.normalizeAndValidate(car.copy(licensePlate = plate)).licensePlate)
        }
    }

    @Test
    fun `user repository rejects invalid registration before database access`() = runBlocking {
        val invalid = listOf(
            user.copy(name = " "), user.copy(email = " "), user.copy(phone = " "), user.copy(address = " "),
            user.copy(name = "a".repeat(101)), user.copy(email = "a".repeat(101)),
            user.copy(phone = "1".repeat(101)), user.copy(address = "a".repeat(101)),
            user.copy(name = "Test\u0000User"), user.copy(address = "Street\n1"),
            user.copy(email = "invalid"), user.copy(password = "short"), user.copy(password = "        "),
            user.copy(password = "é".repeat(37)),
        ) + listOf("letters", "123456", "1".repeat(16), "++31612345678", "+31 (612345678", "31)612345678", "31((6))12345678")
            .map { user.copy(phone = it) }
        for (input in invalid) {
            assertFailsWith<IllegalArgumentException> { UserRepository().create(input) }
        }
    }

    @Test
    fun `user normalization preserves passwords and supports common phone formatting`() {
        val input = user.copy(name = " Test User ", email = " test@example.com ", phone = " 0612345678 ",
            address = " Street 1 ", password = " password123 ")
        assertEquals(user.copy(password = input.password), UserValidation.normalizeAndValidate(input))
        for (phone in listOf("1234567", "1".repeat(15), "+31 (0)6 1234-5678", "+1 202.555.0123")) {
            assertEquals(phone, UserValidation.normalizeAndValidate(user.copy(phone = phone)).phone)
        }
        assertEquals("é".repeat(36), UserValidation.normalizeAndValidate(user.copy(password = "é".repeat(36))).password)
        assertEquals(100, UserValidation.normalizeAndValidate(user.copy(name = "a".repeat(100))).name.length)
    }

    private fun invalidPictures() = listOf(
        List(21) { "https://example.com/car.png" }, listOf("https://example.com/".padEnd(2049, 'a')),
        listOf(""), listOf(" "), listOf("relative.png"), listOf("//example.com/car.png"),
        listOf("ftp://example.com/car.png"), listOf("javascript:alert(1)"), listOf("https:///car.png"),
        listOf("https://user:password@example.com/car.png"), listOf("https://example.com/a b.png"),
        listOf("https://example.com:70000/car.png"), listOf("https://example.com:0/car.png"),
    )
}
