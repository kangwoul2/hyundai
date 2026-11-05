package com.babyrelay.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.babyrelay.app.app.ActionResult
import com.babyrelay.app.app.TripController
import com.babyrelay.app.app.UiModel
import com.babyrelay.app.domain.Basis
import com.babyrelay.app.domain.Candidate
import com.babyrelay.app.domain.model.Amenity
import com.babyrelay.app.domain.model.CareKind
import com.babyrelay.app.domain.model.CarryItem
import com.babyrelay.app.domain.model.CloseReason
import com.babyrelay.app.domain.model.DataOrigin
import com.babyrelay.app.domain.model.Registration
import com.babyrelay.app.domain.model.TripSession
import com.babyrelay.app.ui.theme.LocalScale
import com.babyrelay.app.ui.theme.Hmi
import com.babyrelay.app.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

typealias Runner = (TripController.() -> ActionResult) -> Unit

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private fun fmtTime(ms: Long) = timeFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private val restOptions = listOf(30 to "30분", 60 to "1시간", 90 to "1시간 30분", 120 to "2시간")

@Composable
private fun ScreenPad(wide: Boolean, left: @Composable () -> Unit, right: @Composable () -> Unit, leftWeight: Float = 1f) {
    if (wide) {
        Row(Modifier.fillMaxSize().padding(horizontal = LocalScale.current.pad, vertical = LocalScale.current.vpad), horizontalArrangement = Arrangement.spacedBy(LocalScale.current.pad)) {
            Column(Modifier.weight(leftWeight).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LocalScale.current.gap)) { left() }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LocalScale.current.gap)) { right() }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = LocalScale.current.pad, vertical = LocalScale.current.vpad),
            verticalArrangement = Arrangement.spacedBy(LocalScale.current.gap),
        ) {
            left()
            right()
        }
    }
}

// ------------------------------------------------------------------ 시작 / 종료

@Composable
fun IdleScreen(ui: UiModel, run: Runner) {
    val c = Hmi.colors
    Column(
        Modifier.fillMaxSize().padding(LocalScale.current.pad),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HText("아기와 함께 이동할 때 필요한 정차를 계획해요", Type.title, FontWeight.SemiBold)
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val navi = ui.ctl.naviDestination
            HText(
                if (navi != null) "내비 목적지: ${navi.name}" else "내비에서 받은 목적지가 아직 없어요",
                Type.body, color = c.textSub,
            )
        }
        HmiButton("여행 시작", { run { beginPlanning() } }, Modifier.fillMaxWidth(0.5f))
    }
}

@Composable
fun ClosedScreen(s: TripSession, run: Runner) {
    val msg = when (s.closeReason) {
        CloseReason.COMPLETED -> "인계 카드를 확인하고 여정을 마쳤어요"
        CloseReason.COMPLETED_NO_HANDOFF -> "인계 없이 여정을 마쳤어요"
        CloseReason.CANCELLED_BEFORE_START -> "출발 전에 계획을 취소했어요"
        CloseReason.ROUTE_CANCELLED -> "내비 경로가 취소되어 여정을 종료했어요"
        null -> "여정이 종료되었어요"
    }
    Column(
        Modifier.fillMaxSize().padding(LocalScale.current.pad),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HText(msg, Type.title, FontWeight.SemiBold)
        Column(Modifier.padding(vertical = 12.dp)) {
            HText("돌봄 기록 ${s.records.size}건 · 경유지 ${s.arrivedStopIndexes.size}곳 · 기록은 이 기기에만 저장돼요", Type.body, color = Hmi.colors.textSub)
        }
        HmiButton("처음으로", { run { dismissClosed(); ActionResult.Ok } }, Modifier.fillMaxWidth(0.5f))
    }
}

