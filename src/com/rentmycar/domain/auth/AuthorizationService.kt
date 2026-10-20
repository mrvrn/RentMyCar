package com.rentmycar.domain.auth

import com.rentmycar.domain.auth.PasswordService.verifyPassword
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.dto.LoginUserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthorizationService(private val userRepository: IUserRepository) {
    // Bcrypt is CPU-intensive; share a limit across all concurrent login requests.
    private val passwordVerificationDispatcher = Dispatchers.Default.limitedParallelism(2)

    suspend fun authenticate(credentials: LoginUserDto): User? {
        val user = userRepository.findByEmail(credentials.email) ?: return null
        val storedHash = user.password
        val passwordMatches = withContext(passwordVerificationDispatcher) {
            verifyPassword(credentials.password, storedHash)
        }
        return if (passwordMatches) user else null
    }
}

