package com.rentmycar.domain.car

class DuplicateLicensePlateException(cause: Throwable) : RuntimeException("Dit kenteken is al in gebruik", cause)

class CarHasRentalsException(cause: Throwable) : RuntimeException("Car has associated rentals and cannot be deleted", cause)
