package com.babyrelay.app.app

import android.app.Application
import androidx.room.Room
import com.babyrelay.app.data.facility.AssetFacilityRepository
import com.babyrelay.app.data.facility.FacilityLoad
import com.babyrelay.app.data.local.BabyRelayDb
import com.babyrelay.app.data.local.RoomTripStore
import com.babyrelay.app.data.local.TripStore
import com.babyrelay.app.data.navigation.ReplayDefaults
import com.babyrelay.app.domain.LatLon
import com.babyrelay.app.domain.model.Place

/** 수동 의존성 구성. 별도 DI 프레임워크는 쓰지 않는다. */
class AppContainer(val app: Application) {
    val facilities = AssetFacilityRepository(app)

    private val db: BabyRelayDb by lazy { Room.databaseBuilder(app, BabyRelayDb::class.java, "babyrelay.db").build() }
    val store: TripStore by lazy { RoomTripStore(db.tripDao()) }

    val replayDefaults: ReplayDefaults by lazy {
        when (val r = facilities.load()) {
            is FacilityLoad.Loaded -> ReplayDefaults(
                LatLon(r.snapshot.demoOrigin.lat, r.snapshot.demoOrigin.lon),
                r.snapshot.destinations.ifEmpty { listOf(FALLBACK_DEST) },
            )
            is FacilityLoad.Failed -> ReplayDefaults(LatLon(37.4692, 127.0393), listOf(FALLBACK_DEST))
        }
    }

    private companion object {
        val FALLBACK_DEST = Place("fallback", "목적지 미정", "시설 데이터 없음", 37.0, 127.0)
    }
}
