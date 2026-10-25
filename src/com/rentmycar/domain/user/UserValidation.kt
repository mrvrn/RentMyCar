package com.rentmycar.domain.user

import com.rentmycar.domain.user.dto.CreateUserDto

object UserValidation {
    fun normalizeAndValidate(input: CreateUserDto): CreateUserDto {
        val user = input.copy(name = input.name.trim(), email = input.email.trim(),
            phone = input.phone.trim(), address = input.address.trim())
        val fields = listOf(user.name, user.email, user.phone, user.address)
        require(fields.none { it.isBlank() }) { "Name, email, phone and address are required" }
        require(fields.all { it.length <= 100 }) { "Name, email, phone and address must be at most 100 characters" }
        require(fields.none { value -> value.any(Char::isISOControl) }) { "Contact fields must not contain control characters" }
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(user.email)) { "Invalid email address" }
        require(validPhone(user.phone)) { "Phone must contain 7 to 15 digits with optional leading + and formatting separators" }
        require(user.password.length >= 8 && user.password.isNotBlank()) { "Password must contain at least 8 characters" }
        require(user.password.toByteArray(Charsets.UTF_8).size <= 72) { "Password must be at most 72 UTF-8 bytes" }
        return user
    }

    private fun validPhone(phone: String): Boolean {
        if (!Regex("^\\+?[0-9() .-]+$").matches(phone) || phone.count { it in '0'..'9' } !in 7..15) return false
        var openParenthesis = false
        for (character in phone) {
            when (character) {
                '(' -> { if (openParenthesis) return false; openParenthesis = true }
                ')' -> { if (!openParenthesis) return false; openParenthesis = false }
            }
        }
        return !openParenthesis
    }
}
