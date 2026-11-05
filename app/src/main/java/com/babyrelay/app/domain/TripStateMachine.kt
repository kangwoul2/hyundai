package com.babyrelay.app.domain

import com.babyrelay.app.domain.model.CloseReason
import com.babyrelay.app.domain.model.EventSource
import com.babyrelay.app.domain.model.LogEntry
import com.babyrelay.app.domain.model.Registration
import com.babyrelay.app.domain.model.TripSession
import com.babyrelay.app.domain.model.TripState

/** 상태 머신 입력. 내비 이벤트(자동)와 사용자 조작(수동)을 같은 형식으로 다룬다. 이름은 프로젝트 내부용이다. */
sealed interface TripEvent {
    val source: EventSource
    val label: String

    data class StartTrip(override val source: EventSource) : TripEvent {
        override val label get() = "StartTrip"
    }

    /** index: 계획 경유지 순번(0부터). 내비가 순번을 주지 않으면 null. */
    data class WaypointArrived(val index: Int?, val name: String, override val source: EventSource) : TripEvent {
        override val label get() = "WaypointArrived(${index ?: "?"}, $name)"
    }

    data class Resume(override val source: EventSource) : TripEvent {
        override val label get() = "Resume"
    }

    data class DestinationArrived(override val source: EventSource) : TripEvent {
        override val label get() = "DestinationArrived"
    }

    data class RouteCancelled(override val source: EventSource) : TripEvent {
        override val label get() = "RouteCancelled"
    }

    data class CancelPlanning(override val source: EventSource = EventSource.USER) : TripEvent {
        override val label get() = "CancelPlanning"
    }

    data class Close(val noHandoff: Boolean, override val source: EventSource = EventSource.USER) : TripEvent {
        override val label get() = if (noHandoff) "Close(noHandoff)" else "Close"
    }

    data class RegistrationChanged(val registration: Registration, override val source: EventSource) : TripEvent {
        override val label get() = "RegistrationChanged($registration)"
    }
}

sealed interface Outcome {
    data object Applied : Outcome
    /** 이미 반영된 사실의 반복 등 무해한 이벤트. 상태는 바뀌지 않는다. */
    data class Ignored(val reason: String) : Outcome
    /** 현재 상태에서 허용되지 않는 전이. 상태는 바뀌지 않는다. */
    data class Rejected(val reason: String) : Outcome
}

data class ReduceResult(val session: TripSession, val outcome: Outcome)

/**
 * 순수 함수형 상태 머신.
 * PLANNING → DRIVING → (STOPPED ↔ DRIVING)* → ARRIVED → CLOSED
 *
 * 주의: 이 상태는 "내비가 알려준 이동 사실"만 나타낸다. 화면 조작 가능 여부는 UxPolicy(CarUxRestrictions)가 따로 결정한다.
 */
object TripStateMachine {
    private const val MAX_LOG = 60

    fun reduce(session: TripSession, event: TripEvent, now: Long): ReduceResult {
        val (next, outcome) = apply(session, event, now)
        val outcomeText = when (outcome) {
            Outcome.Applied -> "적용"
            is Outcome.Ignored -> "무시: ${outcome.reason}"
            is Outcome.Rejected -> "거부: ${outcome.reason}"
        }
        val entry = LogEntry(now, event.label, event.source, outcomeText)
        val withLog = next.copy(
            log = (next.log + entry).takeLast(MAX_LOG),
            updatedAt = if (outcome == Outcome.Applied) now else session.updatedAt,
        )
        return ReduceResult(withLog, outcome)
    }

