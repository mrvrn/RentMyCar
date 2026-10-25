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
     * Checks if the user is authorized to add, edit, or delete cars (owner only).
     */
    fun canManageCars(role: UserRole): Boolean = role == UserRole.OWNER

    /**
     * Checks if the user is authorized to edit or delete a specific car (must be the owner and the car owner).
     */
    fun canManageCar(userId: Long, role: UserRole, carOwnerId: Long): Boolean =
        role == UserRole.OWNER && userId == carOwnerId

    /**
     * Checks if the user is authorized to calculate consumption cost or TCO (owner only).
     */
    fun canCalculateCost(role: UserRole): Boolean = role == UserRole.OWNER

    /**
     * Checks if the user is authorized to reserve cars.
     */
    fun canReserveCar(role: UserRole): Boolean = role.includes(UserRole.RENTER)

    /**
     * Checks if the user can manage or cancel a reservation.
     * A renter can manage their own reservation; an owner can manage reservations on their car.
     */
    fun canManageReservation(userId: Long, role: UserRole, renterId: Long, carOwnerId: Long): Boolean =
        when (role) {
            UserRole.RENTER -> userId == renterId
            UserRole.OWNER -> userId == renterId || userId == carOwnerId
        }
}
