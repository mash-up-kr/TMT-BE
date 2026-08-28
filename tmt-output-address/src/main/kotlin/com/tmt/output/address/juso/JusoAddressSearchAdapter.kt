package com.tmt.output.address.juso

import com.tmt.application.domain.address.AddressQuerySanitizer
import com.tmt.application.port.output.address.AddressCandidate
import com.tmt.application.port.output.address.AddressPage
import com.tmt.application.port.output.address.AddressSearchPort
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode

private val logger = KotlinLogging.logger {}

/**
 * 도로명주소 검색 API 프록시 (F §2-2). 검색어 정제는 **이 진입점에서 무조건** 적용한다 —
 * 다른 호출 경로가 생겨도 정제되지 않은 문자열이 juso에 도달할 수 없어야 한다 (F §2-3).
 */
@Component
class JusoAddressSearchAdapter(
    private val httpClient: JusoHttpClient,
    private val circuitBreaker: JusoCircuitBreaker,
    @param:Value("\${tmt.address.juso.search-key:}") private val confmKey: String,
) : AddressSearchPort {
    override fun search(
        query: String,
        page: Int,
        size: Int,
    ): AddressPage {
        val sanitized = AddressQuerySanitizer.sanitizeOrThrow(query)
        if (confmKey.isBlank()) {
            logger.error { "juso 검색 승인키가 없다 - tmt.address.juso.search-key 설정 확인" }
            throw TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
        }
        if (circuitBreaker.isOpen) {
            logger.warn { "juso 차단기가 열려 있어 호출을 건너뛴다" }
            throw TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
        }

        val response =
            runCatching {
                httpClient.getJson(
                    SEARCH_PATH,
                    mapOf(
                        "confmKey" to confmKey,
                        "currentPage" to page.toString(),
                        "countPerPage" to size.toString(),
                        "keyword" to sanitized,
                        "resultType" to "json",
                    ),
                )
            }.getOrElse {
                circuitBreaker.recordFailure()
                logger.warn(it) { "juso 검색 호출 실패" }
                throw TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
            }

        val common = response.path("results").path("common")
        val errorCode = common.path("errorCode").asString()
        if (errorCode != SUCCESS_CODE) {
            circuitBreaker.recordFailure()
            // 차단과 일반 오류를 구분할 수 있게 공급자 코드를 그대로 남긴다 (F §2-3)
            logger.error {
                "juso 검색 오류 - errorCode=$errorCode, errorMessage=${common.path("errorMessage").asString()}"
            }
            throw TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
        }
        circuitBreaker.recordSuccess()

        val totalCount = common.path("totalCount").asString().toIntOrNull() ?: 0
        val items = response.path("results").path("juso").mapNotNull { toCandidate(it) }
        return AddressPage(items = items, hasMore = totalCount > page * size)
    }

    private fun toCandidate(node: JsonNode): AddressCandidate? {
        val roadAddress = node.text("roadAddr")
        if (roadAddress.isBlank()) return null
        return AddressCandidate(
            admCd = node.text("admCd"),
            rnMgtSn = node.text("rnMgtSn"),
            udrtYn = node.text("udrtYn"),
            buldMnnm = node.text("buldMnnm"),
            buldSlno = node.text("buldSlno"),
            roadAddress = roadAddress,
            jibunAddress = node.text("jibunAddr").takeIf { it.isNotBlank() },
            regionName = regionName(node),
        )
    }

    /**
     * `시군구명 + " " + 읍면동명`. 세종특별자치시처럼 sggNm이 비면 siNm을 쓴다.
     * 도 단위는 sggNm에 "수원시 영통구"처럼 2단계가 들어오므로 그대로 쓴다.
     * place.region_name 이 VARCHAR(50)이라 넘치면 저장이 실패한다 (F §7).
     */
    private fun regionName(node: JsonNode): String {
        val sgg = node.text("sggNm").ifBlank { node.text("siNm") }
        val assembled = listOf(sgg, node.text("emdNm")).filter { it.isNotBlank() }.joinToString(" ")
        if (assembled.length <= REGION_NAME_MAX) return assembled
        logger.warn { "regionName이 ${REGION_NAME_MAX}자를 넘어 잘랐다 - value=$assembled" }
        return assembled.take(REGION_NAME_MAX)
    }

    private fun JsonNode.text(field: String): String = path(field).asString().orEmpty().trim()

    companion object {
        const val SEARCH_PATH = "/addrLinkApi.do"
        private const val SUCCESS_CODE = "0"

        /** place.region_name VARCHAR(50) */
        private const val REGION_NAME_MAX = 50
    }
}
