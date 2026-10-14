package com.rentmycar.domain.user

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

class UserDAO(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserDAO>(Users)

    var name by Users.name
    var email by Users.email
    var phone by Users.phone
    var address by Users.address
    var bonusPoints by Users.bonusPoints
    var password by Users.password
}
