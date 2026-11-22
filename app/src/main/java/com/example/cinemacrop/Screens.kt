package com.example.cinemacrop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassCircle(Icons.Rounded.ArrowBack, "Назад", onClick = onBack)
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(s.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Настройки", onBack)

        SectionLabel("ОФОРМЛЕНИЕ")
        GroupCard {
            ThemeMode.entries.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider(color = s.outline.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                RowItem(m.label, onClick = { vm.setTheme(m) }) { CheckMark(vm.themeMode == m) }
            }
        }
        Spacer(Modifier.height(10.dp))
        GroupCard {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Accent.entries.forEach { a ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .border(2.dp, if (vm.accent == a) s.onSurface else Color.Transparent, CircleShape)
                            .clickable { vm.applyAccent(a) },
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.size(24.dp).clip(CircleShape).background(a.color)) }
                }
            }
        }

        SectionLabel("ПО УМОЛЧАНИЮ")
        GroupCard {
            RowItem("Формат", trailing = {
                Segmented(listOf("JPEG", "PNG"), if (vm.format == ExportFormat.JPEG) 0 else 1,
                    { vm.applyFormat(if (it == 0) ExportFormat.JPEG else ExportFormat.PNG) }, Modifier.width(140.dp))
            })
            if (vm.format == ExportFormat.JPEG) {
                HorizontalDivider(color = s.outline.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                Box(Modifier.padding(16.dp)) { RulerSlider("Качество", vm.quality, 50..100, "%", vm::applyQuality) }
            }
            HorizontalDivider(color = s.outline.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            Column(Modifier.padding(16.dp)) {
                Text("Пропорция", fontSize = 15.sp, color = s.onSurface)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RATIOS.forEachIndexed { i, r -> SmallPill(r.label, vm.ratioIndex == i) { vm.setDefaultRatio(i) } }
                }
            }
        }

        SectionLabel("КАДР")
        GroupCard {
            RowItem("Сетка третей", trailing = { IosSwitch(vm.showGrid, vm::setGridDefault) })
            HorizontalDivider(color = s.outline.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            RowItem("Полосы 16:9", "Леттербокс при экспорте", trailing = { IosSwitch(vm.letterbox, vm::setLetterboxDefault) })
        }

        SectionLabel("ПРИЛОЖЕНИЕ")
        GroupCard {
            RowItem("Об авторе", onClick = { vm.screen = Screen.ABOUT }) {
                Icon(Icons.Rounded.ChevronRight, null, tint = s.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            HorizontalDivider(color = s.outline.copy(alpha = 0.4f), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            RowItem("Лицензионное соглашение", onClick = { vm.screen = Screen.LICENSE }) {
                Icon(Icons.Rounded.ChevronRight, null, tint = s.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        Text("Cinema Crop ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = s.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp), textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(s.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenHeader("Об авторе", onBack)
        Text("CINEMA CROP", color = s.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp)
        Text("Версия ${BuildConfig.VERSION_NAME} · редакция 17-11-2026", color = s.onSurfaceVariant, fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp))
        GroupCard {
            Column(Modifier.padding(16.dp)) {
                Text("Автор и разработчик", fontSize = 12.sp, color = s.onSurfaceVariant)
                Text(AUTHOR_NAME, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = s.onSurface)
                Text(AUTHOR_CONTACT, color = s.primary, fontSize = 14.sp)
            }
        }
        Text(ABOUT_TEXT, fontSize = 14.sp, lineHeight = 21.sp, color = s.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
        SectionLabel("ТЕХНОЛОГИИ")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Kotlin", "Jetpack Compose", "RAW preview", "BitmapRegionDecoder").forEach { SmallPill(it, false) {} }
        }
        Text("© 2026 $AUTHOR_NAME. Все права защищены.", color = s.onSurfaceVariant, fontSize = 12.sp,
            modifier = Modifier.padding(top = 20.dp, bottom = 24.dp))
    }
}

@Composable
fun LicenseScreen(gate: Boolean, acceptedAt: String?, onAccept: () -> Unit, onDecline: () -> Unit, onBack: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(s.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        if (gate) Text("Лицензионное соглашение", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = s.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        else ScreenHeader("Лицензионное соглашение", onBack)
        if (gate) Text("Перед началом работы прими условия.", color = s.onSurfaceVariant, fontSize = 13.sp)
        GroupCard(Modifier.weight(1f).padding(vertical = 12.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(EULA_TEXT, fontSize = 13.sp, lineHeight = 19.sp, color = s.onSurfaceVariant)
            }
        }
        if (gate) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
            GlassAction("Отказаться", onDecline, Modifier.weight(1f))
            PrimaryPill("Принимаю", onAccept, Modifier.weight(1f))
        } else Text("Принято: ${acceptedAt?.ifBlank { "—" } ?: "—"} · редакция $EULA_VERSION",
            color = s.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}