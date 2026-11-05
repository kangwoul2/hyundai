package com.babyrelay.app.domain

import com.babyrelay.app.Fx
import com.babyrelay.app.domain.model.CareKind
import com.babyrelay.app.domain.model.CareRecord
import com.babyrelay.app.domain.model.CarryItem
import com.babyrelay.app.domain.model.EventSource
import com.babyrelay.app.domain.model.NavMode
import com.babyrelay.app.domain.model.PlannedStop
import com.babyrelay.app.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoffAndUxTest {
    private fun rec(id: String, kind: CareKind, minutesBeforeNow: Long, stop: Int? = 0) =
        CareRecord(id, kind, stop, Fx.NOW - minutesBeforeNow * 60_000, EventSource.USER, NavMode.REPLAY)

    private val arrivedSession = Fx.session(
        state = TripState.ARRIVED,
        stops = listOf(PlannedStop(Fx.facility("s1", 0.5))),
        items = setOf(CarryItem.WIPES, CarryItem.DIAPER),
    )

    @Test fun `인계 카드는 마지막 수유와 기저귀 교환 시각, 전달 물품을 담는다`() {
        val s = arrivedSession.copy(
            records = listOf(
                rec("1", CareKind.FEEDING, 90),
                rec("2", CareKind.FEEDING, 40),     // 마지막 수유
                rec("3", CareKind.DIAPER, 25),
            ),
        )
        val card = HandoffCardBuilder.build(s, Fx.NOW)
        assertEquals(40L, card.lastFeeding!!.minutesAgo)
        assertEquals(25L, card.lastDiaper!!.minutesAgo)
        assertEquals(listOf("기저귀", "물티슈"), card.carryItems) // 정의 순서로 정렬
        assertEquals("대전역", card.destinationName)
        assertTrue(card.hasAnyRecord)
    }

    @Test fun `기록이 없으면 기록 없음으로 표시한다`() {
        val card = HandoffCardBuilder.build(arrivedSession, Fx.NOW)
        assertNull(card.lastFeeding)
        assertFalse(card.hasAnyRecord)
        assertTrue(card.toPlainText().contains("마지막 수유: 기록 없음"))
    }

    @Test fun `시설 이용 기록은 시설 이름과 함께 메모가 된다`() {
        val s = arrivedSession.copy(records = listOf(rec("1", CareKind.FACILITY_OK, 10), rec("2", CareKind.FACILITY_UNAVAILABLE, 5, stop = null)))
        val card = HandoffCardBuilder.build(s, Fx.NOW)
        assertEquals(listOf("시설 s1: 이용 가능"), card.facilityNotes) // 정차 지점을 알 수 없는 기록은 제외
    }

    @Test fun `카드 텍스트에는 아이 이름과 위치 이력이 없다 - 최소 정보 원칙`() {
        val s = arrivedSession.copy(records = listOf(rec("1", CareKind.FEEDING, 10)))
        val text = HandoffCardBuilder.build(s, Fx.NOW).toPlainText()
        listOf("이름", "좌표", "위도", "경도", "37.", "127.").forEach { assertFalse("'$it' 포함: $text", text.contains(it)) }
        assertTrue(text.contains("모드: REPLAY"))
    }

    @Test fun `기록도 물품도 없으면 인계 카드가 유용하지 않다고 판단한다`() {
        assertFalse(HandoffCardBuilder.isHandoffUseful(Fx.session(state = TripState.ARRIVED)))
        assertTrue(HandoffCardBuilder.isHandoffUseful(arrivedSession))
    }

    @Test fun `미래 시각 기록이 와도 분 단위는 음수가 되지 않는다`() {
        val s = arrivedSession.copy(records = listOf(rec("1", CareKind.FEEDING, -5)))
        assertEquals(0L, HandoffCardBuilder.build(s, Fx.NOW).lastFeeding!!.minutesAgo)
    }

    // ---- 주행 제한

    @Test fun `주행 제한 중에는 시설 선택 기록 입력 QR 확인 인계 수정이 모두 막힌다`() {
        val a = UxPolicy.evaluate(UxRestrictionState(true, UxSource.PLATFORM))
        assertFalse(a.canPlan); assertFalse(a.canSelectStop); assertFalse(a.canRecord)
        assertFalse(a.canEditHandoff); assertFalse(a.canShowQr)
        assertNotNull(a.reason)
    }

    @Test fun `제한이 없을 때만 조작이 열린다`() {
        val a = UxPolicy.evaluate(UxRestrictionState(false, UxSource.PLATFORM))
        assertTrue(a.canPlan && a.canSelectStop && a.canRecord && a.canEditHandoff && a.canShowQr)
        assertNull(a.reason)
    }

    @Test fun `제한 상태를 모르면 안전하게 막는다`() {
        val a = UxPolicy.evaluate(UxRestrictionState.Unknown)
        assertFalse(a.canRecord)
        assertTrue(a.reason!!.contains("확인할 수 없"))
    }
}
