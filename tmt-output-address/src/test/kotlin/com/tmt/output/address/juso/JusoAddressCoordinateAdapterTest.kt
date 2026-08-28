package com.tmt.output.address.juso

import com.tmt.application.port.output.address.AddressCoordinateKey
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JusoAddressCoordinateAdapterTest {
    private val http = FakeJusoHttpClient()
    private val breaker = JusoCircuitBreaker(failureThreshold = 2, openSeconds = 60)
    private val adapter = JusoAddressCoordinateAdapter(http, breaker, confmKey = "COORD-KEY")

    private val key = AddressCoordinateKey("1147010100", "114704166011", "0", "1", "0")

    @Test
    fun `entX entY를 평면좌표로 읽는다`() {
        http.respondWith(COORD_RESULT)

        val point = adapter.findCoordinate(key)!!

        assertEquals(946565.0, point.x)
        assertEquals(1946240.0, point.y)
    }

    @Test
    fun `좌표용 승인키는 검색용과 다른 설정에서 온다`() {
        http.respondWith(COORD_RESULT)

        adapter.findCoordinate(key)

        assertEquals(
            "COORD-KEY",
            http.calls
                .single()
                .second
                .getValue("confmKey"),
        )
        assertEquals(JusoAddressCoordinateAdapter.COORD_PATH, http.calls.single().first)
    }

    @Test
    fun `좌표가 없으면 null이다 - 장애가 아니라 ADDRESS_NOT_FOUND로 갈린다`() {
        http.respondWith("""{"results":{"common":{"errorCode":"0","errorMessage":"정상"},"juso":[]}}""")

        assertNull(adapter.findCoordinate(key))
    }

    @Test
    fun `장애는 502다`() {
        http.throwOnCall = true

        val e = assertFailsWith<TmtException> { adapter.findCoordinate(key) }
        assertEquals(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `좌표 승인키가 없으면 호출하지 않고 502다`() {
        val keyless = JusoAddressCoordinateAdapter(http, breaker, confmKey = "")

        val e = assertFailsWith<TmtException> { keyless.findCoordinate(key) }

        assertEquals(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE, e.errorCode)
        assertTrue(http.calls.isEmpty())
    }

    companion object {
        private val COORD_RESULT =
            """
            {"results":{
              "common":{"errorCode":"0","errorMessage":"정상","totalCount":"1"},
              "juso":[{"entX":"946565.0","entY":"1946240.0","admCd":"1147010100"}]
            }}
            """.trimIndent()
    }
}