    private fun apply(s: TripSession, e: TripEvent, now: Long): Pair<TripSession, Outcome> = when (e) {
        is TripEvent.StartTrip -> when (s.state) {
            TripState.PLANNING -> s.copy(state = TripState.DRIVING, startedAt = now) to Outcome.Applied
            TripState.DRIVING, TripState.STOPPED -> s to Outcome.Ignored("이미 출발함")
            else -> s to Outcome.Rejected("${s.state}에서는 출발할 수 없음")
        }

        is TripEvent.WaypointArrived -> arrived(s, e)

        is TripEvent.Resume -> when (s.state) {
            TripState.STOPPED -> s.copy(state = TripState.DRIVING, currentStopIndex = null) to Outcome.Applied
            TripState.DRIVING -> s to Outcome.Ignored("이미 이동 중")
            else -> s to Outcome.Rejected("${s.state}에서는 재출발할 수 없음")
        }

        is TripEvent.DestinationArrived -> when (s.state) {
            TripState.DRIVING, TripState.STOPPED ->
                s.copy(state = TripState.ARRIVED, currentStopIndex = null, arrivedAt = now) to Outcome.Applied
            TripState.ARRIVED, TripState.CLOSED -> s to Outcome.Ignored("중복 도착 이벤트")
            TripState.PLANNING -> s to Outcome.Rejected("출발 전에는 도착할 수 없음")
        }

        is TripEvent.RouteCancelled -> when (s.state) {
            TripState.DRIVING, TripState.STOPPED ->
                s.copy(state = TripState.CLOSED, closeReason = CloseReason.ROUTE_CANCELLED, currentStopIndex = null) to Outcome.Applied
            // 도착 후 내비가 경로를 정리하는 것은 정상 동작이다.
            TripState.ARRIVED, TripState.CLOSED -> s to Outcome.Ignored("도착/종료 후 경로 정리")
            TripState.PLANNING -> s to Outcome.Ignored("출발 전 경로 취소")
        }

        is TripEvent.CancelPlanning -> when (s.state) {
            TripState.PLANNING -> s.copy(state = TripState.CLOSED, closeReason = CloseReason.CANCELLED_BEFORE_START) to Outcome.Applied
            TripState.CLOSED -> s to Outcome.Ignored("이미 종료됨")
            else -> s to Outcome.Rejected("출발 후에는 계획 취소 불가")
        }

        is TripEvent.Close -> when (s.state) {
            TripState.ARRIVED -> s.copy(
                state = TripState.CLOSED,
                closeReason = if (e.noHandoff) CloseReason.COMPLETED_NO_HANDOFF else CloseReason.COMPLETED,
            ) to Outcome.Applied
            TripState.CLOSED -> s to Outcome.Ignored("이미 종료됨")
            else -> s to Outcome.Rejected("도착 전에는 종료할 수 없음")
        }

        is TripEvent.RegistrationChanged -> when {
            s.state == TripState.ARRIVED || s.state == TripState.CLOSED -> s to Outcome.Ignored("종료 단계")
            s.plannedStops.isEmpty() -> s to Outcome.Ignored("계획된 경유지 없음")
            s.plannedStops.first().registration == e.registration -> s to Outcome.Ignored("동일 상태")
            else -> s.copy(
                plannedStops = s.plannedStops.mapIndexed { i, p -> if (i == 0) p.copy(registration = e.registration) else p },
            ) to Outcome.Applied
        }
    }

    private fun arrived(s: TripSession, e: TripEvent.WaypointArrived): Pair<TripSession, Outcome> {
        val idx = e.index
        if (idx != null && idx in s.arrivedStopIndexes) return s to Outcome.Ignored("중복 경유지 도착 이벤트")
        return when (s.state) {
            TripState.DRIVING, TripState.STOPPED -> {
                val known = idx != null && idx in s.plannedStops.indices
                s.copy(
                    state = TripState.STOPPED,
                    currentStopIndex = if (known) idx else null,
                    arrivedStopIndexes = if (idx != null) s.arrivedStopIndexes + idx else s.arrivedStopIndexes,
                ) to Outcome.Applied
            }
            TripState.PLANNING -> s to Outcome.Rejected("출발 전에는 경유지에 도착할 수 없음")
            else -> s to Outcome.Rejected("${s.state}에서는 경유지 도착을 받을 수 없음")
        }
    }
}
