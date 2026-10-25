package test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.domain.car.Car
import com.rentmycar.domain.car.ICarRepository
import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.rental.*
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.rental.dto.RentalOverviewDto
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.domain.user.dto.UserDto
import com.rentmycar.server.auth.configureSecurity
import com.rentmycar.server.routing.rentalRoutes
import com.rentmycar.server.routing.carRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.*

class RentalRoutesTest {
    private val input = CreateRentalDto(12L, "2100-11-01T10:00:00", "2100-11-01T11:30:00")

    @Test
    fun `creation passes car body and authenticated renter and returns created rental`() = checkRoute()

    @Test
    fun `creation requires authentication`() = checkRoute(
        authenticated = false, expectedStatus = HttpStatusCode.Unauthorized, expectCall = false
    )

    @Test
    fun `creation rejects invalid car id`() = checkRoute(
        path = "/cars/invalid/rental", expectedStatus = HttpStatusCode.BadRequest, expectCall = false
    )

    @Test
    fun `missing numeric car stops before creating rental`() = checkRoute(
        path = "/cars/9223372036854775807/rental", expectedStatus = HttpStatusCode.NotFound, expectCall = false
    )

    @Test
    fun `missing numeric car lookup returns one error`() = checkRoute(
        method = HttpMethod.Get, path = "/cars/9223372036854775807",
        authenticated = false, expectedStatus = HttpStatusCode.NotFound, expectCall = false
    )

    @Test
    fun `creation rejects malformed or incomplete bodies`() {
        for (body in listOf("{", "{}", """{"carId":12,"startAt":"2100-11-01T10:00:00"}""")) {
            checkRoute(body = body, expectedStatus = HttpStatusCode.BadRequest, expectCall = false)
        }
    }

    @Test
    fun `creation maps missing car or renter to not found`() = checkRoute(
        failure = RentalNotFoundException("Car not found"), expectedStatus = HttpStatusCode.NotFound
    )

    @Test
    fun `creation maps unavailable or overlapping car to conflict`() = checkRoute(
        failure = RentalUnavailableException("Car is already reserved for this period"),
        expectedStatus = HttpStatusCode.Conflict
    )

    @Test
    fun `creation maps validation failure to bad request`() = checkRoute(
        failure = IllegalArgumentException("End time must be after start time"),
        expectedStatus = HttpStatusCode.BadRequest
    )

    @Test
    fun `owner can request confirmation and rejection`() {
        for (status in listOf(RentalStatus.CONFIRMED, RentalStatus.REJECTED)) {
            checkStatus(status = status)
        }
    }

    @Test
    fun `owner status change requires authentication`() = checkStatus(
        authenticated = false, expectedStatus = HttpStatusCode.Unauthorized, expectCall = false
    )

    @Test
    fun `owner status change rejects invalid ids`() {
        for (id in listOf("invalid", "0", "-1", "9223372036854775808")) {
            checkStatus(path = "/rentals/$id/status", expectedStatus = HttpStatusCode.BadRequest, expectCall = false)
        }
    }

    @Test
    fun `owner status change rejects malformed missing and unknown statuses`() {
        for (body in listOf("{", "{}", "", """{"status":"UNKNOWN"}""", """{"status":null}""")) {
            checkStatus(body = body, expectedStatus = HttpStatusCode.BadRequest, expectCall = false)
        }
    }

    @Test
    fun `owner status endpoint rejects every unsupported target`() {
        for (status in listOf(RentalStatus.PENDING, RentalStatus.ACTIVE, RentalStatus.COMPLETED, RentalStatus.CANCELLED)) {
            checkStatus(status = status, expectedStatus = HttpStatusCode.BadRequest, expectCall = false)
        }
    }

    @Test
    fun `owner status change maps repository failures`() {
        for ((failure, status) in listOf(
            RentalNotFoundException("Rental not found") to HttpStatusCode.NotFound,
            RentalAccessDeniedException("Only the car owner can approve or reject a reservation") to HttpStatusCode.Forbidden,
            RentalUnavailableException("Only pending reservations can be approved or rejected") to HttpStatusCode.Conflict,
            IllegalArgumentException("Invalid status") to HttpStatusCode.BadRequest
        )) {
            checkStatus(failure = failure, expectedStatus = status)
        }
    }

    private fun checkStatus(
        status: RentalStatus = RentalStatus.CONFIRMED,
        path: String = "/rentals/42/status",
        body: String = """{"status":"$status"}""",
        authenticated: Boolean = true,
        failure: RuntimeException? = null,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        expectCall: Boolean = true,
    ) = checkRoute(HttpMethod.Patch, path, body, status, authenticated, failure, expectedStatus, expectCall)

