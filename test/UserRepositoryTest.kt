package test

import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.migrateDatabase
import com.rentmycar.domain.auth.PasswordService.verifyPassword
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.UserRepository
import com.rentmycar.domain.user.DuplicateEmailException
import com.rentmycar.domain.user.dto.CreateUserDto
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.mysql.MySQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class UserRepositoryTest {
    @Test
    fun `concurrent registration stores one account and preserves its credentials`() = runBlocking {
        MySQLContainer("mysql:8.1").use { mysql ->
            mysql.start()
            val settings = DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password)
            migrateDatabase(settings)
            val database = settings.connect()
            val repository = UserRepository()
            val gate = CompletableDeferred<Unit>()
            val inputs = listOf("First", "Second").map {
                CreateUserDto(it, "race@example.com", "0612345678", "Street 1", "$it-password-123")
            }
            val outcomes = coroutineScope {
                val attempts = inputs.map { input ->
                    async(Dispatchers.IO) { gate.await(); runCatching { repository.create(input) } }
                }
                gate.complete(Unit)
                attempts.awaitAll()
            }
            assertEquals(1, outcomes.count { it.isSuccess })
            assertIs<DuplicateEmailException>(outcomes.single { it.isFailure }.exceptionOrNull())
            val winner = inputs[outcomes.indexOfFirst { it.isSuccess }]
            transaction(database) {
                val saved = User.all().single()
                assertEquals(winner.name, saved.name)
                assertTrue(verifyPassword(winner.password, saved.password))
                assertFalse(verifyPassword(inputs.single { it != winner }.password, saved.password))
            }
        }
    }

    @Test
    fun `duplicate email does not create another user`() = runBlocking {
        MySQLContainer("mysql:8.1").use { mysql ->
            mysql.start()
            val settings = DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password)
            migrateDatabase(settings)
            val database = settings.connect()
            val repository = UserRepository()
            val input = CreateUserDto(
                "Test User", "duplicate@example.com", "0612345678", "Test Street 1", "test-password-123"
            )

            repository.create(input)
            assertFailsWith<DuplicateEmailException> { repository.create(input) }
            transaction(database) {
                assertEquals(1L, User.all().count())
            }
        }
    }

    @Test
    fun `create persists user with hashed password`() = runBlocking {
        MySQLContainer("mysql:8.1").use { mysql ->
            mysql.start()
            val settings = DatabaseSettings(mysql.jdbcUrl, mysql.username, mysql.password)
            migrateDatabase(settings)
            val database = settings.connect()
            val repository = UserRepository()
            val input = CreateUserDto(
                name = "Test User",
                email = "test@example.com",
                phone = "0612345678",
                address = "Test Street 1",
                password = "test-password-123",
            )

            // create opens and commits its own transaction.
            repository.create(input)

            // A separate transaction verifies that the user was committed.
            transaction(database) {
                val saved = User.all().single()
                assertTrue(saved.id.value > 0)
                assertEquals(input.name, saved.name)
                assertEquals(input.email, saved.email)
                assertEquals(input.phone, saved.phone)
                assertEquals(input.address, saved.address)
                assertNull(saved.bonusPoints)
                assertNotEquals(input.password, saved.password)
                assertTrue(verifyPassword(input.password, saved.password))
                assertFalse(verifyPassword("wrong-password", saved.password))
            }
        }
    }
}
