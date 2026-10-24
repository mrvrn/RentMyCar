package com.rentmycar.domain.car

import java.util.Locale

fun normalizeLicensePlate(licensePlate: String): String =
    licensePlate.trim().uppercase(Locale.ROOT)
