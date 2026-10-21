package test

import com.rentmycar.database.DatabaseSettings
import com.rentmycar.database.migrateDatabase
import com.rentmycar.domain.auth.PasswordService.verifyPassword
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.UserRepository
import com.rentmycar.domain.user.DuplicateEmailException
import com.rentmycar.domain.user.dto.CreateUserDto
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.mysql.MySQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserRepositoryTest {
    @Test
    fun duplicateEmailDoesNotCreateAnotherUser() = runBlocking {
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
    fun createPersistsUserWithHashedPassword() = runBlocking {
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
