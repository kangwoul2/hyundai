package com.babyrelay.app.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.babyrelay.app.data.navigation.Connection
import com.babyrelay.app.data.navigation.GatewayStatus
import com.babyrelay.app.data.navigation.NavigationGateway
import com.babyrelay.app.data.navigation.PleosNaviAdapter
import com.babyrelay.app.data.navigation.ReplayDirector
import com.babyrelay.app.data.navigation.ReplayNaviAdapter
import com.babyrelay.app.data.navigation.ReplayScenario
import com.babyrelay.app.data.platform.AaosUxRestrictionSource
import com.babyrelay.app.data.platform.SimulatedUxSource
import com.babyrelay.app.data.platform.UxRestrictionSource
import com.babyrelay.app.domain.UiAccess
import com.babyrelay.app.domain.UxPolicy
import com.babyrelay.app.domain.UxRestrictionState
import com.babyrelay.app.domain.model.NavMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 화면이 그리는 전체 상태. */
data class UiModel(
    val ctl: ControllerState = ControllerState(),
    val mode: NavMode = NavMode.REPLAY,
    val gateway: GatewayStatus = GatewayStatus(Connection.IDLE),
    val ux: UxRestrictionState = UxRestrictionState.Unknown,
    val access: UiAccess = UxPolicy.evaluate(UxRestrictionState.Unknown),
    val connecting: Boolean = true,
    /** PLEOS 연결 실패로 REPLAY로 전환한 이유. 화면에 계속 표시한다. */
    val fallbackReason: String? = null,
)

private class Runtime(
    val controller: TripController,
    val gateway: NavigationGateway,
    val uxSource: UxRestrictionSource,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as BabyRelayApp).container
    private val runtime = MutableStateFlow<Runtime?>(null)
    private val fallback = MutableStateFlow<String?>(null)
    private val connecting = MutableStateFlow(true)
    private var watchdog: Job? = null

    val ui: StateFlow<UiModel> = runtime.flatMapLatest { rt ->
        if (rt == null) {
            kotlinx.coroutines.flow.flowOf(UiModel())
        } else {
            combine(rt.controller.state, rt.gateway.status, rt.uxSource.state, fallback, connecting) { ctl, gw, ux, fb, c ->
                UiModel(ctl, rt.gateway.mode, gw, ux, UxPolicy.evaluate(ux), c, fb)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiModel())

    init {
        startPleosWithFallback()
    }

    val controller: TripController? get() = runtime.value?.controller

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun clearToast() { _toast.value = null }

    /** 화면 조작을 컨트롤러에 전달하고, 주행 제한 등으로 거부되면 이유를 안내한다. */
    fun run(action: TripController.() -> ActionResult) {
        when (val r = controller?.action()) {
            is ActionResult.Blocked -> _toast.value = r.reason
            is ActionResult.Invalid -> _toast.value = r.reason
            else -> Unit
        }
    }
    val replayAvailable: Boolean get() = runtime.value?.gateway is ReplayNaviAdapter

    // ------------------------------------------------------------ 모드 전환

    /** 먼저 실제 Pleos 연동을 시도하고, 연결되지 않으면 REPLAY로 전환한다(화면에 이유를 표시). */
    private fun startPleosWithFallback() {
        connecting.value = true
        fallback.value = null
        val ux = AaosUxRestrictionSource(getApplication())
        val gateway = PleosNaviAdapter(getApplication())
        launchRuntime(gateway, ux)
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            var waited = 0L
            while (waited < PLEOS_CONNECT_TIMEOUT_MS) {
                val st = gateway.status.value
                if (st.connection == Connection.CONNECTED) {
                    connecting.value = false
                    return@launch
                }
                if (st.connection == Connection.FAILED) break
                delay(200)
                waited += 200
            }
            val why = gateway.status.value.detail ?: "시간 초과"
            switchToReplay("Pleos 내비 서비스에 연결하지 못했습니다 ($why)")
        }
    }

    fun switchToReplay(reason: String? = null) {
        watchdog?.cancel()
        runtime.value?.let { stopRuntime(it) }
        fallback.value = reason
        val scope = viewModelScope
        val replay = ReplayNaviAdapter(scope, container.replayDefaults)
        launchRuntime(replay, SimulatedUxSource(false))
        connecting.value = false
    }

    fun retryPleos() {
        watchdog?.cancel()
        runtime.value?.let { stopRuntime(it) }
        startPleosWithFallback()
    }

    private fun launchRuntime(gateway: NavigationGateway, ux: UxRestrictionSource) {
        val ctl = TripController(
            gateway = gateway,
            store = container.store,
            facilities = container.facilities,
            ux = ux.state,
            scope = viewModelScope,
        )
        runtime.value = Runtime(ctl, gateway, ux)
        ux.start()
        viewModelScope.launch { ctl.start() }
    }

    private fun stopRuntime(rt: Runtime) {
        rt.uxSource.stop()
        rt.controller.stop()
    }

    // ------------------------------------------------------------ REPLAY 개발 패널

    fun playScenario(scenario: ReplayScenario) {
        val rt = runtime.value ?: return
        val replay = rt.gateway as? ReplayNaviAdapter ?: return
        val events = ReplayDirector.eventsFor(scenario, rt.controller.state.value.session, container.replayDefaults)
        viewModelScope.launch { replay.play(events) }
    }

    fun setSimulatedRestricted(restricted: Boolean) {
        (runtime.value?.uxSource as? SimulatedUxSource)?.setRestricted(restricted)
    }

    fun setAutoConfirm(enabled: Boolean) {
        (runtime.value?.gateway as? ReplayNaviAdapter)?.autoConfirmRoute = enabled
    }

    override fun onCleared() {
        runtime.value?.let { stopRuntime(it) }
        super.onCleared()
    }

    private companion object {
        const val PLEOS_CONNECT_TIMEOUT_MS = 6_000L
    }
}
