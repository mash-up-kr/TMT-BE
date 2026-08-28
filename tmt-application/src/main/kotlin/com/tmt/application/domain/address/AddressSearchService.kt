package com.tmt.application.domain.address

import com.tmt.application.port.input.AddressSearchRequest
import com.tmt.application.port.input.AddressSearchResult
import com.tmt.application.port.input.SearchAddressesUseCase
import com.tmt.application.port.output.address.AddressSearchPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 주소 검색 (F §2-2). 외부 호출이 페이지당 1회라 페이지를 무한히 넘기면 juso 호출도 무한히 는다 —
 * 페이지 상한에 닿으면 hasNext=false · truncated=true로 끊는다.
 */
@Service
class AddressSearchService(
    private val addressSearchPort: AddressSearchPort,
    @param:Value("\${tmt.address.max-page:5}") private val maxPage: Int,
) : SearchAddressesUseCase {
    override fun search(request: AddressSearchRequest): AddressSearchResult {
        val sanitized = AddressQuerySanitizer.sanitizeOrThrow(request.query)
        val page = request.page.coerceAtLeast(1)
        val result = addressSearchPort.search(sanitized, page, request.limit)

        val truncated = result.hasMore && page >= maxPage
        val hasNext = result.hasMore && !truncated
        return AddressSearchResult(
            items = result.items,
            sanitizedQuery = sanitized,
            hasNext = hasNext,
            truncated = truncated,
            nextPage = if (hasNext) page + 1 else null,
        )
    }
}
