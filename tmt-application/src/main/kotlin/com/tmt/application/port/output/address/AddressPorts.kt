package com.tmt.application.port.output.address

/**
 * 주소 공급자(행안부 juso) 경계. 애플리케이션은 juso의 파라미터·응답 형태를 모른다 (F §2-2).
 */
data class AddressCandidate(
    /** 좌표제공 API 입력 — 토큰에 실려 저장 시점에 다시 쓰인다 */
    val admCd: String,
    val rnMgtSn: String,
    val udrtYn: String,
    val buldMnnm: String,
    val buldSlno: String,
    val roadAddress: String,
    /** juso가 주지 않는 경우가 있다 */
    val jibunAddress: String?,
    /** 시군구명 + " " + 읍면동명. place.region_name 이 VARCHAR(50) */
    val regionName: String,
)

data class AddressPage(
    val items: List<AddressCandidate>,
    /** 공급자에 다음 페이지가 더 있는지 */
    val hasMore: Boolean,
)

/** 좌표제공 API 입력 키 — 전부 건물 식별자다 (F §4-1) */
data class AddressCoordinateKey(
    val admCd: String,
    val rnMgtSn: String,
    val udrtYn: String,
    val buldMnnm: String,
    val buldSlno: String,
)

/** 좌표제공 API가 주는 평면좌표. juso는 EPSG:5179(GRS80 UTM-K)로만 준다 */
data class ProjectedPoint(
    val x: Double,
    val y: Double,
) {
    companion object {
        const val JUSO_SRID = 5179
    }
}

interface AddressSearchPort {
    /**
     * 검색어는 어댑터 진입점에서 무조건 정제된다 (F §2-3) — 호출자가 정제를 건너뛸 수 없다.
     * `page`는 1부터.
     */
    fun search(
        query: String,
        page: Int,
        size: Int,
    ): AddressPage
}

interface AddressCoordinatePort {
    /** 좌표를 못 찾으면 null — 호출자가 ADDRESS_NOT_FOUND로 바꾼다 */
    fun findCoordinate(key: AddressCoordinateKey): ProjectedPoint?
}
