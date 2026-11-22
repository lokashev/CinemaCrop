package com.example.cinemacrop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

private class Fit(val ox: Float, val oy: Float, val w: Float, val h: Float) {
    fun screen(c: CropRect) = Rect(ox + c.left * w, oy + c.top * h, ox + c.right * w, oy + c.bottom * h)
    fun norm(r: Rect) = CropRect(
        ((r.left - ox) / w).coerceIn(0f, 1f), ((r.top - oy) / h).coerceIn(0f, 1f),
        ((r.right - ox) / w).coerceIn(0f, 1f), ((r.bottom - oy) / h).coerceIn(0f, 1f)
    )
}

private fun fit(size: Size, img: ImageBitmap): Fit {
    val s = min(size.width / img.width, size.height / img.height)
    val w = img.width * s; val h = img.height * s
    return Fit((size.width - w) / 2f, (size.height - h) / 2f, w, h)
}

@Composable
fun CropCanvas(
    image: ImageBitmap,
    crop: CropRect,
    ratio: Float,
    showGrid: Boolean,
    accent: Color,
    adj: Adjustments,
    letterbox: Boolean,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier,
    onGestureStart: () -> Unit = {}
) {
    val curCrop by rememberUpdatedState(crop)
    val curRatio by rememberUpdatedState(ratio)
    val onChange by rememberUpdatedState(onCropChange)
    val onStart by rememberUpdatedState(onGestureStart)
    val touch = with(LocalDensity.current) { 24.dp.toPx() }
    val minSide = with(LocalDensity.current) { 44.dp.toPx() }
    val grade = remember(adj) { if (adj.isNeutral) null else ColorFilter.colorMatrix(uiColorMatrix(adj)) }

    Canvas(modifier.pointerInput(image) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val sr0 = fit(size.toSize(), image).screen(curCrop)
            val corners = listOf(sr0.topLeft, sr0.topRight, sr0.bottomLeft, sr0.bottomRight)
            var mode = corners.indexOfFirst { (it - down.position).getDistance() <= touch }
            if (mode < 0) mode = if (sr0.contains(down.position)) 4 else -1
            if (mode < 0) return@awaitEachGesture
            onStart()

            var prev = down.position
            var prevDist = 0f
            var prevCenter = down.position
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                val f = fit(size.toSize(), image)
                if (pressed.size >= 2) {
                    val a = pressed[0].position; val b = pressed[1].position
                    val dist = (a - b).getDistance()
                    val center = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                    if (prevDist > 0f && dist > 0f)
                        onChange(pinch(curCrop, f, dist / prevDist, center - prevCenter, minSide))
                    prevDist = dist; prevCenter = center; mode = 5
                } else {
                    val pos = pressed[0].position
                    if (mode == 5) { mode = 4; prev = pos }
                    if (mode in 0..3) onChange(resizeCorner(curCrop, curRatio, f, mode, pos, minSide))
                    else onChange(moveCrop(curCrop, f, pos - prev))
                    prev = pos; prevDist = 0f
                }
                event.changes.forEach { it.consume() }
            }
        }
    }) {
        val f = fit(size, image)
        drawImage(
            image,
            srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(f.ox.roundToInt(), f.oy.roundToInt()),
            dstSize = IntSize(f.w.roundToInt(), f.h.roundToInt()),
            colorFilter = grade,
            filterQuality = FilterQuality.Medium
        )
        val sr = f.screen(crop)
        val ir = Rect(f.ox, f.oy, f.ox + f.w, f.oy + f.h)
        val dim = Color.Black.copy(alpha = 0.55f)

        if (letterbox) {
            val target = if (sr.height > sr.width) 9f / 16f else 16f / 9f
            val cur = sr.width / sr.height
            val fw: Float; val fh: Float
            if (cur > target) { fw = sr.width; fh = sr.width / target } else { fh = sr.height; fw = sr.height * target }
            val fr = Rect(sr.center.x - fw / 2, sr.center.y - fh / 2, sr.center.x + fw / 2, sr.center.y + fh / 2)
            val dr = Rect(max(ir.left, fr.left), max(ir.top, fr.top), min(ir.right, fr.right), min(ir.bottom, fr.bottom))
            drawRect(dim, Offset(ir.left, ir.top), Size(ir.width, max(0f, dr.top - ir.top)))
            drawRect(dim, Offset(ir.left, dr.bottom), Size(ir.width, max(0f, ir.bottom - dr.bottom)))
            drawRect(dim, Offset(ir.left, dr.top), Size(max(0f, dr.left - ir.left), dr.height))
            drawRect(dim, Offset(dr.right, dr.top), Size(max(0f, ir.right - dr.right), dr.height))
            drawRect(Color.Black, Offset(fr.left, fr.top), Size(fr.width, max(0f, sr.top - fr.top)))
            drawRect(Color.Black, Offset(fr.left, sr.bottom), Size(fr.width, max(0f, fr.bottom - sr.bottom)))
            drawRect(Color.Black, Offset(fr.left, sr.top), Size(max(0f, sr.left - fr.left), sr.height))
            drawRect(Color.Black, Offset(sr.right, sr.top), Size(max(0f, fr.right - sr.right), sr.height))
        } else {
            drawRect(dim, Offset(ir.left, ir.top), Size(ir.width, max(0f, sr.top - ir.top)))
            drawRect(dim, Offset(ir.left, sr.bottom), Size(ir.width, max(0f, ir.bottom - sr.bottom)))
            drawRect(dim, Offset(ir.left, sr.top), Size(max(0f, sr.left - ir.left), sr.height))
            drawRect(dim, Offset(sr.right, sr.top), Size(max(0f, ir.right - sr.right), sr.height))
        }

        if (adj.vignette > 0) {
            drawRect(
                Brush.radialGradient(
                    colorStops = arrayOf(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = vignetteAlpha(adj))),
                    center = sr.center, radius = max(sr.width, sr.height) * 0.75f
                ),
                topLeft = sr.topLeft, size = sr.size
            )
        }

        if (showGrid) {
            val gc = Color.White.copy(alpha = 0.28f); val gw = 1.dp.toPx()
            for (i in 1..2) {
                val x = sr.left + sr.width * i / 3f
                drawLine(gc, Offset(x, sr.top), Offset(x, sr.bottom), gw)
                val y = sr.top + sr.height * i / 3f
                drawLine(gc, Offset(sr.left, y), Offset(sr.right, y), gw)
            }
        }
        drawRect(Color.White.copy(alpha = 0.9f), sr.topLeft, sr.size, style = Stroke(1.dp.toPx()))

        val len = 16.dp.toPx(); val th = 3.dp.toPx()
        fun corner(p: Offset, dx: Float, dy: Float) {
            drawLine(accent, p, Offset(p.x + dx * len, p.y), th, StrokeCap.Round)
            drawLine(accent, p, Offset(p.x, p.y + dy * len), th, StrokeCap.Round)
        }
        corner(sr.topLeft, 1f, 1f); corner(sr.topRight, -1f, 1f)
        corner(sr.bottomLeft, 1f, -1f); corner(sr.bottomRight, -1f, -1f)
    }
}

