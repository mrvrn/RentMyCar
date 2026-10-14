package com.rentmycar.domain.user

import com.rentmycar.domain.base.BaseRepository
import com.rentmycar.domain.user.dto.UserDto

// Inherits findById and delete. The constructor argument UserDAO is its companion
// object (LongEntityClass), which tells the base repository how to query users.
class UserRepository : BaseRepository<UserDAO, UserDto>(UserDAO) {
    // Delegate to the user mapper, which excludes the password from responses.
    override fun toDto(entity: UserDAO): UserDto = entity.toDto()

//    suspend fun create()
}
