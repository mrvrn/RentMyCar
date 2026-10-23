package test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.migrateDatabase
import com.rentmycar.domain.car.CarRepository
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import com.rentmycar.domain.location.Location
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.UserRepository
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.server.auth.configureSecurity
import com.rentmycar.server.routing.carRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.mysql.MySQLContainer
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class CarCrudTest {
    private companion object {
        const val SECRET = "car-test-secret"
        const val ISSUER = "http://car-test-issuer/"
        const val AUDIENCE = "rentmycar-test"

        val carRepository = CarRepository()
        val userRepository = UserRepository()

        var testUserId: Long = 0
        var otherUserId: Long = 0
        var pickupLocId: Long = 0
        var dropOffLocId: Long = 0

        val initDatabase: Boolean by lazy {
            val mysql = MySQLContainer("mysql:8.1").apply { start() }
            val settings = DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password)
            migrateDatabase(settings)
            val db = settings.connect()
            runBlocking {
                userRepository.create(CreateUserDto("Car Owner", "owner@example.com", "0611111111", "Street 1", "password123"))
                userRepository.create(CreateUserDto("Other Owner", "other@example.com", "0622222222", "Street 2", "password123"))
                testUserId = userRepository.findAll().single { it.email == "owner@example.com" }.id
                otherUserId = userRepository.findAll().single { it.email == "other@example.com" }.id

                transaction(db) {
                    val u = User.findById(testUserId)!!
                    val loc1 = Location.new {
                        streetName = "Kalverstraat"
                        houseNumber = "1"
                        postalCode = "1012NX"
                        city = "Amsterdam"
                        country = "Nederland"
                        user = u
                    }
                    val loc2 = Location.new {
                        streetName = "Coolsingel"
                        houseNumber = "2"
                        postalCode = "3011AD"
                        city = "Rotterdam"
                        country = "Nederland"
                        user = u
                    }
                    pickupLocId = loc1.id.value
                    dropOffLocId = loc2.id.value
                }
            }
            true
        }
    }

    private fun carTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        initDatabase
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
            routing { carRoutes(carRepository) }
        }
        block()
    }

    private fun token(userId: Long): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC256(SECRET))

    @Test
    fun getCarsReturnsOk() = carTest {
        val response = client.get("/cars")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun postCarWithoutAuthReturnsUnauthorized() = carTest {
        val response = client.post("/cars") {
            contentType(ContentType.Application.Json)
            setBody("""{"brand":"Tesla","type":"Sedan","licensePlate":"XX-11-YY","pricePerHour":10.00,"pickupId":$pickupLocId,"dropOffId":$dropOffLocId}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun postCarWithAuthCreatesCar() = carTest {
        val response = client.post("/cars") {
            bearerAuth(token(testUserId))
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateCarDto(
                brand = "Tesla",
                type = "Model 3",
                licensePlate = "AA-11-BB",
                pricePerHour = "8.55".toBigDecimal(),
                pickupId = pickupLocId,
                dropOffId = dropOffLocId,
                pictures = listOf("https://example.com/car.png"),
                isAvailable = true
            )))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val carId = Json.parseToJsonElement(response.bodyAsText()).jsonObject["id"]!!.jsonPrimitive.long

        // Fetch by ID
        val getRes = client.get("/cars/$carId")
        assertEquals(HttpStatusCode.OK, getRes.status)
        val body = Json.parseToJsonElement(getRes.bodyAsText()).jsonObject
        assertEquals("Tesla", body["brand"]!!.jsonPrimitive.content)
        assertEquals(testUserId, body["ownerId"]!!.jsonPrimitive.long)
        assertEquals("8.55", body["pricePerHour"]!!.jsonPrimitive.content)

        // Duplicate license plate rejected with 409
        val dupRes = client.post("/cars") {
            bearerAuth(token(testUserId))
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CreateCarDto(
                brand = "Tesla",
                type = "Model 3",
                licensePlate = "AA-11-BB",
                pricePerHour = "9.00".toBigDecimal(),
                pickupId = pickupLocId,
                dropOffId = dropOffLocId
            )))
        }
        assertEquals(HttpStatusCode.Conflict, dupRes.status)

        // Other user cannot update car
        val forbiddenUpdate = client.put("/cars/$carId") {
            bearerAuth(token(otherUserId))
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateCarDto(brand = "Hacked")))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenUpdate.status)

        // Owner can update car
        val updateRes = client.put("/cars/$carId") {
            bearerAuth(token(testUserId))
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(UpdateCarDto(brand = "Tesla Updated", pricePerHour = "9.90".toBigDecimal())))
        }
        assertEquals(HttpStatusCode.OK, updateRes.status)
        val updatedBody = Json.parseToJsonElement(updateRes.bodyAsText()).jsonObject
        assertEquals("Tesla Updated", updatedBody["brand"]!!.jsonPrimitive.content)
        assertEquals("9.90", updatedBody["pricePerHour"]!!.jsonPrimitive.content)

        // Other user cannot delete car
        val forbiddenDelete = client.delete("/cars/$carId") {
            bearerAuth(token(otherUserId))
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenDelete.status)

        // Owner can delete car
        val deleteRes = client.delete("/cars/$carId") {
            bearerAuth(token(testUserId))
        }
        assertEquals(HttpStatusCode.NoContent, deleteRes.status)

        // Afterwards 404
        val afterDelete = client.get("/cars/$carId")
        assertEquals(HttpStatusCode.NotFound, afterDelete.status)
    }

    private fun carJson(
        licensePlate: String,
        brand: String = "Audi",
        type: String = "A3",
        pricePerHour: String = "10.00",
        pickupId: Long = pickupLocId,
    ) = """{"brand":"$brand","type":"$type","licensePlate":"$licensePlate","pricePerHour":$pricePerHour,"pickupId":$pickupId,"dropOffId":$dropOffLocId}"""

    private suspend fun ApplicationTestBuilder.postCar(json: String, userId: Long = testUserId) = client.post("/cars") {
        bearerAuth(token(userId))
        contentType(ContentType.Application.Json)
        setBody(json)
    }

    private suspend fun ApplicationTestBuilder.putCar(id: Long, json: String, userId: Long = testUserId) = client.put("/cars/$id") {
        bearerAuth(token(userId))
        contentType(ContentType.Application.Json)
        setBody(json)
    }

    @Test
    fun invalidCreateInputReturnsBadRequest() = carTest {
        assertEquals(HttpStatusCode.BadRequest, postCar(carJson("BAD-01", pricePerHour = "0")).status)
        assertEquals(HttpStatusCode.BadRequest, postCar(carJson("BAD-02", pricePerHour = "-5.00")).status)
        assertEquals(HttpStatusCode.BadRequest, postCar(carJson("BAD-03", pricePerHour = "\"abc\"")).status)
        assertEquals(HttpStatusCode.BadRequest, postCar(carJson("BAD-04", brand = " ")).status)
        assertEquals(HttpStatusCode.BadRequest, postCar(carJson("BAD-05", type = "")).status)
        assertEquals(HttpStatusCode.BadRequest, postCar(carJson("  ")).status)
        assertEquals(HttpStatusCode.BadRequest, postCar(carJson("BAD-06", pickupId = 999_999)).status)
        assertEquals(HttpStatusCode.BadRequest, postCar("""{"brand":"Audi"}""").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/cars/999999").status)
    }

    @Test
    fun invalidUpdateInputIsRejected() = carTest {
        val firstId = Json.parseToJsonElement(postCar(carJson("UPD-01")).bodyAsText()).jsonObject["id"]!!.jsonPrimitive.long
        assertEquals(HttpStatusCode.Created, postCar(carJson("UPD-02")).status)

        assertEquals(HttpStatusCode.BadRequest, putCar(firstId, """{"brand":" "}""").status)
        assertEquals(HttpStatusCode.BadRequest, putCar(firstId, """{"licensePlate":""}""").status)
        assertEquals(HttpStatusCode.BadRequest, putCar(firstId, """{"pricePerHour":0}""").status)
        assertEquals(HttpStatusCode.BadRequest, putCar(firstId, """{"dropOffId":999999}""").status)
        assertEquals(HttpStatusCode.Conflict, putCar(firstId, """{"licensePlate":"UPD-02"}""").status)
        assertEquals(HttpStatusCode.NotFound, putCar(999_999, """{"brand":"Audi"}""").status)

        val unchanged = Json.parseToJsonElement(client.get("/cars/$firstId").bodyAsText()).jsonObject
        assertEquals("UPD-01", unchanged["licensePlate"]!!.jsonPrimitive.content)
        assertEquals("10.00", unchanged["pricePerHour"]!!.jsonPrimitive.content)
    }
}
