package com.rentmycar.domain.car

class DuplicateLicensePlateException(cause: Throwable) : RuntimeException("Dit kenteken is al in gebruik", cause)
