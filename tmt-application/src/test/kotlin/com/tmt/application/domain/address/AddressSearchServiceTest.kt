package com.tmt.application.domain.address

import com.tmt.application.port.input.AddressSearchRequest
import com.tmt.application.port.output.address.AddressCandidate
import com.tmt.application.port.output.address.AddressPage
import com.tmt.application.port.output.address.AddressSearchPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddressSearchServiceTest {
    private val port = RecordingSearchPort()
    private val service = AddressSearchService(port, maxPage = 3)

    @Test
    fun `정제된 검색어로 공급자를 부른다`() {
        service.search(AddressSearchRequest(query = "양천구 OR 오목로32길'", page = 1, limit = 20))

        assertEquals("양천구 오목로32길", port.lastQuery)
    }

    @Test
    fun `정제 후 2자 미만이면 공급자를 부르지 않는다`() {
        val e = assertFailsWith<TmtException> { service.search(AddressSearchRequest("OR", 1, 20)) }

        assertEquals(ErrorCode.VALIDATION_FAILED, e.errorCode)
        assertNull(port.lastQuery)
    }

    @Test
    fun `공급자에 다음 페이지가 있으면 hasNext와 nextPage가 있다`() {
        port.hasMore = true

        val result = service.search(AddressSearchRequest("오목로", 1, 20))

        assertTrue(result.hasNext)
        assertEquals(2, result.nextPage)
        assertFalse(result.truncated)
    }

    @Test
    fun `페이지 상한에 닿으면 hasNext가 false이고 truncated가 true다`() {
        port.hasMore = true

        val result = service.search(AddressSearchRequest("오목로", 3, 20))

        assertFalse(result.hasNext)
        assertTrue(result.truncated)
        assertNull(result.nextPage)
    }

    @Test
    fun `마지막 페이지면 truncated가 아니다`() {
        port.hasMore = false

        val result = service.search(AddressSearchRequest("오목로", 3, 20))

        assertFalse(result.hasNext)
        assertFalse(result.truncated)
    }

    private class RecordingSearchPort : AddressSearchPort {
        var lastQuery: String? = null
        var hasMore = false

        override fun search(
            query: String,
            page: Int,
            size: Int,
        ): AddressPage {
            lastQuery = query
            return AddressPage(items = listOf(candidate()), hasMore = hasMore)
        }

        private fun candidate() =
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
    }
}
