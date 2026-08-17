package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.controller.dto.response.CursorPage
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 매장 직접 입력에서 주소를 고르기 위한 검색 (F §2-2). mock은 검색어 기반 가짜 주소를 만들어 내린다. */
@Tag(name = "주소 검색 (mock)", description = "명세 v2 — F §2-2")
@RestController
@RequestMapping("/v1/addresses/search")
class AddressMockController(
    private val mockAddressStore: InMemoryStore<MockAddress>,
) {
    @Operation(summary = "주소 검색", description = "addressId는 이 검색 결과 안에서만 유효한 임시 식별자다. POST /v1/places에 그대로 전달한다.")
    @GetMapping
    fun searchAddresses(
        @UserId userId: Long,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): CursorPage<MockAddress> {
        if (query.isNullOrBlank()) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "query는 필수입니다.")
        }

        // 같은 검색어는 같은 결과를 돌려준다 — FE가 재검색해도 addressId가 흔들리지 않게
        val existing = mockAddressStore.findAll().filter { it.roadAddress.contains(query) }
        val results = existing.ifEmpty { generate(query) }
        return MockCursor.paginate(results, cursor, limit) { it }
    }

    private fun generate(query: String): List<MockAddress> =
        SAMPLES.mapIndexed { index, sample ->
            mockAddressStore.create { id ->
                MockAddress(
                    addressId = id,
                    roadAddress = "서울 ${sample.first} $query ${index + 1}길 ${index + 10}",
                    jibunAddress = "서울 ${sample.first} ${sample.second} ${900 + index}-${index + 1}",
                    latitude = 37.52 + index * 0.003,
                    longitude = 126.85 + index * 0.003,
                )
            }
        }

    companion object {
        private val SAMPLES = listOf("양천구" to "신정동", "마포구" to "도화동", "중구" to "을지로3가")
    }
}
