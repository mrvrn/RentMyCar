package test

import com.rentmycar.domain.user.DuplicateEmailException
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.User
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
    fun acceptsRegistrationWithoutJwtAndTrimsContactFields() {
        val input = validUser.copy(
            name = " Test User ", email = " test@example.com ",
            phone = " 0612345678 ", address = " Test Street 1 ",
            password = " keep password spaces ",
        )
        checkRegistration(
            input = input,
            status = HttpStatusCode.Created,
            saved = validUser.copy(password = input.password),
        )
    }

    @Test
    fun rejectsMissingFields() = checkRegistration(body = "{}")

    @Test
    fun rejectsMalformedJson() = checkRegistration(body = "{")

    @Test
    fun rejectsBlankContactField() = checkRegistration(input = validUser.copy(name = "   "))

    @Test
    fun rejectsOversizedContactField() = checkRegistration(input = validUser.copy(address = "a".repeat(101)))

    @Test
    fun rejectsInvalidEmail() = checkRegistration(input = validUser.copy(email = "not-an-email"))

    @Test
    fun rejectsShortPassword() = checkRegistration(input = validUser.copy(password = "short"))

    @Test
    fun rejectsPasswordOverBcryptByteLimit() = checkRegistration(
        input = validUser.copy(password = "é".repeat(37))
    )

    @Test
    fun acceptsPasswordAtBcryptByteLimit() {
        val input = validUser.copy(password = "é".repeat(36))
        checkRegistration(input = input, status = HttpStatusCode.Created, saved = input)
    }

    @Test
    fun duplicateEmailReturnsConflict() = checkRegistration(
        status = HttpStatusCode.Conflict,
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
        assertFalse(response.bodyAsText().contains(input.password))
        if (duplicate) assertEquals("Email is already registered", response.bodyAsText())
    }

    // Only creation is needed: registration must not require a user lookup or JWT.
    private class RegistrationRepository(private val duplicate: Boolean) : IUserRepository {
        var received: CreateUserDto? = null

        override suspend fun create(user: CreateUserDto) {
            received = user
            if (duplicate) throw DuplicateEmailException(IllegalStateException("Duplicate test email"))
        }

        override fun toDto(entity: User): UserDto = error("Unexpected user mapping")
        override suspend fun findAll(): List<UserDto> = error("Unexpected user lookup")
        override suspend fun findById(id: Long): UserDto = error("Unexpected user lookup")
        override suspend fun delete(id: Long): Boolean = error("Unexpected user deletion")
        override suspend fun findByEmail(email: String): User = error("Unexpected user lookup")
    }
}
