package com.example.cinemacrop

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.content.edit

enum class ThemeMode(val label: String) { SYSTEM("Системная"), DARK("Тёмная"), LIGHT("Светлая"), AMOLED("AMOLED") }

enum class Accent(val label: String, val color: Color) {
    WHITE("Белый", Color(0xFFF5F5F7)),
    ORANGE("Оранж", Color(0xFFFF6B2C)),
    GOLD("Золото", Color(0xFFE8B44A)),
    AZURE("Лазурь", Color(0xFF5AB8FF)),
    EMERALD("Изумруд", Color(0xFF4FD08A))
}

fun Color.onAccent(): Color = if (luminance() > 0.5f) Color.Black else Color.White

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cinema_crop_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("theme", ThemeMode.DARK.name)!!) }.getOrDefault(ThemeMode.DARK)
        set(v) = sp.edit { putString("theme", v.name) }

    var accent: Accent
        get() = runCatching { Accent.valueOf(sp.getString("accent", Accent.WHITE.name)!!) }.getOrDefault(Accent.WHITE)
        set(v) = sp.edit { putString("accent", v.name) }

    var defaultRatio: Int
        get() = sp.getInt("ratio", 0)
        set(v) = sp.edit { putInt("ratio", v) }

    var defaultFormat: ExportFormat
        get() = runCatching { ExportFormat.valueOf(sp.getString("format", ExportFormat.PNG.name)!!) }.getOrDefault(ExportFormat.PNG)
        set(v) = sp.edit { putString("format", v.name) }

    var defaultQuality: Int
        get() = sp.getInt("quality", 95)
        set(v) = sp.edit { putInt("quality", v) }

    var gridDefault: Boolean
        get() = sp.getBoolean("grid", true)
        set(v) = sp.edit { putBoolean("grid", v) }

    var letterboxDefault: Boolean
        get() = sp.getBoolean("letterbox", false)
        set(v) = sp.edit { putBoolean("letterbox", v) }

    var eulaVersion: Int
        get() = sp.getInt("eula_version", 0)
        set(v) = sp.edit { putInt("eula_version", v) }

    var eulaDate: String
        get() = sp.getString("eula_date", "") ?: ""
        set(v) = sp.edit { putString("eula_date", v) }
}
