package com.babyrelay.app.app

import com.babyrelay.app.data.facility.FacilityLoad
import com.babyrelay.app.data.facility.FacilityRepository
import com.babyrelay.app.data.local.TripStore
import com.babyrelay.app.data.navigation.NaviEvent
import com.babyrelay.app.data.navigation.NavigationGateway
import com.babyrelay.app.data.navigation.RequestResult
import com.babyrelay.app.domain.GeoMath
import com.babyrelay.app.domain.HandoffCard
import com.babyrelay.app.domain.HandoffCardBuilder
import com.babyrelay.app.domain.LatLon
import com.babyrelay.app.domain.Outcome
import com.babyrelay.app.domain.RecommendRequest
import com.babyrelay.app.domain.Recommendation
import com.babyrelay.app.domain.StopRecommender
import com.babyrelay.app.domain.TripEvent
import com.babyrelay.app.domain.TripStateMachine
import com.babyrelay.app.domain.UiAccess
import com.babyrelay.app.domain.UxPolicy
import com.babyrelay.app.domain.UxRestrictionState
import com.babyrelay.app.domain.model.Amenity
import com.babyrelay.app.domain.model.CareKind
import com.babyrelay.app.domain.model.CareRecord
import com.babyrelay.app.domain.model.CarryItem
import com.babyrelay.app.domain.model.EventSource
import com.babyrelay.app.domain.model.NavMode
import com.babyrelay.app.domain.model.PlannedStop
import com.babyrelay.app.domain.model.Place
import com.babyrelay.app.domain.model.Registration
import com.babyrelay.app.domain.model.TripSession
import com.babyrelay.app.domain.model.TripState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class ControllerState(
    val session: TripSession? = null,
    val recommendation: Recommendation? = null,
    val origin: LatLon? = null,
    /** 내비가 알려준 목적지 */
    val naviDestination: Place? = null,
    val destinationFromNavi: Boolean = false,
    /** 사용자에게 보여줄 경유지 등록 상태 설명 */
    val registrationNote: String? = null,
    /** 내비가 알려준 목적지까지 남은 시간(초). 내비가 주지 않으면 null (추정하지 않는다). */
    val etaRemainSec: Int? = null,
    val handoff: HandoffCard? = null,
    val destinations: List<Place> = emptyList(),
    val dataKind: String? = null,
    val dataNote: String? = null,
    val facilityCount: Int = 0,
    /** 개발 패널용 최근 이벤트 로그 */
    val recentEvents: List<String> = emptyList(),
)

sealed interface ActionResult {
    data object Ok : ActionResult
    data class Blocked(val reason: String) : ActionResult
    data class Invalid(val reason: String) : ActionResult
}

/**
 * 내비 이벤트 → 상태 머신 → 저장소를 잇는 오케스트레이터. Android 프레임워크에 의존하지 않아 JVM에서 테스트한다.
 * 모든 메서드는 scope의 디스패처(메인)에서 호출한다.
 */
