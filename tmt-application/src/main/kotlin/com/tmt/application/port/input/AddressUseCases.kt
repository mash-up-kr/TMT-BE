package com.tmt.application.port.input

import com.tmt.application.port.output.address.AddressCandidate
import com.tmt.application.port.output.address.AddressCoordinateKey
import com.tmt.application.port.output.persistence.Wgs84Point

data class AddressSearchRequest(
    /** 원문. 정제는 서비스·어댑터가 한다 */
    val query: String?,
    /** 1부터 */
    val page: Int,
    val limit: Int,
)

data class AddressSearchResult(
    val items: List<AddressCandidate>,
    /** 정제된 검색어 — 커서의 conditionHash 재료다 (F §2-3) */
    val sanitizedQuery: String,
    val hasNext: Boolean,
    /** 페이지 상한에 걸려 더 내리지 않는다. 공급자에 결과가 더 있어도 hasNext는 false다 */
    val truncated: Boolean,
    val nextPage: Int?,
)

interface SearchAddressesUseCase {
    fun search(request: AddressSearchRequest): AddressSearchResult
}

/** 확정된 주소 1건의 좌표를 WGS84로 확보한다 (F §4-1 처리 순서 3번) */
interface ResolveAddressCoordinateUseCase {
    fun resolve(key: AddressCoordinateKey): Wgs84Point
}
