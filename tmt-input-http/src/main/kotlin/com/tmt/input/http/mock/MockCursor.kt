package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.controller.dto.response.CursorPage
import java.util.Base64

/**
 * mock 전용 오프셋 커서 — 공통 규약 §5의 형태(불투명 문자열, nextCursor 그대로 전달)만 지킨다.
 * 실구현의 키셋 커서와 인코딩이 달라도 클라이언트 계약(불투명성)은 같다.
 */
object MockCursor {
    private const val PREFIX = "offset:"
    const val DEFAULT_LIMIT = 20

    fun <T, R> paginate(
        source: List<T>,
        cursor: String?,
        limit: Int?,
        transform: (T) -> R,
    ): CursorPage<R> {
        val offset = decode(cursor)
        val pageSize = limit ?: DEFAULT_LIMIT
        val page = source.drop(offset).take(pageSize)
        val nextOffset = offset + page.size
        val nextCursor = if (nextOffset < source.size) encode(nextOffset) else null
        return CursorPage.of(page.map(transform), nextCursor)
    }

    private fun encode(offset: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("$PREFIX$offset".toByteArray())

    private fun decode(cursor: String?): Int {
        if (cursor == null) return 0
        return runCatching {
            String(Base64.getUrlDecoder().decode(cursor))
                .removePrefix(PREFIX)
                .toInt()
                .also { require(it >= 0) }
        }.getOrElse { throw TmtException(ErrorCode.INVALID_CURSOR) }
    }
}
