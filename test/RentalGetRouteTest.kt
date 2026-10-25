package test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.domain.rental.*
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.rental.dto.RentalDetailsDto
import com.rentmycar.domain.car.dto.RentalCarDto
import com.rentmycar.domain.rental.dto.RentalOverviewDto
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.domain.user.dto.UserDto
import com.rentmycar.server.auth.configureSecurity
import com.rentmycar.server.routing.rentalRoutes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RentalGetRouteTest {
    @Test
    fun `existing rental returns ok and rental body with one lookup`() = checkGet()

    @Test
    fun `missing rental returns not found`() = checkGet(
        exists = false,
        expectedStatus = HttpStatusCode.NotFound,
        expectedBody = "Rental not found",
    )

    @Test
    fun `invalid ids return bad request without repository lookup`() {
        for (id in listOf("invalid", "0", "-1", "9223372036854775808")) {
            checkGet(
                id = id,
                expectedStatus = HttpStatusCode.BadRequest,
                expectedBody = "Invalid rental ID",
                expectedLookups = emptyList(),
            )
        }
    }

    @Test
    fun `missing token returns unauthorized without repository lookup`() = checkGet(
        authenticated = false,
        expectedStatus = HttpStatusCode.Unauthorized,
        expectedBody = "Token is not valid or has expired",
        expectedLookups = emptyList(),
    )

    @Test
    fun `owner can read rental details`() = checkGet(userId = 8L)

    @Test
    fun `missing role is forbidden before rental lookup`() = checkGet(
        role = null, expectedStatus = HttpStatusCode.Forbidden,
        expectedBody = "Access denied: insufficient permissions", expectedLookups = emptyList(),
    )

    @Test
    fun `unrelated user cannot read rental details`() = checkGet(
        userId = 9L,
        expectedStatus = HttpStatusCode.Forbidden,
        expectedBody = "Only the renter or car owner can view rental details",
    )

    @Test
    fun `deleted user returns unauthorized without rental lookup`() = checkGet(
        userId = 10L,
        expectedStatus = HttpStatusCode.Unauthorized,
        expectedBody = "",
        expectedLookups = emptyList(),
    )

    private fun checkGet(
        id: String = "42",
        userId: Long = 7L,
        exists: Boolean = true,
        authenticated: Boolean = true,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        expectedBody: String? = null,
        expectedLookups: List<Long> = listOf(42L),
        role: String? = if (userId == 8L) "OWNER" else "RENTER",
    ) = testApplication {
        val repository = LookupRepository(exists)
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to "rental-get-test-secret",
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience",
                "jwt.realm" to "test-realm",
            )
        }
        application {
            install(ContentNegotiation) { json() }
            configureSecurity()
            routing { rentalRoutes(repository, TestUserRepository()) }
        }
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withAudience("test-audience")
            .withClaim("userId", userId)
            .withClaim("role", role)
            .sign(Algorithm.HMAC256("rental-get-test-secret"))

        val response = client.get("/rentals/$id") {
            if (authenticated) bearerAuth(token)
        }

        assertEquals(expectedStatus, response.status)
        assertEquals(expectedLookups, repository.lookups)
        if (expectedStatus == HttpStatusCode.OK) {
            assertEquals(repository.rental, Json.decodeFromString<RentalDetailsDto>(response.bodyAsText()))
        } else {
            assertEquals(expectedBody, response.bodyAsText())
        }
    }

    private class LookupRepository(private val exists: Boolean) : IRentalRepository {
        val lookups = mutableListOf<Long>()
        val rental = RentalDetailsDto(
            42L, "2100-11-01T10:00", "2100-11-01T11:30", RentalStatus.PENDING,
            "18.75", RentalCarDto(12L, "Test", "Car", null), 7L, "2026-10-23T10:00", null,
        )

        override suspend fun findDetailsById(id: Long, loggedInUserId: Long): RentalDetailsDto? {
            lookups.add(id)
            if (!exists || id != rental.id) return null
            if (loggedInUserId != rental.renterId && loggedInUserId != 8L) {
                throw RentalAccessDeniedException("Only the renter or car owner can view rental details")
            }
            return rental
        }

        override suspend fun findById(id: Long): RentalDto? = error("Unexpected raw lookup")

        override fun toDto(entity: Rental): RentalDto = error("Unexpected mapping")
        override suspend fun findAll(): List<RentalDto> = error("Unexpected lookup")
        override suspend fun findAllByUserId(userId: Long): List<RentalOverviewDto> = error("Unexpected lookup")
        override suspend fun create(rental: CreateRentalDto, renterId: Long, carId: Long): RentalDto =
            error("Unexpected creation")
        override suspend fun changeStatus(rentalId: Long, status: RentalStatus, loggedInUserId: Long): RentalDto =
            error("Unexpected status change")
    }

    private class TestUserRepository : IUserRepository {
        override fun toDto(entity: User): UserDto = error("Unexpected mapping")
        override suspend fun findAll(): List<UserDto> = error("Unexpected lookup")
        override suspend fun findById(id: Long): UserDto? =
            if (id in 7L..9L) UserDto(id, "Test User", "test@example.com", "", "", 0L) else null
        override suspend fun delete(id: Long): Boolean = error("Unexpected deletion")
        override suspend fun create(user: CreateUserDto, role: com.rentmycar.domain.user.UserRole): Unit = error("Unexpected creation")
        override suspend fun findByEmail(email: String): User? = error("Unexpected lookup")
    }
}
