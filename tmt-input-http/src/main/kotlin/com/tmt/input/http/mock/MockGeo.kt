package com.tmt.input.http.mock

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object MockGeo {
    /** 근처보기 반경 — 서버가 1km로 고정한다 (E1). 클라이언트가 바꿀 수 없다. */
    const val NEARBY_RADIUS_METERS = 1_000

    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Int {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return (earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))).roundToInt()
    }
}
