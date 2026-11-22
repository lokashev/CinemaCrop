package com.example.cinemacrop

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class ExportFormat(val label: String, val mime: String, val ext: String, val compress: Bitmap.CompressFormat) {
    JPEG("JPEG", "image/jpeg", "jpg", Bitmap.CompressFormat.JPEG),
    PNG("PNG", "image/png", "png", Bitmap.CompressFormat.PNG)
}

data class SavedFile(val uri: Uri, val mime: String, val format: ExportFormat)

data class LoadedImage(
    val preview: ImageBitmap,
    val fullWidth: Int,
    val fullHeight: Int,
    val orientation: Int,
    val uri: Uri,
    val sourceUri: Uri,
    val isRaw: Boolean,
    val note: String
)

object ImageOps {

    private val RAW_EXTS = setOf("dng", "cr2", "cr3", "nef", "nrw", "arw", "srf", "sr2", "raf", "orf", "rw2", "pef", "srw", "raw", "iiq", "3fr")

    fun clearCache(context: Context) {
        File(context.cacheDir, "raw_preview").deleteRecursively()
    }

    fun maxCrop(w: Int, h: Int, ratio: Float): CropRect {
        val ir = w.toFloat() / h
        return if (ir > ratio) {
            val nw = ratio / ir; val l = (1f - nw) / 2f
            CropRect(l, 0f, l + nw, 1f)
        } else {
            val nh = ir / ratio; val t = (1f - nh) / 2f
            CropRect(0f, t, 1f, t + nh)
        }
    }

    fun load(context: Context, uri: Uri): LoadedImage? {
        val cr = context.contentResolver
        val ext = displayName(context, uri).substringAfterLast('.', "").lowercase()
        val bounds = decodeBounds(cr, uri)
        if (bounds != null && ext !in RAW_EXTS) return loadNormal(cr, uri, uri, false, "")

        val extracted = RawPreview.extract(context, uri)
        if (extracted != null) {
            val dir = File(context.cacheDir, "raw_preview").apply { deleteRecursively(); mkdirs() }
            val f = File(dir, "preview_${System.currentTimeMillis()}.jpg")
            f.writeBytes(extracted.jpeg)
            val fileUri = Uri.fromFile(f)
            val b = decodeBounds(cr, fileUri) ?: return null
            return loadNormal(cr, uri, fileUri, true, "${ext.uppercase()} · превью камеры", extracted.orientation, b)
        }
        if (bounds != null) return loadNormal(cr, uri, uri, false, "")
        return null
    }

