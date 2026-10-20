package com.rentmycar.domain.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.dto.LoginUserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AuthorizationService {
    // Bcrypt is CPU-intensive; share a limit across all concurrent login requests.
    private val passwordVerificationDispatcher = Dispatchers.Default.limitedParallelism(2)

    fun hashPassword(password: String): String {
        return BCrypt.withDefaults()
            .hashToString(12, password.toCharArray())
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        return BCrypt.verifyer()
            .verify(password.toCharArray(), storedHash)
            .verified
    }

    suspend fun authenticate(credentials: LoginUserDto, users: IUserRepository): User? {
        val user = users.findByEmail(credentials.email) ?: return null
        val storedHash = user.password
        val passwordMatches = withContext(passwordVerificationDispatcher) {
            verifyPassword(credentials.password, storedHash)
        }
        return if (passwordMatches) user else null
    }
}

