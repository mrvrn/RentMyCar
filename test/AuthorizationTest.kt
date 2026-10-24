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

        val ownerHandlerCalls = AtomicInteger()
        val unauthenticatedHandlerCalls = AtomicInteger()

        var ownerId: Long = 0
        var renterId: Long = 0

        val initDb: Boolean by lazy {
            val mysql = MySQLContainer("mysql:8.1").apply { start() }
            val settings = DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password)
            migrateDatabase(settings)
            settings.connect()
            runBlocking {
                repository.create(CreateUserDto(
                    name = "Owner User",
                    email = "owner@example.com",
                    phone = "0611111111",
                    address = "Street 1",
                    password = "password123",
                ), UserRole.OWNER)
                repository.create(CreateUserDto(
                    name = "Renter User",
                    email = "renter@example.com",
                    phone = "0622222222",
                    address = "Street 2",
                    password = "password123",
                ), UserRole.RENTER)
                ownerId = repository.findAll().single { it.email == "owner@example.com" }.id
                renterId = repository.findAll().single { it.email == "renter@example.com" }.id
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
                    route("/owner-only") {
                        authorize(UserRole.OWNER) {
                            get {
                                ownerHandlerCalls.incrementAndGet()
                                call.respondText("Welcome Owner")
                            }
                        }
                    }

                    route("/renter-only") {
                        authorize(UserRole.RENTER) {
                            get {
                                call.respondText("Welcome Renter")
                            }
                        }
                    }
                }

                // authorize {} supplies its own authentication boundary.
                route("/authorize-without-authenticate") {
                    authorize(UserRole.OWNER) {
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
            setBody("""{"email":"owner@example.com","password":"password123"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val token = body["token"]?.jsonPrimitive?.content
        val role = body["role"]?.jsonPrimitive?.content

        assertNotNull(token)
        assertEquals("OWNER", role)

        val decoded = JWT.require(Algorithm.HMAC256(SECRET))
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .build()
            .verify(token)
        assertEquals("OWNER", decoded.getClaim("role").asString())
        assertEquals(ownerId, decoded.getClaim("userId").asLong())
    }

    @Test
    fun ownerCanAccessOwnerRoute() = authzTest {
        val loginRes = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"owner@example.com","password":"password123"}""")
        }
        val token = Json.parseToJsonElement(loginRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val response = client.get("/owner-only") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Welcome Owner", response.bodyAsText())
    }

    @Test
    fun renterCannotAccessOwnerRouteGetsForbidden() = authzTest {
        val loginRes = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"renter@example.com","password":"password123"}""")
        }
        val token = Json.parseToJsonElement(loginRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val response = client.get("/owner-only") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun renterCanAccessRenterRoute() = authzTest {
        val loginRes = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"renter@example.com","password":"password123"}""")
        }
        val token = Json.parseToJsonElement(loginRes.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val response = client.get("/renter-only") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Welcome Renter", response.bodyAsText())
    }

    @Test
    fun unauthenticatedRequestGetsUnauthorized() = authzTest {
        val response = client.get("/owner-only")
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
        val token = loginToken("renter@example.com")
        val before = ownerHandlerCalls.get()

        val response = client.get("/owner-only") { bearerAuth(token) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("Access denied: insufficient permissions", response.bodyAsText())
        assertEquals(before, ownerHandlerCalls.get())
    }

    @Test
    fun authorizeWithoutAuthenticateFailsClosed() = authzTest {
        val response = client.get("/authorize-without-authenticate")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(0, unauthenticatedHandlerCalls.get())
    }

    @Test
    fun selfRegisteredUserCannotBecomeOwner() = authzTest {
        val email = "wannabe-owner@example.com"
        val withRole = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Wannabe","email":"$email","phone":"0633333333","address":"Street 3","password":"password123","role":"OWNER"}""")
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
        assertEquals("RENTER", Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject["role"]!!.jsonPrimitive.content)
        val token = Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
        val decoded = JWT.require(Algorithm.HMAC256(SECRET)).withIssuer(ISSUER).withAudience(AUDIENCE).build().verify(token)
        assertEquals("RENTER", decoded.getClaim("role").asString())

        assertEquals(HttpStatusCode.Forbidden, client.get("/owner-only") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/renter-only") { bearerAuth(token) }.status)
    }

    @Test
    fun renterLoginTokenContainsOnlyStoredRole() = authzTest {
        val decoded = JWT.require(Algorithm.HMAC256(SECRET)).withIssuer(ISSUER).withAudience(AUDIENCE).build()
            .verify(loginToken("renter@example.com"))
        assertEquals("RENTER", decoded.getClaim("role").asString())
        assertEquals(renterId, decoded.getClaim("userId").asLong())
    }

    @Test
    fun roleForgedWithOtherSecretIsRejected() = authzTest {
        val forged = signedToken(renterId, "OWNER", secret = "attacker-secret")
        assertEquals(HttpStatusCode.Unauthorized, client.get("/owner-only") { bearerAuth(forged) }.status)
    }

    @Test
    fun unknownOrMalformedRoleClaimIsForbidden() = authzTest {
        for (role in listOf("OWNER ", "ADMIN", "")) {
            val response = client.get("/owner-only") { bearerAuth(signedToken(ownerId, role)) }
            assertEquals(HttpStatusCode.Forbidden, response.status, "role=$role")
        }
    }

    @Test
    fun authorizationServiceValidatesPermissions() {
        // Owner permissions
        assertTrue(authService.canManageCars(UserRole.OWNER))
        assertFalse(authService.canManageCars(UserRole.RENTER))

        // Car ownership
        assertTrue(authService.canManageCar(userId = 10, role = UserRole.OWNER, carOwnerId = 10))
        assertFalse(authService.canManageCar(userId = 11, role = UserRole.OWNER, carOwnerId = 10))
        assertFalse(authService.canManageCar(userId = 10, role = UserRole.RENTER, carOwnerId = 10))

        // Cost calculation
        assertTrue(authService.canCalculateCost(UserRole.OWNER))
        assertFalse(authService.canCalculateCost(UserRole.RENTER))

        // Reservation permissions
        assertTrue(authService.canReserveCar(UserRole.RENTER))
        assertFalse(authService.canReserveCar(UserRole.OWNER))
    }
}
