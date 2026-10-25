package com.rentmycar.domain.car

import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import java.math.BigDecimal
import java.net.URI
import java.util.Locale

/** Shared by HTTP handlers and repository callers, including scripts and future services. */
object CarValidation {
    const val MAX_TEXT_LENGTH = 255
    const val MAX_PICTURES = 20
    const val MAX_PICTURE_URL_LENGTH = 2048

    fun normalizeAndValidate(input: CreateCarDto): CreateCarDto = input.copy(
        brand = text(input.brand, "Brand"),
        type = text(input.type, "Type"),
        licensePlate = plate(input.licensePlate),
        pictures = pictures(input.pictures),
    ).also {
        hourlyRate(it.pricePerHour)
        require(it.pickupId > 0 && it.dropOffId > 0) { "Location IDs must be positive" }
    }

    fun normalizeAndValidate(input: UpdateCarDto): UpdateCarDto = input.copy(
        brand = input.brand?.let { text(it, "Brand") },
        type = input.type?.let { text(it, "Type") },
        licensePlate = input.licensePlate?.let(::plate),
        pictures = pictures(input.pictures),
    ).also {
        it.pricePerHour?.let(::hourlyRate)
        require((it.pickupId == null || it.pickupId > 0) && (it.dropOffId == null || it.dropOffId > 0)) {
            "Location IDs must be positive"
        }
    }

    private fun text(value: String, label: String): String = value.trim().also {
        require(it.isNotBlank()) { "$label must not be blank" }
        require(it.length <= MAX_TEXT_LENGTH) { "$label must be at most $MAX_TEXT_LENGTH characters" }
        require(it.none(Char::isISOControl)) { "$label must not contain control characters" }
    }

    private fun plate(value: String): String = text(value.trim().uppercase(Locale.ROOT), "License plate").also {
        // Country-neutral syntax: letters/numbers separated by single spaces or hyphens.
        require(Regex("^[\\p{L}\\p{N}]+(?:[ -][\\p{L}\\p{N}]+)*$").matches(it)) {
            "License plate must contain letters or numbers separated by spaces or hyphens"
        }
    }

    private fun hourlyRate(price: BigDecimal) {
        require(price.signum() > 0) { "Hourly rate must be greater than zero" }
        require(price <= "99999999.99".toBigDecimal()) { "Hourly rate exceeds the supported maximum" }
        require(price.stripTrailingZeros().scale() <= 2) { "Hourly rate must have at most two decimal places" }
    }

    private fun pictures(values: List<String>?): List<String>? {
        if (values == null) return null
        require(values.size <= MAX_PICTURES) { "At most $MAX_PICTURES pictures are allowed" }
        return values.map { value ->
            val url = value.trim()
            require(url.length in 1..MAX_PICTURE_URL_LENGTH) { "Picture URLs must contain 1 to $MAX_PICTURE_URL_LENGTH characters" }
            val uri = runCatching { URI(url) }.getOrNull()
            require(uri != null && uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                !uri.host.isNullOrBlank() && uri.rawUserInfo == null && (uri.port == -1 || uri.port in 1..65535)) {
                "Pictures must be absolute HTTP or HTTPS URLs without credentials"
            }
            url
        }
    }
}
