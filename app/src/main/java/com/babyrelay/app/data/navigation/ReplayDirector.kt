package com.babyrelay.app.data.navigation

import com.babyrelay.app.domain.LatLon
import com.babyrelay.app.domain.model.Place
import com.babyrelay.app.domain.model.TripSession

/** 시연 기본값. 값은 facilities_snapshot.json의 demoOrigin/destinations에서 온다. */
data class ReplayDefaults(val origin: LatLon, val destinations: List<Place>)

/** 개발 패널에서 고를 수 있는 재생 시나리오. 출시 사용자용 기능이 아니다. */
enum class ReplayScenario(val label: String, val hint: String) {
    PLAN("출발 계획", "현재 위치와 내비 목적지를 주입"),
    SELECT_STOP("경유지 등록 확인", "내비가 경로·경유지를 확인하는 이벤트"),
    WAYPOINT_ARRIVAL("경유지 도착", "첫 번째 경유지 도착 이벤트"),
    DESTINATION_ARRIVAL("목적지 도착", "최종 목적지 도착 이벤트"),
    NO_DATA("데이터 없음", "시설 데이터가 없는 지역 목적지"),
    DUPLICATE_ARRIVAL("중복 도착 이벤트", "같은 경유지·목적지 이벤트를 두 번 전송"),
    ROUTE_CANCEL("경로 취소", "내비 경로 취소 이벤트"),
}

object ReplayDirector {
    /** 현재 세션 문맥(계획된 경유지 이름 등)을 이용해 시나리오의 이벤트 목록을 만든다. */
    fun eventsFor(scenario: ReplayScenario, session: TripSession?, defaults: ReplayDefaults): List<NaviEvent> {
        val stop = session?.plannedStops?.firstOrNull()?.facility
        val dest = session?.destination ?: defaults.destinations.first()
        return when (scenario) {
            ReplayScenario.PLAN -> listOf(
                NaviEvent.CurrentLocation(defaults.origin.lat, defaults.origin.lon),
                NaviEvent.DestinationInfo(defaults.destinations.first()),
            )
            ReplayScenario.SELECT_STOP -> listOfNotNull(
                NaviEvent.RouteStarted(dest.name, 0, 0),
                NaviEvent.RoutingCompleted,
                stop?.let { NaviEvent.WaypointInfo(listOf(it.toPlace())) },
            )
            ReplayScenario.WAYPOINT_ARRIVAL -> listOf(
                NaviEvent.WaypointArrived(0, stop?.name ?: "경유지", stop?.address ?: ""),
            )
            ReplayScenario.DESTINATION_ARRIVAL -> listOf(NaviEvent.DestinationArrived(dest.name, dest.address))
            ReplayScenario.NO_DATA -> listOf(
                NaviEvent.CurrentLocation(defaults.origin.lat, defaults.origin.lon),
                NaviEvent.DestinationInfo(defaults.destinations.last()),
            )
            ReplayScenario.DUPLICATE_ARRIVAL -> {
                val w = NaviEvent.WaypointArrived(0, stop?.name ?: "경유지", stop?.address ?: "")
                val d = NaviEvent.DestinationArrived(dest.name, dest.address)
                listOf(w, w, d, d)
            }
            ReplayScenario.ROUTE_CANCEL -> listOf(NaviEvent.RouteCancelled)
        }
    }
}
