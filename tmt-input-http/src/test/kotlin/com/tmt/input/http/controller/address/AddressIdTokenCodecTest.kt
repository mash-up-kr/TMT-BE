package com.tmt.input.http.controller.address

import com.tmt.application.port.output.address.AddressCandidate
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AddressIdTokenCodecTest {
    private val codec = AddressIdTokenCodec("test-secret")

    private val candidate =
        AddressCandidate(
            admCd = "1147010100",
            rnMgtSn = "114704166011",
            udrtYn = "0",
            buldMnnm = "1",
            buldSlno = "0",
            roadAddress = "서울특별시 양천구 오목로32길 1",
            jibunAddress = "서울특별시 양천구 신정동 948-1",
            regionName = "양천구 신정동",
        )

    @Test
    fun `같은 주소는 항상 같은 토큰이다`() {
        assertEquals(codec.encode(candidate), codec.encode(candidate))
    }

    @Test
    fun `토큰을 디코딩하면 좌표 조회에 필요한 값이 그대로 복원된다`() {
        assertEquals(candidate, codec.decode(codec.encode(candidate)))
    }

    @Test
    fun `payload를 조작한 토큰은 거부된다`() {
        val token = codec.encode(candidate)
        val forged = codec.encode(candidate.copy(roadAddress = "남의 주소"))
        val tampered = forged.substringBeforeLast('.') + "." + token.substringAfterLast('.')

        val e = assertFailsWith<TmtException> { codec.decode(tampered) }
        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
    }

    @Test
    fun `서명을 바꾼 토큰은 거부된다`() {
        val token = codec.encode(candidate)

        assertFailsWith<TmtException> { codec.decode(token.substringBeforeLast('.') + ".bogus") }
        assertFailsWith<TmtException> { codec.decode(token.substringBeforeLast('.')) }
        assertFailsWith<TmtException> { codec.decode("") }
    }

    @Test
    fun `다른 키로 서명한 토큰은 거부된다`() {
        val other = AddressIdTokenCodec("another-secret").encode(candidate)

        assertFailsWith<TmtException> { codec.decode(other) }
    }

    @Test
    fun `지번이 없는 주소도 왕복된다`() {
        val noJibun = candidate.copy(jibunAddress = null)

        assertEquals(noJibun, codec.decode(codec.encode(noJibun)))
    }
}
