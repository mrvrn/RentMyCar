package test

import com.rentmycar.domain.user.DuplicateEmailException
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.UserRole
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.domain.user.dto.UserDto
import com.rentmycar.server.auth.configureSecurity
import com.rentmycar.server.routing.userRoutes
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RegistrationTest {
    private val validUser = CreateUserDto(
        "Test User", "test@example.com", "0612345678", "Test Street 1", "test-password-123"
    )

    @Test
    fun `accepts registration without jwt and trims contact fields`() {
        val input = validUser.copy(
            name = " Test User ", email = " test@example.com ",
            phone = " 0612345678 ", address = " Test Street 1 ",
            password = " keep password spaces ",
        )
        checkRegistration(
            input = input,
            status = HttpStatusCode.OK,
            saved = validUser.copy(password = input.password),
        )
    }

    @Test
    fun rejectsClientSuppliedOwnerRole() = checkRegistration(
        body = """{"name":"Test User","email":"test@example.com","phone":"0612345678","address":"Test Street 1","password":"test-password-123","role":"OWNER"}"""
    )

    @Test
    fun rejectsClientSuppliedRenterRole() = checkRegistration(
        body = """{"name":"Test User","email":"test@example.com","phone":"0612345678","address":"Test Street 1","password":"test-password-123","role":"RENTER"}"""
    )

    @Test
    fun rejectsMissingFields() = checkRegistration(body = "{}")

    @Test
    fun `rejects malformed json`() = checkRegistration(body = "{")

    @Test
    fun `rejects blank contact field`() = checkRegistration(input = validUser.copy(name = "   "))

    @Test
    fun `rejects oversized contact field`() = checkRegistration(input = validUser.copy(address = "a".repeat(101)))

    @Test
    fun `rejects invalid email`() = checkRegistration(input = validUser.copy(email = "not-an-email"))

    @Test
    fun `rejects invalid phone formatting`() {
        for (phone in listOf("call me", "123456", "1".repeat(16), "++31612345678", "31(612345678")) {
            checkRegistration(input = validUser.copy(phone = phone))
        }
    }

    @Test
    fun `accepts international phone formatting`() {
        val input = validUser.copy(phone = "+31 (0)6 1234-5678")
        checkRegistration(input = input, status = HttpStatusCode.OK, saved = input)
    }

    @Test
    fun `rejects control characters in contact fields`() = checkRegistration(input = validUser.copy(name = "Test\u0000User"))

    @Test
    fun `rejects short password`() = checkRegistration(input = validUser.copy(password = "short"))

    @Test
    fun `rejects password over bcrypt byte limit`() = checkRegistration(
        input = validUser.copy(password = "é".repeat(37))
    )

    @Test
    fun `accepts password at bcrypt byte limit`() {
        val input = validUser.copy(password = "é".repeat(36))
        checkRegistration(input = input, status = HttpStatusCode.OK, saved = input)
    }

    @Test
    fun `duplicate email returns the same acknowledgement as new registration`() = checkRegistration(
        status = HttpStatusCode.OK,
        duplicate = true,
        saved = validUser,
    )

    private fun checkRegistration(
        input: CreateUserDto = validUser,
        body: String = Json.encodeToString(input),
        status: HttpStatusCode = HttpStatusCode.BadRequest,
        saved: CreateUserDto? = null,
        duplicate: Boolean = false,
    ) = testApplication {
        val repository = RegistrationRepository(duplicate)
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to "registration-test-only-secret-1234567890",
                "jwt.issuer" to "test-issuer",
                "jwt.audience" to "test-audience",
                "jwt.realm" to "test-realm",
            )
        }
        application {
            install(ContentNegotiation) { json() }
            configureSecurity()
            routing { userRoutes(repository) }
        }
        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(status, response.status)
        assertEquals(saved, repository.received)
        assertEquals(if (saved != null) UserRole.RENTER else null, repository.receivedRole)
        assertFalse(response.bodyAsText().contains(input.password))
        if (status == HttpStatusCode.OK) {
            assertEquals("Registration request processed.", response.bodyAsText())
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), response.contentType())
        }
    }

    // Only creation is needed: registration must not require a user lookup or JWT.
    private class RegistrationRepository(private val duplicate: Boolean) : IUserRepository {
        var received: CreateUserDto? = null
        var receivedRole: UserRole? = null

        override suspend fun create(user: CreateUserDto, role: UserRole) {
            received = user
            receivedRole = role
            if (duplicate) throw DuplicateEmailException(IllegalStateException("Duplicate test email"))
        }

        override fun toDto(entity: User): UserDto = error("Unexpected user mapping")
        override suspend fun findAll(): List<UserDto> = error("Unexpected user lookup")
        override suspend fun findById(id: Long): UserDto = error("Unexpected user lookup")
        override suspend fun delete(id: Long): Boolean = error("Unexpected user deletion")
        override suspend fun findByEmail(email: String): User = error("Unexpected user lookup")
    }
}
