package com.babyrelay.app

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.babyrelay.app.data.facility.AssetFacilityRepository
import com.babyrelay.app.data.facility.FacilityLoad
import com.babyrelay.app.data.local.BabyRelayDb
import com.babyrelay.app.data.local.RoomTripStore
import com.babyrelay.app.data.platform.AaosUxRestrictionSource
import com.babyrelay.app.domain.UxSource
import com.babyrelay.app.domain.model.Amenity
import com.babyrelay.app.domain.model.CareKind
import com.babyrelay.app.domain.model.CareRecord
import com.babyrelay.app.domain.model.EventSource
import com.babyrelay.app.domain.model.NavMode
import com.babyrelay.app.domain.model.Place
import com.babyrelay.app.domain.model.TripSession
import com.babyrelay.app.domain.model.TripState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 실제 기기/에뮬레이터에서만 확인할 수 있는 항목: Room 저장·복구, 번들 자산, AAOS CarUxRestrictions 읽기. */
@RunWith(AndroidJUnit4::class)
class DeviceTests {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun session(id: String, state: TripState, updatedAt: Long) = TripSession(
        id = id, state = state, destination = Place("d", "대전역", "대전", 36.33, 127.43),
        restInMinutes = 60, needs = setOf(Amenity.NURSING_ROOM), carryItems = emptySet(),
        records = listOf(CareRecord("r1", CareKind.FEEDING, 0, updatedAt, EventSource.USER, NavMode.REPLAY)),
        createdAt = updatedAt, updatedAt = updatedAt, navMode = NavMode.REPLAY,
    )

    @Test fun roomStore_saveLoadActive_andRestartRecovery(): Unit = runBlocking {
        val name = "test-restart.db"
        ctx.deleteDatabase(name)
        var db = Room.databaseBuilder(ctx, BabyRelayDb::class.java, name).build()
        RoomTripStore(db.tripDao()).save(session("a", TripState.STOPPED, 1_000))
        db.close() // 앱 종료를 흉내 낸다

        db = Room.databaseBuilder(ctx, BabyRelayDb::class.java, name).build()
        val restored = RoomTripStore(db.tripDao()).loadActive()
        assertNotNull(restored)
        assertEquals(TripState.STOPPED, restored!!.state)
        assertEquals(1, restored.records.size)
        db.close()
        ctx.deleteDatabase(name)
    }

    @Test fun roomStore_closedTripsAreNotActive_andPurged(): Unit = runBlocking {
        val name = "test-purge.db"
        ctx.deleteDatabase(name)
        val db = Room.databaseBuilder(ctx, BabyRelayDb::class.java, name).build()
        val store = RoomTripStore(db.tripDao())
        store.save(session("old-closed", TripState.CLOSED, 1_000))
        store.save(session("new-active", TripState.DRIVING, 5_000))
        assertEquals("new-active", store.loadActive()!!.id)
        store.purgeClosedBefore(2_000)
        assertEquals(1, db.tripDao().count())
        store.save(session("new-active", TripState.CLOSED, 6_000))
        assertNull(store.loadActive())
        db.close()
        ctx.deleteDatabase(name)
    }

    @Test fun bundledSnapshot_loadsFromAssets() {
        val r = AssetFacilityRepository(ctx).load()
        assertTrue(r is FacilityLoad.Loaded)
        assertEquals("SAMPLE", (r as FacilityLoad.Loaded).snapshot.meta.kind)
    }

    /** AAOS(CarService가 있는 이미지)에서 CarUxRestrictions의 값을 플랫폼 출처로 읽는지 확인한다. 일반 단말에서는 건너뛴다. */
    @Test fun aaosUxRestrictions_readsPlatformValue(): Unit = runBlocking {
        val isAuto = ctx.packageManager.hasSystemFeature("android.hardware.type.automotive")
        assumeTrue("자동차(AAOS) 이미지가 아님", isAuto)
        val src = AaosUxRestrictionSource(ctx)
        src.start()
        var waited = 0
        while (src.state.value.source != UxSource.PLATFORM && waited < 10_000) {
            delay(250); waited += 250
        }
        val v = src.state.value
        println("BABYRELAY_UX source=${v.source} requiresDO=${v.requiresDistractionOptimization} waitedMs=$waited")
        src.stop()
        assertEquals(UxSource.PLATFORM, v.source)
        assertNotNull(v.requiresDistractionOptimization)
    }
}
