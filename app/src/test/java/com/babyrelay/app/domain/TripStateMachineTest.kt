package com.babyrelay.app.domain

import com.babyrelay.app.Fx
import com.babyrelay.app.domain.model.CloseReason
import com.babyrelay.app.domain.model.EventSource
import com.babyrelay.app.domain.model.PlannedStop
import com.babyrelay.app.domain.model.Registration
import com.babyrelay.app.domain.model.TripSession
import com.babyrelay.app.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripStateMachineTest {
    private val nav = EventSource.NAVI_SDK
    private val stop = PlannedStop(Fx.facility("s1", 0.5))

    private fun step(s: TripSession, e: TripEvent) = TripStateMachine.reduce(s, e, Fx.NOW + 1)

    private fun planned() = Fx.session(stops = listOf(stop))

    @Test fun `정상 전이 계획-이동-휴식-이동-도착-종료`() {
        var s = planned()
        s = step(s, TripEvent.StartTrip(nav)).also { assertEquals(Outcome.Applied, it.outcome) }.session
        assertEquals(TripState.DRIVING, s.state)
        s = step(s, TripEvent.WaypointArrived(0, "시설 s1", nav)).session
        assertEquals(TripState.STOPPED, s.state)
        assertEquals(0, s.currentStopIndex)
        s = step(s, TripEvent.Resume(EventSource.USER)).session
        assertEquals(TripState.DRIVING, s.state)
        assertNull(s.currentStopIndex)
        s = step(s, TripEvent.DestinationArrived(nav)).session
        assertEquals(TripState.ARRIVED, s.state)
        s = step(s, TripEvent.Close(noHandoff = false)).session
        assertEquals(TripState.CLOSED, s.state)
        assertEquals(CloseReason.COMPLETED, s.closeReason)
    }

    @Test fun `경유지 없이 목적지에 도착해도 된다`() {
        var s = step(Fx.session(), TripEvent.StartTrip(nav)).session
        s = step(s, TripEvent.DestinationArrived(nav)).session
        assertEquals(TripState.ARRIVED, s.state)
        assertTrue(s.arrivedStopIndexes.isEmpty())
    }

    @Test fun `경유지에 정차한 채 목적지 도착 이벤트가 와도 도착으로 간다`() {
        var s = step(planned(), TripEvent.StartTrip(nav)).session
        s = step(s, TripEvent.WaypointArrived(0, "x", nav)).session
        s = step(s, TripEvent.DestinationArrived(nav)).session
        assertEquals(TripState.ARRIVED, s.state)
    }

    @Test fun `출발 전 도착 이벤트는 거부되고 상태가 변하지 않는다`() {
        val r = step(Fx.session(), TripEvent.DestinationArrived(nav))
        assertTrue(r.outcome is Outcome.Rejected)
        assertEquals(TripState.PLANNING, r.session.state)
        val w = step(Fx.session(), TripEvent.WaypointArrived(0, "x", nav))
        assertTrue(w.outcome is Outcome.Rejected)
        assertEquals(TripState.PLANNING, w.session.state)
    }

    @Test fun `잘못된 전이 - 이동 중 재출발, 출발 전 종료, 도착 후 경유지 도착은 무시되거나 거부`() {
        val driving = step(Fx.session(), TripEvent.StartTrip(nav)).session
        assertTrue(step(driving, TripEvent.Resume(EventSource.USER)).outcome is Outcome.Ignored)
        assertTrue(step(Fx.session(), TripEvent.Close(false)).outcome is Outcome.Rejected)
        val arrived = step(driving, TripEvent.DestinationArrived(nav)).session
        assertTrue(step(arrived, TripEvent.WaypointArrived(0, "x", nav)).outcome is Outcome.Rejected)
        assertTrue(step(arrived, TripEvent.CancelPlanning()).outcome is Outcome.Rejected)
        assertEquals(TripState.ARRIVED, step(arrived, TripEvent.CancelPlanning()).session.state)
    }

    @Test fun `중복 경유지 도착 이벤트는 한 번만 반영된다`() {
        var s = step(planned(), TripEvent.StartTrip(nav)).session
        val first = step(s, TripEvent.WaypointArrived(0, "x", nav))
        assertEquals(Outcome.Applied, first.outcome)
        val second = step(first.session, TripEvent.WaypointArrived(0, "x", nav))
        assertTrue(second.outcome is Outcome.Ignored)
        assertEquals(first.session.state, second.session.state)
        assertEquals(setOf(0), second.session.arrivedStopIndexes)
        // 재출발 후 같은 경유지 이벤트가 또 와도 다시 정차 상태로 돌아가지 않는다.
        s = step(first.session, TripEvent.Resume(EventSource.USER)).session
        assertTrue(step(s, TripEvent.WaypointArrived(0, "x", nav)).outcome is Outcome.Ignored)
        assertEquals(TripState.DRIVING, step(s, TripEvent.WaypointArrived(0, "x", nav)).session.state)
    }

    @Test fun `중복 목적지 도착 이벤트는 무시된다`() {
        val driving = step(Fx.session(), TripEvent.StartTrip(nav)).session
        val a = step(driving, TripEvent.DestinationArrived(nav))
        val b = step(a.session, TripEvent.DestinationArrived(nav))
        assertTrue(b.outcome is Outcome.Ignored)
        assertEquals(a.session.arrivedAt, b.session.arrivedAt)
    }

    @Test fun `내비 경로 취소 - 이동 중이면 종료, 도착 후 정리 이벤트는 무시`() {
        val driving = step(planned(), TripEvent.StartTrip(nav)).session
        val cancelled = step(driving, TripEvent.RouteCancelled(nav))
        assertEquals(TripState.CLOSED, cancelled.session.state)
        assertEquals(CloseReason.ROUTE_CANCELLED, cancelled.session.closeReason)

        val arrived = step(driving, TripEvent.DestinationArrived(nav)).session
        val after = step(arrived, TripEvent.RouteCancelled(nav))
        assertTrue(after.outcome is Outcome.Ignored)
        assertEquals(TripState.ARRIVED, after.session.state)
    }

    @Test fun `시작 전 취소`() {
        val r = step(Fx.session(), TripEvent.CancelPlanning())
        assertEquals(TripState.CLOSED, r.session.state)
        assertEquals(CloseReason.CANCELLED_BEFORE_START, r.session.closeReason)
        assertTrue(step(r.session, TripEvent.CancelPlanning()).outcome is Outcome.Ignored)
    }

    @Test fun `인계 없이 종료`() {
        val arrived = step(step(Fx.session(), TripEvent.StartTrip(nav)).session, TripEvent.DestinationArrived(nav)).session
        val closed = step(arrived, TripEvent.Close(noHandoff = true)).session
        assertEquals(CloseReason.COMPLETED_NO_HANDOFF, closed.closeReason)
    }

    @Test fun `수동 도착 처리는 USER 출처로 기록되고 자동 이벤트와 구분된다`() {
        val driving = step(Fx.session(), TripEvent.StartTrip(nav)).session
        val manual = step(driving, TripEvent.DestinationArrived(EventSource.USER)).session
        assertEquals(EventSource.USER, manual.log.last().source)
        assertEquals(false, manual.log.last().source.automatic)
        assertEquals(true, manual.log.first().source.automatic)
    }

    @Test fun `경유지 등록 상태 변경은 첫 경유지에만 적용되고 같은 값은 무시`() {
        val s = planned()
        val a = step(s, TripEvent.RegistrationChanged(Registration.CONFIRMED, nav))
        assertEquals(Registration.CONFIRMED, a.session.plannedStops.first().registration)
        assertTrue(step(a.session, TripEvent.RegistrationChanged(Registration.CONFIRMED, nav)).outcome is Outcome.Ignored)
        assertTrue(step(Fx.session(), TripEvent.RegistrationChanged(Registration.CONFIRMED, nav)).outcome is Outcome.Ignored)
    }

    @Test fun `거부된 이벤트도 로그에 남고 갱신 시각은 바뀌지 않는다`() {
        val r = step(Fx.session(), TripEvent.DestinationArrived(nav))
        assertTrue(r.session.log.last().outcome.startsWith("거부"))
        assertEquals(Fx.NOW, r.session.updatedAt)
    }

    @Test fun `로그는 상한을 넘지 않는다`() {
        var s = Fx.session()
        repeat(200) { s = step(s, TripEvent.DestinationArrived(nav)).session }
        assertTrue(s.log.size <= 60)
    }
}