class TripController(
    private val gateway: NavigationGateway,
    private val store: TripStore,
    private val facilities: FacilityRepository,
    private val ux: StateFlow<UxRestrictionState>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val registrationTimeoutMs: Long = 20_000, // 실제 Pleos에서 onRouteStarted까지 약 13초 걸림(관찰)
    private val idGen: () -> String = { UUID.randomUUID().toString() },
) {
    private val _state = MutableStateFlow(ControllerState())
    val state: StateFlow<ControllerState> = _state.asStateFlow()

    private var facilityList: List<com.babyrelay.app.domain.model.Facility> = emptyList()
    private var facilityLoadError: String? = null
    private var pendingRoute = false
    private var registrationJob: Job? = null
    private var started = false
    private val jobs = mutableListOf<Job>()
    private val saveChannel = Channel<TripSession>(Channel.CONFLATED)

    val mode: NavMode get() = gateway.mode
    val access: UiAccess get() = UxPolicy.evaluate(ux.value)
    private val session: TripSession? get() = _state.value.session
    private val source: EventSource get() = if (gateway.mode == NavMode.PLEOS) EventSource.NAVI_SDK else EventSource.REPLAY

    // ---------------------------------------------------------------- 시작/종료

    /** 시설 데이터 로드, 이전 여정 복구, 내비 이벤트 구독 시작. */
    suspend fun start() {
        if (started) return
        started = true
        jobs += scope.launch { for (s in saveChannel) store.save(s) }
        loadFacilities()
        store.purgeClosedBefore(clock() - RETENTION_MS)
        store.loadActive()?.let { restored ->
            _state.update { it.copy(session = restored) }
            note("복구: ${restored.state} (${restored.id.take(6)})")
            updateHandoff()
        }
        jobs += scope.launch { gateway.events.collect { onNavi(it) } }
        jobs += scope.launch {
            gateway.status.collect { st ->
                if (st.connection == com.babyrelay.app.data.navigation.Connection.CONNECTED) refreshFromNavi()
            }
        }
        gateway.connect()
        recompute()
    }

    fun stop() {
        registrationJob?.cancel()
        gateway.disconnect()
        // 저장 대기 중인 마지막 상태가 유실되지 않도록 채널을 닫고(소비 코루틴이 비운 뒤 종료) 구독만 끊는다.
        saveChannel.close()
        jobs.filterIndexed { i, _ -> i != 0 }.forEach { it.cancel() }
    }

    private fun loadFacilities() {
        when (val r = facilities.load()) {
            is FacilityLoad.Loaded -> {
                facilityList = r.snapshot.facilities
                facilityLoadError = null
                _state.update {
                    it.copy(
                        destinations = r.snapshot.destinations,
                        dataKind = r.snapshot.meta.kind,
                        dataNote = r.snapshot.meta.note,
                        facilityCount = r.snapshot.facilities.size,
                    )
                }
            }
            is FacilityLoad.Failed -> {
                facilityList = emptyList()
                facilityLoadError = r.reason
                _state.update { it.copy(dataNote = r.reason, dataKind = null, facilityCount = 0) }
            }
        }
    }

    private fun refreshFromNavi() {
        gateway.requestCurrentLocation()
        gateway.requestDestinationInfo()
    }

    // ---------------------------------------------------------------- 계획 단계

    fun beginPlanning(): ActionResult {
        val cur = session
        if (cur != null && cur.state != TripState.CLOSED) return ActionResult.Invalid("진행 중인 여정이 있습니다")
        val now = clock()
        val s = TripSession(
            id = idGen(),
            state = TripState.PLANNING,
            destination = _state.value.naviDestination,
            restInMinutes = DEFAULT_REST_MIN,
            needs = setOf(Amenity.NURSING_ROOM, Amenity.DIAPER_TABLE),
            carryItems = emptySet(),
            createdAt = now,
            updatedAt = now,
            navMode = gateway.mode,
        )
        _state.update { it.copy(session = s, registrationNote = null, etaRemainSec = null, handoff = null, destinationFromNavi = _state.value.naviDestination != null) }
        pendingRoute = false
        persist(s)
        refreshFromNavi()
        recompute()
        return ActionResult.Ok
    }

    fun setDestination(place: Place, fromNavi: Boolean = false): ActionResult = editPlan { s ->
        _state.update { it.copy(destinationFromNavi = fromNavi) }
        s.copy(destination = place, plannedStops = emptyList())
    }

    /** 내비에서 받은 목적지를 사용한다. */
    fun useNaviDestination(): ActionResult {
        val p = _state.value.naviDestination ?: return ActionResult.Invalid("내비 목적지가 없습니다")
        return setDestination(p, fromNavi = true)
    }

    fun setRestMinutes(min: Int): ActionResult = editPlan { it.copy(restInMinutes = min) }

    fun toggleNeed(a: Amenity): ActionResult = editPlan { it.copy(needs = if (a in it.needs) it.needs - a else it.needs + a) }

    /** 전달 물품. 계획 단계에서는 계획 수정 권한, 도착 단계에서는 인계 수정 권한(주행 제한 없음)이 필요하다. */
    fun toggleCarryItem(i: CarryItem): ActionResult {
        val s = session ?: return ActionResult.Invalid("여정이 없습니다")
        if (s.state == TripState.ARRIVED) {
            if (!access.canEditHandoff) return ActionResult.Blocked(access.reason ?: "주행 제한")
            commit(s.copy(carryItems = s.carryItems.toggle(i), updatedAt = clock()))
            return ActionResult.Ok
        }
        return editPlan { it.copy(carryItems = it.carryItems.toggle(i)) }
    }

    private fun Set<CarryItem>.toggle(i: CarryItem) = if (i in this) this - i else this + i

    private inline fun editPlan(change: (TripSession) -> TripSession): ActionResult {
        val s = session ?: return ActionResult.Invalid("여정이 없습니다")
        if (s.state != TripState.PLANNING) return ActionResult.Invalid("출발 후에는 계획을 바꿀 수 없습니다")
        if (!access.canPlan) return ActionResult.Blocked(access.reason ?: "주행 제한")
        val next = change(s).copy(updatedAt = clock())
        _state.update { it.copy(session = next, registrationNote = if (next.plannedStops.isEmpty()) null else it.registrationNote) }
        persist(next)
        recompute()
        return ActionResult.Ok
    }

    /** 추천 후보를 내비게이션 경유지로 등록 요청한다. */
    fun selectStop(facilityId: String): ActionResult {
        val s = session ?: return ActionResult.Invalid("여정이 없습니다")
        if (s.state != TripState.PLANNING) return ActionResult.Invalid("출발 후에는 경유지를 바꿀 수 없습니다")
        if (!access.canSelectStop) return ActionResult.Blocked(access.reason ?: "주행 제한")
        val dest = s.destination ?: return ActionResult.Invalid("목적지를 먼저 정하세요")
        val cand = _state.value.recommendation?.candidates?.firstOrNull { it.facility.id == facilityId }
            ?: return ActionResult.Invalid("추천 목록에 없는 시설입니다")

        val stop = PlannedStop(cand.facility, Registration.REQUESTED)
        val withStop = s.copy(plannedStops = listOf(stop), updatedAt = clock())
        _state.update { it.copy(session = withStop) }
        persist(withStop)

        when (val r = gateway.requestRoute(dest, listOf(cand.facility.toPlace()))) {
            RequestResult.Sent -> {
                pendingRoute = true
                _state.update { it.copy(registrationNote = "내비게이션에 경유지 등록을 요청했습니다. 내비 화면에서 확인하세요.") }
                registrationJob?.cancel()
                registrationJob = scope.launch {
                    delay(registrationTimeoutMs)
                    val cur = session
                    if (cur?.plannedStops?.firstOrNull()?.registration == Registration.REQUESTED) {
                        applyTrip(TripEvent.RegistrationChanged(Registration.UNCONFIRMED, source))
                        _state.update { it.copy(registrationNote = unconfirmedNote(cand.facility.name, cand.facility.address)) }
                    }
                }
            }
            RequestResult.NotConnected -> failRegistration(cand.facility.name, cand.facility.address, "내비게이션에 연결되지 않았습니다")
            is RequestResult.Failed -> failRegistration(cand.facility.name, cand.facility.address, r.reason)
        }
        return ActionResult.Ok
    }

    fun clearStop(): ActionResult = editPlan { it.copy(plannedStops = emptyList()) }

    private fun failRegistration(name: String, address: String, why: String) {
        pendingRoute = false
        applyTrip(TripEvent.RegistrationChanged(Registration.FAILED, source))
        _state.update { it.copy(registrationNote = "경유지 등록 실패: $why. 직접 확인하세요 → $name · $address") }
    }

    private fun unconfirmedNote(name: String, address: String) =
        "내비에서 경유지 등록이 확인되지 않았습니다. 내비 화면에서 직접 확인하세요 → $name · $address"

    // ---------------------------------------------------------------- 수동 조작(내비 이벤트가 없을 때)

    fun startTripManually(): ActionResult {
        val s = session ?: return ActionResult.Invalid("여정이 없습니다")
        if (s.state != TripState.PLANNING) return ActionResult.Invalid("이미 출발했습니다")
        val dest = s.destination ?: return ActionResult.Invalid("목적지를 먼저 정하세요")
        if (!access.canPlan) return ActionResult.Blocked(access.reason ?: "주행 제한")
        if (!pendingRoute && s.plannedStops.isEmpty()) gateway.requestRoute(dest, emptyList())
        applyTrip(TripEvent.StartTrip(EventSource.USER))
        pendingRoute = false
        return ActionResult.Ok
    }

    fun markWaypointArrivedManually(): ActionResult {
        val s = session ?: return ActionResult.Invalid("여정이 없습니다")
        if (s.state != TripState.DRIVING) return ActionResult.Invalid("이동 중에만 사용할 수 있습니다")
        if (!access.canRecord) return ActionResult.Blocked(access.reason ?: "주행 제한")
        val idx = s.plannedStops.indices.firstOrNull { it !in s.arrivedStopIndexes } ?: return ActionResult.Invalid("남은 경유지가 없습니다")
        applyTrip(TripEvent.WaypointArrived(idx, s.plannedStops[idx].facility.name, EventSource.USER))
        return ActionResult.Ok
    }

    fun markDestinationArrivedManually(): ActionResult {
        if (!access.canRecord) return ActionResult.Blocked(access.reason ?: "주행 제한")
        return when (applyTrip(TripEvent.DestinationArrived(EventSource.USER))) {
            Outcome.Applied -> ActionResult.Ok
            else -> ActionResult.Invalid("지금은 도착 처리할 수 없습니다")
        }
    }

    /** 휴식을 마치고 다시 출발. 상태 기록만 바꾸므로 주행 제한과 무관하게 허용한다. */
    fun resume(): ActionResult = when (applyTrip(TripEvent.Resume(EventSource.USER))) {
        Outcome.Applied -> ActionResult.Ok
        else -> ActionResult.Invalid("휴식 중이 아닙니다")
    }

    fun cancelPlanning(): ActionResult {
        registrationJob?.cancel()
        pendingRoute = false
        return when (applyTrip(TripEvent.CancelPlanning())) {
            Outcome.Applied -> ActionResult.Ok
            else -> ActionResult.Invalid("출발 후에는 취소할 수 없습니다")
        }
    }

    // ---------------------------------------------------------------- 돌봄 기록과 인계

    fun record(kind: CareKind): ActionResult {
        val s = session ?: return ActionResult.Invalid("여정이 없습니다")
        if (!access.canRecord) return ActionResult.Blocked(access.reason ?: "주행 제한")
        if (s.state != TripState.STOPPED && s.state != TripState.ARRIVED) return ActionResult.Invalid("정차 후에만 기록할 수 있습니다")
        val now = clock()
        val isFacility = kind == CareKind.FACILITY_OK || kind == CareKind.FACILITY_UNAVAILABLE
        val dup = s.records.lastOrNull { it.kind == kind && it.stopIndex == s.currentStopIndex }
        if (dup != null && now - dup.recordedAt < DOUBLE_TAP_MS) return ActionResult.Ok
        val base = if (isFacility) {
            s.records.filterNot {
                (it.kind == CareKind.FACILITY_OK || it.kind == CareKind.FACILITY_UNAVAILABLE) && it.stopIndex == s.currentStopIndex
            }
        } else s.records
        val rec = CareRecord(idGen(), kind, s.currentStopIndex, now, EventSource.USER, gateway.mode)
        commit(s.copy(records = base + rec, updatedAt = now))
        return ActionResult.Ok
    }

    fun closeTrip(noHandoff: Boolean): ActionResult {
        if (!access.canEditHandoff) return ActionResult.Blocked(access.reason ?: "주행 제한")
        return when (applyTrip(TripEvent.Close(noHandoff))) {
            Outcome.Applied -> ActionResult.Ok
            else -> ActionResult.Invalid("도착 후에만 종료할 수 있습니다")
        }
    }

    /** 종료된 여정 화면을 닫고 처음 화면으로. */
    fun dismissClosed() {
        if (session?.state == TripState.CLOSED) {
            _state.update { it.copy(session = null, recommendation = null, registrationNote = null, etaRemainSec = null, handoff = null) }
        }
    }

    // ---------------------------------------------------------------- 내비 이벤트

    private fun onNavi(e: NaviEvent) {
        note(e.label)
        when (e) {
            is NaviEvent.CurrentLocation -> {
                _state.update { it.copy(origin = LatLon(e.lat, e.lon)) }
                recompute()
            }
            is NaviEvent.DestinationInfo -> {
                _state.update { it.copy(naviDestination = e.place) }
                val s = session
                if (s != null && s.state == TripState.PLANNING && (s.destination == null || _state.value.destinationFromNavi)) {
                    val next = s.copy(destination = e.place, plannedStops = if (s.destination?.id == e.place.id) s.plannedStops else emptyList(), updatedAt = clock())
                    _state.update { it.copy(session = next, destinationFromNavi = true) }
                    persist(next)
                }
                recompute()
            }
            is NaviEvent.RouteStarted -> {
                if (pendingRoute) {
                    applyTrip(TripEvent.StartTrip(source))
                    pendingRoute = false
                    // 경로가 시작되면 경유지 반영 여부를 내비에 조회한다(Pleos는 onWayPointInfo를 자동 전송하지 않음).
                    val reg = session?.plannedStops?.firstOrNull()?.registration
                    if (reg == Registration.REQUESTED || reg == Registration.UNCONFIRMED) gateway.requestWaypointInfo()
                } else {
                    note("무시: 앱이 요청하지 않은 경로 시작")
                }
            }
            NaviEvent.RoutingCompleted -> {
                if (session?.plannedStops?.firstOrNull()?.registration == Registration.REQUESTED) gateway.requestWaypointInfo()
            }
            is NaviEvent.WaypointArrived -> applyTrip(TripEvent.WaypointArrived(e.index, e.name, source))
            is NaviEvent.DestinationArrived -> applyTrip(TripEvent.DestinationArrived(source))
            NaviEvent.RouteCancelled -> {
                pendingRoute = false
                applyTrip(TripEvent.RouteCancelled(source))
            }
            is NaviEvent.WaypointChanged -> if (e.added) confirmRegistration()
            is NaviEvent.WaypointInfo -> {
                val stop = session?.plannedStops?.firstOrNull()?.facility
                if (stop != null && e.waypoints.any { it.matches(stop) }) confirmRegistration()
            }
            is NaviEvent.DrivingProgress -> _state.update { it.copy(etaRemainSec = e.destinationRemainSec) }
            is NaviEvent.Error -> {
                val reg = session?.plannedStops?.firstOrNull()
                if (reg != null && reg.registration == Registration.REQUESTED) {
                    pendingRoute = false
                    failRegistration(reg.facility.name, reg.facility.address, "내비 오류 ${e.code}")
                }
            }
        }
    }

    private fun Place.matches(f: com.babyrelay.app.domain.model.Facility): Boolean =
        name == f.name || GeoMath.haversineKm(lat, lon, f.lat, f.lon) < MATCH_KM

    private fun confirmRegistration() {
        val reg = session?.plannedStops?.firstOrNull()?.registration ?: return
        if (reg == Registration.REQUESTED || reg == Registration.UNCONFIRMED) {
            registrationJob?.cancel()
            applyTrip(TripEvent.RegistrationChanged(Registration.CONFIRMED, source))
            _state.update { it.copy(registrationNote = "내비게이션에 경유지가 등록되었습니다.") }
        }
    }

    // ---------------------------------------------------------------- 내부

    private fun applyTrip(ev: TripEvent): Outcome? {
        val s = session ?: return null
        val r = TripStateMachine.reduce(s, ev, clock())
        commit(r.session)
        return r.outcome
    }

    private fun commit(next: TripSession) {
        _state.update { it.copy(session = next) }
        persist(next)
        updateHandoff()
    }

    private fun persist(s: TripSession) {
        saveChannel.trySend(s)
    }

    private fun updateHandoff() {
        val s = session
        val card = if (s != null && (s.state == TripState.ARRIVED || s.state == TripState.CLOSED)) HandoffCardBuilder.build(s, clock()) else null
        _state.update { it.copy(handoff = card) }
    }

    private fun recompute() {
        val s = session
        val rec = if (s == null || s.state != TripState.PLANNING || s.destination == null) {
            null
        } else if (facilityLoadError != null) {
            Recommendation(emptyList(), com.babyrelay.app.domain.Basis.NONE, facilityLoadError)
        } else {
            StopRecommender.recommend(
                facilityList,
                RecommendRequest(_state.value.origin, s.destination, s.restInMinutes, s.needs, today()),
            )
        }
        _state.update { it.copy(recommendation = rec) }
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()

    private fun note(text: String) {
        _state.update { it.copy(recentEvents = (listOf(text) + it.recentEvents).take(12)) }
    }

    companion object {
        const val DEFAULT_REST_MIN = 60
        private const val DOUBLE_TAP_MS = 2_000L
        private const val MATCH_KM = 0.3
        private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
