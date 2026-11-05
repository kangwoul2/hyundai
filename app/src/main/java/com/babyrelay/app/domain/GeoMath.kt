package com.babyrelay.app.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 지도 서비스 없이 계산하는 직선 기반 기하. 실제 도로 경로/ETA가 아니다. */
object GeoMath {
    private const val EARTH_KM = 6371.0088

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    /** 출발→도착 직선에 대한 점의 위치. alongKm: 출발점부터 직선을 따라 간 거리, offKm: 직선에서의 수직 거리. */
    data class Projection(val alongKm: Double, val offKm: Double, val totalKm: Double) {
        /** 0.0=출발점, 1.0=도착점 */
        val fraction: Double get() = if (totalKm == 0.0) 0.0 else alongKm / totalKm
    }

    fun project(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double,
        pLat: Double, pLon: Double,
    ): Projection {
        val kmPerDegLat = PI * EARTH_KM / 180.0
        val kmPerDegLon = kmPerDegLat * cos(Math.toRadians(originLat))
        val bx = (destLon - originLon) * kmPerDegLon
        val by = (destLat - originLat) * kmPerDegLat
        val px = (pLon - originLon) * kmPerDegLon
        val py = (pLat - originLat) * kmPerDegLat
        val total = sqrt(bx * bx + by * by)
        if (total == 0.0) return Projection(0.0, sqrt(px * px + py * py), 0.0)
        val along = (px * bx + py * by) / total
        val off = abs(px * by - py * bx) / total
        return Projection(along, off, total)
    }
}
