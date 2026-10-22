package test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.migrateDatabase
import com.rentmycar.domain.auth.AuthorizationService
import com.rentmycar.domain.user.UserRole
import com.rentmycar.domain.user.UserRepository
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.domain.user.dto.LoginUserDto
import com.rentmycar.server.auth.authorize
import com.rentmycar.server.auth.configureSecurity
import com.rentmycar.server.routing.userRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.mysql.MySQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

class AuthorizationTest {
    private companion object {
        const val SECRET = "authz-test-secret"
        const val ISSUER = "http://authz-test-issuer/"
        const val AUDIENCE = "rentmycar-test"

        val repository = UserRepository()
        val authService = AuthorizationService(repository)

        val aanbiederHandlerCalls = AtomicInteger()
        val unauthenticatedHandlerCalls = AtomicInteger()

        var aanbiederId: Long = 0
        var huurderId: Long = 0

        val initDb: Boolean by lazy {
            val mysql = MySQLContainer("mysql:8.1").apply { start() }
            val settings = DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password)
            migrateDatabase(settings)
            settings.connect()
            runBlocking {
                repository.create(CreateUserDto(
                    name = "Aanbieder User",
                    email = "aanbieder@example.com",
                    phone = "0611111111",
                    address = "Street 1",
                    password = "password123",
                ), UserRole.AANBIEDER)
                repository.create(CreateUserDto(
                    name = "Huurder User",
                    email = "huurder@example.com",
                    phone = "0622222222",
                    address = "Street 2",
                    password = "password123",
                ), UserRole.HUURDER)
                aanbiederId = repository.findAll().single { it.email == "aanbieder@example.com" }.id
                huurderId = repository.findAll().single { it.email == "huurder@example.com" }.id
            }
            true
        }
    }

    private fun authzTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        initDb
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
            routing {
                userRoutes(repository)

                authenticate("auth-jwt") {
                    route("/aanbieder-only") {
                        authorize(UserRole.AANBIEDER) {
                            get {
                                aanbiederHandlerCalls.incrementAndGet()
                                call.respondText("Welcome Aanbieder")
                            }
                        }
                    }

                    route("/huurder-only") {
                        authorize(UserRole.HUURDER) {
                            get {
                                call.respondText("Welcome Huurder")
                            }
                        }
                    }
                }

                // Misconfiguration guard: authorize {} without authenticate {} must fail closed.
                route("/authorize-without-authenticate") {
                    authorize(UserRole.AANBIEDER) {
                        get {
                            unauthenticatedHandlerCalls.incrementAndGet()
                            call.respondText("Should not be reachable")
                        }
                    }
                }
            }
        }
        block()
    }

    @Test
    fun loginReturnsJwtWithRoleClaim() = authzTest {
        val response = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"aanbieder@example.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val token = body["token"]?.jsonPrimitive?.content
        val role = body["role"]?.jsonPrimitive?.content

        assertNotNull(token)
        assertEquals("AANBIEDER", role)

        val decoded = JWT.require(Algorithm.HMAC256(SECRET))
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()
            .verify(token)
        assertEquals("AANBIEDER", decoded.getClaim("role").asString())
        assertEquals(aanbiederId, decoded.getClaim("userId").asLong())
    }

    @Test
    fun aanbiederCanAccessAanbiederRoute() = authzTest {
        val loginRes = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"aanbieder@example.com","password":"password123"}""")
        }
        val token = Json.parseToJsonElement(loginRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val response = client.get("/aanbieder-only") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Welcome Aanbieder", response.bodyAsText())
    }

    @Test
    fun huurderCannotAccessAanbiederRouteGetsForbidden() = authzTest {
        val loginRes = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"huurder@example.com","password":"password123"}""")
        }
        val token = Json.parseToJsonElement(loginRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val response = client.get("/aanbieder-only") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun huurderCanAccessHuurderRoute() = authzTest {
        val loginRes = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"huurder@example.com","password":"password123"}""")
        }
        val token = Json.parseToJsonElement(loginRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val response = client.get("/huurder-only") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Welcome Huurder", response.bodyAsText())
    }

    @Test
    fun unauthenticatedRequestGetsUnauthorized() = authzTest {
        val response = client.get("/aanbieder-only")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private suspend fun ApplicationTestBuilder.loginToken(email: String, password: String = "password123"): String {
        val response = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
    }

    private fun signedToken(userId: Long, role: String, secret: String = SECRET): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim("userId", userId)
        .withClaim("role", role)
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC256(secret))

    @Test
    fun forbiddenRequestDoesNotExecuteProtectedHandler() = authzTest {
        val token = loginToken("huurder@example.com")
        val before = aanbiederHandlerCalls.get()

        val response = client.get("/aanbieder-only") { bearerAuth(token) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("Access denied: insufficient permissions", response.bodyAsText())
        assertEquals(before, aanbiederHandlerCalls.get())
    }

    @Test
    fun authorizeWithoutAuthenticateFailsClosed() = authzTest {
        val response = client.get("/authorize-without-authenticate") {
            bearerAuth(signedToken(aanbiederId, "AANBIEDER"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, unauthenticatedHandlerCalls.get())
    }

    @Test
    fun selfRegisteredUserCannotBecomeAanbieder() = authzTest {
        val email = "wannabe-aanbieder@example.com"
        val withRole = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Wannabe","email":"$email","phone":"0633333333","address":"Street 3","password":"password123","role":"AANBIEDER"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, withRole.status)

        val register = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Wannabe","email":"$email","phone":"0633333333","address":"Street 3","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.Created, register.status)

        val loginResponse = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123"}""")
        }
        assertEquals("HUURDER", Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject["role"]!!.jsonPrimitive.content)
        val token = Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        val decoded = JWT.require(Algorithm.HMAC256(SECRET)).withIssuer(ISSUER).withAudience(AUDIENCE).build().verify(token)
        assertEquals("HUURDER", decoded.getClaim("role").asString())

        assertEquals(HttpStatusCode.Forbidden, client.get("/aanbieder-only") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/huurder-only") { bearerAuth(token) }.status)
    }

    @Test
    fun huurderLoginTokenContainsOnlyStoredRole() = authzTest {
        val decoded = JWT.require(Algorithm.HMAC256(SECRET)).withIssuer(ISSUER).withAudience(AUDIENCE).build()
            .verify(loginToken("huurder@example.com"))
        assertEquals("HUURDER", decoded.getClaim("role").asString())
        assertEquals(huurderId, decoded.getClaim("userId").asLong())
    }

    @Test
    fun roleForgedWithOtherSecretIsRejected() = authzTest {
        val forged = signedToken(huurderId, "AANBIEDER", secret = "attacker-secret")
        assertEquals(HttpStatusCode.Unauthorized, client.get("/aanbieder-only") { bearerAuth(forged) }.status)
    }

    @Test
    fun unknownOrAliasRoleClaimIsForbidden() = authzTest {
        for (role in listOf("OWNER", "aanbieder", "ADMIN", "")) {
            val response = client.get("/aanbieder-only") { bearerAuth(signedToken(aanbiederId, role)) }
            assertEquals(HttpStatusCode.Forbidden, response.status, "role=$role")
        }
    }

    @Test
    fun authorizationServiceValidatesPermissions() {
        // Aanbieder permissions
        assertTrue(authService.canManageCars(UserRole.AANBIEDER))
        assertFalse(authService.canManageCars(UserRole.HUURDER))

        // Car ownership
        assertTrue(authService.canManageCar(userId = 10, role = UserRole.AANBIEDER, carOwnerId = 10))
        assertFalse(authService.canManageCar(userId = 11, role = UserRole.AANBIEDER, carOwnerId = 10))
        assertFalse(authService.canManageCar(userId = 10, role = UserRole.HUURDER, carOwnerId = 10))

        // Cost calculation
        assertTrue(authService.canCalculateCost(UserRole.AANBIEDER))
        assertFalse(authService.canCalculateCost(UserRole.HUURDER))

        // Reservation permissions
        assertTrue(authService.canReserveCar(UserRole.HUURDER))
        assertFalse(authService.canReserveCar(UserRole.AANBIEDER))
    }
}
