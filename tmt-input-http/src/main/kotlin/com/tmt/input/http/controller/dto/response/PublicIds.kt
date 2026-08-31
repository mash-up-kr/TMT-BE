package com.tmt.input.http.controller.dto.response

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException

/**
 * 외부 ID 표기 — mock·명세 예시와 같은 접두 형식(`rv_`·`place_`·`user_`·`sp_`)을
 * 실구현에서도 유지한다 (TMT-228 결정). FE가 들고 있는 표기가 실구현 전환에서
 * 흔들리지 않게 하기 위한 것으로, 계약 문서 "명세 반영이 필요한 것"에 기록돼 있다.
 */
object PublicIds {
    fun review(id: Long) = "rv_$id"

    fun place(id: Long) = "place_$id"

    fun user(id: Long) = "user_$id"

    fun save(id: Long) = "save_$id"

    fun savePhoto(id: Long) = "sp_$id"

    fun group(id: Long) = "group_$id"

    /** 접두·형식이 어긋나면 없는 자원과 같다 — 존재 여부를 새지 않게 NOT_FOUND 계열로 던진다. */
    fun parsePlaceId(publicId: String): Long =
        publicId.removePrefix("place_").toLongOrNull()
            ?: throw TmtException(ErrorCode.PLACE_NOT_FOUND)

    /** 표기(`user_7`)와 숫자(`7`) 둘 다 받는다 — FE가 카드의 `author.userId`를 그대로 경로에 넣는다. */
    fun parseUserId(publicId: String): Long =
        publicId.removePrefix("user_").toLongOrNull()
            ?: throw TmtException(ErrorCode.USER_NOT_FOUND)
}
