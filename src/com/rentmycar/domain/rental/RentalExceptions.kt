package com.rentmycar.domain.rental

class RentalAccessDeniedException(message: String) : RuntimeException(message)

class RentalNotFoundException(message: String) : RuntimeException(message)

class RentalUnavailableException(message: String) : RuntimeException(message)
