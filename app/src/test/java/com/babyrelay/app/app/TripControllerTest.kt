package com.babyrelay.app.app

import com.babyrelay.app.Fx
import com.babyrelay.app.data.facility.DemoOrigin
import com.babyrelay.app.data.facility.FacilityLoad
import com.babyrelay.app.data.facility.FacilityRepository
import com.babyrelay.app.data.facility.FacilitySnapshot
import com.babyrelay.app.data.facility.SnapshotMeta
import com.babyrelay.app.data.local.InMemoryTripStore
import com.babyrelay.app.data.local.SessionCodec
import com.babyrelay.app.data.navigation.Connection
import com.babyrelay.app.data.navigation.GatewayStatus
import com.babyrelay.app.data.navigation.NaviEvent
import com.babyrelay.app.data.navigation.NavigationGateway
import com.babyrelay.app.data.navigation.ReplayDefaults
import com.babyrelay.app.data.navigation.ReplayDirector
import com.babyrelay.app.data.navigation.ReplayNaviAdapter
import com.babyrelay.app.data.navigation.ReplayScenario
import com.babyrelay.app.data.navigation.RequestResult
import com.babyrelay.app.domain.Basis
import com.babyrelay.app.domain.LatLon
import com.babyrelay.app.domain.UxRestrictionState
import com.babyrelay.app.domain.UxSource
import com.babyrelay.app.domain.model.CareKind
import com.babyrelay.app.domain.model.CloseReason
import com.babyrelay.app.domain.model.Facility
import com.babyrelay.app.domain.model.NavMode
import com.babyrelay.app.domain.model.Place
import com.babyrelay.app.domain.model.Registration
import com.babyrelay.app.domain.model.TripState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerTest {
    private val defaults = ReplayDefaults(LatLon(Fx.ORIGIN_LAT, Fx.ORIGIN_LON), listOf(Fx.daejeon, Fx.gangneung))

    private fun repo(list: List<Facility>) = object : FacilityRepository {
        override fun load() = FacilityLoad.Loaded(
            FacilitySnapshot(
                SnapshotMeta("test", "SAMPLE", "2026-11-05", "테스트"),
                DemoOrigin(Fx.ORIGIN_LAT, Fx.ORIGIN_LON, "test"),
                listOf(Fx.daejeon, Fx.gangneung),
                list,
            ),
        )
    }

    private val facilities = listOf(Fx.facility("best", 0.46), Fx.facility("alt", 0.30), Fx.facility("late", 0.8))

    private class Env(
        val ctl: TripController,
        val replay: ReplayNaviAdapter,
        val store: InMemoryTripStore,
        val ux: MutableStateFlow<UxRestrictionState>,
    )

    private fun TestScope.env(
        store: InMemoryTripStore = InMemoryTripStore(),
        list: List<Facility> = facilities,
        restricted: Boolean = false,
        clockValue: () -> Long = { Fx.NOW },
    ): Env {
        val replay = ReplayNaviAdapter(backgroundScope, defaults, confirmDelayMs = 400)
        val ux = MutableStateFlow(UxRestrictionState(restricted, UxSource.SIMULATED))
        val ctl = TripController(replay, store, repo(list), ux, backgroundScope, clockValue, ZoneOffset.UTC, registrationTimeoutMs = 8_000)
        return Env(ctl, replay, store, ux)
    }

    private suspend fun TestScope.boot(e: Env) {
        e.ctl.start()
        runCurrent()
    }

    private fun TestScope.play(e: Env, sc: ReplayScenario) {
        val events = ReplayDirector.eventsFor(sc, e.ctl.state.value.session, defaults)
        events.forEach { e.replay.emit(it) }
        runCurrent()
    }

    private val TripController.session get() = state.value.session

    private suspend fun TestScope.planToStop(e: Env) {
        boot(e)
        play(e, ReplayScenario.PLAN)
        assertEquals(com.babyrelay.app.app.ActionResult.Ok, e.ctl.beginPlanning())
        runCurrent()
    }

    @Test fun `Replay 전체 여정 - 계획, 추천, 경유지 등록, 이동, 휴식 기록, 도착, 인계 카드, 종료`() = runTest {
        val e = env()
        planToStop(e)
        val s0 = e.ctl.session!!
        assertEquals(TripState.PLANNING, s0.state)
        assertEquals("대전역", s0.destination?.name) // 내비 목적지를 가져옴
        val rec = e.ctl.state.value.recommendation!!
        assertEquals(Basis.ROUTE_LINE, rec.basis)
        assertEquals("best", rec.candidates.first().facility.id)
        assertTrue(rec.candidates.size <= 2)

        // 경유지 등록 요청 → REQUESTED
        e.ctl.toggleCarryItem(com.babyrelay.app.domain.model.CarryItem.DIAPER)
        assertEquals(ActionResult.Ok, e.ctl.selectStop("best"))
        assertEquals(Registration.REQUESTED, e.ctl.session!!.plannedStops.first().registration)

        // 내비 확인(RouteStarted → 출발, RoutingCompleted → 경유지 조회 → 등록 확인)
        advanceTimeBy(500); runCurrent()
        assertEquals(TripState.DRIVING, e.ctl.session!!.state)
        assertEquals(Registration.CONFIRMED, e.ctl.session!!.plannedStops.first().registration)

        play(e, ReplayScenario.WAYPOINT_ARRIVAL)
        assertEquals(TripState.STOPPED, e.ctl.session!!.state)
        assertEquals(0, e.ctl.session!!.currentStopIndex)

        assertEquals(ActionResult.Ok, e.ctl.record(CareKind.FEEDING))
        assertEquals(ActionResult.Ok, e.ctl.record(CareKind.DIAPER))
        assertEquals(ActionResult.Ok, e.ctl.record(CareKind.FACILITY_OK))
        assertEquals(ActionResult.Ok, e.ctl.resume())
        assertEquals(TripState.DRIVING, e.ctl.session!!.state)

        play(e, ReplayScenario.DESTINATION_ARRIVAL)
        val arrived = e.ctl.session!!
        assertEquals(TripState.ARRIVED, arrived.state)
        val card = e.ctl.state.value.handoff!!
        assertNotNull(card.lastFeeding); assertNotNull(card.lastDiaper)
        assertEquals(listOf("기저귀"), card.carryItems)
        assertEquals(listOf("시설 best: 이용 가능"), card.facilityNotes)
        assertEquals("REPLAY", card.navModeLabel)

        assertEquals(ActionResult.Ok, e.ctl.closeTrip(noHandoff = false))
        assertEquals(CloseReason.COMPLETED, e.ctl.session!!.closeReason)
        runCurrent()
        assertNull(e.store.loadActive()) // 종료된 여정은 활성 목록에 없다
    }

    @Test fun `기록 출처와 시각이 저장되고 모드가 함께 남는다`() = runTest {
        val e = env()
        planToStop(e)
        e.ctl.selectStop("best"); advanceTimeBy(500); runCurrent()
        play(e, ReplayScenario.WAYPOINT_ARRIVAL)
        e.ctl.record(CareKind.FEEDING)
        val r = e.ctl.session!!.records.single()
        assertEquals(Fx.NOW, r.recordedAt)
        assertEquals(com.babyrelay.app.domain.model.EventSource.USER, r.source)
        assertEquals(NavMode.REPLAY, r.navMode)
        assertEquals(0, r.stopIndex)
    }

    @Test fun `주행 제한 중에는 목적지 변경 시설 선택 기록 입력이 거부되고 상태는 그대로다`() = runTest {
        val e = env()
        planToStop(e)
        e.ux.value = UxRestrictionState(true, UxSource.PLATFORM)
        val before = e.ctl.session
        assertTrue(e.ctl.selectStop("best") is ActionResult.Blocked)
        assertTrue(e.ctl.setDestination(Fx.gangneung) is ActionResult.Blocked)
        assertTrue(e.ctl.toggleCarryItem(com.babyrelay.app.domain.model.CarryItem.BOTTLE) is ActionResult.Blocked)
        assertEquals(before, e.ctl.session)

        e.ux.value = UxRestrictionState(false, UxSource.PLATFORM)
        e.ctl.selectStop("best"); advanceTimeBy(500); runCurrent()
        e.ux.value = UxRestrictionState(true, UxSource.PLATFORM)
        play(e, ReplayScenario.WAYPOINT_ARRIVAL)
        // 경유지 도착 이벤트는 상태만 바꾸고, 주행 제한 중이면 입력은 여전히 막힌다.
        assertEquals(TripState.STOPPED, e.ctl.session!!.state)
        assertTrue(e.ctl.record(CareKind.FEEDING) is ActionResult.Blocked)
        assertTrue(e.ctl.session!!.records.isEmpty())
        // 제한이 풀리면 기록 가능
        e.ux.value = UxRestrictionState(false, UxSource.PLATFORM)
        assertEquals(ActionResult.Ok, e.ctl.record(CareKind.FEEDING))
    }

    @Test fun `주행 제한 상태를 알 수 없으면 입력을 막는다`() = runTest {
        val e = env()
        planToStop(e)
        e.ux.value = UxRestrictionState.Unknown
        assertTrue(e.ctl.selectStop("best") is ActionResult.Blocked)
    }

    @Test fun `주행 제한 중에도 재출발과 도착 상태 반영은 막지 않는다`() = runTest {
        val e = env()
        planToStop(e)
        e.ctl.selectStop("best"); advanceTimeBy(500); runCurrent()
        play(e, ReplayScenario.WAYPOINT_ARRIVAL)
        e.ux.value = UxRestrictionState(true, UxSource.PLATFORM)
        assertEquals(ActionResult.Ok, e.ctl.resume())
        play(e, ReplayScenario.DESTINATION_ARRIVAL)
        assertEquals(TripState.ARRIVED, e.ctl.session!!.state)
        assertTrue(e.ctl.closeTrip(false) is ActionResult.Blocked) // 인계 확인/종료는 제한 중 불가
    }

    @Test fun `중복 도착 이벤트는 한 번만 반영된다`() = runTest {
        val e = env()
        planToStop(e)
        e.ctl.selectStop("best"); advanceTimeBy(500); runCurrent()
        play(e, ReplayScenario.DUPLICATE_ARRIVAL)
        val s = e.ctl.session!!
        assertEquals(TripState.ARRIVED, s.state)
        assertEquals(setOf(0), s.arrivedStopIndexes)
        assertTrue(s.log.count { it.outcome.startsWith("무시") } >= 2)
    }

    @Test fun `경유지 없이 출발해 목적지에 도착하는 여정`() = runTest {
        val e = env()
        planToStop(e)
        assertEquals(ActionResult.Ok, e.ctl.startTripManually())
        assertEquals(TripState.DRIVING, e.ctl.session!!.state)
        play(e, ReplayScenario.DESTINATION_ARRIVAL)
        assertEquals(TripState.ARRIVED, e.ctl.session!!.state)
        assertEquals(0, e.ctl.session!!.arrivedStopIndexes.size)
        assertNull(e.ctl.state.value.handoff?.lastFeeding)
    }

    @Test fun `이동 중 내비 경로 취소는 여정을 종료하고 도착 후 취소 이벤트는 무시한다`() = runTest {
        val e = env()
        planToStop(e)
        e.ctl.selectStop("best"); advanceTimeBy(500); runCurrent()
        play(e, ReplayScenario.ROUTE_CANCEL)
        assertEquals(CloseReason.ROUTE_CANCELLED, e.ctl.session!!.closeReason)

        val e2 = env()
        planToStop(e2)
        e2.ctl.startTripManually()
        play(e2, ReplayScenario.DESTINATION_ARRIVAL)
        play(e2, ReplayScenario.ROUTE_CANCEL)
        assertEquals(TripState.ARRIVED, e2.ctl.session!!.state)
    }

    @Test fun `시작 전 취소`() = runTest {
        val e = env()
        planToStop(e)
        assertEquals(ActionResult.Ok, e.ctl.cancelPlanning())
        assertEquals(CloseReason.CANCELLED_BEFORE_START, e.ctl.session!!.closeReason)
        e.ctl.dismissClosed()
        assertNull(e.ctl.session)
        assertEquals(ActionResult.Ok, e.ctl.beginPlanning()) // 새 여행 가능
    }

    @Test fun `내비 확인 이벤트가 오지 않으면 미확인으로 바뀌고 장소 정보를 보여준다`() = runTest {
        val e = env()
        e.replay.autoConfirmRoute = false
        planToStop(e)
        e.ctl.selectStop("best")
        advanceTimeBy(7_000); runCurrent()
        assertEquals(Registration.REQUESTED, e.ctl.session!!.plannedStops.first().registration)
        advanceTimeBy(1_500); runCurrent()
        assertEquals(Registration.UNCONFIRMED, e.ctl.session!!.plannedStops.first().registration)
        val note = e.ctl.state.value.registrationNote!!
        assertTrue(note, "시설 best" in note && "주소 best" in note)
    }

    @Test fun `내비 요청 실패 시 직접 확인할 장소 정보를 제공한다`() = runTest {
        val gw = FakeGateway(RequestResult.Failed("권한 없음"))
        val ctl = TripController(gw, InMemoryTripStore(), repo(facilities), MutableStateFlow(UxRestrictionState(false, UxSource.SIMULATED)), backgroundScope, { Fx.NOW }, ZoneOffset.UTC)
        ctl.start(); runCurrent()
        gw.events.emit(NaviEvent.CurrentLocation(Fx.ORIGIN_LAT, Fx.ORIGIN_LON))
        gw.events.emit(NaviEvent.DestinationInfo(Fx.daejeon))
        runCurrent()
        ctl.beginPlanning(); runCurrent()
        assertEquals(ActionResult.Ok, ctl.selectStop("best"))
        assertEquals(Registration.FAILED, ctl.state.value.session!!.plannedStops.first().registration)
        val note = ctl.state.value.registrationNote!!
        assertTrue(note, "권한 없음" in note && "시설 best" in note && "주소 best" in note)
        // 실패해도 앱은 계속 동작: 수동 출발 가능
        assertEquals(ActionResult.Ok, ctl.startTripManually())
    }

    @Test fun `내비에 연결되지 않은 상태의 요청도 안전하게 실패 처리된다`() = runTest {
        val gw = FakeGateway(RequestResult.NotConnected)
        val ctl = TripController(gw, InMemoryTripStore(), repo(facilities), MutableStateFlow(UxRestrictionState(false, UxSource.SIMULATED)), backgroundScope, { Fx.NOW }, ZoneOffset.UTC)
        ctl.start(); runCurrent()
        gw.events.emit(NaviEvent.CurrentLocation(Fx.ORIGIN_LAT, Fx.ORIGIN_LON))
        gw.events.emit(NaviEvent.DestinationInfo(Fx.daejeon))
        runCurrent()
        ctl.beginPlanning(); runCurrent()
        ctl.selectStop("best")
        assertEquals(Registration.FAILED, ctl.state.value.session!!.plannedStops.first().registration)
        assertTrue(ctl.state.value.registrationNote!!.contains("연결되지 않았"))
    }

    @Test fun `경유지 등록 요청 직후 내비 오류 이벤트가 오면 실패로 표시한다`() = runTest {
        val e = env()
        e.replay.autoConfirmRoute = false
        planToStop(e)
        e.ctl.selectStop("best")
        e.replay.emit(NaviEvent.Error("CAUSE_SEARCH_ROUTE")); runCurrent()
        assertEquals(Registration.FAILED, e.ctl.session!!.plannedStops.first().registration)
        assertTrue(e.ctl.state.value.registrationNote!!.contains("CAUSE_SEARCH_ROUTE"))
    }

    @Test fun `앱이 요청하지 않은 경로 시작 이벤트는 출발로 간주하지 않는다`() = runTest {
        val e = env()
        planToStop(e)
        e.replay.emit(NaviEvent.RouteStarted("아무데나", 0, 0)); runCurrent()
        assertEquals(TripState.PLANNING, e.ctl.session!!.state)
    }

    @Test fun `시설 데이터 없는 지역은 후보 없음 안내를 준다`() = runTest {
        val e = env()
        boot(e)
        e.ctl.beginPlanning(); runCurrent()
        play(e, ReplayScenario.NO_DATA)
        assertEquals("강릉역", e.ctl.session!!.destination?.name)
        val rec = e.ctl.state.value.recommendation!!
        assertTrue(rec.candidates.isEmpty())
        assertNotNull(rec.notice)
        // 추천이 없어도 경유지 없이 출발할 수 있다
        assertEquals(ActionResult.Ok, e.ctl.startTripManually())
    }

    @Test fun `시설 데이터 파일을 못 읽어도 앱은 계속 동작한다`() = runTest {
        val broken = object : FacilityRepository { override fun load() = FacilityLoad.Failed("파일 없음") }
        val replay = ReplayNaviAdapter(backgroundScope, defaults)
        val ctl = TripController(replay, InMemoryTripStore(), broken, MutableStateFlow(UxRestrictionState(false, UxSource.SIMULATED)), backgroundScope, { Fx.NOW }, ZoneOffset.UTC)
        ctl.start(); runCurrent()
        replay.emit(NaviEvent.DestinationInfo(Fx.daejeon)); runCurrent()
        ctl.beginPlanning(); runCurrent()
        val rec = ctl.state.value.recommendation!!
        assertTrue(rec.candidates.isEmpty())
        assertTrue(rec.notice!!.contains("파일 없음"))
        assertEquals(ActionResult.Ok, ctl.startTripManually())
    }

    @Test fun `앱 재시작 후 진행 중인 여정이 복구된다`() = runTest {
        val store = InMemoryTripStore()
        val e1 = env(store)
        planToStop(e1)
        e1.ctl.selectStop("best"); advanceTimeBy(500); runCurrent()
        play(e1, ReplayScenario.WAYPOINT_ARRIVAL)
        e1.ctl.record(CareKind.FEEDING)
        runCurrent()
        e1.ctl.stop()

        val e2 = env(store)
        boot(e2)
        val s = e2.ctl.session!!
        assertEquals(TripState.STOPPED, s.state)
        assertEquals(1, s.records.size)
        assertEquals("best", s.plannedStops.first().facility.id)
        assertEquals(ActionResult.Ok, e2.ctl.resume()) // 복구 후에도 정상 진행
    }

    @Test fun `7일 지난 종료 여정은 삭제된다 - 보관 최소화`() = runTest {
        val store = InMemoryTripStore()
        var now = Fx.NOW
        val e1 = env(store, clockValue = { now })
        planToStop(e1)
        e1.ctl.cancelPlanning(); runCurrent()
        assertEquals(1, store.rowCount())
        now += 8L * 24 * 60 * 60 * 1000
        val e2 = env(store, clockValue = { now })
        boot(e2)
        assertEquals(0, store.rowCount())
    }

    @Test fun `저장된 여정에는 사용자 현재 위치도 아이 이름 필드도 없다`() = runTest {
        val e = env()
        planToStop(e)
        e.ctl.selectStop("best"); advanceTimeBy(500); runCurrent()
        play(e, ReplayScenario.WAYPOINT_ARRIVAL)
        e.ctl.record(CareKind.FEEDING)
        val json = SessionCodec.encode(e.ctl.session!!)
        assertFalse("현재 위치 좌표가 저장되면 안 됨", json.contains(Fx.ORIGIN_LAT.toString()) || json.contains(Fx.ORIGIN_LON.toString()))
        listOf("babyName", "childName", "photo", "phone", "email", "currentLocation", "userLocation", "originLat", "originLon").forEach {
            assertFalse("'$it' 필드 없음", json.contains("\"$it\"", ignoreCase = true))
        }
        assertEquals(e.ctl.session, SessionCodec.decode(json)) // 라운드트립
    }

    @Test fun `진행 중 여정이 있으면 새 계획을 시작할 수 없다`() = runTest {
        val e = env()
        planToStop(e)
        assertTrue(e.ctl.beginPlanning() is ActionResult.Invalid)
    }

    @Test fun `출발 후에는 계획을 바꿀 수 없다`() = runTest {
        val e = env()
        planToStop(e)
        e.ctl.startTripManually()
        assertTrue(e.ctl.setRestMinutes(90) is ActionResult.Invalid)
        assertTrue(e.ctl.selectStop("best") is ActionResult.Invalid)
    }

    @Test fun `기록 연타는 한 번만 저장되고 시설 이용 기록은 덮어쓴다`() = runTest {
        var now = Fx.NOW
        val e = env(clockValue = { now })
        planToStop(e)
        e.ctl.selectStop("best"); advanceTimeBy(500); runCurrent()
        play(e, ReplayScenario.WAYPOINT_ARRIVAL)
        e.ctl.record(CareKind.FEEDING); now += 500; e.ctl.record(CareKind.FEEDING)
        assertEquals(1, e.ctl.session!!.records.count { it.kind == CareKind.FEEDING })
        now += 10_000
        e.ctl.record(CareKind.FACILITY_OK); now += 10_000; e.ctl.record(CareKind.FACILITY_UNAVAILABLE)
        val fac = e.ctl.session!!.records.filter { it.kind == CareKind.FACILITY_OK || it.kind == CareKind.FACILITY_UNAVAILABLE }
        assertEquals(listOf(CareKind.FACILITY_UNAVAILABLE), fac.map { it.kind })
    }

    @Test fun `주행 중에는 기록할 수 없다`() = runTest {
        val e = env()
        planToStop(e)
        e.ctl.startTripManually()
        assertTrue(e.ctl.record(CareKind.FEEDING) is ActionResult.Invalid)
    }

    @Test fun `ETA는 내비가 준 값만 표시한다`() = runTest {
        val e = env()
        planToStop(e)
        assertNull(e.ctl.state.value.etaRemainSec)
        e.replay.emit(NaviEvent.DrivingProgress(1800)); runCurrent()
        assertEquals(1800, e.ctl.state.value.etaRemainSec)
    }

    @Test fun `도착 후 전달 물품 수정은 주행 제한이 없을 때만 가능하고 인계 카드에 반영된다`() = runTest {
        val e = env()
        planToStop(e)
        e.ctl.startTripManually()
        play(e, ReplayScenario.DESTINATION_ARRIVAL)
        e.ux.value = UxRestrictionState(true, UxSource.PLATFORM)
        assertTrue(e.ctl.toggleCarryItem(com.babyrelay.app.domain.model.CarryItem.WIPES) is ActionResult.Blocked)
        assertTrue(e.ctl.state.value.handoff!!.carryItems.isEmpty())
        e.ux.value = UxRestrictionState(false, UxSource.PLATFORM)
        assertEquals(ActionResult.Ok, e.ctl.toggleCarryItem(com.babyrelay.app.domain.model.CarryItem.WIPES))
        assertEquals(listOf("물티슈"), e.ctl.state.value.handoff!!.carryItems)
    }

    @Test fun `Replay 시나리오는 같은 입력에서 항상 같은 이벤트를 만든다`() {
        ReplayScenario.entries.forEach {
            assertEquals(ReplayDirector.eventsFor(it, null, defaults), ReplayDirector.eventsFor(it, null, defaults))
        }
        assertEquals(NaviEvent.RouteCancelled, ReplayDirector.eventsFor(ReplayScenario.ROUTE_CANCEL, null, defaults).single())
    }

    @Test fun `Replay 어댑터 stop 후 이벤트를 받지 않는다`() = runTest {
        val e = env()
        planToStop(e)
        e.ctl.stop(); runCurrent()
        val before = e.ctl.session
        e.replay.emit(NaviEvent.DestinationArrived("x", "y")); runCurrent()
        assertEquals(before, e.ctl.session)
    }

    private class FakeGateway(private val routeResult: RequestResult) : NavigationGateway {
        override val mode = NavMode.PLEOS
        override val status: StateFlow<GatewayStatus> = MutableStateFlow(GatewayStatus(Connection.CONNECTED))
        override val events: MutableSharedFlow<NaviEvent> = MutableSharedFlow(extraBufferCapacity = 16)
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun requestDestinationInfo() = RequestResult.Sent
        override fun requestCurrentLocation() = RequestResult.Sent
        override fun requestWaypointInfo() = RequestResult.Sent
        override fun requestRoute(destination: Place, stops: List<Place>) = routeResult
    }
}
