package com.babyrelay.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 프로젝트 임시 디자인 토큰.
 * Pleos 공식 색상 페이지는 값을 이미지로만 제공해 텍스트로 확인할 수 없었다. 따라서 아래 값은 공식 토큰이 아니라
 * "Pleos Connect 화면의 어두운 중성 배경 + 단일 포인트 색" 구조를 참고한 BabyRelay 자체 값이다.
 * 공식 토큰 이름(Background/Surface/Text/Informative 등) 구조에 대응시켜 나중에 교체하기 쉽게 한다.
 */
@Immutable
class HmiColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val textSub: Color,
    val textDisabled: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
)

val DarkHmi = HmiColors(
    background = Color(0xFF0E0F11),
    surface = Color(0xFF181A1E),
    surfaceHigh = Color(0xFF23262B),
    outline = Color(0xFF3A3F47),
    text = Color(0xFFF3F5F8),
    textSub = Color(0xFFA3AAB5),
    textDisabled = Color(0xFF5E646E),
    accent = Color(0xFF5AA2FF),
    onAccent = Color(0xFF04101F),
    accentSoft = Color(0xFF1B2A40),
    ok = Color(0xFF4CC38A),
    warn = Color(0xFFF5B547),
    danger = Color(0xFFFF6B63),
)

val LightHmi = HmiColors(
    background = Color(0xFFF1F3F6),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE8EBF0),
    outline = Color(0xFFC9CFD8),
    text = Color(0xFF14161A),
    textSub = Color(0xFF545B66),
    textDisabled = Color(0xFF9AA1AB),
    accent = Color(0xFF1F63D6),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFDCE8FB),
    ok = Color(0xFF14804A),
    warn = Color(0xFF8F5A00),
    danger = Color(0xFFC0322A),
)

/** 차량용 크기 기준(프로젝트 임시값). 터치 영역은 76dp 이상, 본문은 24sp 이상을 목표로 한다. */
object Dimens {
    val radius = 14.dp
}

/**
 * 앱 창의 사용 가능한 높이에 맞춘 크기 체계(프로젝트 임시값).
 * AAOS는 상단 상태바·하단 GNB가 높이를 차지하므로, 에뮬레이터(1920x720 @240dpi)에서 앱 영역이 약 308dp였다.
 * 이런 낮은 창에서는 compact로 전환해 터치 영역·글자·여백을 줄인다(글자는 20sp 이상 유지).
 */
@Immutable
class UiScale(val compact: Boolean, val large: Boolean = false) {
    // large: 창 너비 1400dp 이상(Pleos Connect 에뮬레이터 2560x1440@mdpi 등). 같은 dp라도 물리 크기가 작아 보여 키운다.
    val touch get() = if (large) 100.dp else if (compact) 60.dp else 76.dp
    val chip get() = if (large) 84.dp else if (compact) 48.dp else 68.dp
    val pad get() = if (large) 32.dp else if (compact) 14.dp else 24.dp
    val vpad get() = if (large) 24.dp else if (compact) 8.dp else 24.dp
    val gap get() = if (large) 18.dp else if (compact) 8.dp else 14.dp
    val topBar get() = if (large) 88.dp else if (compact) 52.dp else 72.dp
    val textScale get() = if (large) 1.35f else if (compact) 0.85f else 1f
}

val LocalScale = staticCompositionLocalOf { UiScale(false) }

object Type {
    val display = 44.sp
    val title = 32.sp
    val body = 24.sp
    val label = 20.sp
    val caption = 18.sp
}

val LocalHmi = staticCompositionLocalOf { DarkHmi }

object Hmi {
    val colors: HmiColors
        @Composable @ReadOnlyComposable get() = LocalHmi.current
}

@Composable
fun BabyRelayTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkHmi else LightHmi
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.surface, onSurface = c.text)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.background, surface = c.surface, onSurface = c.text)
    }
    CompositionLocalProvider(LocalHmi provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
