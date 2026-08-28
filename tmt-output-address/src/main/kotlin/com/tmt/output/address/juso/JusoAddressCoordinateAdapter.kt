package com.tmt.output.address.juso

import com.tmt.application.port.output.address.AddressCoordinateKey
import com.tmt.application.port.output.address.AddressCoordinatePort
import com.tmt.application.port.output.address.ProjectedPoint
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * 좌표제공 API (F §4-1 처리 순서 3번). **확정된 주소 1건에만** 호출한다 —
 * 검색 결과 전체가 아니라서 호출량 = 실제 Place 등록 건수다.
 *
 * 승인키가 검색 API와 다르다. 설정 키를 분리해 둔다.
 */
@Component
class JusoAddressCoordinateAdapter(
    private val httpClient: JusoHttpClient,
    private val circuitBreaker: JusoCircuitBreaker,
    @param:Value("\${tmt.address.juso.coord-key:}") private val confmKey: String,
) : AddressCoordinatePort {
    override fun findCoordinate(key: AddressCoordinateKey): ProjectedPoint? {
        if (confmKey.isBlank()) {
            logger.error { "juso 좌표 승인키가 없다 - tmt.address.juso.coord-key 설정 확인 (검색 승인키와 다른 키다)" }
            throw TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
        }
        if (circuitBreaker.isOpen) {
            logger.warn { "juso 차단기가 열려 있어 좌표 호출을 건너뛴다" }
            throw TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
        }

        val response =
            runCatching {
                httpClient.getJson(
                    COORD_PATH,
                    mapOf(
                        "confmKey" to confmKey,
                        "admCd" to key.admCd,
                        "rnMgtSn" to key.rnMgtSn,
                        "udrtYn" to key.udrtYn,
                        "buldMnnm" to key.buldMnnm,
                        "buldSlno" to key.buldSlno,
                        "resultType" to "json",
                    ),
                )
            }.getOrElse {
                circuitBreaker.recordFailure()
                logger.warn(it) { "juso 좌표 호출 실패" }
                throw TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
            }

        val common = response.path("results").path("common")
        val errorCode = common.path("errorCode").asString()
        if (errorCode != SUCCESS_CODE) {
            circuitBreaker.recordFailure()
            logger.error {
                "juso 좌표 오류 - errorCode=$errorCode, errorMessage=${common.path("errorMessage").asString()}"
            }
            throw TmtException(ErrorCode.ADDRESS_PROVIDER_UNAVAILABLE)
        }
        circuitBreaker.recordSuccess()

        // 장애가 아니라 "그 주소에 좌표가 없음"이다 — 호출자가 ADDRESS_NOT_FOUND로 가른다
        val juso = response.path("results").path("juso").firstOrNull() ?: return null
        val x = juso.path("entX").asString().toDoubleOrNull() ?: return null
        val y = juso.path("entY").asString().toDoubleOrNull() ?: return null
        return ProjectedPoint(x = x, y = y)
    }

    companion object {
        const val COORD_PATH = "/addrCoordApi.do"
        private const val SUCCESS_CODE = "0"
    }
}
