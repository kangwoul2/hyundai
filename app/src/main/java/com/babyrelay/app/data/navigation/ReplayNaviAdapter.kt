package com.babyrelay.app.data.navigation

import com.babyrelay.app.domain.model.NavMode
import com.babyrelay.app.domain.model.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 시연·검증용 재생 어댑터. 실제 내비/차량과 통신하지 않으며 실제 SDK 검증을 대신하지 않는다.
 * 도메인 로직은 PleosNaviAdapter와 동일한 NaviEvent 흐름을 통해 검증된다.
 */
class ReplayNaviAdapter(
    private val scope: CoroutineScope,
    private val defaults: ReplayDefaults,
    private val confirmDelayMs: Long = 400,
) : NavigationGateway {
    override val mode = NavMode.REPLAY

    private val _status = MutableStateFlow(GatewayStatus(Connection.IDLE))
    override val status: StateFlow<GatewayStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    /** true면 경로 요청에 내비 확인 이벤트를 자동 재생한다. false면 확인이 오지 않는 상황(미확인)을 재현한다. */
    @Volatile var autoConfirmRoute: Boolean = true
    private var lastStops: List<Place> = emptyList()

    override fun connect() {
        _status.value = GatewayStatus(Connection.CONNECTED, "REPLAY (시뮬레이션)")
    }

    override fun disconnect() {
        _status.value = GatewayStatus(Connection.IDLE)
    }

    override fun requestDestinationInfo(): RequestResult {
        _events.tryEmit(NaviEvent.DestinationInfo(defaults.destinations.first()))
        return RequestResult.Sent
    }

    override fun requestCurrentLocation(): RequestResult {
        _events.tryEmit(NaviEvent.CurrentLocation(defaults.origin.lat, defaults.origin.lon))
        return RequestResult.Sent
    }

    override fun requestWaypointInfo(): RequestResult {
        _events.tryEmit(NaviEvent.WaypointInfo(lastStops))
        return RequestResult.Sent
    }

    override fun requestRoute(destination: Place, stops: List<Place>): RequestResult {
        lastStops = stops
        if (autoConfirmRoute) {
            scope.launch {
                delay(confirmDelayMs)
                emit(NaviEvent.RouteStarted(destination.name, 0, 0))
                emit(NaviEvent.RoutingCompleted)
            }
        }
        return RequestResult.Sent
    }

    fun emit(event: NaviEvent) {
        _events.tryEmit(event)
    }

    /** 이벤트 목록을 순서대로 재생한다. stepDelayMs=0이면 즉시. */
    suspend fun play(events: List<NaviEvent>, stepDelayMs: Long = 0) {
        events.forEachIndexed { i, e ->
            if (i > 0 && stepDelayMs > 0) delay(stepDelayMs)
            emit(e)
        }
    }
}
