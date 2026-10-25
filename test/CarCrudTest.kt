package test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.migrateDatabase
import com.rentmycar.domain.car.CarRepository
import com.rentmycar.domain.car.Car
import com.rentmycar.domain.rental.Rental
import com.rentmycar.domain.rental.RentalStatus
import kotlinx.datetime.LocalDateTime
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.mysql.MySQLContainer
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertIs
import com.rentmycar.domain.car.DuplicateLicensePlateException

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
            routing { carRoutes(carRepository, com.rentmycar.domain.rental.RentalRepository(), userRepository) }
        }
        block()
    }

    private fun token(userId: Long): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim("userId", userId)
        .withClaim("role", "OWNER")
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC256(SECRET))

    @Test
    fun getCarsReturnsOk() = carTest {
        val created = postCar(carJson("LIST-01"))
        assertEquals(HttpStatusCode.Created, created.status)
        val expected = Json.parseToJsonElement(created.bodyAsText()).jsonObject
        val response = client.get("/cars")
        assertEquals(HttpStatusCode.OK, response.status)
        val cars = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(expected, cars.single { it.jsonObject["id"] == expected["id"] }.jsonObject)
    }

    @Test
    fun `deleting cars with rentals returns conflict and preserves history for every status`() = carTest {
        for (rentalStatus in RentalStatus.entries) {
            val created = postCar(carJson("DEL-${rentalStatus.name}"))
            assertEquals(HttpStatusCode.Created, created.status)
            val carId = Json.parseToJsonElement(created.bodyAsText()).jsonObject.getValue("id").jsonPrimitive.long
            val rentalId = transaction {
                Rental.new {
                    car = Car[carId]
                    renter = User[otherUserId]
                    startAt = LocalDateTime.parse("2100-11-01T10:00:00")
                    endAt = LocalDateTime.parse("2100-11-01T11:00:00")
                    status = rentalStatus
                    totalPrice = "10.00".toBigDecimal()
                    cancelledAt = if (rentalStatus == RentalStatus.CANCELLED) {
                        LocalDateTime.parse("2026-10-25T10:00:00")
                    } else null
                }.id.value
            }

            val response = client.delete("/cars/$carId") { bearerAuth(token(testUserId)) }
            assertEquals(HttpStatusCode.Conflict, response.status, "Rental status: $rentalStatus")
            assertEquals("Car has associated rentals and cannot be deleted",
                Json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("error").jsonPrimitive.content)
            assertEquals(HttpStatusCode.OK, client.get("/cars/$carId").status)
            transaction {
                assertEquals(rentalStatus, Rental[rentalId].status)
                assertEquals(carId, Rental[rentalId].car.id.value)
            }
        }
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
        assertEquals(HttpStatusCode.BadRequest, putCar(firstId, """{"brand":"Must roll back","dropOffId":999999}""").status)
        assertEquals(HttpStatusCode.Conflict, putCar(firstId, """{"licensePlate":"UPD-02"}""").status)
        assertEquals(HttpStatusCode.NotFound, putCar(999_999, """{"brand":"Audi"}""").status)

        val unchanged = Json.parseToJsonElement(client.get("/cars/$firstId").bodyAsText()).jsonObject
        assertEquals("UPD-01", unchanged["licensePlate"]!!.jsonPrimitive.content)
        assertEquals("10.00", unchanged["pricePerHour"]!!.jsonPrimitive.content)
        assertEquals("Audi", unchanged["brand"]!!.jsonPrimitive.content)
    }

    @Test
    fun `plates are normalized and normalized duplicates are rejected`() = carTest {
        val created = postCar(carJson("  norm-01  "))
        assertEquals(HttpStatusCode.Created, created.status)
        val body = Json.parseToJsonElement(created.bodyAsText()).jsonObject
        val id = body.getValue("id").jsonPrimitive.long
        assertEquals("NORM-01", body.getValue("licensePlate").jsonPrimitive.content)
        assertEquals(HttpStatusCode.Conflict, postCar(carJson(" norm-01 ")).status)
        assertEquals(HttpStatusCode.Created, postCar(carJson("NORM-02")).status)
        assertEquals(HttpStatusCode.Conflict, putCar(id, """{"licensePlate":" norm-02 "}""").status)
        assertEquals(HttpStatusCode.OK, putCar(id, """{"licensePlate":" norm-01 "}""").status)
        assertEquals(HttpStatusCode.OK, putCar(id, """{"licensePlate":" norm-03 "}""").status)
        assertEquals("NORM-03", carRepository.findById(id)?.licensePlate)
    }

    @Test
    fun `shared car validation returns bad request without altering stored data`() = carTest {
        val input = CreateCarDto("Validation", "Car", "VALIDATE-01", "12.50".toBigDecimal(), pickupLocId, dropOffLocId)
        val created = postCar(Json.encodeToString(input))
        assertEquals(HttpStatusCode.Created, created.status)
        val id = Json.parseToJsonElement(created.bodyAsText()).jsonObject.getValue("id").jsonPrimitive.long
        val original = carRepository.findById(id)
        val invalid = listOf(
            input.copy(brand = "a".repeat(256)), input.copy(type = "a".repeat(256)),
            input.copy(licensePlate = "A".repeat(256)), input.copy(licensePlate = "ß".repeat(128)),
            input.copy(licensePlate = "INVALID/<plate>"), input.copy(brand = "Bad\u0000Brand"),
            input.copy(pictures = List(21) { "https://example.com/car.png" }),
            input.copy(pictures = listOf("https://example.com/".padEnd(2049, 'a'))),
            input.copy(pictures = listOf("relative.png")), input.copy(pictures = listOf("javascript:alert(1)")),
            input.copy(pictures = listOf("https://user:password@example.com/car.png")),
            input.copy(pickupId = 0), input.copy(dropOffId = -1),
        )
        for (badInput in invalid) {
            assertEquals(HttpStatusCode.BadRequest, postCar(Json.encodeToString(badInput)).status)
            val update = UpdateCarDto(badInput.brand, badInput.type, badInput.licensePlate, badInput.pricePerHour,
                badInput.pickupId, badInput.dropOffId, badInput.pictures, badInput.isAvailable)
            assertEquals(HttpStatusCode.BadRequest, putCar(id, Json.encodeToString(update)).status)
            assertEquals(original, carRepository.findById(id))
        }
        val boundary = input.copy(brand = "a".repeat(255), type = "b".repeat(255), licensePlate = "C".repeat(255),
            pictures = List(20) { "https://example.com/".padEnd(2048, 'a') })
        val boundaryResponse = postCar(Json.encodeToString(boundary))
        assertEquals(HttpStatusCode.Created, boundaryResponse.status)
        val boundaryId = Json.parseToJsonElement(boundaryResponse.bodyAsText()).jsonObject.getValue("id").jsonPrimitive.long
        assertEquals(boundary.pictures, carRepository.findById(boundaryId)?.pictures)
        assertEquals(255, carRepository.findById(boundaryId)?.licensePlate?.length)
    }

    @Test
    fun `updates persist all editable fields and preserve omitted fields`() = carTest {
        val created = postCar(carJson("FIELDS-01"))
        assertEquals(HttpStatusCode.Created, created.status)
        val id = Json.parseToJsonElement(created.bodyAsText()).jsonObject.getValue("id").jsonPrimitive.long
        assertEquals(false, carRepository.findById(id)?.isAvailable)
        val update = UpdateCarDto("Updated", "Estate", "FIELDS-02", "22.25".toBigDecimal(),
            dropOffLocId, pickupLocId, listOf("https://example.com/updated.png"), true)
        val response = putCar(id, Json.encodeToString(update))
        assertEquals(HttpStatusCode.OK, response.status)
        val saved = carRepository.findById(id)!!
        assertEquals(testUserId, saved.ownerId)
        assertEquals(update.brand, saved.brand)
        assertEquals(update.type, saved.type)
        assertEquals(update.licensePlate, saved.licensePlate)
        assertEquals(update.pricePerHour, saved.pricePerHour)
        assertEquals(update.pickupId, saved.pickupId)
        assertEquals(update.dropOffId, saved.dropOffId)
        assertEquals(update.pictures, saved.pictures)
        assertTrue(saved.isAvailable)
        assertEquals(HttpStatusCode.OK, putCar(id, "{}").status)
        assertEquals(saved, carRepository.findById(id))
        assertEquals(HttpStatusCode.OK, putCar(id, """{"pictures":[],"isAvailable":false}""").status)
        assertEquals(saved.copy(pictures = emptyList(), isAvailable = false), carRepository.findById(id))
    }

    @Test
    fun `price limits reject invalid create and update without changing stored data`() = carTest {
        val created = postCar(carJson("LIMIT-01", pricePerHour = "99999999.99"))
        assertEquals(HttpStatusCode.Created, created.status)
        val id = Json.parseToJsonElement(created.bodyAsText()).jsonObject.getValue("id").jsonPrimitive.long
        val original = carRepository.findById(id)
        for (price in listOf("100000000", "1.001")) {
            assertEquals(HttpStatusCode.BadRequest, postCar(carJson("LIMIT-BAD", pricePerHour = price)).status)
            assertEquals(HttpStatusCode.BadRequest, putCar(id, """{"pricePerHour":$price}""").status)
            assertEquals(original, carRepository.findById(id))
        }
        assertEquals(HttpStatusCode.OK, putCar(id, """{"pricePerHour":12.5000}""").status)
        assertEquals("12.50".toBigDecimal(), carRepository.findById(id)?.pricePerHour)
        assertEquals(HttpStatusCode.OK, putCar(id, """{"pricePerHour":99999999.99}""").status)
        assertEquals(original, carRepository.findById(id))
    }

    @Test
    fun `concurrent car creation stores one normalized plate`() = carTest {
        val gate = CompletableDeferred<Unit>()
        val input = CreateCarDto("Test", "Car", " race-01 ", "10.00".toBigDecimal(), pickupLocId, dropOffLocId)
        val outcomes = coroutineScope {
            val attempts = List(2) {
                async(Dispatchers.IO) { gate.await(); runCatching { carRepository.create(input, testUserId) } }
            }
            gate.complete(Unit)
            attempts.awaitAll()
        }
        assertEquals(1, outcomes.count { it.isSuccess })
        assertIs<DuplicateLicensePlateException>(outcomes.single { it.isFailure }.exceptionOrNull())
        assertEquals(1, carRepository.findAll().count { it.licensePlate == "RACE-01" })
    }
}