private fun moveCrop(c: CropRect, f: Fit, d: Offset): CropRect {
    val l = (c.left + d.x / f.w).coerceIn(0f, 1f - c.width)
    val t = (c.top + d.y / f.h).coerceIn(0f, 1f - c.height)
    return CropRect(l, t, l + c.width, t + c.height)
}

private fun resizeCorner(c: CropRect, ratio: Float, f: Fit, idx: Int, p: Offset, minSide: Float): CropRect {
    val sr = f.screen(c)
    val anchor = when (idx) { 0 -> sr.bottomRight; 1 -> sr.bottomLeft; 2 -> sr.topRight; else -> sr.topLeft }
    val dirX = if (idx == 1 || idx == 3) 1f else -1f
    val dirY = if (idx >= 2) 1f else -1f
    val dx = (p.x - anchor.x) * dirX
    val dy = (p.y - anchor.y) * dirY
    val availW = if (dirX > 0) f.ox + f.w - anchor.x else anchor.x - f.ox
    val availH = if (dirY > 0) f.oy + f.h - anchor.y else anchor.y - f.oy
    val maxW = min(availW, availH * ratio)
    val minW = min(max(minSide, minSide * ratio), maxW)
    val w = max(max(dx, dy * ratio), minW).coerceAtMost(maxW)
    val h = w / ratio
    val cx = anchor.x + dirX * w; val cy = anchor.y + dirY * h
    return f.norm(Rect(min(anchor.x, cx), min(anchor.y, cy), max(anchor.x, cx), max(anchor.y, cy)))
}

private fun pinch(c: CropRect, f: Fit, zoom: Float, pan: Offset, minSide: Float): CropRect {
    val maxZ = min(1f / c.width, 1f / c.height)
    val minZ = min(max(minSide / (f.w * c.width), minSide / (f.h * c.height)), maxZ)
    val z = zoom.coerceIn(minZ, maxZ)
    val nw = c.width * z; val nh = c.height * z
    val cx = (c.left + c.right) / 2f + pan.x / f.w
    val cy = (c.top + c.bottom) / 2f + pan.y / f.h
    val l = (cx - nw / 2f).coerceIn(0f, 1f - nw)
    val t = (cy - nh / 2f).coerceIn(0f, 1f - nh)
    return CropRect(l, t, l + nw, t + nh)
}