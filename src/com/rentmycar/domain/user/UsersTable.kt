package com.rentmycar.domain.user

import org.jetbrains.exposed.v1.core.Table

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100)
    val email = varchar("email", 100).uniqueIndex()
    val phone = varchar("phone", 100)
    val address = varchar("address", 100)
    val bonusPoints = long("bonusPoints").nullable()
    val password = varchar("password", 100)

    override val primaryKey = PrimaryKey(id)
}
