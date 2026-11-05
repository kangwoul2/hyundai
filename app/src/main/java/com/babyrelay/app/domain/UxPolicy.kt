package com.babyrelay.app.domain

/** 플랫폼 주행 제한 상태의 출처. */
enum class UxSource(val label: String) {
    /** AAOS CarUxRestrictionsManager가 알려준 실제 값 */
    PLATFORM("플랫폼"),
    /** 개발 패널에서 사람이 지정한 시뮬레이션 (실제 차량 값이 아님) */
    SIMULATED("시뮬레이션"),
    /** Car API를 쓸 수 없음 */
    UNAVAILABLE("확인 불가"),
}

/**
 * CarUxRestrictions의 isRequiresDistractionOptimization 값.
 * null은 값을 모른다는 뜻이며 안전 쪽(제한)으로 처리한다.
 */
data class UxRestrictionState(val requiresDistractionOptimization: Boolean?, val source: UxSource) {
    val restricted: Boolean get() = requiresDistractionOptimization != false

    companion object {
        val Unknown = UxRestrictionState(null, UxSource.UNAVAILABLE)
    }
}

data class UiAccess(
    val canPlan: Boolean,
    val canSelectStop: Boolean,
    val canRecord: Boolean,
    val canEditHandoff: Boolean,
    val canShowQr: Boolean,
    val reason: String?,
)

/**
 * 주행 제한 정책. TripState(내비 사실)가 아니라 플랫폼의 UX 제한 상태만으로 조작 가능 여부를 정한다.
 * 경유지 도착 이벤트가 왔다는 이유로 정차/주차라고 가정하지 않는다.
 */
object UxPolicy {
    fun evaluate(state: UxRestrictionState): UiAccess {
        val free = !state.restricted
        val reason = when {
            free -> null
            state.requiresDistractionOptimization == null -> "주행 제한 상태를 확인할 수 없어 조작을 제한합니다"
            else -> "주행 제한 중에는 조작할 수 없습니다. 안전한 곳에 정차한 뒤 사용하세요"
        }
        return UiAccess(
            canPlan = free,
            canSelectStop = free,
            canRecord = free,
            canEditHandoff = free,
            canShowQr = free,
            reason = reason,
        )
    }
}
