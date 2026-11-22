package com.example.cinemacrop


import android.graphics.ColorMatrix as GMatrix
import androidx.compose.ui.graphics.ColorMatrix as UiMatrix
import kotlin.math.pow

data class Adjustments(
    val exposure: Int = 0,      // -20..20  => -2..+2 EV
    val contrast: Int = 0,      // -100..100
    val saturation: Int = 0,    // -100..100
    val temperature: Int = 0,   // -100..100 (холод → тепло)
    val vignette: Int = 0       // 0..100
) {
    val isNeutral: Boolean
        get() = exposure == 0 && contrast == 0 && saturation == 0 && temperature == 0 && vignette == 0
}

fun vignetteAlpha(a: Adjustments): Float = a.vignette / 100f * 0.55f

/** Матрица для live-превью (Compose). */
fun uiColorMatrix(a: Adjustments): UiMatrix {
    val m = UiMatrix()
    m.setToSaturation(1f + a.saturation / 100f)
    val c = 1f + a.contrast / 100f
    val cm = UiMatrix()
    cm.setToScale(c, c, c, 1f)
    cm[0, 4] = (1 - c) * 128f; cm[1, 4] = (1 - c) * 128f; cm[2, 4] = (1 - c) * 128f
    m.timesAssign(cm)
    val e = 2f.pow(a.exposure / 10f)
    val em = UiMatrix(); em.setToScale(e, e, e, 1f); m.timesAssign(em)
    val w = a.temperature / 300f
    val tm = UiMatrix(); tm.setToScale(1 + w, 1f, 1 - w, 1f); m.timesAssign(tm)
    return m
}

/** Та же математика для экспорта (android.graphics). */
fun gColorMatrix(a: Adjustments): GMatrix {
    val m = GMatrix()
    m.setSaturation(1f + a.saturation / 100f)
    val c = 1f + a.contrast / 100f
    val off = (1 - c) * 128f
    m.postConcat(GMatrix(floatArrayOf(
        c, 0f, 0f, 0f, off,
        0f, c, 0f, 0f, off,
        0f, 0f, c, 0f, off,
        0f, 0f, 0f, 1f, 0f
    )))
    val e = 2f.pow(a.exposure / 10f)
    val em = GMatrix(); em.setScale(e, e, e, 1f); m.postConcat(em)
    val w = a.temperature / 300f
    m.postConcat(GMatrix(floatArrayOf(
        1 + w, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1 - w, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )))
    return m
}