// ------------------------------------------------------------------ 출발 계획

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(ui: UiModel, run: Runner, wide: Boolean) {
    val s = ui.ctl.session ?: return
    val can = ui.access.canPlan
    val sc = LocalScale.current
    ScreenPad(
        wide = wide,
        leftWeight = 0.9f,
        left = {
            Section("목적지") {
                if (!sc.compact) {
                    HmiCard(Modifier.fillMaxWidth()) {
                        if (s.destination != null) {
                            HText(s.destination.name, Type.title, FontWeight.SemiBold)
                            HText(s.destination.address, Type.label, color = Hmi.colors.textSub, maxLines = 2)
                        } else {
                            HText("목적지를 선택하세요", Type.title, FontWeight.SemiBold, Hmi.colors.textSub)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(sc.gap), verticalArrangement = Arrangement.spacedBy(sc.gap)) {
                    val navi = ui.ctl.naviDestination
                    // 내비 목적지가 목록에 이미 있으면 그 칩에 "내비" 표시를 붙여 중복 칩을 만들지 않는다.
                    if (navi != null && ui.ctl.destinations.none { it.id == navi.id }) {
                        SelectChip("내비: ${navi.name}", s.destination?.id == navi.id, { run { useNaviDestination() } }, can)
                    }
                    ui.ctl.destinations.forEach { d ->
                        val isNavi = navi?.id == d.id
                        SelectChip(
                            if (isNavi) "${d.name} · 내비" else d.name,
                            s.destination?.id == d.id,
                            { run { setDestination(d, fromNavi = isNavi) } },
                            can,
                        )
                    }
                }
            }
            Section("다음 휴식") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(sc.gap), verticalArrangement = Arrangement.spacedBy(sc.gap)) {
                    restOptions.forEach { (min, label) -> SelectChip(label, s.restInMinutes == min, { run { setRestMinutes(min) } }, can) }
                }
            }
            Section("필요한 시설") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(sc.gap), verticalArrangement = Arrangement.spacedBy(sc.gap)) {
                    Amenity.entries.forEach { a -> SelectChip(a.label, a in s.needs, { run { toggleNeed(a) } }, can) }
                }
            }
        },
        right = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sc.gap)) {
                SectionLabel("추천 휴식 장소")
                ui.ctl.recommendation?.takeIf { it.basis != Basis.NONE }?.let { Tag(it.basis.label, Hmi.colors.textSub) }
            }
            val rec = ui.ctl.recommendation
            when {
                s.destination == null -> HmiCard(Modifier.fillMaxWidth()) { HText("목적지를 선택하면 조건에 맞는 장소를 추천해요", Type.body, color = Hmi.colors.textSub) }
                rec == null -> Unit
                rec.candidates.isEmpty() -> HmiCard(Modifier.fillMaxWidth()) {
                    HText("추천할 수 있는 시설이 없어요", Type.title, FontWeight.SemiBold, Hmi.colors.warn)
                    HText(rec.notice ?: "", Type.body)
                    HText("내비게이션에서 직접 휴식 장소를 검색하거나, 경유지 없이 출발할 수 있어요.", Type.label, color = Hmi.colors.textSub)
                }
                else -> {
                    rec.notice?.let { HText(it, Type.label, color = Hmi.colors.warn) }
                    rec.candidates.forEach { cand ->
                        CandidateCard(cand, s, ui, onSelect = { run { selectStop(cand.facility.id) } }, onClear = { run { clearStop() } })
                    }
                }
            }
            ui.ctl.registrationNote?.let { note ->
                val reg = s.plannedStops.firstOrNull()?.registration
                val tone = when (reg) {
                    Registration.CONFIRMED -> Hmi.colors.ok
                    Registration.FAILED, Registration.UNCONFIRMED -> Hmi.colors.danger
                    else -> Hmi.colors.accent
                }
                HmiCard(Modifier.fillMaxWidth(), highlight = true) { HText(note, Type.label, FontWeight.Medium, tone, maxLines = 5) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sc.gap)) {
                HmiButton("계획 취소", { run { cancelPlanning() } }, Modifier.weight(1f), kind = ButtonKind.Danger, dense = sc.compact)
                HmiButton(
                    "출발 처리(수동)", { run { startTripManually() } }, Modifier.weight(1.4f),
                    kind = ButtonKind.Secondary, enabled = can && s.destination != null,
                    sub = if (sc.compact) null else "내비 이벤트가 없을 때", dense = sc.compact,
                )
            }
        },
    )
}

