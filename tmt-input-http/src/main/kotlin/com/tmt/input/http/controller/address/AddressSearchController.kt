package com.tmt.input.http.controller.address

import com.tmt.application.domain.address.AddressQuerySanitizer
import com.tmt.application.port.input.AddressSearchRequest
import com.tmt.application.port.input.SearchAddressesUseCase
import com.tmt.common.exception.ErrorCode
import com.tmt.input.http.auth.UserId
import com.tmt.input.http.config.ApiErrorCodes
import com.tmt.input.http.controller.paging.CursorCodec
import com.tmt.input.http.controller.paging.CursorCondition
import com.tmt.input.http.controller.paging.CursorSpec
import com.tmt.input.http.controller.paging.PageLimit
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 주소 검색 실구현 (TMT-192) — 행안부 juso 프록시다. mock의 더미 생성기를 대체한다.
 *
 * 응답이 mock에서 바뀌었다 (Breaking): `latitude`·`longitude` 제거, `regionName`·`truncated` 추가,
 * `addressId`가 서명 토큰. 좌표를 빼는 이유는 SSOT가 서버에만 있어야 클라이언트가
 * 임의 좌표로 매장을 만들 수 없기 때문이다 (F §2-2).
 */
@Tag(name = "주소 검색 (mock)", description = "명세 v2 — F §2-2")
@RestController
@RequestMapping("/v1/addresses/search")
class AddressSearchController(
    private val searchAddressesUseCase: SearchAddressesUseCase,
    private val addressIdTokenCodec: AddressIdTokenCodec,
) {
    @Operation(
        summary = "주소 검색",
        description =
            "addressId는 서명된 불투명 토큰이다. 해석하지 말고 POST /v1/saves의 newPlace.addressId에 그대로 전달한다.\n\n" +
                "클라이언트는 400ms 디바운스하고, 2자 미만이면 호출하지 않으며, **502를 자동 재시도하지 않는다** (F §2-3).",
    )
    @ApiErrorCodes(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
    @GetMapping
    fun searchAddresses(
        @UserId userId: Long,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): AddressSearchResponse {
        // 커서 조건 해시는 **정제된** 검색어로 만든다 — 원문으로 만들면 정제 결과가 같은 두 검색어가
        // 다른 커서를 갖는다 (F §2-3)
        val sanitized = AddressQuerySanitizer.sanitizeOrThrow(query)
        val condition = CursorCondition.of(CURSOR_CONDITION_PREFIX, sanitized)
        val page = CursorCodec.decode(AddressPageCursorSpec, cursor, condition) ?: FIRST_PAGE

        val result =
            searchAddressesUseCase.search(
                AddressSearchRequest(query = sanitized, page = page, limit = PageLimit.of(limit)),
            )
        val nextCursor = result.nextPage?.let { CursorCodec.encode(AddressPageCursorSpec, it, condition) }
        return AddressSearchResponse(
            items =
                result.items.map {
                    AddressSearchResponse.AddressItem(
                        addressId = addressIdTokenCodec.encode(it),
                        roadAddress = it.roadAddress,
                        jibunAddress = it.jibunAddress,
                        regionName = it.regionName,
                    )
                },
            nextCursor = nextCursor,
            hasNext = result.hasNext,
            truncated = result.truncated,
        )
    }

    @Schema(description = "주소 검색 결과. truncated 때문에 공용 CursorPage가 아닌 전용 타입이다")
    data class AddressSearchResponse(
        val items: List<AddressItem>,
        val nextCursor: String?,
        val hasNext: Boolean,
        @field:Schema(description = "페이지 상한에 걸려 더 내리지 않는다는 뜻. 화면은 검색어를 더 구체적으로 안내한다")
        val truncated: Boolean,
    ) {
        data class AddressItem(
            @field:Schema(description = "불투명 토큰. 해석하지 말고 그대로 전달한다")
            val addressId: String,
            val roadAddress: String,
            @field:Schema(description = "juso가 주지 않는 경우가 있다", nullable = true)
            val jibunAddress: String?,
            @field:Schema(description = "시군구명 + 읍면동명", example = "양천구 신정동")
            val regionName: String,
        )
    }

    /** 커서에는 juso 페이지 번호만 담는다 — 정렬 키가 아니라 공급자 페이지에 위임한다 (F §2-2) */
    internal object AddressPageCursorSpec : CursorSpec<Int> {
        override fun toKeys(key: Int) = listOf(key.toString())

        override fun fromKeys(keys: List<String>): Int {
            require(keys.size == 1) { "페이지 번호 1개가 필요하다" }
            val page = keys[0].toInt()
            require(page >= FIRST_PAGE) { "페이지는 1부터다" }
            return page
        }
    }

    companion object {
        private const val CURSOR_CONDITION_PREFIX = "ADDRESS_SEARCH"
        private const val FIRST_PAGE = 1
    }
}
