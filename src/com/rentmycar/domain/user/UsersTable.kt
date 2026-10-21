package com.rentmycar.domain.user

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object Users : LongIdTable("users") {
    val name = varchar("name", 100)
    val email = varchar("email", 100).uniqueIndex()
    val phone = varchar("phone", 100)
    val address = varchar("address", 100)
    val bonusPoints = long("bonusPoints").nullable()
    val password = varchar("password", 100)
    val role = enumerationByName("role", 50, UserRole::class).default(UserRole.HUURDER)
}
