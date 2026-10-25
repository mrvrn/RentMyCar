package test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.domain.rental.*
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.rental.dto.RentalOverviewDto
import com.rentmycar.server.auth.configureSecurity
import com.rentmycar.server.routing.rentalRoutes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RentalCancellationRouteTest {
    @Test
    fun `cancellation uses authenticated renter and returns updated rental`() = checkCancellation()

    @Test
    fun `cancellation requires authentication`() = checkCancellation(
        authenticated = false, expectedStatus = HttpStatusCode.Unauthorized, expectedCall = null
    )

    @Test
    fun `cancellation rejects invalid ids`() {
        for (id in listOf("invalid", "0", "-1", "9223372036854775808")) {
            checkCancellation(id = id, expectedStatus = HttpStatusCode.BadRequest, expectedCall = null)
        }
    }

    @Test
    fun `missing rental returns not found`() = checkCancellation(
        failure = RentalNotFoundException("Rental not found"), expectedStatus = HttpStatusCode.NotFound
    )

    @Test
    fun `another renter returns forbidden`() = checkCancellation(
        failure = RentalAccessDeniedException("Only the renter can cancel a rental"),
        expectedStatus = HttpStatusCode.Forbidden
    )

    @Test
    fun `disallowed status returns conflict`() = checkCancellation(
        failure = RentalUnavailableException("Rental cannot be cancelled"), expectedStatus = HttpStatusCode.Conflict
    )

    @Test
    fun `owner status endpoint does not accept cancellation`() = checkCancellation(
        pathSuffix = "status", body = """{"status":"CANCELLED"}""",
        expectedStatus = HttpStatusCode.BadRequest, expectedCall = null
    )

    private fun checkCancellation(
        id: String = "42",
        pathSuffix: String = "cancel",
        body: String? = null,
        authenticated: Boolean = true,
        failure: RuntimeException? = null,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        expectedCall: Pair<Long, Long>? = 42L to 7L,
    ) = testApplication {
        val repository = CancellationRepository(failure)
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to "cancellation-test-secret",
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience",
                "jwt.realm" to "test-realm",
            )
        }
        application {
            install(ContentNegotiation) { json() }
            configureSecurity()
            routing { rentalRoutes(repository, RentalRoutesTest.TestUserRepository()) }
        }
        val token = JWT.create()
            .withIssuer("test-issuer")
            .withAudience("test-audience")
            .withClaim("userId", 7L)
            .withClaim("role", if (pathSuffix == "status") "OWNER" else "RENTER")
            .sign(Algorithm.HMAC256("cancellation-test-secret"))
        val response = client.patch("/rentals/$id/$pathSuffix") {
            if (authenticated) bearerAuth(token)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        assertEquals(expectedStatus, response.status)
        assertEquals(expectedCall, repository.received)
        if (expectedStatus == HttpStatusCode.OK) {
            assertEquals(repository.cancelled, Json.decodeFromString<RentalDto>(response.bodyAsText()))
        } else if (failure != null) {
            assertEquals(failure.message, response.bodyAsText())
        }
    }

    private class CancellationRepository(private val failure: RuntimeException?) : IRentalRepository {
        var received: Pair<Long, Long>? = null
        val cancelled = RentalDto(
            42L, 1L, 7L, "2026-11-01T10:00", "2026-11-01T11:00", RentalStatus.CANCELLED,
            "12.50", "2026-10-23T10:00", "2026-10-23T11:00"
        )

        override suspend fun changeStatus(rentalId: Long, status: RentalStatus, loggedInUserId: Long): RentalDto {
            received = rentalId to loggedInUserId
            assertEquals(RentalStatus.CANCELLED, status)
            failure?.let { throw it }
            return cancelled
        }

        override suspend fun findDetailsById(id: Long, loggedInUserId: Long): com.rentmycar.domain.rental.dto.RentalDetailsDto? =
            error("Unexpected detail lookup")

        override fun toDto(entity: Rental): RentalDto = error("Unexpected mapping")
        override suspend fun findAll(): List<RentalDto> = error("Unexpected lookup")
        override suspend fun findById(id: Long): RentalDto? = if (id == cancelled.id) cancelled else null
        override suspend fun findAllByUserId(userId: Long): List<RentalOverviewDto> = error("Unexpected lookup")
        override suspend fun create(rental: CreateRentalDto, renterId: Long, carId: Long): RentalDto =
            error("Unexpected creation")
    }
}
