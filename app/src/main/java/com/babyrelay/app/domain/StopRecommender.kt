package com.babyrelay.app.domain

import com.babyrelay.app.domain.model.Amenity
import com.babyrelay.app.domain.model.Facility
import com.babyrelay.app.domain.model.Place
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LatLon(val lat: Double, val lon: Double)

data class RecommendRequest(
    /** 현재 위치(또는 출발 위치). 내비가 주지 않으면 null. */
    val origin: LatLon?,
    val destination: Place,
    val restInMinutes: Int,
    val needs: Set<Amenity>,
    val today: LocalDate,
)

enum class Basis(val label: String) {
    ROUTE_LINE("직선거리 기반 근접 후보"),
    NEAR_DESTINATION("목적지 근처 후보 (현재 위치 없음)"),
    NONE("후보 없음"),
}

data class Candidate(
    val facility: Facility,
    val alongKm: Double?,
    val offKm: Double?,
    val distToDestKm: Double,
    val score: Double,
    val reasons: List<String>,
)

data class Recommendation(
    val candidates: List<Candidate>,
    val basis: Basis,
    val notice: String?,
)

/**
 * 설명 가능한 규칙 기반 추천.
 *  1) 필수 시설 조건 필터  2) 출발→도착 직선 기준 위치·시간 적합성  3) 출처/확인일 신뢰도  4) 상위 2개  5) 사유 문장
 *
 * 실제 도로 ETA나 우회 시간은 계산하지 않는다. 모든 거리는 직선 기준이며, 시간 적합성은
 * [ASSUMED_SPEED_KMH] 가정에 의존한다는 점을 사유 문장에 그대로 밝힌다.
 */
object StopRecommender {
    const val ASSUMED_SPEED_KMH = 60.0
    const val MAX_OFF_KM = 15.0
    const val MAX_NEAR_DEST_KM = 15.0
    const val MAX_RESULTS = 2
    private const val STALE_DAYS = 365L
    private const val MIN_FRACTION = 0.05
    private const val MAX_FRACTION = 0.95

    fun recommend(facilities: List<Facility>, req: RecommendRequest): Recommendation {
        if (facilities.isEmpty()) {
            return Recommendation(emptyList(), Basis.NONE, "시설 데이터를 불러오지 못했습니다.")
        }
        val valid = facilities.distinctBy { it.id }.filter { it.hasValidCoordinates() }
        val matching = valid.filter { it.amenities.containsAll(req.needs) }
        if (matching.isEmpty()) {
            return Recommendation(emptyList(), Basis.NONE, "선택한 조건을 모두 갖춘 시설 정보가 없습니다.")
        }
        val origin = req.origin
        val dest = req.destination

        return if (origin == null) nearDestination(matching, req) else routeLine(matching, req, origin, dest)
    }

    private fun routeLine(matching: List<Facility>, req: RecommendRequest, origin: LatLon, dest: Place): Recommendation {
        val total = GeoMath.haversineKm(origin.lat, origin.lon, dest.lat, dest.lon)
        val wanted = min(ASSUMED_SPEED_KMH * req.restInMinutes / 60.0, total * 0.9)
        val scored = matching.mapNotNull { f ->
            val p = GeoMath.project(origin.lat, origin.lon, dest.lat, dest.lon, f.lat, f.lon)
            if (p.fraction < MIN_FRACTION || p.fraction > MAX_FRACTION || p.offKm > MAX_OFF_KM) return@mapNotNull null
            val timeScore = 1.0 - min(1.0, abs(p.alongKm - wanted) / max(10.0, p.totalKm * 0.5))
            val offScore = 1.0 - p.offKm / MAX_OFF_KM
            val (trust, stale) = trust(f, req.today)
            val score = 0.45 * timeScore + 0.35 * offScore + 0.20 * trust
            val reasons = buildList {
                add(amenityReason(f, req.needs))
                add(
                    "출발 후 직선 약 ${km(p.alongKm)}km 지점 · 희망 ${req.restInMinutes}분 ≈ ${km(wanted)}km " +
                        "(평균 ${ASSUMED_SPEED_KMH.toInt()}km/h 가정)",
                )
                add("경로 직선에서 ${km(p.offKm)}km 거리 (직선 기준, 실제 우회 시간 아님)")
                add(sourceReason(f, stale))
            }
            Candidate(f, p.alongKm, p.offKm, GeoMath.haversineKm(f.lat, f.lon, dest.lat, dest.lon), score, reasons)
        }
        val top = scored.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.facility.id }).take(MAX_RESULTS)
        return if (top.isEmpty()) {
            Recommendation(emptyList(), Basis.NONE, "경로 주변 ${MAX_OFF_KM.toInt()}km 안에 조건에 맞는 시설 정보가 없습니다.")
        } else {
            Recommendation(top, Basis.ROUTE_LINE, null)
        }
    }

    private fun nearDestination(matching: List<Facility>, req: RecommendRequest): Recommendation {
        val dest = req.destination
        val scored = matching.mapNotNull { f ->
            val d = GeoMath.haversineKm(f.lat, f.lon, dest.lat, dest.lon)
            if (d > MAX_NEAR_DEST_KM) return@mapNotNull null
            val (trust, stale) = trust(f, req.today)
            val score = 0.7 * (1.0 - d / MAX_NEAR_DEST_KM) + 0.3 * trust
            Candidate(
                f, null, null, d, score,
                listOf(
                    amenityReason(f, req.needs),
                    "목적지에서 직선 ${km(d)}km (현재 위치를 받지 못해 목적지 기준으로 표시)",
                    sourceReason(f, stale),
                ),
            )
        }
        val top = scored.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.facility.id }).take(MAX_RESULTS)
        return if (top.isEmpty()) {
            Recommendation(emptyList(), Basis.NONE, "현재 위치가 없고 목적지 ${MAX_NEAR_DEST_KM.toInt()}km 안에 조건에 맞는 시설 정보도 없습니다.")
        } else {
            Recommendation(top, Basis.NEAR_DESTINATION, "현재 위치를 받지 못해 목적지 근처 후보로 표시합니다.")
        }
    }

    private fun trust(f: Facility, today: LocalDate): Pair<Double, Boolean> {
        val verified = runCatching { LocalDate.parse(f.verifiedOn) }.getOrNull()
        val stale = verified == null || ChronoUnit.DAYS.between(verified, today) > STALE_DAYS
        return (f.origin.baseTrust - if (stale) 0.2 else 0.0) to stale
    }

    private fun amenityReason(f: Facility, needs: Set<Amenity>): String {
        val shown = if (needs.isEmpty()) f.amenities else needs
        val names = shown.sortedBy { it.ordinal }.joinToString("·") { it.label }
        return if (names.isEmpty()) "시설 정보 확인 필요" else "$names 정보 있음"
    }

    private fun sourceReason(f: Facility, stale: Boolean): String =
        "${f.origin.label} · 확인일 ${f.verifiedOn}" + if (stale) " (1년 이상 지남)" else ""

    private fun km(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun Facility.hasValidCoordinates() =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)
}
