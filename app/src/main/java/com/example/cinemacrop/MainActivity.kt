package com.example.cinemacrop

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            CinemaTheme(vm) { Root(vm) }
        }
    }
}

@Composable
fun CinemaTheme(vm: MainViewModel, content: @Composable () -> Unit) {
    val dark = when (vm.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        else -> true
    }
    val a = vm.accent.color
    val scheme = when {
        vm.themeMode == ThemeMode.AMOLED -> darkColorScheme(primary = a, onPrimary = a.onAccent(),
            background = Color.Black, surface = Color(0xFF101012), surfaceVariant = Color(0xFF1A1A1D),
            onBackground = Color(0xFFF5F5F7), onSurface = Color(0xFFF5F5F7), onSurfaceVariant = Color(0xFF8E8E93), outline = Color(0xFF2C2C2E))
        dark -> darkColorScheme(primary = a, onPrimary = a.onAccent(),
            background = Color(0xFF0A0A0B), surface = Color(0xFF141416), surfaceVariant = Color(0xFF1C1C1F),
            onBackground = Color(0xFFF5F5F7), onSurface = Color(0xFFF5F5F7), onSurfaceVariant = Color(0xFF8E8E93), outline = Color(0xFF2C2C2E))
        else -> lightColorScheme(primary = a, onPrimary = a.onAccent(),
            background = Color(0xFFF2F2F7), surface = Color.White, surfaceVariant = Color(0xFFE9E9EE),
            onBackground = Color(0xFF1C1C1E), onSurface = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF6E6E73), outline = Color(0xFFD9D9DE))
    }
    val activity = LocalContext.current as ComponentActivity
    val view = LocalView.current
    SideEffect {
        WindowInsetsControllerCompat(activity.window, view).isAppearanceLightStatusBars = !dark
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun Root(vm: MainViewModel) {
    val activity = LocalContext.current as ComponentActivity
    if (vm.eulaGate) {
        LicenseScreen(true, null, vm::acceptEula, { activity.finish() }, {})
        return
    }
    BackHandler(vm.screen != Screen.EDITOR) { vm.back() }
    Crossfade(vm.screen, label = "nav") { s ->
        when (s) {
            Screen.EDITOR -> EditorScreen(vm)
            Screen.SETTINGS -> SettingsScreen(vm) { vm.back() }
            Screen.ABOUT -> AboutScreen { vm.back() }
            Screen.LICENSE -> LicenseScreen(false, vm.eulaDate, {}, {}) { vm.back() }
        }
    }
}

@Composable
fun EditorScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val s = MaterialTheme.colorScheme
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.pick(it) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.pick(it) }
    val openPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val openFile = { filePicker.launch(arrayOf("image/*", "application/octet-stream")) }
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(0) }
    var colorSel by remember { mutableStateOf(0) }
    var showOpenMenu by remember { mutableStateOf(false) }

    LaunchedEffect(vm.saved) {
        val f = vm.saved ?: return@LaunchedEffect
        val res = snackbar.showSnackbar("${f.format.label} сохранён в Pictures/CinemaCrop",
            actionLabel = "Поделиться", duration = SnackbarDuration.Long)
        if (res == SnackbarResult.ActionPerformed) {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = f.mime; putExtra(Intent.EXTRA_STREAM, f.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, null))
        }
        vm.consumeSaved()
    }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it); vm.consumeError() } }
    LaunchedEffect(vm.notice) { vm.notice?.let { snackbar.showSnackbar(it, duration = SnackbarDuration.Short); vm.consumeNotice() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = s.background) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.image == null) {
                    Text("CINEMA CROP", color = s.primary, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                        fontSize = 16.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.weight(1f))
                } else {
                    Box {
                        GlassCircle(Icons.Rounded.PhotoLibrary, "Открыть другое фото", onClick = { showOpenMenu = true })
                        DropdownMenu(expanded = showOpenMenu, onDismissRequest = { showOpenMenu = false }, containerColor = s.surface) {
                            DropdownMenuItem(
                                text = { Text("Фото из галереи", fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)) },
                                onClick = { showOpenMenu = false; openPhoto() }
                            )
                            DropdownMenuItem(
                                text = { Text("RAW / файл", fontSize = 14.sp) },
                                leadingIcon = { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)) },
                                onClick = { showOpenMenu = false; openFile() }
                            )
                        }
                    }
                    GlassCircle(Icons.Rounded.Undo, "Отменить", vm.canUndo, vm::undo)
                    GlassCircle(Icons.Rounded.Redo, "Повторить", vm.canRedo, vm::redo)
                    Spacer(Modifier.weight(1f))
                    PrimaryPill(if (vm.saving) "…" else "Экспорт", vm::save, enabled = !vm.saving, height = 40.dp)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                val img = vm.image
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(s.surfaceVariant)) {
                    when {
                        vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = s.primary, strokeWidth = 2.dp)
                        img == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Кадр как в кино", color = s.onSurface, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text("Пропорции настоящего кино, цвет и экспорт в полном разрешении",
                                color = s.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 36.dp))
                            Spacer(Modifier.height(24.dp))
                            PrimaryPill("Выбрать фото", openPhoto)
                            Spacer(Modifier.height(6.dp))
                            Text("или открыть RAW / файл", color = s.onSurfaceVariant, fontSize = 13.sp,
                                modifier = Modifier.clip(CircleShape).clickable(onClick = openFile).padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                        else -> {
                            CropCanvas(
                                image = img.preview, crop = vm.crop, ratio = vm.effectiveRatio,
                                showGrid = vm.showGrid, accent = s.primary,
                                adj = vm.adj, letterbox = vm.letterbox,
                                onCropChange = { vm.crop = it }, onGestureStart = vm::beginCropGesture,
                                modifier = Modifier.fillMaxSize()
                            )
                            val w = (img.fullWidth * vm.crop.width).roundToInt()
                            val h = (img.fullHeight * vm.crop.height).roundToInt()
                            GlassPill(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                                Text("${vm.ratio.label} · $w×$h${if (img.isRaw) " · RAW" else ""}",
                                    fontSize = 12.sp, color = s.onSurface.copy(alpha = 0.9f), maxLines = 1, softWrap = false)
                            }
                            Box(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                                GlassCircle(Icons.Rounded.RestartAlt, "Сбросить всё", onClick = vm::resetAll)
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(s.surface)) {
                Crossfade(tab, label = "tool") { t ->
                    when (t) {
                        0 -> LazyRow(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                            itemsIndexed(RATIOS) { i, r -> RatioCard(r, vm.ratioIndex == i) { vm.setRatio(i) } }
                        }
                        1 -> Row(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            ToolButton(Icons.Rounded.GridOn, "Сетка", vm.showGrid) { vm.showGrid = !vm.showGrid }
                            ToolButton(Icons.Rounded.FitScreen, "Полосы", vm.letterbox) { vm.letterbox = !vm.letterbox }
                            ToolButton(Icons.Rounded.SwapVert, "Вертикально", vm.portrait, vm::toggleOrientation)
                            ToolButton(Icons.Rounded.RestartAlt, "Сброс", false, vm::resetAll)
                        }
                        2 -> Column(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(listOf("Экспозиция", "Контраст", "Насыщенность", "Температура", "Виньетка")) { i, n ->
                                    SmallPill(n, colorSel == i) { colorSel = i }
                                }
                            }
                            when (colorSel) {
                                0 -> RulerSlider("Экспозиция", vm.adj.exposure, -20..20, "",
                                    { vm.adj = vm.adj.copy(exposure = it) }, display = { "${it / 10f} EV" })
                                1 -> RulerSlider("Контраст", vm.adj.contrast, -100..100, "",
                                    { vm.adj = vm.adj.copy(contrast = it) })
                                2 -> RulerSlider("Насыщенность", vm.adj.saturation, -100..100, "",
                                    { vm.adj = vm.adj.copy(saturation = it) })
                                3 -> RulerSlider("Температура", vm.adj.temperature, -100..100, "",
                                    { vm.adj = vm.adj.copy(temperature = it) })
                                else -> RulerSlider("Виньетка", vm.adj.vignette, 0..100, "",
                                    { vm.adj = vm.adj.copy(vignette = it) })
                            }
                        }
                        else -> Column(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Segmented(listOf("JPEG", "PNG"), if (vm.format == ExportFormat.JPEG) 0 else 1,
                                { vm.applyFormat(if (it == 0) ExportFormat.JPEG else ExportFormat.PNG) }, Modifier.fillMaxWidth())
                            if (vm.format == ExportFormat.JPEG) RulerSlider("Качество", vm.quality, 50..100, "%", vm::applyQuality)
                            else Text("PNG · без потерь, альфа-канал сохраняется", fontSize = 13.sp, color = s.onSurfaceVariant)
                            PrimaryPill(
                                if (vm.saving) "Сохранение…" else "Сохранить в галерею",
                                vm::save,
                                Modifier.fillMaxWidth(),
                                enabled = vm.image != null && !vm.saving,
                                height = 48.dp
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    listOf(
                        Triple(Icons.Rounded.Crop, "Кадр", 0),
                        Triple(Icons.Rounded.Tune, "Инструменты", 1),
                        Triple(Icons.Rounded.Palette, "Цвет", 2),
                        Triple(Icons.Rounded.IosShare, "Экспорт", 3)
                    ).forEach { (icon, label, i) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { tab = i }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, label, tint = if (tab == i) s.primary else s.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(label, fontSize = 10.sp, color = if (tab == i) s.onSurface else s.onSurfaceVariant, maxLines = 1, softWrap = false)
                        }
                    }
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.screen = Screen.SETTINGS }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Settings, "Ещё", tint = s.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(3.dp))
                        Text("Ещё", fontSize = 10.sp, color = s.onSurfaceVariant, maxLines = 1, softWrap = false)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}