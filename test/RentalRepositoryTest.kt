package test

import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.migrateDatabase
import com.rentmycar.database.withTransaction
import com.rentmycar.domain.car.Car
import com.rentmycar.domain.location.Location
import com.rentmycar.domain.rental.*
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.user.User
import kotlinx.coroutines.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.testcontainers.mysql.MySQLContainer
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class RentalRepositoryTest {
    private companion object {
        // Each fixture gets distinct users and a car. Testcontainers cleans up MySQL on JVM exit.
        val database by lazy {
            val mysql = MySQLContainer("mysql:8.1").apply { start() }
            val settings = DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password)
            migrateDatabase(settings)
            settings.connect()
        }
    }

    private class Fixture {
        val repository = RentalRepository()
        val ownerId: Long
        val renterId: Long
        val strangerId: Long
        val carId: Long
        val input: CreateRentalDto

        init {
            val ids = transaction(database) {
                fun user(label: String) = User.new {
                    name = label
                    email = "${UUID.randomUUID()}@example.com"
                    phone = "0612345678"
                    address = "Test Street 1"
                    password = "test-hash"
                }
                val owner = user("Owner")
                val renter = user("Renter")
                val stranger = user("Stranger")
                val location = Location.new {
                    streetName = "Test Street"
                    houseNumber = "1"
                    postalCode = "1234AB"
                    city = "Amsterdam"
                    country = "NL"
                    user = owner
                }
                val car = Car.new {
                    this.owner = owner
                    brand = "Test"
                    type = "Car"
                    licensePlate = "AB-12-CD"
                    pricePerHour = BigDecimal("12.50")
                    pickup = location
                    dropOff = location
                    isAvailable = true
                }
                listOf(owner.id.value, renter.id.value, stranger.id.value, car.id.value)
            }
            ownerId = ids[0]
            renterId = ids[1]
            strangerId = ids[2]
            carId = ids[3]
            input = CreateRentalDto(carId, "2100-11-01T10:00:00", "2100-11-01T11:30:00")
        }

        suspend fun create(input: CreateRentalDto = this.input) = repository.create(input, renterId, carId)

        fun seed(status: RentalStatus): RentalDto = transaction(database) {
            Rental.new {
                car = Car[carId]
                renter = User[renterId]
                startAt = LocalDateTime.parse(input.startAt)
                endAt = LocalDateTime.parse(input.endAt)
                this.status = status
                totalPrice = BigDecimal("18.75")
                cancelledAt = if (status == RentalStatus.CANCELLED) LocalDateTime.parse("2026-01-01T12:00") else null
            }.toDto()
        }

        suspend fun rentals() = repository.findAll().filter { it.carId == carId }
    }

    private fun rentalTest(block: suspend Fixture.() -> Unit) = runBlocking { Fixture().block() }

    @Test
    fun `creates and reads rental with exact price and defaults`() = rentalTest {
        val result = create()
        assertEquals(carId, result.carId)
        assertEquals(renterId, result.renterId)
        assertEquals(LocalDateTime.parse(input.startAt), LocalDateTime.parse(result.startAt))
        assertEquals(LocalDateTime.parse(input.endAt), LocalDateTime.parse(result.endAt))
        assertEquals("18.75", result.totalPrice)
        assertEquals(RentalStatus.PENDING, result.status)
        assertNull(result.cancelledAt)
        LocalDateTime.parse(result.createdAt)
        assertEquals(result, repository.findById(result.id))
        assertEquals(listOf(result), rentals())
        assertNull(repository.findById(Long.MAX_VALUE))
    }

    @Test
    fun `rental details include overview and metadata for renter`() = rentalTest {
        val created = create()
        val overview = repository.findAllByUserId(renterId).single { it.id == created.id }
        val details = assertNotNull(repository.findDetailsById(created.id, renterId))
        assertEquals(overview.id, details.id)
        assertEquals(overview.car, details.car)
        assertEquals("Test", details.car.brand)
        assertEquals("Car", details.car.type)
        assertNull(details.car.pictures)
        assertEquals(overview.startAt, details.startAt)
        assertEquals(overview.endAt, details.endAt)
        assertEquals(overview.status, details.status)
        assertEquals(overview.totalPrice, details.totalPrice)
        assertEquals(created.renterId, details.renterId)
        assertEquals(created.createdAt, details.createdAt)
        assertNull(details.cancelledAt)
        repository.changeStatus(created.id, RentalStatus.CANCELLED, renterId)
        val saved = assertNotNull(repository.findById(created.id))
        assertEquals(saved.cancelledAt, repository.findDetailsById(created.id, renterId)?.cancelledAt)
    }

    @Test
    fun `rental details allow renter and owner but reject unrelated users for every status`() = rentalTest {
        for (status in RentalStatus.entries) {
            val rental = seed(status)
            for (actor in listOf(ownerId, strangerId)) {
                if (actor == ownerId) {
                    val renterDetails = assertNotNull(repository.findDetailsById(rental.id, renterId))
                    assertEquals(renterDetails, repository.findDetailsById(rental.id, actor))
                } else {
                    assertFailsWith<RentalAccessDeniedException> {
                        repository.findDetailsById(rental.id, actor)
                    }
                }
            }
        }
    }

    @Test
    fun `missing rental details return null`() = rentalTest {
        assertNull(repository.findDetailsById(Long.MAX_VALUE, renterId))
    }

    @Test
    fun `rental history loads distinct cars with two queries`() = rentalTest {
        val cars = listOf(this, Fixture(), Fixture())
        val created = cars.map { repository.create(it.input, renterId, it.carId) }
        val statements = mutableListOf<String>()
        val history = withTransaction {
            addLogger(object : SqlLogger {
                override fun log(context: StatementContext, transaction: Transaction) {
                    statements += context.sql(transaction)
                }
            })
            repository.findAllByUserId(renterId)
        }
        assertEquals(created.map { it.id }.toSet(), history.map { it.id }.toSet())
        assertEquals(cars.map { it.carId }.toSet(), history.map { it.car.id }.toSet())
        assertTrue(history.all { it.car.brand == "Test" && it.car.type == "Car" })
        assertEquals(2, statements.count { it.trimStart().startsWith("SELECT", ignoreCase = true) }, statements.toString())
    }

    @Test
    fun `rejects mismatching car id without saving`() = rentalTest {
        assertFailsWith<IllegalArgumentException> { create(input.copy(carId = Long.MAX_VALUE)) }
        assertTrue(rentals().isEmpty())
    }

    @Test
    fun `rejects malformed dates without saving`() = rentalTest {
        for (invalid in listOf(input.copy(startAt = "invalid"), input.copy(endAt = "invalid"))) {
            assertFailsWith<IllegalArgumentException> { create(invalid) }
        }
        assertTrue(rentals().isEmpty())
    }

    @Test
    fun `rejects sub-microsecond endpoints without saving`() = rentalTest {
        for (invalid in listOf(
            input.copy(startAt = "2100-11-01T10:00:00.000000001"),
            input.copy(endAt = "2100-11-01T11:30:00.000000001"),
            input.copy(startAt = "2100-11-01T10:00:00.000000001", endAt = "2100-11-01T10:00:00.000000002")
        )) {
            val error = assertFailsWith<IllegalArgumentException> { create(invalid) }
            assertEquals("Rental times must have at most microsecond precision", error.message)
        }
        assertTrue(rentals().isEmpty())
    }

    @Test
    fun `microsecond endpoints survive database round trip`() = rentalTest {
        val precise = input.copy(startAt = "2100-11-01T10:00:00.123456", endAt = "2100-11-01T11:30:00.654321")
        val result = create(precise)
        val saved = assertNotNull(repository.findById(result.id))
        assertEquals(LocalDateTime.parse(precise.startAt), LocalDateTime.parse(saved.startAt))
        assertEquals(LocalDateTime.parse(precise.endAt), LocalDateTime.parse(saved.endAt))
    }

    @Test
    fun `rejects endpoints outside MySQL year range without saving`() = rentalTest {
        for (invalid in listOf(
            input.copy(startAt = "+10000-11-01T10:00:00", endAt = "+10000-11-01T11:00:00"),
            input.copy(endAt = "+10000-11-01T11:00:00"),
            input.copy(startAt = "0999-11-01T10:00:00")
        )) {
            val error = assertFailsWith<IllegalArgumentException> { create(invalid) }
            assertEquals("Rental times must be between years 1000 and 9999", error.message)
        }
        assertTrue(rentals().isEmpty())
    }

    @Test
    fun `accepts rental in final supported year`() = rentalTest {
        val result = create(input.copy(startAt = "9999-12-31T22:00:00", endAt = "9999-12-31T23:00:00"))
        assertEquals(result, repository.findById(result.id))
    }

    @Test
    fun `rejects past start and empty or reversed periods without saving`() = rentalTest {
        for (invalid in listOf(
            input.copy(startAt = "2000-01-01T10:00:00"),
            input.copy(endAt = input.startAt),
            input.copy(endAt = "2100-11-01T09:00:00")
        )) {
            assertFailsWith<IllegalArgumentException> { create(invalid) }
        }
        assertTrue(rentals().isEmpty())
    }

    @Test
    fun `reports missing car and renter without saving`() = rentalTest {
        assertFailsWith<RentalNotFoundException> {
            repository.create(input.copy(carId = Long.MAX_VALUE), renterId, Long.MAX_VALUE)
        }
        assertFailsWith<RentalNotFoundException> { repository.create(input, Long.MAX_VALUE, carId) }
        assertTrue(rentals().isEmpty())
    }

    @Test
    fun `rejects unavailable car without saving`() = rentalTest {
        transaction(database) { Car[carId].isAvailable = false }
        assertFailsWith<RentalUnavailableException> { create() }
        assertTrue(rentals().isEmpty())
    }

    @Test
    fun `rejects price above database maximum without saving`() = rentalTest {
        transaction(database) { Car[carId].pricePerHour = BigDecimal("99999999.99") }
        assertFailsWith<IllegalArgumentException> { create() }
        assertTrue(rentals().isEmpty())
    }

    @Test
    fun `accepts price at database maximum`() = rentalTest {
        transaction(database) { Car[carId].pricePerHour = BigDecimal("99999999.99") }
        val result = create(input.copy(endAt = "2100-11-01T11:00:00"))
        assertEquals("99999999.99", result.totalPrice)
        assertEquals(result, repository.findById(result.id))
    }

    @Test
    fun `pending confirmed and active rentals block every kind of overlap`() = rentalTest {
        val periods = listOf(
            input,
            input.copy(startAt = "2100-11-01T09:00:00", endAt = "2100-11-01T10:30:00"),
            input.copy(startAt = "2100-11-01T11:00:00", endAt = "2100-11-01T12:00:00"),
            input.copy(startAt = "2100-11-01T10:15:00", endAt = "2100-11-01T11:00:00"),
            input.copy(startAt = "2100-11-01T09:00:00", endAt = "2100-11-01T12:00:00")
        )
        for (status in listOf(RentalStatus.PENDING, RentalStatus.CONFIRMED, RentalStatus.ACTIVE)) {
            val existing = seed(status)
            for (period in periods) {
                assertFailsWith<RentalUnavailableException>("$status must block $period") { create(period) }
            }
            transaction(database) { Rental[existing.id].status = RentalStatus.CANCELLED }
        }
        assertEquals(3, rentals().size)
    }

    @Test
    fun `rejected completed and cancelled rentals do not block booking`() = rentalTest {
        for (status in listOf(RentalStatus.REJECTED, RentalStatus.COMPLETED, RentalStatus.CANCELLED)) seed(status)
        assertEquals(RentalStatus.PENDING, create().status)
        assertEquals(4, rentals().size)
    }

    @Test
    fun `adjacent and separate periods are allowed`() = rentalTest {
        create()
        create(input.copy(startAt = "2100-11-01T09:00:00", endAt = input.startAt))
        create(input.copy(startAt = input.endAt, endAt = "2100-11-01T12:00:00"))
        create(input.copy(startAt = "2100-11-02T10:00:00", endAt = "2100-11-02T11:00:00"))
        assertEquals(4, rentals().size)
    }

    @Test
    fun `bookings for another car do not block same period`() = rentalTest {
        create()
        val other = Fixture()
        assertEquals(RentalStatus.PENDING, other.create().status)
    }

    @Test
    fun `checks every source and target status and persists only allowed transitions`() = rentalTest {
        for (source in RentalStatus.entries) {
            for (target in RentalStatus.entries) {
                val seeded = seed(source)
                val before = repository.findById(seeded.id)!!
                val actor = if (target == RentalStatus.CANCELLED) renterId else ownerId
                when {
                    target == RentalStatus.CANCELLED && source in listOf(
                        RentalStatus.PENDING, RentalStatus.CONFIRMED, RentalStatus.CANCELLED
                    ) -> {
                        val result = repository.changeStatus(before.id, target, actor)
                        assertEquals(target, result.status, "$source -> $target")
                        assertNotNull(result.cancelledAt)
                        val saved = repository.findById(before.id)!!
                        assertEquals(before.copy(status = target, cancelledAt = saved.cancelledAt), saved)
                        if (source == RentalStatus.CANCELLED) assertEquals(before, saved)
                    }
                    target in listOf(RentalStatus.CONFIRMED, RentalStatus.REJECTED) && source == RentalStatus.PENDING -> {
                        val result = repository.changeStatus(before.id, target, actor)
                        assertEquals(before.copy(status = target), result)
                        assertEquals(result, repository.findById(before.id))
                    }
                    target in listOf(RentalStatus.CONFIRMED, RentalStatus.REJECTED, RentalStatus.CANCELLED) -> {
                        assertFailsWith<RentalUnavailableException>("$source -> $target") {
                            repository.changeStatus(before.id, target, actor)
                        }
                        assertEquals(before, repository.findById(before.id))
                    }
                    else -> {
                        assertFailsWith<IllegalArgumentException>("$source -> $target") {
                            repository.changeStatus(before.id, target, actor)
                        }
                        assertEquals(before, repository.findById(before.id))
                    }
                }
            }
        }
    }

    @Test
    fun `renter and stranger cannot approve or reject any rental`() = rentalTest {
        for (source in RentalStatus.entries) {
            val before = seed(source)
            for (actor in listOf(renterId, strangerId)) {
                for (target in listOf(RentalStatus.CONFIRMED, RentalStatus.REJECTED)) {
                    assertFailsWith<RentalAccessDeniedException> { repository.changeStatus(before.id, target, actor) }
                    assertEquals(before, repository.findById(before.id))
                }
            }
        }
    }

    @Test
    fun `owner and stranger cannot cancel any rental`() = rentalTest {
        for (source in RentalStatus.entries) {
            val before = seed(source)
            for (actor in listOf(ownerId, strangerId)) {
                assertFailsWith<RentalAccessDeniedException> {
                    repository.changeStatus(before.id, RentalStatus.CANCELLED, actor)
                }
                assertEquals(before, repository.findById(before.id))
            }
        }
    }

    @Test
    fun `status changes report missing rental`() = rentalTest {
        for (status in RentalStatus.entries) {
            assertFailsWith<RentalNotFoundException> { repository.changeStatus(Long.MAX_VALUE, status, ownerId) }
        }
    }

    @Test
    fun `cancellation frees period for new booking and cannot be approved again`() = rentalTest {
        val existing = create()
        repository.changeStatus(existing.id, RentalStatus.CONFIRMED, ownerId)
        repository.changeStatus(existing.id, RentalStatus.CANCELLED, renterId)
        assertFailsWith<RentalUnavailableException> {
            repository.changeStatus(existing.id, RentalStatus.CONFIRMED, ownerId)
        }
        assertEquals(RentalStatus.PENDING, create().status)
    }

    @Test
    fun `cancellation records current time and repeated requests preserve it`() = rentalTest {
        val zone = TimeZone.UTC
        val earliest = (Clock.System.now() - 1.seconds).toLocalDateTime(zone)
        val existing = create()
        // Allow for database timestamp precision when comparing the recorded time.
        repository.changeStatus(existing.id, RentalStatus.CANCELLED, renterId)
        val latest = Clock.System.now().toLocalDateTime(zone)
        val saved = repository.findById(existing.id)!!
        val cancelledAt = LocalDateTime.parse(assertNotNull(saved.cancelledAt))
        val createdAt = LocalDateTime.parse(saved.createdAt)
        assertTrue(createdAt >= earliest && createdAt <= cancelledAt)
        assertTrue(cancelledAt.toInstant(zone) - createdAt.toInstant(zone) < 10.seconds)
        assertTrue(cancelledAt >= earliest && cancelledAt <= latest)
        assertEquals(saved, repository.changeStatus(existing.id, RentalStatus.CANCELLED, renterId))
        assertEquals(saved, repository.findById(existing.id))
    }

    @Test
    fun `concurrent bookings cannot reserve same car and period twice`() = rentalTest {
        val fixture = this
        val gate = CompletableDeferred<Unit>()
        val outcomes = coroutineScope {
            val attempts = List(2) {
                async(Dispatchers.IO) { gate.await(); runCatching { fixture.create() } }
            }
            gate.complete(Unit)
            attempts.awaitAll()
        }
        assertEquals(1, outcomes.count { it.isSuccess })
        assertIs<RentalUnavailableException>(outcomes.single { it.isFailure }.exceptionOrNull())
        assertEquals(1, rentals().size)
    }

    @Test
    fun `concurrent owner decisions cannot overwrite each other`() = rentalTest {
        val existing = create()
        val gate = CompletableDeferred<Unit>()
        val outcomes = coroutineScope {
            val attempts = listOf(RentalStatus.CONFIRMED, RentalStatus.REJECTED).map { target ->
                async(Dispatchers.IO) {
                    gate.await()
                    runCatching { repository.changeStatus(existing.id, target, ownerId) }
                }
            }
            gate.complete(Unit)
            attempts.awaitAll()
        }
        assertEquals(1, outcomes.count { it.isSuccess })
        assertIs<RentalUnavailableException>(outcomes.single { it.isFailure }.exceptionOrNull())
        assertEquals(outcomes.single { it.isSuccess }.getOrThrow(), repository.findById(existing.id))
    }

    @Test
    fun `concurrent approval cannot undo renter cancellation`() = rentalTest {
        val existing = create()
        val gate = CompletableDeferred<Unit>()
        val outcomes = coroutineScope {
            val approval = async(Dispatchers.IO) {
                gate.await()
                runCatching { repository.changeStatus(existing.id, RentalStatus.CONFIRMED, ownerId) }
            }
            val cancellation = async(Dispatchers.IO) {
                gate.await()
                runCatching { repository.changeStatus(existing.id, RentalStatus.CANCELLED, renterId) }
            }
            gate.complete(Unit)
            listOf(approval.await(), cancellation.await())
        }
        assertTrue(outcomes[1].isSuccess)
        outcomes[0].exceptionOrNull()?.let { assertIs<RentalUnavailableException>(it) }
        val saved = repository.findById(existing.id)!!
        assertEquals(RentalStatus.CANCELLED, saved.status)
        assertNotNull(saved.cancelledAt)
    }
}
