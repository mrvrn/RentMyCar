package com.rentmycar.domain.rental.dto

import com.rentmycar.domain.rental.RentalStatus
import kotlinx.serialization.Serializable

@Serializable
data class ChangeRentalStatusDto(val status: RentalStatus)
