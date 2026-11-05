package com.babyrelay.app.data.navigation

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointCmdType
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.babyrelay.app.domain.model.NavMode
import com.babyrelay.app.domain.model.Place
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pleos NaviHelper SDK v3.0.5 어댑터. 메서드/타입은 공식 문서와 SDK 배포 소스(sources.jar)에서 확인한 시그니처만 사용한다.
 * 이 클래스가 실제 Pleos Connect Emulator에서 동작하는지는 docs/development/sdk-integration.md의 검증 기록을 따른다.
 */
class PleosNaviAdapter(context: Context) : NavigationGateway {
    override val mode = NavMode.PLEOS

    private val helper = NaviHelper(context.applicationContext)
    private val _status = MutableStateFlow(GatewayStatus(Connection.IDLE))
    override val status: StateFlow<GatewayStatus> = _status.asStateFlow()
    private val _events = MutableSharedFlow<NaviEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            log("onRouteStarted name=${routeStartInfo.name}")
            _events.tryEmit(NaviEvent.RouteStarted(routeStartInfo.name, routeStartInfo.distance, routeStartInfo.duration))
        }

        override fun onRoutingCompleted() {
            log("onRoutingCompleted")
            _events.tryEmit(NaviEvent.RoutingCompleted)
        }

        // 실제 Pleos 에뮬레이터는 onRoutingCompleted 대신 deprecated onRouteEnded를 호출한다(2026-11-05 관찰).
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onRouteEnded() {
            log("onRouteEnded")
            _events.tryEmit(NaviEvent.RoutingCompleted)
        }

        override fun onRouteCancelled() {
            log("onRouteCancelled")
            _events.tryEmit(NaviEvent.RouteCancelled)
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            log("onWayPointArrived index=${waypointArrivedInfo.index} name=${waypointArrivedInfo.name}")
            _events.tryEmit(
                NaviEvent.WaypointArrived(waypointArrivedInfo.index.toIndexOrNull(), waypointArrivedInfo.name, waypointArrivedInfo.address),
            )
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            log("onDestinationArrived name=${destinationArrivedInfo.name}")
            _events.tryEmit(NaviEvent.DestinationArrived(destinationArrivedInfo.name, destinationArrivedInfo.address))
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            log("onWaypointChanged index=${waypointChangedInfo.index} cmd=${waypointChangedInfo.cmdType}")
            _events.tryEmit(
                NaviEvent.WaypointChanged(waypointChangedInfo.index.toIndexOrNull(), waypointChangedInfo.cmdType == WaypointCmdType.ADD),
            )
        }

        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {
            log("onWayPointInfo size=${waypointInfoList.size}")
            _events.tryEmit(
                NaviEvent.WaypointInfo(
                    waypointInfoList.map { Place(it.poiId, it.name, it.address, it.latitude, it.longitude, it.poiSubId ?: "") },
                ),
            )
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            log("onDestinationInfo name='${destinationInfo.name}'")
            // 실제 Pleos 에뮬레이터는 목적지가 없을 때도 빈 이름/좌표로 이 콜백을 보낸다(2026-11-05 확인). 목적지로 취급하지 않는다.
            if (destinationInfo.name.isBlank() || (destinationInfo.latitude == 0.0 && destinationInfo.longitude == 0.0)) return
            _events.tryEmit(
                NaviEvent.DestinationInfo(
                    Place(
                        destinationInfo.poiId, destinationInfo.name, destinationInfo.address,
                        destinationInfo.latitude, destinationInfo.longitude, destinationInfo.poiSubId ?: "",
                    ),
                ),
            )
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            // 위치는 추천 계산에만 쓰고 저장·로그하지 않는다.
            _events.tryEmit(NaviEvent.CurrentLocation(currentLocationInfo.latitude, currentLocationInfo.longitude))
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            _events.tryEmit(NaviEvent.DrivingProgress(drivingInfo.destination.duration))
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            log("onNavigationStatus $status")
        }

        override fun onError(errorCode: NaviErrorCode) {
            log("onError $errorCode")
            _events.tryEmit(NaviEvent.Error(errorCode.name))
        }
    }

    override fun connect() {
        val current = _status.value.connection
        if (current == Connection.CONNECTING || current == Connection.CONNECTED) return
        _status.value = GatewayStatus(Connection.CONNECTING)
        try {
            helper.initialize(
                onConnected = {
                    helper.addListener(listener)
                    _status.value = GatewayStatus(Connection.CONNECTED, "NaviHelper 3.0.5")
                    log("connected")
                },
                onFailed = { t ->
                    _status.value = GatewayStatus(Connection.FAILED, t.message ?: t.javaClass.simpleName)
                    log("connect failed: ${t.message}")
                },
            )
        } catch (t: Throwable) {
            _status.value = GatewayStatus(Connection.FAILED, t.message ?: t.javaClass.simpleName)
        }
    }

    override fun disconnect() {
        try {
            helper.removeListener(listener)
            helper.release()
        } catch (t: Throwable) {
            log("release error: ${t.message}")
        }
        _status.value = GatewayStatus(Connection.IDLE)
    }

    override fun requestDestinationInfo() = guarded { helper.getDestinationInfo() }
    override fun requestCurrentLocation() = guarded { helper.getCurrentLocationInfo() }
    override fun requestWaypointInfo() = guarded { helper.getWaypointInfo() }

    override fun requestRoute(destination: Place, stops: List<Place>): RequestResult = guarded {
        val dest = RouteInfo(
            latitude = destination.lat,
            longitude = destination.lon,
            poiName = destination.name,
            poiId = destination.id,
            address = destination.address,
            poiSubId = destination.poiSubId,
        )
        if (stops.isEmpty()) {
            helper.requestRoute(dest)
        } else {
            val waypoints = stops.take(2).mapIndexed { i, p ->
                RequestWaypointInfo(
                    latitude = p.lat,
                    longitude = p.lon,
                    poiName = p.name,
                    waypointIndex = if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND,
                    poiId = p.id,
                    address = p.address,
                    poiSubId = p.poiSubId,
                )
            }
            helper.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination = dest, waypoints = waypoints))
        }
    }

    private inline fun guarded(block: () -> Unit): RequestResult {
        if (_status.value.connection != Connection.CONNECTED) return RequestResult.NotConnected
        return try {
            block()
            RequestResult.Sent
        } catch (e: SecurityException) {
            RequestResult.Failed("권한 없음: ${e.message}")
        } catch (t: Throwable) {
            RequestResult.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun WaypointIndex.toIndexOrNull(): Int? = when (this) {
        WaypointIndex.FIRST -> 0
        WaypointIndex.SECOND -> 1
        WaypointIndex.ALL -> null
    }

    private fun log(msg: String) = Log.i("BabyRelay.Pleos", msg)
}
