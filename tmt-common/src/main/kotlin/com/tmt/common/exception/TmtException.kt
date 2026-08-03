package com.tmt.common.exception

class TmtException : RuntimeException {
    val exceptionCode: ExceptionCode
    val statusCode: Int
    val defaultMessage: String
    val detailMessage: String?

    constructor(
        exceptionCode: ExceptionCode,
        detailMessage: String,
    ) : super("[$exceptionCode] $detailMessage") {
        this.exceptionCode = exceptionCode
        this.statusCode = exceptionCode.statusCode
        this.defaultMessage = exceptionCode.defaultMessage
        this.detailMessage = detailMessage
    }

    constructor(exceptionCode: ExceptionCode) :
        super("[$exceptionCode] ${exceptionCode.defaultMessage}") {
        this.exceptionCode = exceptionCode
        this.statusCode = exceptionCode.statusCode
        this.defaultMessage = exceptionCode.defaultMessage
        this.detailMessage = null
    }
}
