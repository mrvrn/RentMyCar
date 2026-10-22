package com.rentmycar.domain.rental

import com.rentmycar.database.withTransaction
import com.rentmycar.domain.auth.PasswordService
import com.rentmycar.domain.base.BaseRepository
import com.rentmycar.domain.rental.Rentals.carId
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.user.DuplicateEmailException
import com.rentmycar.domain.user.User
import kotlinx.datetime.LocalDateTime
import java.sql.SQLException

class RentalRepository : IRentalRepository, BaseRepository<Rental, RentalDto>(Rental) {
    override fun toDto(entity: Rental): RentalDto = entity.toDto()

    override suspend fun create(rental: CreateRentalDto, renterId: Long, carId: Long) {
        val start = LocalDateTime.parse(rental.startAt)
        val end = LocalDateTime.parse(rental.endAt)

        require(end > start) {
            "End time must be after start time"
        }



//        try {
//            withTransaction {
//                Rental.new {
////                    carId = rental.carId
//                }
//            }
//        } catch (exception: SQLException) {
//            // MySQL duplicate-key errors also cover concurrent registrations.
//            if (generateSequence<Throwable>(exception) { it.cause }
//                    .filterIsInstance<SQLException>().any { it.errorCode == 1062 }) {
//                throw DuplicateEmailException(exception)
//            }
//            throw exception
//        }
    }
}