    private fun loadNormal(
        cr: ContentResolver,
        originalUri: Uri,
        sourceUri: Uri,
        isRaw: Boolean,
        note: String,
        forcedOrientation: Int = 1,
        boundsIn: BitmapFactory.Options? = null
    ): LoadedImage? {
        val exifO = cr.openInputStream(sourceUri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: 1
        val orientation = if (exifO != 1) exifO else forcedOrientation

        val bounds = boundsIn ?: decodeBounds(cr, sourceUri) ?: return null
        val rawW = bounds.outWidth; val rawH = bounds.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sample = 1
        while (max(rawW, rawH) / (sample * 2) >= 2048) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = cr.openInputStream(sourceUri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val upright = applyExif(raw, orientation)
        val swap = orientation >= ExifInterface.ORIENTATION_TRANSPOSE
        return LoadedImage(
            upright.asImageBitmap(),
            if (swap) rawH else rawW, if (swap) rawW else rawH,
            orientation, originalUri, sourceUri, isRaw, note
        )
    }

    fun export(context: Context, img: LoadedImage, crop: CropRect, letterbox: Boolean, format: ExportFormat, quality: Int, adj: Adjustments): SavedFile? {
        val W = img.fullWidth; val H = img.fullHeight
        val l = (crop.left * W).roundToInt().coerceIn(0, W - 1)
        val t = (crop.top * H).roundToInt().coerceIn(0, H - 1)
        val r = (crop.right * W).roundToInt().coerceIn(l + 1, W)
        val b = (crop.bottom * H).roundToInt().coerceIn(t + 1, H)

        val swap = img.orientation >= ExifInterface.ORIENTATION_TRANSPOSE
        val rawW = if (swap) H else W
        val rawH = if (swap) W else H
        val region = mapRegion(img.orientation, l, t, r, b, rawW, rawH)

        var decoded: Bitmap? = null
        context.contentResolver.openInputStream(img.sourceUri)?.use { stream ->
            val decoder = if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(stream)
            else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(stream, false)
            if (decoder != null) {
                for (s in intArrayOf(1, 2, 4)) {
                    try {
                        decoded = decoder.decodeRegion(region, BitmapFactory.Options().apply {
                            inSampleSize = s
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        })
                        break
                    } catch (_: OutOfMemoryError) { }
                }
                decoder.recycle()
            }
        }
        var result = decoded ?: return null
        result = applyExif(result, img.orientation)

        if (!adj.isNeutral) {
            val graded = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            val cv = Canvas(graded)
            cv.drawBitmap(result, 0f, 0f, Paint().apply {
                isFilterBitmap = true
                colorFilter = ColorMatrixColorFilter(gColorMatrix(adj))
            })
            if (adj.vignette > 0) {
                val a = (vignetteAlpha(adj) * 255).toInt().coerceIn(0, 255)
                cv.drawRect(0f, 0f, graded.width.toFloat(), graded.height.toFloat(), Paint().apply {
                    shader = RadialGradient(
                        graded.width / 2f, graded.height / 2f,
                        max(graded.width, graded.height) * 0.75f,
                        intArrayOf(0x00000000, a shl 24), floatArrayOf(0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                })
            }
            result.recycle(); result = graded
        }

        if (letterbox) {
            val lb = makeLetterbox(result)
            if (lb != result) { result.recycle(); result = lb }
        }
        if (format == ExportFormat.JPEG && result.hasAlpha()) {
            val flat = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(Color.BLACK); drawBitmap(result, 0f, 0f, null) }
            result.recycle(); result = flat
        }

        val uri = saveImage(context, result, format, quality)
        result.recycle()
        return uri?.let { SavedFile(it, format.mime, format) }
    }

    private fun decodeBounds(cr: ContentResolver, uri: Uri): BitmapFactory.Options? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        return if (o.outWidth > 0 && o.outHeight > 0) o else null
    }

    private fun displayName(context: Context, uri: Uri): String {
        var name = uri.lastPathSegment ?: ""
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
        }
        return name
    }

    private fun mapRegion(o: Int, l: Int, t: Int, r: Int, b: Int, rawW: Int, rawH: Int): Rect = when (o) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Rect(rawW - r, t, rawW - l, b)
        ExifInterface.ORIENTATION_ROTATE_180 -> Rect(rawW - r, rawH - b, rawW - l, rawH - t)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Rect(l, rawH - b, r, rawH - t)
        ExifInterface.ORIENTATION_TRANSPOSE -> Rect(t, l, b, r)
        ExifInterface.ORIENTATION_ROTATE_90 -> Rect(t, rawH - r, b, rawH - l)
        ExifInterface.ORIENTATION_TRANSVERSE -> Rect(rawW - b, rawH - r, rawW - t, rawH - l)
        ExifInterface.ORIENTATION_ROTATE_270 -> Rect(rawW - b, l, rawW - t, r)
        else -> Rect(l, t, r, b)
    }

    private fun applyExif(b: Bitmap, o: Int): Bitmap {
        val m = exifMatrix(o) ?: return b
        val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        if (r != b) b.recycle()
        return r
    }

    private fun exifMatrix(o: Int): Matrix? = when (o) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { setRotate(-90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
        else -> null
    }

    private fun makeLetterbox(b: Bitmap): Bitmap {
        val target = if (b.height > b.width) 9f / 16f else 16f / 9f
        val cur = b.width.toFloat() / b.height
        if (abs(cur - target) < 0.001f) return b
        val cw: Int; val ch: Int
        if (cur > target) { cw = b.width; ch = (b.width / target).roundToInt() }
        else { ch = b.height; cw = (b.height * target).roundToInt() }
        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.BLACK)
            drawBitmap(b, (cw - b.width) / 2f, (ch - b.height) / 2f, null)
        }
        return out
    }

    private fun saveImage(context: Context, b: Bitmap, format: ExportFormat, quality: Int): Uri? {
        val name = "CinemaCrop_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + "." + format.ext
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CinemaCrop")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val cr = context.contentResolver
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val q = if (format == ExportFormat.PNG) 100 else quality.coerceIn(50, 100)
        val ok = cr.openOutputStream(uri)?.use { b.compress(format.compress, q, it) } ?: false
        if (!ok) { cr.delete(uri, null, null); return null }
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
        cr.update(uri, values, null, null)
        return uri
    }
}