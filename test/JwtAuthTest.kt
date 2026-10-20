package test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.migrateDatabase
import com.rentmycar.domain.user.UserRepository
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.server.auth.configureSecurity
import com.rentmycar.server.routing.userRoutes
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.testcontainers.mysql.MySQLContainer
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.ktor.client.request.header

class JwtAuthTest {
    private companion object {
        const val SECRET = "test-secret"
        const val ISSUER = "http://test-issuer/"
        const val AUDIENCE = "rentmycar-test"
        const val EMAIL = "jwt@example.com"
        const val PASSWORD = "correct-password"

        val repository = UserRepository()

        // One container for the whole class: starting MySQL per test is slow.
        // Testcontainers removes it when the JVM exits.
        val userId: Long by lazy {
            val mysql = MySQLContainer("mysql:8.1").apply { start() }
            val settings = DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password)
            migrateDatabase(settings)
            settings.connect()
            runBlocking {
                repository.create(CreateUserDto("JWT User", EMAIL, "0612345678", "Test Street 1", PASSWORD))
                repository.findAll().single { it.email == EMAIL }.id
            }
        }
    }

    private fun jwtTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        userId // make sure the database and test user exist
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to SECRET,
                "jwt.issuer" to ISSUER,
                "jwt.audience" to AUDIENCE,
                "jwt.realm" to "RentMyCar",
            )
        }
        application {
            install(ContentNegotiation) { json() }
            configureSecurity()
            routing { userRoutes(repository) }
        }
        block()
    }

    private fun token(
        secret: String = SECRET,
        issuer: String = ISSUER,
        audience: String = AUDIENCE,
        userId: Long? = JwtAuthTest.userId,
        expiresAt: Date = Date(System.currentTimeMillis() + 60_000),
    ): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .apply { if (userId != null) withClaim("userId", userId) }
        .withExpiresAt(expiresAt)
        .sign(Algorithm.HMAC256(secret))

    private suspend fun HttpClient.login(email: String, password: String): HttpResponse =
        post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }

    private suspend fun HttpClient.getUser(token: String? = null): HttpResponse =
        get("/users/$userId") { if (token != null) bearerAuth(token) }

    // --- /login ---

    @Test
    fun loginWithValidCredentialsReturnsSignedToken() = jwtTest {
        val response = client.login(EMAIL, PASSWORD)
        assertEquals(HttpStatusCode.OK, response.status)

        val token = Json.parseToJsonElement(response.bodyAsText()).jsonObject["token"]?.jsonPrimitive?.content
        assertNotNull(token)

        // Throws if the signature, issuer or audience is wrong.
        val decoded = JWT.require(Algorithm.HMAC256(SECRET))
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()
            .verify(token)
        assertEquals(userId, decoded.getClaim("userId").asLong())
        assertTrue(decoded.expiresAt.after(Date()))
    }

    @Test
    fun loginWithWrongPasswordIsRejected() = jwtTest {
        assertEquals(HttpStatusCode.Unauthorized, client.login(EMAIL, "wrong-password").status)
    }

    @Test
    fun loginWithUnknownEmailIsRejected() = jwtTest {
        assertEquals(HttpStatusCode.Unauthorized, client.login("nobody@example.com", PASSWORD).status)
    }

    @Test
    fun loginTokenGivesAccessToProtectedRoute() = jwtTest {
        val body = client.login(EMAIL, PASSWORD).bodyAsText()
        val token = Json.parseToJsonElement(body).jsonObject["token"]!!.jsonPrimitive.content

        val response = client.getUser(token)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(userId, Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.long)
    }

    // --- protected routes ---

    @Test
    fun validTokenIsAccepted() = jwtTest {
        assertEquals(HttpStatusCode.OK, client.getUser(token()).status)
    }

    @Test
    fun missingTokenIsRejectedWithChallengeMessage() = jwtTest {
        val response = client.getUser()
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("Token is not valid or has expired"))
    }

    @Test
    fun malformedTokenIsRejected() = jwtTest {
        assertEquals(HttpStatusCode.Unauthorized, client.getUser("not-a-jwt").status)
    }

    @Test
    fun tokenSignedWithOtherSecretIsRejected() = jwtTest {
        assertEquals(HttpStatusCode.Unauthorized, client.getUser(token(secret = "attacker-secret")).status)
    }

    @Test
    fun expiredTokenIsRejected() = jwtTest {
        val expired = token(expiresAt = Date(System.currentTimeMillis() - 60_000))
        assertEquals(HttpStatusCode.Unauthorized, client.getUser(expired).status)
    }

    @Test
    fun tokenWithWrongIssuerIsRejected() = jwtTest {
        assertEquals(HttpStatusCode.Unauthorized, client.getUser(token(issuer = "http://evil/")).status)
    }

    @Test
    fun tokenWithWrongAudienceIsRejected() = jwtTest {
        assertEquals(HttpStatusCode.Unauthorized, client.getUser(token(audience = "other-app")).status)
    }

    @Test
    fun tokenWithoutUserIdClaimIsRejected() = jwtTest {
        assertEquals(HttpStatusCode.Unauthorized, client.getUser(token(userId = null)).status)
    }

    @Test
    fun wrongAuthenticationSchemeIsRejected() = jwtTest {
        val response = client.get("/users/$userId") {
            header(HttpHeaders.Authorization, "Basic dXNlcjpwYXNz")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun nonNumericUserIdIsRejected() = jwtTest {
        val invalidToken = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("userId", "not-a-number")
            .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
            .sign(Algorithm.HMAC256(SECRET))

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.getUser(invalidToken).status
        )
    }
}
