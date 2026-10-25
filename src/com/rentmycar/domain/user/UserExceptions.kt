package com.rentmycar.domain.user

class DuplicateEmailException(cause: Throwable) : RuntimeException("Email is already registered", cause)
