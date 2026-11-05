package com.babyrelay.app.data.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.babyrelay.app.domain.UxRestrictionState
import com.babyrelay.app.domain.UxSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.reflect.Proxy

interface UxRestrictionSource {
    val state: StateFlow<UxRestrictionState>
    fun start()
    fun stop()
}

/** REPLAY 모드용. 개발 패널에서 사람이 지정한 값이며 실제 차량 상태가 아니다. */
class SimulatedUxSource(initialRestricted: Boolean = false) : UxRestrictionSource {
    private val _state = MutableStateFlow(UxRestrictionState(initialRestricted, UxSource.SIMULATED))
    override val state: StateFlow<UxRestrictionState> = _state.asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
    fun setRestricted(restricted: Boolean) {
        _state.value = UxRestrictionState(restricted, UxSource.SIMULATED)
    }
}

/**
 * REPLAY 모드용 선택 소스: 시뮬레이션 값 또는 실제 AAOS 플랫폼 값(Pleos 연동과 무관하게 CarUxRestrictions만 읽는다).
 * 플랫폼 값을 고르면 화면 제한이 차량(에뮬레이터) 주행 상태를 따른다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableUxSource(
    private val simulated: SimulatedUxSource,
    private val platform: UxRestrictionSource,
    scope: CoroutineScope,
) : UxRestrictionSource {
    private val usePlatformFlag = MutableStateFlow(false)
    val usingPlatform: StateFlow<Boolean> = usePlatformFlag.asStateFlow()
    override val state: StateFlow<UxRestrictionState> = usePlatformFlag
        .flatMapLatest { if (it) platform.state else simulated.state }
        .stateIn(scope, SharingStarted.Eagerly, simulated.state.value)

    fun setUsePlatform(use: Boolean) {
        if (use == usePlatformFlag.value) return
        if (use) platform.start() else platform.stop()
        usePlatformFlag.value = use
    }

    fun setSimulatedRestricted(restricted: Boolean) = simulated.setRestricted(restricted)
    override fun start() = Unit
    override fun stop() = platform.stop()
}

/**
 * AAOS CarUxRestrictionsManager를 리플렉션으로 사용한다(android.car 스텁이 공개 SDK에 없기 때문).
 * 읽는 값은 CarUxRestrictions.isRequiresDistractionOptimization 하나뿐이다.
 * 사용할 수 없으면 UNAVAILABLE(=제한 상태로 취급)로 둔다. 실제 AAOS 에뮬레이터 동작은 검증 기록을 따른다.
 */
class AaosUxRestrictionSource(private val context: Context) : UxRestrictionSource {
    private val _state = MutableStateFlow(UxRestrictionState.Unknown)
    override val state: StateFlow<UxRestrictionState> = _state.asStateFlow()

    private var car: Any? = null
    private var manager: Any? = null
    private var managerListener: Any? = null // 프록시 참조 유지(GC 방지)

    override fun start() {
        try {
            val carClass = Class.forName("android.car.Car")
            val lifecycleIface = Class.forName("android.car.Car\$CarServiceLifecycleListener")
            val proxy = Proxy.newProxyInstance(lifecycleIface.classLoader, arrayOf(lifecycleIface)) { _, method, args ->
                if (method.name == "onLifecycleChanged" && args != null && args.size >= 2) {
                    val ready = args[1] as? Boolean ?: false
                    val c = args[0]
                    if (ready && c != null) onCarReady(c) else markUnavailable("Car service down")
                }
                null
            }
            val create = carClass.getMethod(
                "createCar", Context::class.java, Handler::class.java, Long::class.javaPrimitiveType, lifecycleIface,
            )
            car = create.invoke(null, context.applicationContext, Handler(Looper.getMainLooper()), 0L, proxy)
        } catch (t: Throwable) {
            markUnavailable("Car API unavailable: ${t.javaClass.simpleName}")
        }
    }

    private fun onCarReady(c: Any) {
        try {
            val serviceName = "uxrestriction" // Car.CAR_UX_RESTRICTION_SERVICE
            val mgr = Class.forName("android.car.Car").getMethod("getCarManager", String::class.java).invoke(c, serviceName)
                ?: return markUnavailable("uxrestriction manager null")
            manager = mgr
            val mgrClass = Class.forName("android.car.drivingstate.CarUxRestrictionsManager")
            val listenerIface = Class.forName("android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener")
            val l = Proxy.newProxyInstance(listenerIface.classLoader, arrayOf(listenerIface)) { _, method, args ->
                if (method.name == "onUxRestrictionsChanged" && args != null && args.isNotEmpty()) publish(args[0])
                null
            }
            managerListener = l
            mgrClass.getMethod("registerListener", listenerIface).invoke(mgr, l)
            publish(mgrClass.getMethod("getCurrentCarUxRestrictions").invoke(mgr))
        } catch (t: Throwable) {
            markUnavailable("uxrestriction init failed: ${t.javaClass.simpleName}")
        }
    }

    private fun publish(restrictions: Any?) {
        if (restrictions == null) return markUnavailable("restrictions null")
        try {
            val req = restrictions.javaClass.getMethod("isRequiresDistractionOptimization").invoke(restrictions) as Boolean
            _state.value = UxRestrictionState(req, UxSource.PLATFORM)
            Log.i(TAG, "requiresDistractionOptimization=$req")
        } catch (t: Throwable) {
            markUnavailable("read failed: ${t.javaClass.simpleName}")
        }
    }

    private fun markUnavailable(why: String) {
        Log.w(TAG, why)
        _state.value = UxRestrictionState.Unknown
    }

    override fun stop() {
        try {
            manager?.let { mgr ->
                // CarUxRestrictionsManager.unregisterListener()는 등록된 리스너를 해제한다(인자 없음).
                runCatching { Class.forName("android.car.drivingstate.CarUxRestrictionsManager").getMethod("unregisterListener").invoke(mgr) }
            }
            car?.let { Class.forName("android.car.Car").getMethod("disconnect").invoke(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "stop: ${t.javaClass.simpleName}")
        } finally {
            manager = null
            managerListener = null
            car = null
        }
    }

    private companion object {
        const val TAG = "BabyRelay.Ux"
    }
}
