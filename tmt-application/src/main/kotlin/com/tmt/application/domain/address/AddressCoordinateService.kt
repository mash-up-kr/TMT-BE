package com.tmt.application.domain.address

import com.tmt.application.port.input.ResolveAddressCoordinateUseCase
import com.tmt.application.port.output.address.AddressCoordinateKey
import com.tmt.application.port.output.address.AddressCoordinatePort
import com.tmt.application.port.output.address.ProjectedPoint
import com.tmt.application.port.output.persistence.CoordinateTransformPort
import com.tmt.application.port.output.persistence.Wgs84Point
import com.tmt.common.exception.ErrorCode
import com.tmt.common.exception.TmtException
import org.springframework.stereotype.Service

/**
 * juso 좌표제공 API는 EPSG:5179(GRS80 UTM-K) 평면좌표만 준다. place.location 이
 * geography(Point,4326)이라 4326으로 변환해야 한다 (P4). 적재분의 EPSG:5174와는 다른 좌표계다.
 */
@Service
class AddressCoordinateService(
    private val addressCoordinatePort: AddressCoordinatePort,
    private val coordinateTransformPort: CoordinateTransformPort,
) : ResolveAddressCoordinateUseCase {
    override fun resolve(key: AddressCoordinateKey): Wgs84Point {
        val point = addressCoordinatePort.findCoordinate(key) ?: throw TmtException(ErrorCode.ADDRESS_NOT_FOUND)
        return coordinateTransformPort.toWgs84(point.x, point.y, ProjectedPoint.JUSO_SRID)
    }
}