@Composable
private fun CandidateCard(cand: Candidate, s: TripSession, ui: UiModel, onSelect: () -> Unit, onClear: () -> Unit) {
    val c = Hmi.colors
    val compact = LocalScale.current.compact
    val planned = s.plannedStops.firstOrNull()?.takeIf { it.facility.id == cand.facility.id }
    HmiCard(Modifier.fillMaxWidth(), highlight = planned != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    HText(cand.facility.name, 28.sp, FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                    cand.facility.kind?.let { Tag(it, c.textSub) }
                    val originColor = if (cand.facility.origin == DataOrigin.SAMPLE) c.warn else c.ok
                    Tag(cand.facility.origin.label, originColor)
                }
                val lines = if (compact && cand.reasons.size >= 4) {
                    listOf(cand.reasons[0] + " · " + cand.reasons[3]) + cand.reasons.subList(1, 3)
                } else {
                    cand.reasons
                }
                lines.forEach { HText("· $it", Type.label, color = c.textSub, maxLines = 2) }
                if (planned != null) {
                    HText("장소: ${cand.facility.name} · ${cand.facility.address}", Type.caption, color = c.textSub, maxLines = 1)
                }
            }
            Column(
                Modifier.widthIn(min = if (compact) 140.dp else 190.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (planned == null) {
                    HmiButton(
                        "경유지로 등록", onSelect,
                        enabled = ui.access.canSelectStop,
                        sub = if (ui.access.canSelectStop) null else "주행 제한 중",
                    )
                } else {
                    val (label, tone) = when (planned.registration) {
                        Registration.NOT_REQUESTED -> "선택됨" to c.accent
                        Registration.REQUESTED -> "내비 확인 대기" to c.accent
                        Registration.CONFIRMED -> "내비에 등록됨" to c.ok
                        Registration.FAILED -> "등록 실패" to c.danger
                        Registration.UNCONFIRMED -> "내비 확인 안 됨" to c.danger
                    }
                    HText(
                        (if (planned.registration == Registration.CONFIRMED) "✓ " else "") + label,
                        Type.body, FontWeight.SemiBold, tone, align = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    HmiButton("선택 취소", onClear, kind = ButtonKind.Secondary, enabled = ui.access.canPlan, dense = true)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 이동 중

@Composable
fun TripScreen(ui: UiModel, run: Runner, wide: Boolean) {
    val s = ui.ctl.session ?: return
    val c = Hmi.colors
    val compact = LocalScale.current.compact
    ScreenPad(
        wide = wide,
        left = {
            Tag("이동 중", c.accent)
            HText("이동 중", if (compact) Type.title else Type.display, FontWeight.Bold)
            HText("목적지 · ${s.destination?.name ?: "미확인"}", if (compact) Type.body else Type.title, FontWeight.Medium)
            val eta = ui.ctl.etaRemainSec
            HText(
                if (eta != null) "내비 제공 남은 시간 약 ${(eta + 59) / 60}분" else "남은 시간은 내비가 알려줄 때 표시돼요",
                Type.body, color = c.textSub,
            )
        },
        right = {
            SectionLabel("다음 휴식 장소")
            val nextIdx = s.plannedStops.indices.firstOrNull { it !in s.arrivedStopIndexes }
            if (nextIdx != null) {
                val next = s.plannedStops[nextIdx]
                HmiCard(Modifier.fillMaxWidth()) {
                    HText(next.facility.name, Type.title, FontWeight.SemiBold)
                    if (!compact) HText(next.facility.address, Type.label, color = c.textSub, maxLines = 1)
                    HText(next.facility.amenities.sortedBy { it.ordinal }.joinToString(" · ") { it.label }, Type.label)
                    Tag(registrationLabel(next.registration), registrationColor(next.registration))
                }
            } else {
                HmiCard(Modifier.fillMaxWidth()) { HText("남은 계획 경유지가 없어요", Type.body, color = c.textSub) }
            }
            if (ui.access.canRecord) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LocalScale.current.gap)) {
                    HmiButton("경유지 도착(수동)", { run { markWaypointArrivedManually() } }, Modifier.weight(1f), ButtonKind.Secondary, nextIdx != null, dense = compact)
                    HmiButton("목적지 도착(수동)", { run { markDestinationArrivedManually() } }, Modifier.weight(1f), ButtonKind.Secondary, dense = compact)
                }
            } else {
                HText("주행 제한 중이라 화면은 보기 전용이에요", Type.label, color = c.textSub)
            }
        },
    )
}

private fun registrationLabel(r: Registration) = when (r) {
    Registration.NOT_REQUESTED -> "등록 전"
    Registration.REQUESTED -> "내비 확인 대기"
    Registration.CONFIRMED -> "내비에 등록됨"
    Registration.FAILED -> "등록 실패"
    Registration.UNCONFIRMED -> "내비 확인 안 됨"
}

@Composable
private fun registrationColor(r: Registration): Color = when (r) {
    Registration.CONFIRMED -> Hmi.colors.ok
    Registration.FAILED, Registration.UNCONFIRMED -> Hmi.colors.danger
    else -> Hmi.colors.accent
}

// ------------------------------------------------------------------ 휴식(경유지 도착)

@Composable
fun StopScreen(ui: UiModel, run: Runner, wide: Boolean) {
    val s = ui.ctl.session ?: return
    val c = Hmi.colors
    val compact = LocalScale.current.compact
    val stop = s.currentStopIndex?.let { s.plannedStops.getOrNull(it) }
    val can = ui.access.canRecord
    ScreenPad(
        wide = wide,
        left = {
            Tag("휴식 중", c.ok)
            HText(stop?.facility?.name ?: "경유지 도착", if (compact) Type.title else Type.display, FontWeight.Bold, maxLines = 2)
            stop?.facility?.let { HText(it.address, Type.label, color = c.textSub, maxLines = 1) }
            if (!compact) {
                HText("내비게이션이 경유지 도착을 알려왔어요. 차량이 정차했는지는 이 이벤트로 판단하지 않아요.", Type.label, color = c.textSub, maxLines = 3)
            }
            if (!can && !compact) {
                HText(ui.access.reason ?: "", Type.label, FontWeight.Medium, c.warn, maxLines = 3)
            }
            HmiButton("다시 출발", { run { resume() } }, Modifier.fillMaxWidth(), dense = compact)
        },
        right = {
            SectionLabel("돌봄 기록")
            listOf(
                listOf(CareKind.FEEDING, CareKind.DIAPER),
                listOf(CareKind.FACILITY_OK, CareKind.FACILITY_UNAVAILABLE),
            ).forEach { rowKinds ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LocalScale.current.gap)) {
                    rowKinds.forEach { kind ->
                        val last = s.records.filter { it.kind == kind && it.stopIndex == s.currentStopIndex }.maxByOrNull { it.recordedAt }
                        RecordButton(kind, last?.recordedAt, can, { run { record(kind) } }, Modifier.weight(1f))
                    }
                }
            }
        },
    )
}

