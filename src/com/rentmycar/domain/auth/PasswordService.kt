package com.rentmycar.domain.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PasswordService {
    private val passwordVerificationDispatcher = Dispatchers.Default.limitedParallelism(2)

    suspend fun hashPassword(password: String): String {
        return withContext(passwordVerificationDispatcher) {
            BCrypt.withDefaults()
                .hashToString(12, password.toCharArray())
        }
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        return password.toByteArray(Charsets.UTF_8).size <= 72 && BCrypt.verifyer()
            .verify(password.toCharArray(), storedHash)
            .verified
    }
}