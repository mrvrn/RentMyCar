package com.rentmycar.domain.rental

import com.rentmycar.database.withTransaction
import com.rentmycar.domain.base.BaseRepository
import com.rentmycar.domain.car.Car
import com.rentmycar.domain.car.Cars
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.rental.dto.RentalOverviewDto
import com.rentmycar.domain.user.User
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal fun calculateRentalPrice(
    pricePerHour: BigDecimal,
    start: LocalDateTime,
    end: LocalDateTime
): BigDecimal {
    require(end > start) { "End time must be after start time" }
    // Use UTC to measure the supplied local clock times without DST adjustments.
    val duration = end.toInstant(TimeZone.UTC) - start.toInstant(TimeZone.UTC)
    val nanos = (duration - duration.inWholeSeconds.seconds).inWholeNanoseconds
    val seconds = "${duration.inWholeSeconds}.${nanos.toString().padStart(9, '0')}".toBigDecimal()
    return (pricePerHour * seconds)
        .divide("3600".toBigDecimal(), 2, RoundingMode.HALF_UP)
}

class RentalRepository : IRentalRepository, BaseRepository<Rental, RentalDto>(Rental) {
    override fun toDto(entity: Rental): RentalDto = entity.toDto()

    override suspend fun findAllByUserId(userId: Long): List<RentalOverviewDto> = withTransaction {
        Rental.find { Rentals.renterId eq userId }.map { it.toOverviewDto() }
    }

    override suspend fun create(
        rental: CreateRentalDto,
        renterId: Long,
        carId: Long
    ): RentalDto {
        require(rental.carId == carId) { "Car ID must match the car in the URL" }
        val start = LocalDateTime.parse(rental.startAt)
        val end = LocalDateTime.parse(rental.endAt)
        val now = kotlin.time.Clock.System.now()
        val zone = TimeZone.of("Europe/Amsterdam")

        require(start.toInstant(zone) > now) {
            "Start time must be in the future"
        }
        require(end > start) {
            "End time must be after start time"
        }

        return withTransaction {
            // Serialize bookings for this car before checking for overlapping dates.
            Cars.selectAll().where { Cars.id eq carId }.forUpdate().singleOrNull()
                ?: throw RentalNotFoundException("Car not found")
            val selectedCar = Car[carId]
            val selectedRenter = User.findById(renterId)
                ?: throw RentalNotFoundException("Renter not found")

            if (!selectedCar.isAvailable) {
                throw RentalUnavailableException("Car is unavailable")
            }
            val overlapping = Rental.find {
                (Rentals.carId eq carId) and
                    (Rentals.status inList listOf(
                        RentalStatus.PENDING, RentalStatus.CONFIRMED, RentalStatus.ACTIVE
                    )) and
                    (Rentals.startAt less end) and (Rentals.endAt greater start)
            }.firstOrNull()
            if (overlapping != null) {
                throw RentalUnavailableException("Car is already reserved for this period")
            }

            val price = calculateRentalPrice(selectedCar.pricePerHour, start, end)
            require(price <= "99999999.99".toBigDecimal()) { "Total price exceeds the supported maximum" }
            Rental.new {
                car = selectedCar
                renter = selectedRenter
                startAt = start
                endAt = end
                status = RentalStatus.PENDING
                totalPrice = price
                cancelledAt = null
                // createdAt uses the database default.
            }.toDto()
        }
    }

    override suspend fun changeStatus(rentalId: Long, status: RentalStatus, loggedInUserId: Long): RentalDto = withTransaction {
        // Lock the row so concurrent decisions cannot overwrite one another.
        Rentals.selectAll().where { Rentals.id eq rentalId }.forUpdate().singleOrNull()
            ?: throw RentalNotFoundException("Rental not found")

        val rental = Rental[rentalId]
        if (status == RentalStatus.CANCELLED) {
            if (rental.renter.id.value != loggedInUserId) {
                throw RentalAccessDeniedException("Only the renter can cancel a rental")
            }

            if (rental.status in listOf(RentalStatus.REJECTED, RentalStatus.ACTIVE, RentalStatus.COMPLETED)) {
                throw RentalUnavailableException("Rejected, active or completed rentals cannot be cancelled")
            }

            if (rental.status != RentalStatus.CANCELLED) {
                rental.status = RentalStatus.CANCELLED
                rental.cancelledAt = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Amsterdam"))
            }
            return@withTransaction rental.toDto()
        }

        if (rental.car.owner.id.value != loggedInUserId) {
            throw RentalAccessDeniedException("Only the car owner can approve or reject a reservation")
        }

        require(status == RentalStatus.CONFIRMED || status == RentalStatus.REJECTED) {
            "Status must be CONFIRMED, REJECTED or CANCELLED"
        }

        if (rental.status != RentalStatus.PENDING) {
            throw RentalUnavailableException("Only pending reservations can be approved or rejected")
        }

        rental.status = status
        rental.toDto()
    }
}
