package com.rentmycar.domain.car

class DuplicateLicensePlateException(cause: Throwable) : RuntimeException("This license plate is already in use", cause)

class CarHasRentalsException(cause: Throwable) : RuntimeException("Car has associated rentals and cannot be deleted", cause)