@Composable
private fun RecordButton(kind: CareKind, at: Long?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    HmiButton(
        text = (if (at != null) "✓ " else "") + kind.label,
        onClick = onClick,
        modifier = modifier,
        kind = if (at != null) ButtonKind.Primary else ButtonKind.Secondary,
        enabled = enabled,
        sub = at?.let { "${fmtTime(it)} 기록 · 직접 입력" },
    )
}

// ------------------------------------------------------------------ 목적지 도착 / 인계 카드

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArrivalScreen(ui: UiModel, run: Runner, wide: Boolean) {
    val s = ui.ctl.session ?: return
    val card = ui.ctl.handoff ?: return
    val c = Hmi.colors
    val compact = LocalScale.current.compact
    val gap = LocalScale.current.gap
    ScreenPad(
        wide = wide,
        leftWeight = 0.8f,
        left = {
            Tag("도착", c.ok)
            HText("도착했어요", if (compact) Type.title else Type.display, FontWeight.Bold)
            HText("${s.destination?.name ?: ""} · 경유지 ${card.stopCount}곳 · 기록 ${s.records.size}건", if (compact) Type.body else Type.title, FontWeight.Medium, maxLines = 2)
            if (!ui.access.canEditHandoff && !compact) {
                HText(ui.access.reason ?: "", Type.label, FontWeight.Medium, c.warn, maxLines = 3)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                HmiButton("확인 완료 · 여정 종료", { run { closeTrip(false) } }, Modifier.weight(1.3f), enabled = ui.access.canEditHandoff, dense = compact)
                HmiButton("인계 없이 종료", { run { closeTrip(true) } }, Modifier.weight(1f), ButtonKind.Secondary, ui.access.canEditHandoff, dense = compact)
            }
        },
        right = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                SectionLabel("인계 카드")
                Tag(if (card.navModeLabel == "REPLAY") "REPLAY 시뮬레이션 기록" else "PLEOS 기록", if (card.navModeLabel == "REPLAY") c.warn else c.ok)
                Tag("아이 이름·위치 이력 없음", c.textSub)
            }
            HmiCard(Modifier.fillMaxWidth(), highlight = true) {
                CardRow("마지막 수유", card.lastFeeding?.let { "${it.minutesAgo}분 전 (${fmtTime(it.at)})" } ?: "기록 없음")
                CardRow("마지막 기저귀 교환", card.lastDiaper?.let { "${it.minutesAgo}분 전 (${fmtTime(it.at)})" } ?: "기록 없음")
                if (card.facilityNotes.isNotEmpty()) CardRow("시설 메모", card.facilityNotes.joinToString(" · "))
            }
            Section("전달 물품") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                    CarryItem.entries.forEach { i ->
                        SelectChip(i.label, i in s.carryItems, { run { toggleCarryItem(i) } }, ui.access.canEditHandoff)
                    }
                }
            }
            if (!card.hasAnyRecord) {
                HText("돌봄 기록이 없어요. 인계할 내용이 없으면 바로 종료해도 돼요.", Type.label, color = c.textSub)
            }
        },
    )
}

@Composable
private fun CardRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        HText(label, Type.label, FontWeight.SemiBold, Hmi.colors.textSub, Modifier.width(if (LocalScale.current.compact) 190.dp else 220.dp))
        HText(value, Type.body, FontWeight.Medium, maxLines = 2)
    }
}
