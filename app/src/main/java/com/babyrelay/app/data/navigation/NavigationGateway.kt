package com.babyrelay.app.data.navigation

import com.babyrelay.app.domain.model.NavMode
import com.babyrelay.app.domain.model.Place
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 내비게이션에서 앱으로 들어오는 사실. SDK 타입과 분리된 프로젝트 내부 표현이다. */
sealed interface NaviEvent {
    val label: String

    data class RouteStarted(val name: String, val distanceM: Int, val durationS: Int) : NaviEvent {
        override val label get() = "RouteStarted($name)"
    }

    data object RoutingCompleted : NaviEvent {
        override val label get() = "RoutingCompleted"
    }

    data object RouteCancelled : NaviEvent {
        override val label get() = "RouteCancelled"
    }

    /** index: FIRST=0, SECOND=1. 알 수 없으면 null */
    data class WaypointArrived(val index: Int?, val name: String, val address: String) : NaviEvent {
        override val label get() = "WaypointArrived($index, $name)"
    }

    data class DestinationArrived(val name: String, val address: String) : NaviEvent {
        override val label get() = "DestinationArrived($name)"
    }

    data class WaypointChanged(val index: Int?, val added: Boolean) : NaviEvent {
        override val label get() = "WaypointChanged($index, ${if (added) "ADD" else "REMOVE"})"
    }

    data class WaypointInfo(val waypoints: List<Place>) : NaviEvent {
        override val label get() = "WaypointInfo(${waypoints.size})"
    }

    data class DestinationInfo(val place: Place) : NaviEvent {
        override val label get() = "DestinationInfo(${place.name})"
    }

    data class CurrentLocation(val lat: Double, val lon: Double) : NaviEvent {
        override val label get() = "CurrentLocation"
    }

    /** 내비가 알려준 목적지까지 예상 소요(초). 내비가 주지 않으면 이벤트도 오지 않는다. */
    data class DrivingProgress(val destinationRemainSec: Int) : NaviEvent {
        override val label get() = "DrivingProgress(${destinationRemainSec}s)"
    }

    data class Error(val code: String) : NaviEvent {
        override val label get() = "Error($code)"
    }
}

enum class Connection { IDLE, CONNECTING, CONNECTED, FAILED }

data class GatewayStatus(val connection: Connection, val detail: String? = null)

sealed interface RequestResult {
    /** 요청을 내비에 보냈다. 성공 여부는 이후 이벤트로만 알 수 있다. */
    data object Sent : RequestResult
    data object NotConnected : RequestResult
    data class Failed(val reason: String) : RequestResult
}

/** 내비게이션 연동 경계. 구현: PleosNaviAdapter(실제 SDK), ReplayNaviAdapter(재생). */
interface NavigationGateway {
    val mode: NavMode
    val status: StateFlow<GatewayStatus>
    val events: SharedFlow<NaviEvent>

    fun connect()
    fun disconnect()

    fun requestDestinationInfo(): RequestResult
    fun requestCurrentLocation(): RequestResult
    fun requestWaypointInfo(): RequestResult

    /** stops가 비어 있으면 목적지만 요청한다. */
    fun requestRoute(destination: Place, stops: List<Place>): RequestResult
}
