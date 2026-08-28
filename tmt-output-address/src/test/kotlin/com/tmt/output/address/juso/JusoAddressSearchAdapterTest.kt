package com.tmt.output.address.juso

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JusoAddressSearchAdapterTest {
    private val http = FakeJusoHttpClient()
    private val breaker = JusoCircuitBreaker(failureThreshold = 2, openSeconds = 60)
    private val adapter = JusoAddressSearchAdapter(http, breaker, confmKey = "TEST-KEY")

    @Test
    fun `특수문자와 예약어는 juso에 도달하지 않는다`() {
        adapter.search("오목로32길' OR 1=1; --", page = 1, size = 20)

        val keyword =
            http.calls
                .single()
                .second
                .getValue("keyword")
        assertTrue(keyword.none { it in "'=;<>%\"()" }, "특수문자가 나갔다: $keyword")
        assertTrue(keyword.split(" ").none { it.equals("OR", ignoreCase = true) }, "예약어가 나갔다: $keyword")
    }

    @Test
    fun `예약어를 부분 문자열로 포함한 검색어는 그대로 나간다`() {
        adapter.search("UNIONMALL", page = 1, size = 20)

        assertEquals(
            "UNIONMALL",
            http.calls
                .single()
                .second
                .getValue("keyword"),
        )
    }

    @Test
    fun `정제 후 2자 미만이면 juso를 부르지 않고 VALIDATION_FAILED다`() {
        val e = assertFailsWith<TmtException> { adapter.search("OR ';'", page = 1, size = 20) }

        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
        assertTrue(http.calls.isEmpty())
    }

    @Test
    fun `juso 응답을 우리 형태로 정규화한다`() {
        http.respondWith(SEARCH_RESULT)

        val page = adapter.search("오목로32길", page = 1, size = 20)

        val item = page.items.single()
        assertEquals("서울특별시 양천구 오목로32길 1", item.roadAddress)
        assertEquals("서울특별시 양천구 신정동 948-1", item.jibunAddress)
        assertEquals("양천구 신정동", item.regionName)
        assertEquals("1147010100", item.admCd)
        assertTrue(page.hasMore)
    }

    @Test
    fun `지번이 비면 null이다`() {
        http.respondWith(SEARCH_RESULT.replace("서울특별시 양천구 신정동 948-1", ""))

        assertNull(
            adapter
                .search("오목로32길", 1, 20)
                .items
                .single()
                .jibunAddress,
        )
    }

    @Test
    fun `sggNm이 비면 siNm으로 regionName을 만든다`() {
        http.respondWith(SEARCH_RESULT.replace("\"sggNm\":\"양천구\"", "\"sggNm\":\"\""))

        assertEquals(
            "세종특별자치시 신정동",
            adapter
                .search("오목로32길", 1, 20)
                .items
                .single()
                .regionName,
        )
    }

    @Test
    fun `regionName은 50자를 넘지 않는다`() {
        http.respondWith(SEARCH_RESULT.replace("\"emdNm\":\"신정동\"", "\"emdNm\":\"${"동".repeat(80)}\""))

        assertTrue(
            adapter
                .search("오목로32길", 1, 20)
                .items
                .single()
                .regionName.length <= 50,
        )
    }

    @Test
    fun `연속 실패가 쌓이면 차단기가 열려 호출이 멈춘다`() {
        http.throwOnCall = true
        repeat(2) {
            assertFailsWith<TmtException> { adapter.search("오목로32길", 1, 20) }
        }
        val callsBeforeOpen = http.calls.size

        val e = assertFailsWith<TmtException> { adapter.search("오목로32길", 1, 20) }

        assertEquals(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE, e.errorCode)
        assertEquals(callsBeforeOpen, http.calls.size, "차단기가 열렸는데 호출이 나갔다")
    }

    @Test
    fun `juso errorCode가 정상이 아니면 502다`() {
        http.respondWith("""{"results":{"common":{"errorCode":"E0005","errorMessage":"검색 실패"},"juso":[]}}""")

        val e = assertFailsWith<TmtException> { adapter.search("오목로32길", 1, 20) }
        assertEquals(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE, e.errorCode)
    }

    @Test
    fun `승인키가 없으면 호출하지 않고 502다`() {
        val keyless = JusoAddressSearchAdapter(http, breaker, confmKey = "")

        val e = assertFailsWith<TmtException> { keyless.search("오목로32길", 1, 20) }

        assertEquals(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE, e.errorCode)
        assertTrue(http.calls.isEmpty())
    }

    companion object {
        private val SEARCH_RESULT =
            """
            {"results":{
              "common":{"errorCode":"0","errorMessage":"정상","totalCount":"25"},
              "juso":[{
                "roadAddr":"서울특별시 양천구 오목로32길 1",
                "jibunAddr":"서울특별시 양천구 신정동 948-1",
                "siNm":"세종특별자치시","sggNm":"양천구","emdNm":"신정동",
                "admCd":"1147010100","rnMgtSn":"114704166011","udrtYn":"0",
                "buldMnnm":"1","buldSlno":"0"
              }]
            }}
            """.trimIndent()
    }
}
