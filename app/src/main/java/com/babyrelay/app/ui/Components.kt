package com.babyrelay.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.babyrelay.app.ui.theme.Dimens
import com.babyrelay.app.ui.theme.Hmi
import com.babyrelay.app.ui.theme.Type

enum class ButtonKind { Primary, Secondary, Danger }

@Composable
fun HText(
    text: String,
    size: TextUnit = Type.body,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Hmi.colors.text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    Text(
        text = text, fontSize = size, fontWeight = weight, color = color, modifier = modifier,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align, lineHeight = size * 1.25f,
    )
}

/** 큰 터치 영역(76dp)의 기본 버튼. 비활성 상태에서는 이유를 보조 문구로 함께 보여줄 수 있다. */
@Composable
fun HmiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    sub: String? = null,
) {
    val c = Hmi.colors
    val bg = when {
        !enabled -> c.surfaceHigh
        kind == ButtonKind.Primary -> c.accent
        kind == ButtonKind.Danger -> c.surfaceHigh
        else -> c.surfaceHigh
    }
    val fg = when {
        !enabled -> c.textDisabled
        kind == ButtonKind.Primary -> c.onAccent
        kind == ButtonKind.Danger -> c.danger
        else -> c.text
    }
    Column(
        modifier = modifier
            .heightIn(min = Dimens.touch)
            .clip(RoundedCornerShape(Dimens.radius))
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HText(text, Type.body, FontWeight.SemiBold, fg, align = TextAlign.Center)
        if (sub != null) HText(sub, Type.caption, FontWeight.Normal, if (enabled) fg else c.textDisabled, align = TextAlign.Center)
    }
}

/** 선택 상태가 색과 ✓ 표시로 함께 드러나는 선택 칩. */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val c = Hmi.colors
    val bg = if (selected) c.accentSoft else c.surfaceHigh
    val fg = when {
        !enabled -> c.textDisabled
        selected -> c.accent
        else -> c.text
    }
    Box(
        modifier = modifier
            .heightIn(min = Dimens.chip)
            .clip(RoundedCornerShape(Dimens.radius))
            .background(bg)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline), RoundedCornerShape(Dimens.radius))
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        HText((if (selected) "✓ " else "") + label, Type.body, if (selected) FontWeight.SemiBold else FontWeight.Normal, fg)
    }
}

@Composable
fun HmiCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Hmi.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radius + 2.dp))
            .background(c.surface)
            .border(BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) c.accent else c.outline), RoundedCornerShape(Dimens.radius + 2.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** 출처·상태를 짧게 알려주는 태그. 색만으로 구분하지 않고 글자를 항상 함께 둔다. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, color), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) { HText(text, Type.caption, FontWeight.SemiBold, color) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    HText(text, Type.label, FontWeight.SemiBold, Hmi.colors.textSub, modifier)
}

@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun Banner(text: String, color: Color, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.6f)))
            .padding(horizontal = Dimens.pad, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HText(text, Type.label, FontWeight.Medium, Hmi.colors.text, Modifier.weight(1f), maxLines = 3)
        action?.invoke()
    }
}
