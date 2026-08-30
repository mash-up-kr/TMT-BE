package com.tmt.input.http.controller.address

import com.tmt.application.port.output.address.AddressCandidate
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * addressId — 주소를 통째로 담는 **서명된 불투명 토큰** (F §2-2, TMT-191).
 *
 * ```
 * addressId = base64url(payload) + "." + base64url(HMAC-SHA256(payload, 서버키))
 * ```
 *
 * 저장소를 쓰지 않는다 — Redis도 세션도 없다. `POST /v1/saves`가 서명을 검증하고 payload를 그대로 꺼내 쓴다.
 * **HMAC이 없으면 클라이언트가 임의의 주소·좌표 파라미터로 Place를 등록할 수 있다.** 위조 방지가 목적이다.
 *
 * 만료를 두지 않는다 — 담는 건 juso에서 누구나 조회 가능한 공개 주소 정보이고, 비울 저장소도 없다.
 */
@Component
class AddressIdTokenCodec(
    @param:Value("\${tmt.address.token.secret}") secret: String,
) {
    // 키가 비면 기동을 막는다 — 코드에 박힌 기본값으로 서명하면 위조 방지가 무의미해진다
    private val key: SecretKeySpec =
        SecretKeySpec(
            secret.ifBlank { throw IllegalStateException("tmt.address.token.secret이 비어 있다") }.toByteArray(),
            HMAC_ALGORITHM,
        )

    private val mapper: JsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()

    /** 같은 주소는 항상 같은 토큰이 된다 — 재검색해도 addressId가 흔들리지 않는다 */
    fun encode(candidate: AddressCandidate): String {
        val payload = BASE64.encodeToString(mapper.writeValueAsBytes(candidate.toPayload()))
        return "$payload.${sign(payload)}"
    }

    fun decode(token: String): AddressCandidate {
        val payload = token.substringBeforeLast('.', "")
        val signature = token.substringAfterLast('.', "")
        // 서명 비교는 상수 시간으로 — 바이트 단위 조기 반환이 위조 힌트가 된다
        if (payload.isEmpty() || !MessageDigest.isEqual(signature.toByteArray(), sign(payload).toByteArray())) {
            throw invalid()
        }
        return runCatching { mapper.readValue<Payload>(Base64.getUrlDecoder().decode(payload)).toCandidate() }
            .getOrElse { throw invalid() }
    }

    private fun sign(payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply { init(key) }
        return BASE64.encodeToString(mac.doFinal(payload.toByteArray()))
    }

    private fun invalid() = TmtException(ErrorCode.VALIDATION_FAILED, "addressId가 올바르지 않습니다.")

    private fun AddressCandidate.toPayload() =
        Payload(
            admCd = admCd,
            rnMgtSn = rnMgtSn,
            udrtYn = udrtYn,
            buldMnnm = buldMnnm,
            buldSlno = buldSlno,
            roadAddr = roadAddress,
            jibunAddr = jibunAddress,
            regionName = regionName,
        )

    /** 필드 순서가 토큰 문자열을 정한다 — 순서를 바꾸면 이미 발급된 토큰과 값이 달라진다 */
    private data class Payload(
        val admCd: String,
        val rnMgtSn: String,
        val udrtYn: String,
        val buldMnnm: String,
        val buldSlno: String,
        val roadAddr: String,
        val jibunAddr: String?,
        val regionName: String,
    ) {
        fun toCandidate() =
            AddressCandidate(
                admCd = admCd,
                rnMgtSn = rnMgtSn,
                udrtYn = udrtYn,
                buldMnnm = buldMnnm,
                buldSlno = buldSlno,
                roadAddress = roadAddr,
                jibunAddress = jibunAddr,
                regionName = regionName,
            )
    }

    companion object {
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private val BASE64: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
