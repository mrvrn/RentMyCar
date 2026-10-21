package com.rentmycar.domain.user

import com.rentmycar.domain.user.dto.UserDto

fun User.toDto(): UserDto = UserDto(
    id = id.value,
    name = name,
    email = email,
    phone = phone,
    address = address,
    bonusPoints = bonusPoints,
    role = role
)
