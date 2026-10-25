package test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.domain.car.Car
import com.rentmycar.domain.car.CarCategory
import com.rentmycar.domain.car.ICarRepository
import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import com.rentmycar.domain.rental.RentalRepository
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.UserRole
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.domain.user.dto.UserDto
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.server.auth.configureSecurity
import com.rentmycar.server.routing.carRoutes
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.math.BigDecimal
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class CarCostRoutesTest {
    private companion object {
        const val secret = "cost-test-secret"
        const val issuer = "http://cost-test-issuer/"
        const val audience = "cost-test-audience"
        const val ownerId = 10L
        const val otherOwnerId = 11L
        const val renterId = 12L
    }

    private fun token(userId: Long, role: UserRole): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("userId", userId)
        .withClaim("role", role.name)
        .withExpiresAt(Date(System.currentTimeMillis() + 60_000))
        .sign(Algorithm.HMAC256(secret))

    private fun costTest(block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        val cars = CostCarRepository()
        val users = CostUserRepository()
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to secret,
                "jwt.issuer" to issuer,
                "jwt.audience" to audience,
                "jwt.realm" to "RentMyCar"
            )
        }
        application {
            install(ContentNegotiation) { json() }
            configureSecurity()
            routing { carRoutes(cars, RentalRepository(), users) }
        }
        block()
    }

    @Test
    fun `owner can calculate consumption cost and tco`() = costTest {
        assertEquals(HttpStatusCode.OK, client.get("/cars/1/consumption-cost") {
            bearerAuth(token(ownerId, UserRole.OWNER))
        }.status)
        assertEquals(HttpStatusCode.OK, client.get("/cars/1/tco?kmPerYear=12000") {
            bearerAuth(token(ownerId, UserRole.OWNER))
        }.status)
    }

    @Test
    fun `cost routes enforce authentication ownership and input`() = costTest {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/cars/1/consumption-cost").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/cars/1/consumption-cost") {
            bearerAuth(token(otherOwnerId, UserRole.OWNER))
        }.status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/cars/1/consumption-cost") {
            bearerAuth(token(renterId, UserRole.RENTER))
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/cars/999/consumption-cost") {
            bearerAuth(token(ownerId, UserRole.OWNER))
        }.status)
        for (query in listOf("", "?kmPerYear=abc", "?kmPerYear=0", "?kmPerYear=-5")) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/cars/1/tco$query") {
                bearerAuth(token(ownerId, UserRole.OWNER))
            }.status)
        }
    }
}

private class CostCarRepository : ICarRepository {
    private val car = CarDto(
        id = 1,
        ownerId = 10,
        brand = "Test",
        type = "Hatchback",
        licensePlate = "COST-01",
        pricePerHour = BigDecimal("10.00"),
        pickupId = 1,
        dropOffId = 1,
        pictures = null,
        isAvailable = true,
        category = CarCategory.ICE,
        fuelType = "GASOLINE",
        consumptionPer100Km = BigDecimal("6.00")
    )

    override fun toDto(entity: Car): CarDto = error("Unexpected DAO mapping")
    override suspend fun findAll(): List<CarDto> = listOf(car)
    override suspend fun findById(id: Long): CarDto? = if (id == car.id) car else null
    override suspend fun delete(id: Long): Boolean = false
    override suspend fun create(car: CreateCarDto, ownerId: Long): CarDto = error("Unexpected create")
    override suspend fun update(id: Long, car: UpdateCarDto): CarDto? = null
}

private class CostUserRepository : IUserRepository {
    private val users = listOf(
        UserDto(10, "Owner", "owner@example.com", "0611111111", "Street 1", null, UserRole.OWNER),
        UserDto(11, "Other", "other@example.com", "0622222222", "Street 2", null, UserRole.OWNER),
        UserDto(12, "Renter", "renter@example.com", "0633333333", "Street 3", null, UserRole.RENTER)
    )

    override fun toDto(entity: User): UserDto = error("Unexpected DAO mapping")
    override suspend fun findAll(): List<UserDto> = users
    override suspend fun findById(id: Long): UserDto? = users.firstOrNull { it.id == id }
    override suspend fun delete(id: Long): Boolean = false
    override suspend fun create(user: CreateUserDto, role: UserRole) = Unit
    override suspend fun findByEmail(email: String): User? = null
}
