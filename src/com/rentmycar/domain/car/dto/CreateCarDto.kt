package com.rentmycar.domain.car.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal

object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())

    override fun deserialize(decoder: Decoder): BigDecimal = decoder.decodeString().toBigDecimal()
}

@Serializable
data class CreateCarDto(
    val ownerId: Long,
    val brand: String,
    val type: String,
    val licensePlate: String,
    @Serializable(with = BigDecimalSerializer::class) val pricePerHour: BigDecimal,
    val pickupId: Long,
    val dropOffId: Long,
    val pictures: List<String>? = null,
    val isAvailable: Boolean = false
)