    private fun checkRoute(
        method: HttpMethod = HttpMethod.Post,
        path: String = "/cars/12/rental",
        body: String = Json.encodeToString(input),
        target: RentalStatus = RentalStatus.PENDING,
        authenticated: Boolean = true,
        failure: RuntimeException? = null,
        expectedStatus: HttpStatusCode = HttpStatusCode.Created,
        expectCall: Boolean = true,
    ) = testApplication {
        val repository = RecordingRepository(target, failure)
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to "rental-routes-test-secret",
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience",
                "jwt.realm" to "test-realm",
            )
        }
        application {
            install(ContentNegotiation) { json() }
            configureSecurity()
            routing {
                val users = TestUserRepository()
                rentalRoutes(repository, users)
                carRoutes(TestCarRepository(), repository, users)
            }
        }
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withAudience("test-audience")
            .withClaim("userId", 7L)
            .sign(Algorithm.HMAC256("rental-routes-test-secret"))
        val response = client.request(path) {
            this.method = method
            if (authenticated) bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(expectedStatus, response.status, "$method $path with $body")
        if (!expectCall) {
            assertNull(repository.created)
            assertNull(repository.changed)
        } else if (method == HttpMethod.Post) {
            assertEquals(Triple(input, 7L, 12L), repository.created)
            assertNull(repository.changed)
        } else {
            assertEquals(Triple(42L, target, 7L), repository.changed)
            assertNull(repository.created)
        }
        if (expectedStatus == HttpStatusCode.OK || expectedStatus == HttpStatusCode.Created) {
            assertEquals(repository.result, Json.decodeFromString<RentalDto>(response.bodyAsText()))
        } else if (failure != null) {
            assertEquals(failure.message, response.bodyAsText())
        }
    }

    internal class TestUserRepository : IUserRepository {
        override suspend fun findById(id: Long): UserDto? =
            if (id == 7L) UserDto(7L, "Test User", "test@example.com", "", "", 0L) else null

        override fun toDto(entity: User): UserDto = error("Unexpected mapping")
        override suspend fun findAll(): List<UserDto> = error("Unexpected lookup")
        override suspend fun delete(id: Long): Boolean = error("Unexpected deletion")
        override suspend fun create(user: CreateUserDto): Unit = error("Unexpected creation")
        override suspend fun findByEmail(email: String): User? = error("Unexpected lookup")
    }

    private class TestCarRepository : ICarRepository {
        override suspend fun findById(id: Long): CarDto? = if (id == 12L) {
            CarDto(12L, 8L, "Test", "Car", "TEST-12", java.math.BigDecimal("12.50"), 1L, 1L, emptyList(), true)
        } else null

        override fun toDto(entity: Car): CarDto = error("Unexpected mapping")
        override suspend fun findAll(): List<CarDto> = error("Unexpected lookup")
        override suspend fun delete(id: Long): Boolean = error("Unexpected deletion")
        override suspend fun create(car: CreateCarDto): Unit = error("Unexpected creation")
    }

    private class RecordingRepository(target: RentalStatus, private val failure: RuntimeException?) : IRentalRepository {
        var created: Triple<CreateRentalDto, Long, Long>? = null
        var changed: Triple<Long, RentalStatus, Long>? = null
        val result = RentalDto(
            42L, 12L, 7L, "2100-11-01T10:00", "2100-11-01T11:30", target,
            "18.75", "2026-10-23T10:00", null
        )

        override suspend fun create(rental: CreateRentalDto, renterId: Long, carId: Long): RentalDto {
            created = Triple(rental, renterId, carId)
            failure?.let { throw it }
            return result
        }

        override suspend fun changeStatus(rentalId: Long, status: RentalStatus, loggedInUserId: Long): RentalDto {
            changed = Triple(rentalId, status, loggedInUserId)
            failure?.let { throw it }
            return result
        }

        override suspend fun findDetailsById(id: Long, loggedInUserId: Long): com.rentmycar.domain.rental.dto.RentalDetailsDto? =
            error("Unexpected detail lookup")

        override fun toDto(entity: Rental): RentalDto = error("Unexpected mapping")
        override suspend fun findAll(): List<RentalDto> = error("Unexpected lookup")
        override suspend fun findAllByUserId(userId: Long): List<RentalOverviewDto> = error("Unexpected lookup")
        override suspend fun findById(id: Long): RentalDto? = if (id == result.id) result else null
    }
}
