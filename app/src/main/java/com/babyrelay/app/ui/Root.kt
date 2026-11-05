package com.babyrelay.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.babyrelay.app.app.MainViewModel
import com.babyrelay.app.app.UiModel
import com.babyrelay.app.data.navigation.Connection
import com.babyrelay.app.data.navigation.ReplayScenario
import com.babyrelay.app.domain.model.NavMode
import com.babyrelay.app.domain.model.TripState
import com.babyrelay.app.ui.theme.BabyRelayTheme
import com.babyrelay.app.ui.theme.Dimens
import com.babyrelay.app.ui.theme.Hmi
import com.babyrelay.app.ui.theme.Type
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BabyRelayRoot(vm: MainViewModel, darkOverride: Boolean?, onDarkOverride: (Boolean?) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    var devOpen by remember { mutableStateOf(false) }

    BabyRelayTheme(dark = darkOverride ?: isSystemInDarkTheme()) {
        val c = Hmi.colors
        Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
            TopBar(ui, onBadge = { devOpen = true })
            if (ui.mode == NavMode.PLEOS && ui.connecting) {
                Banner("Pleos 내비게이션 서비스에 연결하는 중…", c.accent)
            }
            if (ui.mode == NavMode.REPLAY) {
                Banner(
                    "REPLAY 모드 · 실제 내비/차량 연동이 아닌 시연·검증용 시뮬레이션입니다" +
                        (ui.fallbackReason?.let { "\n$it" } ?: ""),
                    c.warn,
                ) { HmiButton("Pleos 다시 연결", { vm.retryPleos() }, kind = ButtonKind.Secondary) }
            }
            ui.access.reason?.let { reason ->
                Banner("주행 제한 · $reason (출처: ${ui.ux.source.label})", c.danger)
            }
            toast?.let { msg ->
                Banner(msg, c.warn)
                LaunchedEffect(msg) {
                    delay(4000)
                    vm.clearToast()
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp && maxWidth > maxHeight
                val s = ui.ctl.session
                when (s?.state) {
                    null -> IdleScreen(ui, vm::run)
                    TripState.PLANNING -> PlanScreen(ui, vm::run, wide)
                    TripState.DRIVING -> TripScreen(ui, vm::run, wide)
                    TripState.STOPPED -> StopScreen(ui, vm::run, wide)
                    TripState.ARRIVED -> ArrivalScreen(ui, vm::run, wide)
                    TripState.CLOSED -> ClosedScreen(s, vm::run)
                }
            }
        }
        if (devOpen) DevPanel(vm, ui, darkOverride, onDarkOverride) { devOpen = false }
    }
}

@Composable
private fun TopBar(ui: UiModel, onBadge: () -> Unit) {
    val c = Hmi.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.topBar).background(c.surface).padding(horizontal = Dimens.pad, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        HText("BabyRelay", 28.sp, FontWeight.Bold)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val cur = ui.ctl.session?.state
            val order = listOf(TripState.PLANNING, TripState.DRIVING, TripState.STOPPED, TripState.ARRIVED)
            order.forEach { st ->
                val active = cur == st
                val passed = cur != null && (cur == TripState.CLOSED || order.indexOf(cur) > order.indexOf(st))
                val fg = if (active) c.onAccent else if (passed) c.text else c.textDisabled
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(if (active) c.accent else c.surfaceHigh).padding(horizontal = 14.dp, vertical = 8.dp),
                ) { HText(st.label, Type.label, if (active) FontWeight.Bold else FontWeight.Medium, fg) }
            }
        }
        // 배지를 누르면 개발·시연 패널이 열린다.
        Row(
            Modifier.heightIn(min = 56.dp).clickable(onClick = onBadge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ui.ctl.dataKind == "SAMPLE") Tag("SAMPLE 데이터", c.warn)
            if (ui.mode == NavMode.PLEOS) {
                val ok = ui.gateway.connection == Connection.CONNECTED
                Tag(if (ok) "PLEOS · 내비 연결됨" else "PLEOS · 연결 안 됨", if (ok) c.ok else c.danger)
            } else {
                Tag("REPLAY · 시뮬레이션", c.warn)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DevPanel(vm: MainViewModel, ui: UiModel, dark: Boolean?, onDark: (Boolean?) -> Unit, onClose: () -> Unit) {
    val c = Hmi.colors
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.9f).heightIn(max = 640.dp)
                .clip(RoundedCornerShape(Dimens.radius + 4.dp)).background(c.surface)
                .border(BorderStroke(1.dp, c.outline), RoundedCornerShape(Dimens.radius + 4.dp))
                .verticalScroll(rememberScrollState()).padding(Dimens.pad),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HText("개발·시연 패널", Type.title, FontWeight.Bold)
            HText("출시 사용자용 기능이 아닙니다. REPLAY는 실제 Pleos/차량 검증을 대신하지 않습니다.", Type.label, color = c.warn)
            HText("모드: ${ui.mode.label} · 연결: ${ui.gateway.connection}${ui.gateway.detail?.let { " ($it)" } ?: ""}", Type.body)
            HText("주행 제한 값: ${ui.ux.requiresDistractionOptimization ?: "알 수 없음"} (출처 ${ui.ux.source.label})", Type.body)

            SectionLabel("테마")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectChip("시스템", dark == null, { onDark(null) })
                SelectChip("다크", dark == true, { onDark(true) })
                SelectChip("라이트", dark == false, { onDark(false) })
            }

            if (ui.mode == NavMode.REPLAY) {
                SectionLabel("주행 제한 시뮬레이션 (실제 차량 값 아님)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectChip("제한 없음(정차)", ui.ux.requiresDistractionOptimization == false, { vm.setSimulatedRestricted(false) })
                    SelectChip("제한 중(주행)", ui.ux.requiresDistractionOptimization == true, { vm.setSimulatedRestricted(true) })
                }
                SectionLabel("경유지 등록 확인")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SelectChip("내비가 자동 확인", true, { vm.setAutoConfirm(true) })
                    SelectChip("확인 이벤트 없음", false, { vm.setAutoConfirm(false) })
                }
                SectionLabel("시나리오 이벤트 재생")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReplayScenario.entries.forEach { sc ->
                        HmiButton(sc.label, { vm.playScenario(sc) }, kind = ButtonKind.Secondary, sub = sc.hint)
                    }
                }
            } else {
                HmiButton("REPLAY 모드로 전환", { vm.switchToReplay("개발 패널에서 직접 전환") }, kind = ButtonKind.Secondary)
            }

            SectionLabel("최근 이벤트")
            ui.ctl.recentEvents.forEach { HText(it, Type.caption, color = c.textSub, maxLines = 2) }
            HmiButton("닫기", onClose, Modifier.fillMaxWidth())
        }
    }
}
