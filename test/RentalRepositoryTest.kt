package test

import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.migrateDatabase
import com.rentmycar.domain.car.Car
import com.rentmycar.domain.location.Location
import com.rentmycar.domain.rental.*
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.user.User
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.mysql.MySQLContainer
import java.math.BigDecimal
import kotlin.test.*

class RentalRepositoryTest {
    @Test
    fun createsRentalWithCarRenterAndCalculatedPrice() = runBlocking {
        MySQLContainer("mysql:8.1").use { mysql ->
            mysql.start()
            val settings = DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password)
            migrateDatabase(settings)
            val database = settings.connect()
            val (carId, renterId) = transaction(database) {
                val user = User.new {
                    name = "Renter"
                    email = "renter@example.com"
                    phone = "0612345678"
                    address = "Test Street 1"
                    password = "test-hash"
                }
                val location = Location.new {
                    streetName = "Test Street"
                    houseNumber = "1"
                    postalCode = "1234AB"
                    city = "Amsterdam"
                    country = "NL"
                    this.user = user
                }
                val car = Car.new {
                    owner = user
                    brand = "Test"
                    type = "Car"
                    licensePlate = "AB-12-CD"
                    pricePerHour = BigDecimal("12.50")
                    pickup = location
                    dropOff = location
                    isAvailable = true
                }
                car.id.value to user.id.value
            }
            val repository = RentalRepository()
            val input = CreateRentalDto(carId, "2026-11-01T10:00:00", "2026-11-01T11:30:00")
            val result = repository.create(input, renterId, carId)
            assertEquals(carId, result.carId)
            assertEquals(renterId, result.renterId)
            assertEquals("18.75", result.totalPrice)
            assertEquals(RentalStatus.PENDING, result.status)
            assertNull(result.cancelledAt)
            assertTrue(result.createdAt.isNotBlank())
            assertEquals(result, repository.findById(result.id))
            assertFailsWith<RentalUnavailableException> {
                repository.create(input, renterId, carId)
            }
            // A reservation can start exactly when the previous one ends.
            repository.create(input.copy(startAt = "2026-11-01T11:30:00", endAt = "2026-11-01T12:00:00"), renterId, carId)
            assertEquals(2, repository.findAll().size)
        }
    }
}
