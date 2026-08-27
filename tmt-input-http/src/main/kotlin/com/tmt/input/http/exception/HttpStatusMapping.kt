package com.tmt.input.http.exception

import com.tmt.common.exception.ErrorType
import org.springframework.http.HttpStatus

internal fun ErrorType.toHttpStatus(): HttpStatus =
    when (this) {
        ErrorType.VALIDATION -> HttpStatus.BAD_REQUEST
        ErrorType.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
        ErrorType.FORBIDDEN -> HttpStatus.FORBIDDEN
        ErrorType.NOT_FOUND -> HttpStatus.NOT_FOUND
        ErrorType.CONFLICT -> HttpStatus.CONFLICT
        ErrorType.GONE -> HttpStatus.GONE
        ErrorType.UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY
        ErrorType.RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS
        ErrorType.EXTERNAL_UNAVAILABLE -> HttpStatus.BAD_GATEWAY
        ErrorType.SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE
        ErrorType.INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR
    }
