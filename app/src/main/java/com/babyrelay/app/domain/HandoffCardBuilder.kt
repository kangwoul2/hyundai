package com.babyrelay.app.domain

import com.babyrelay.app.domain.model.CareKind
import com.babyrelay.app.domain.model.CareRecord
import com.babyrelay.app.domain.model.TripSession
import com.babyrelay.app.domain.model.TripState

data class CareSummary(val at: Long, val minutesAgo: Long)

data class HandoffCard(
    val destinationName: String,
    val lastFeeding: CareSummary?,
    val lastDiaper: CareSummary?,
    val carryItems: List<String>,
    /** 경유지 시설 이용 기록 요약. 예: "OO: 이용 가능". */
    val facilityNotes: List<String>,
    val stopCount: Int,
    val generatedAt: Long,
    /** 어떤 모드(PLEOS/REPLAY)에서 만들어진 기록인지. 시연 구분용. */
    val navModeLabel: String,
) {
    val hasAnyRecord get() = lastFeeding != null || lastDiaper != null

    /** 공유/표시용 최소 텍스트. 아이 이름·위치 이력은 포함하지 않는다. */
    fun toPlainText(): String = buildString {
        appendLine("[BabyRelay 인계 카드] $destinationName")
        appendLine("마지막 수유: ${lastFeeding.describe()}")
        appendLine("마지막 기저귀 교환: ${lastDiaper.describe()}")
        appendLine("전달 물품: ${if (carryItems.isEmpty()) "없음" else carryItems.joinToString(", ")}")
        facilityNotes.forEach { appendLine("시설 메모: $it") }
        append("모드: $navModeLabel")
    }

    private fun CareSummary?.describe() = if (this == null) "기록 없음" else "${minutesAgo}분 전"
}

object HandoffCardBuilder {
    fun build(session: TripSession, now: Long): HandoffCard {
        val feeding = session.records.lastOf(CareKind.FEEDING)?.toSummary(now)
        val diaper = session.records.lastOf(CareKind.DIAPER)?.toSummary(now)
        val notes = session.records
            .filter { it.kind == CareKind.FACILITY_OK || it.kind == CareKind.FACILITY_UNAVAILABLE }
            .mapNotNull { r ->
                val name = r.stopIndex?.let { session.plannedStops.getOrNull(it)?.facility?.name } ?: return@mapNotNull null
                "$name: ${if (r.kind == CareKind.FACILITY_OK) "이용 가능" else "이용 불가"}"
            }
            .distinct()
        return HandoffCard(
            destinationName = session.destination?.name ?: "목적지 미확인",
            lastFeeding = feeding,
            lastDiaper = diaper,
            carryItems = session.carryItems.sortedBy { it.ordinal }.map { it.label },
            facilityNotes = notes,
            stopCount = session.arrivedStopIndexes.size,
            generatedAt = now,
            navModeLabel = session.navMode.label,
        )
    }

    /** 인계 카드가 의미 있는 여행인가. 기록과 전달 물품이 모두 없으면 바로 종료를 권한다. */
    fun isHandoffUseful(session: TripSession): Boolean =
        session.state == TripState.ARRIVED &&
            (session.records.any { it.kind == CareKind.FEEDING || it.kind == CareKind.DIAPER } || session.carryItems.isNotEmpty())

    private fun List<CareRecord>.lastOf(kind: CareKind) = filter { it.kind == kind }.maxByOrNull { it.recordedAt }

    private fun CareRecord.toSummary(now: Long) = CareSummary(recordedAt, ((now - recordedAt).coerceAtLeast(0)) / 60_000)
}
