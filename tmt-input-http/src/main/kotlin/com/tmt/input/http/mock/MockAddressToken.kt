package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import java.util.Base64

/**
 * addressId — 주소를 통째로 담는 불투명 토큰 (F §2-2). 서버는 아무것도 저장하지 않는다.
 * 형태는 실구현과 같은 `base64url(payload).signature`이고, mock은 서명 자리에 비암호 체크섬을 쓴다.
 * 조작된 토큰은 VALIDATION_FAILED다.
 */
object MockAddressToken {
    // 주소 문자열에 들어갈 수 없는 제어문자를 구분자로 쓴다
    private const val FIELD_SEPARATOR = "\u001F"
    private const val FIELD_COUNT = 6

    fun encode(address: MockAddress): String {
        val raw =
            listOf(
                address.roadAddress,
                address.jibunAddress,
                address.regionName,
                address.latitude.toString(),
                address.longitude.toString(),
                address.hasCoordinate.toString(),
            ).joinToString(FIELD_SEPARATOR)
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        return "$payload.${sign(payload)}"
    }

    fun decode(token: String): MockAddress {
        val payload = token.substringBeforeLast('.', "")
        if (payload.isEmpty() || token.substringAfterLast('.', "") != sign(payload)) {
            throw invalid()
        }
        val fields =
            runCatching { String(Base64.getUrlDecoder().decode(payload)).split(FIELD_SEPARATOR) }
                .getOrElse { throw invalid() }
        if (fields.size != FIELD_COUNT) throw invalid()
        return runCatching {
            MockAddress(
                roadAddress = fields[0],
                jibunAddress = fields[1],
                regionName = fields[2],
                latitude = fields[3].toDouble(),
                longitude = fields[4].toDouble(),
                hasCoordinate = fields[5].toBooleanStrict(),
            )
        }.getOrElse { throw invalid() }
    }

    private fun sign(payload: String): String = Integer.toHexString(payload.hashCode())

    private fun invalid() = TmtException(ErrorCode.VALIDATION_FAILED, "addressId가 올바르지 않습니다.")
}
