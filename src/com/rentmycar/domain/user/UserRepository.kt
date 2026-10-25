package com.rentmycar.domain.user

import com.rentmycar.database.withTransaction
import com.rentmycar.domain.auth.PasswordService
import com.rentmycar.domain.base.BaseRepository
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.domain.user.dto.UserDto
import org.jetbrains.exposed.v1.core.eq
import java.sql.SQLException

// Inherits findById and delete. The constructor argument User is its companion
// object (LongEntityClass), which tells the base repository how to query users.
class UserRepository : IUserRepository, BaseRepository<User, UserDto>(User) {
    // Delegate to the user mapper, which excludes the password from responses.
    override fun toDto(entity: User): UserDto = entity.toDto()

    override suspend fun create(user: CreateUserDto, userRole: UserRole) {
        try {
            val userPassword = PasswordService.hashPassword(user.password)

            withTransaction {
                User.new {
                    name = user.name
                    email = user.email
                    phone = user.phone
                    address = user.address
                    password = userPassword
                    role = userRole
                }
            }
        } catch (exception: SQLException) {
            // MySQL duplicate-key errors also cover concurrent registrations.
            if (generateSequence<Throwable>(exception) { it.cause }
                    .filterIsInstance<SQLException>().any { it.errorCode == 1062 }) {
                throw DuplicateEmailException(exception)
            }
            throw exception
        }
    }

    override suspend fun findByEmail(email: String): User? = withTransaction {
        User.find { Users.email eq email }.firstOrNull()
    }
}
