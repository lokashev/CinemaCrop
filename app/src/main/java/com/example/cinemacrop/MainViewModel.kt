package com.example.cinemacrop

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CinemaRatio(val label: String, val sub: String, val value: Float)

val RATIOS = listOf(
    CinemaRatio("2.39:1", "Scope", 2.39f),
    CinemaRatio("2.35:1", "Scope classic", 2.35f),
    CinemaRatio("2.20:1", "70 мм", 2.20f),
    CinemaRatio("2.00:1", "Univisium", 2.00f),
    CinemaRatio("1.85:1", "Flat", 1.85f),
    CinemaRatio("16:9", "HDTV", 16f / 9f),
    CinemaRatio("4:3", "Классика", 4f / 3f),
    CinemaRatio("1:1", "Квадрат", 1f)
)

enum class Screen { EDITOR, SETTINGS, ABOUT, LICENSE }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var screen by mutableStateOf(Screen.EDITOR)
    var eulaGate by mutableStateOf(prefs.eulaVersion < EULA_VERSION); private set
    var eulaDate by mutableStateOf(prefs.eulaDate); private set

    var themeMode by mutableStateOf(prefs.themeMode); private set
    var accent by mutableStateOf(prefs.accent); private set

    var image by mutableStateOf<LoadedImage?>(null); private set
    var crop by mutableStateOf(CropRect(0f, 0f, 1f, 1f))
    var adj by mutableStateOf(Adjustments())
    var ratioIndex by mutableStateOf(prefs.defaultRatio.coerceIn(RATIOS.indices)); private set
    var portrait by mutableStateOf(false); private set
    var showGrid by mutableStateOf(prefs.gridDefault)
    var letterbox by mutableStateOf(prefs.letterboxDefault)
    var format by mutableStateOf(prefs.defaultFormat)
    var quality by mutableStateOf(prefs.defaultQuality)
    var loading by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var saved by mutableStateOf<SavedFile?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set

    private data class Snap(val crop: CropRect, val adj: Adjustments)
    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    val ratio: CinemaRatio get() = RATIOS[ratioIndex]
    val effectiveRatio: Float get() = if (portrait) 1f / ratio.value else ratio.value

    init { ImageOps.clearCache(app) }

    fun setTheme(m: ThemeMode) { themeMode = m; prefs.themeMode = m }
    fun applyAccent(a: Accent) { accent = a; prefs.accent = a }
    fun setDefaultRatio(i: Int) { prefs.defaultRatio = i; setRatio(i) }
    fun applyFormat(f: ExportFormat) { format = f; prefs.defaultFormat = f }
    fun applyQuality(q: Int) { quality = q; prefs.defaultQuality = q }
    fun setGridDefault(b: Boolean) { showGrid = b; prefs.gridDefault = b }
    fun setLetterboxDefault(b: Boolean) { letterbox = b; prefs.letterboxDefault = b }

    private fun pushHistory() {
        undoStack.addLast(Snap(crop, adj))
        if (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        canUndo = true; canRedo = false
    }

    fun beginCropGesture() = pushHistory()

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(Snap(crop, adj))
        val s = undoStack.removeLast()
        crop = s.crop; adj = s.adj
        canUndo = undoStack.isNotEmpty(); canRedo = true
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(Snap(crop, adj))
        val s = redoStack.removeLast()
        crop = s.crop; adj = s.adj
        canRedo = redoStack.isNotEmpty(); canUndo = true
    }

    fun acceptEula() {
        prefs.eulaVersion = EULA_VERSION
        prefs.eulaDate = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(Date())
        eulaDate = prefs.eulaDate
        eulaGate = false
        screen = Screen.EDITOR
    }

    fun back() {
        screen = when (screen) {
            Screen.ABOUT, Screen.LICENSE -> Screen.SETTINGS
            else -> Screen.EDITOR
        }
    }

    fun pick(uri: Uri?) {
        if (uri == null) return
        runCatching {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModelScope.launch {
            loading = true
            val res = withContext(Dispatchers.IO) {
                runCatching { ImageOps.load(getApplication(), uri) }.getOrNull()
            }
            if (res == null) error = "Не удалось открыть файл. Для RAW нужно встроенное превью."
            else {
                image = res
                adj = Adjustments()
                undoStack.clear(); redoStack.clear(); canUndo = false; canRedo = false
                refit()
            }
            loading = false
        }
    }

    fun setRatio(i: Int) { pushHistory(); ratioIndex = i; refit() }
    fun toggleOrientation() { pushHistory(); portrait = !portrait; refit() }

    fun resetAll() {
        pushHistory()
        adj = Adjustments()
        refit()
        notice = "Кадр и цвет сброшены"
    }

    private fun refit() { image?.let { crop = ImageOps.maxCrop(it.fullWidth, it.fullHeight, effectiveRatio) } }

    fun save() {
        val img = image ?: return
        viewModelScope.launch {
            saving = true
            val res = withContext(Dispatchers.IO) {
                runCatching { ImageOps.export(getApplication(), img, crop, letterbox, format, quality, adj) }.getOrNull()
            }
            if (res == null) error = "Не удалось сохранить кадр" else saved = res
            saving = false
        }
    }

    fun consumeSaved() { saved = null }
    fun consumeError() { error = null }
    fun consumeNotice() { notice = null }
}