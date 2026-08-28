package com.tmt.application.domain.address

import com.tmt.application.port.output.address.AddressCoordinateKey
import com.tmt.application.port.output.address.AddressCoordinatePort
import com.tmt.application.port.output.address.ProjectedPoint
import com.tmt.application.port.output.persistence.CoordinateTransformPort
import com.tmt.application.port.output.persistence.Wgs84Point
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AddressCoordinateServiceTest {
    private val key = AddressCoordinateKey("1147010100", "114704166011", "0", "1", "0")

    @Test
    fun `좌표를 못 찾으면 ADDRESS_NOT_FOUND다`() {
        val service = AddressCoordinateService(coordinatePort(null), failingTransform())

        val e = assertFailsWith<TmtException> { service.resolve(key) }
        assertEquals(ErrorCode.ADDRESS_NOT_FOUND, e.errorCode)
    }

    @Test
    fun `평면좌표는 5179로 변환 요청된다 - 적재분의 5174와 섞이지 않는다`() {
        val transform = RecordingTransform()
        val service = AddressCoordinateService(coordinatePort(ProjectedPoint(946000.0, 1946000.0)), transform)

        val point = service.resolve(key)

        assertEquals(5179, transform.lastSrid)
        assertEquals(946000.0, transform.lastX)
        assertEquals(Wgs84Point(37.5, 126.9), point)
    }

    private fun coordinatePort(point: ProjectedPoint?) =
        object : AddressCoordinatePort {
            override fun findCoordinate(key: AddressCoordinateKey) = point
        }

    private fun failingTransform() =
        object : CoordinateTransformPort {
            override fun toWgs84(
                x: Double,
                y: Double,
                sourceSrid: Int,
            ): Wgs84Point = error("좌표가 없으면 변환을 부르면 안 된다")
        }

    private class RecordingTransform : CoordinateTransformPort {
        var lastSrid: Int? = null
        var lastX: Double? = null

        override fun toWgs84(
            x: Double,
            y: Double,
            sourceSrid: Int,
        ): Wgs84Point {
            lastX = x
            lastSrid = sourceSrid
            return Wgs84Point(37.5, 126.9)
        }
    }
}
