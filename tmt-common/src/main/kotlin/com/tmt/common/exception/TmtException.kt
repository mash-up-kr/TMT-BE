package com.tmt.common.exception

/**
 * @param detailMessage 응답의 `detail`로 나간다.
 */
class TmtException(
    val errorCode: ErrorCode,
    val detailMessage: String? = null,
) : RuntimeException("[${errorCode.name}] ${detailMessage ?: errorCode.defaultMessage}")
