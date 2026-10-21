package com.rentmycar.domain.auth

import com.rentmycar.domain.auth.PasswordService.verifyPassword
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.User
import com.rentmycar.domain.user.UserRole
import com.rentmycar.domain.user.dto.LoginUserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthorizationService(private val userRepository: IUserRepository) {
    // Bcrypt is CPU-intensive; share a limit across all concurrent login requests.
    private val passwordVerificationDispatcher = Dispatchers.Default.limitedParallelism(2)

    suspend fun authenticate(credentials: LoginUserDto): User? {
        val user = userRepository.findByEmail(credentials.email.trim()) ?: return null
        val storedHash = user.password
        val passwordMatches = withContext(passwordVerificationDispatcher) {
            verifyPassword(credentials.password, storedHash)
        }
        return if (passwordMatches) user else null
    }

    /**
     * Checks if the user is authorized to add, edit or delete cars (Aanbieder only).
     */
    fun canManageCars(role: UserRole): Boolean = role == UserRole.AANBIEDER

    /**
     * Checks if the user is authorized to edit or delete a specific car (must be Aanbieder AND the car owner).
     */
    fun canManageCar(userId: Long, role: UserRole, carOwnerId: Long): Boolean =
        role == UserRole.AANBIEDER && userId == carOwnerId

    /**
     * Checks if the user is authorized to calculate consumption cost or TCO (Aanbieder only).
     */
    fun canCalculateCost(role: UserRole): Boolean = role == UserRole.AANBIEDER

    /**
     * Checks if the user is authorized to reserve cars (Huurder only).
     */
    fun canReserveCar(role: UserRole): Boolean = role == UserRole.HUURDER

    /**
     * Checks if the user can manage or cancel a reservation.
     * Huurder can manage their own reservation; Aanbieder can manage reservations on their car.
     */
    fun canManageReservation(userId: Long, role: UserRole, renterId: Long, carOwnerId: Long): Boolean =
        when (role) {
            UserRole.HUURDER -> userId == renterId
            UserRole.AANBIEDER -> userId == carOwnerId
        }
}
