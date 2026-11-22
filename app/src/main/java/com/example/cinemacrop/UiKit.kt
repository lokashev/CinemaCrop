package com.example.cinemacrop

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun GlassCircle(icon: ImageVector, desc: String?, enabled: Boolean = true, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Box(
        Modifier.size(40.dp).alpha(if (enabled) 1f else 0.35f).clip(CircleShape)
            .background(s.onSurface.copy(alpha = 0.07f))
            .border(1.dp, s.onSurface.copy(alpha = 0.12f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = s.onSurface.copy(alpha = 0.92f), modifier = Modifier.size(19.dp)) }
}

@Composable
fun GlassPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier.clip(CircleShape).background(s.onSurface.copy(alpha = 0.07f))
            .border(1.dp, s.onSurface.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

@Composable
fun GlassAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Box(
        modifier.clip(CircleShape).background(s.onSurface.copy(alpha = 0.07f))
            .border(1.dp, s.onSurface.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = s.onSurface, maxLines = 1, softWrap = false) }
}

@Composable
fun PrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 44.dp
) {
    val s = MaterialTheme.colorScheme
    Box(
        modifier.height(height).alpha(if (enabled) 1f else 0.4f).clip(CircleShape).background(s.primary)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = s.onPrimary, maxLines = 1, softWrap = false) }
}

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val s = MaterialTheme.colorScheme
    val t by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "sw")
    Box(
        Modifier.size(51.dp, 31.dp).clip(CircleShape)
            .background(lerp(Color(0xFF39393D), s.primary, t))
            .clickable { onChange(!checked) }
    ) {
        Box(
            Modifier.padding(2.dp).offset(x = (20 * t).dp).size(27.dp)
                .shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White)
        )
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.height(36.dp).clip(CircleShape).background(s.onSurface.copy(alpha = 0.06f))) {
        val w = (maxWidth - 4.dp) / options.size
        val x by animateDpAsState(w * selected, tween(220), label = "seg")
        Box(Modifier.fillMaxSize().padding(2.dp)) {
            Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(s.primary))
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, label ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
                            color = if (i == selected) s.onPrimary else s.onSurface.copy(alpha = 0.65f))
                    }
                }
            }
        }
    }
}

@Composable
fun RulerSlider(
    title: String,
    value: Int,
    range: IntRange,
    suffix: String,
    onChange: (Int) -> Unit,
    display: (Int) -> String = { "$it$suffix" }
) {
    val s = MaterialTheme.colorScheme
    var f by remember { mutableFloatStateOf(value.toFloat()) }
    LaunchedEffect(value) { if (value != f.roundToInt()) f = value.toFloat() }
    val tick = s.onSurface.copy(alpha = 0.28f)
    val tickMajor = s.onSurface.copy(alpha = 0.55f)
    val ind = s.primary
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, color = s.onSurfaceVariant, maxLines = 1, softWrap = false)
            Spacer(Modifier.weight(1f))
            Text(display(value), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = s.onSurface, maxLines = 1, softWrap = false)
        }
        Canvas(
            Modifier.fillMaxWidth().height(44.dp).pointerInput(range) {
                val ppu = 6.dp.toPx()
                detectHorizontalDragGestures { change, drag ->
                    change.consume()
                    f = (f - drag / ppu).coerceIn(range.first.toFloat(), range.last.toFloat())
                    val r = f.roundToInt()
                    if (r != value) onChange(r)
                }
            }
        ) {
            val ppu = 6.dp.toPx()
            val cx = size.width / 2f
            for (i in range.first..range.last) {
                val x = cx + (i - f) * ppu
                if (x < 0f || x > size.width) continue
                val major = i % 5 == 0
                val h = if (major) 18.dp.toPx() else 10.dp.toPx()
                drawLine(if (major) tickMajor else tick, Offset(x, size.height / 2 - h / 2), Offset(x, size.height / 2 + h / 2), 1.dp.toPx())
            }
            drawLine(ind, Offset(cx, 6.dp.toPx()), Offset(cx, size.height - 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(s.onSurface.copy(alpha = 0.06f))
                .border(1.dp, if (active) s.primary.copy(alpha = 0.7f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = if (active) s.primary else s.onSurface.copy(alpha = 0.85f), modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = if (active) s.onSurface else s.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

@Composable
fun RatioCard(r: CinemaRatio, selected: Boolean, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val frame = if (selected) s.primary else s.onSurface.copy(alpha = 0.5f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.size(76.dp, 56.dp).clip(RoundedCornerShape(14.dp))
                .background(s.onSurface.copy(alpha = 0.05f))
                .border(if (selected) 1.5.dp else 1.dp, if (selected) s.primary else s.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(50.dp, 32.dp)) {
                val ir = size.width / size.height
                val (w, h) = if (ir > r.value) size.height * r.value to size.height else size.width to size.width / r.value
                drawRect(frame, Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h), style = Stroke(1.5.dp.toPx()))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(r.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) s.onSurface else s.onSurfaceVariant, maxLines = 1, softWrap = false)
    }
}

@Composable
fun SmallPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Box(
        Modifier.clip(CircleShape).background(if (selected) s.primary else s.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false,
        color = if (selected) s.onPrimary else s.onSurfaceVariant) }
}

@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface), content = content)
}

@Composable
fun RowItem(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    val s = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = s.onSurface)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = s.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun CheckMark(selected: Boolean) {
    if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
}