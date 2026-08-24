package com.tmt.input.http.mock

import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 매장 직접 입력에서 주소를 고르기 위한 검색 (F §2-2).
 * 실구현은 행안부 juso API 프록시이고, mock은 검색어로 가짜 주소를 만들어 같은 형태로 내린다.
 */
@Tag(name = "주소 검색 (mock)", description = "명세 v2 — F §2-2")
@RestController
@RequestMapping("/v1/addresses/search")
class AddressMockController {
    @Operation(
        summary = "주소 검색",
        description =
            "addressId는 불투명 토큰이다. 해석하지 말고 POST /v1/saves의 newPlace.addressId에 그대로 전달한다.\n\n" +
                "mock 재현용 검색어 — `장애`: 502 ADDRESS_PROVIDER_UNAVAILABLE, " +
                "`좌표없음`: 저장 시 404 ADDRESS_NOT_FOUND, `많음`: truncated=true",
    )
    @ApiErrorCodes(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
    @GetMapping
    fun searchAddresses(
        @UserId userId: Long,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): AddressSearchResponse {
        val sanitized = sanitize(query.orEmpty())
        if (sanitized.length < QUERY_MIN_LENGTH) {
            throw TmtException(ErrorCode.VALIDATION_FAILED, "query는 정제 후 ${QUERY_MIN_LENGTH}자 이상이어야 합니다.")
        }
        if (sanitized.contains(PROVIDER_DOWN_QUERY)) {
            throw TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
        }

        val generated = generate(sanitized)
        val page = MockCursor.paginate(generated.take(MAX_RESULTS), cursor, limit) { toItem(it) }
        return AddressSearchResponse(
            items = page.items,
            nextCursor = page.nextCursor,
            hasNext = page.hasNext,
            truncated = generated.size > MAX_RESULTS,
        )
    }

    private fun toItem(address: MockAddress) =
        AddressSearchResponse.AddressItem(
            addressId = MockAddressToken.encode(address),
            roadAddress = address.roadAddress,
            jibunAddress = address.jibunAddress,
            regionName = address.regionName,
        )

    /** 같은 검색어는 같은 결과를 준다 — 재검색해도 addressId가 흔들리지 않는다. */
    private fun generate(query: String): List<MockAddress> {
        val count = if (query.contains(MANY_RESULTS_QUERY)) MAX_RESULTS + 5 else DEFAULT_COUNT
        val hasCoordinate = !query.contains(NO_COORDINATE_QUERY)
        return (1..count).map { n ->
            val (gu, dong) = SAMPLES[(n - 1) % SAMPLES.size]
            MockAddress(
                roadAddress = "서울특별시 $gu $query${n}길 $n",
                jibunAddress = "서울특별시 $gu $dong ${900 + n}-$n",
                regionName = "$gu $dong",
                latitude = 37.52 + n * 0.001,
                longitude = 126.85 + n * 0.001,
                hasCoordinate = hasCoordinate,
            )
        }
    }

    data class AddressSearchResponse(
        val items: List<AddressItem>,
        val nextCursor: String?,
        val hasNext: Boolean,
        val truncated: Boolean,
    ) {
        data class AddressItem(
            val addressId: String,
            val roadAddress: String,
            val jibunAddress: String,
            val regionName: String,
        )
    }

    companion object {
        const val QUERY_MIN_LENGTH = 2

        /** 페이지 상한. 실구현 값은 juso 호출 한도를 확인한 뒤 정한다 (F §8 juso 2번) */
        private const val MAX_RESULTS = 20
        private const val DEFAULT_COUNT = 8

        private const val PROVIDER_DOWN_QUERY = "장애"
        private const val NO_COORDINATE_QUERY = "좌표없음"
        private const val MANY_RESULTS_QUERY = "많음"

        private val SAMPLES = listOf("양천구" to "신정동", "마포구" to "도화동", "중구" to "을지로3가")

        private val DISALLOWED = Regex("[^가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9\\s-]")
        private val WHITESPACE = Regex("\\s+")

        /**
         * juso는 특수문자·SQL 예약어가 섞인 검색어를 SQL Injection으로 보고 호출한 IP를 차단한다.
         * 차단은 재시도로 풀리지 않으므로 외부에 나가기 전에 두 단계로 거른다 (F §2-3).
         * 예약어는 토큰 단위로 맞춰야 한다 — 부분 문자열로 하면 ORIGIN·UNIONMALL이 깨진다.
         */
        fun sanitize(raw: String): String =
            raw
                .replace(DISALLOWED, "")
                .split(WHITESPACE)
                .filter { it.isNotBlank() && it.uppercase() !in SQL_KEYWORDS }
                .joinToString(" ")

        private val SQL_KEYWORDS =
            setOf(
                "OR",
                "AND",
                "NOT",
                "UNION",
                "SELECT",
                "INSERT",
                "UPDATE",
                "DELETE",
                "DROP",
                "ALTER",
                "CREATE",
                "FROM",
                "WHERE",
                "JOIN",
                "EXEC",
                "DECLARE",
            )
    }
}
