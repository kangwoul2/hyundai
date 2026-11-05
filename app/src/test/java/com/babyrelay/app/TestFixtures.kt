package com.babyrelay.app

import com.babyrelay.app.domain.model.Amenity
import com.babyrelay.app.domain.model.CarryItem
import com.babyrelay.app.domain.model.DataOrigin
import com.babyrelay.app.domain.model.Facility
import com.babyrelay.app.domain.model.NavMode
import com.babyrelay.app.domain.model.Place
import com.babyrelay.app.domain.model.PlannedStop
import com.babyrelay.app.domain.model.TripSession
import com.babyrelay.app.domain.model.TripState
import java.time.Instant

object Fx {
    val NOW: Long = Instant.parse("2026-11-05T09:00:00Z").toEpochMilli()

    const val ORIGIN_LAT = 37.4692
    const val ORIGIN_LON = 127.0393

    val daejeon = Place("dest-daejeon", "대전역", "대전 동구", 36.3323, 127.4342)
    val gangneung = Place("dest-gangneung", "강릉역", "강원 강릉", 37.7640, 128.8993)

    /** 출발→대전 직선 위 t 지점. lonShiftDeg로 직선에서 옆으로 이동시킨다(약 88km/도). */
    fun onLine(t: Double, lonShiftDeg: Double = 0.0): Pair<Double, Double> =
        (ORIGIN_LAT + t * (daejeon.lat - ORIGIN_LAT)) to (ORIGIN_LON + t * (daejeon.lon - ORIGIN_LON) + lonShiftDeg)

    fun facility(
        id: String,
        t: Double,
        off: Double = 0.0,
        amenities: Set<Amenity> = setOf(Amenity.NURSING_ROOM, Amenity.DIAPER_TABLE),
        origin: DataOrigin = DataOrigin.SAMPLE,
        verifiedOn: String = "2026-10-20",
        kind: String? = "휴게소",
    ): Facility {
        val (lat, lon) = onLine(t, off)
        return Facility(id, "시설 $id", "주소 $id", lat, lon, kind, amenities, origin, verifiedOn)
    }

    fun session(
        state: TripState = TripState.PLANNING,
        stops: List<PlannedStop> = emptyList(),
        destination: Place? = daejeon,
        items: Set<CarryItem> = emptySet(),
    ) = TripSession(
        id = "t1", state = state, destination = destination, restInMinutes = 60,
        needs = setOf(Amenity.NURSING_ROOM, Amenity.DIAPER_TABLE), carryItems = items,
        plannedStops = stops, createdAt = NOW, updatedAt = NOW, navMode = NavMode.REPLAY,
    )
}
