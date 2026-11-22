package com.example.cinemacrop

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object RawPreview {

    data class Result(val jpeg: ByteArray, val orientation: Int)

    private const val MAX_CANDIDATES = 16
    private const val MAX_JPEG = 32L * 1024 * 1024

    fun extract(context: Context, uri: Uri): Result? {
        val tmp = File(context.cacheDir, "raw_tmp.bin")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { input.copyTo(it, 1 shl 16) }
            } ?: return null
            RandomAccessFile(tmp, "r").use { raf ->
                val header = ByteArray(16)
                raf.seek(0); raf.readFully(header)
                if (String(header, 0, 8, Charsets.US_ASCII) == "FUJIFILM") rafPreview(raf)?.let { return it }
                val le = header[0] == 'I'.code.toByte() && header[1] == 'I'.code.toByte()
                val be = header[0] == 'M'.code.toByte() && header[1] == 'M'.code.toByte()
                if (le || be) {
                    val magic = u16(header, 2, le)
                    if (magic == 42 || magic == 0x4F52 || magic == 0x5352) tiffPreview(raf, le)?.let { return it }
                }
                scanPreview(raf)
            }
        } catch (_: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    private fun rafPreview(raf: RandomAccessFile): Result? {
        val b = ByteArray(8)
        raf.seek(84); raf.readFully(b)
        val off = u32(b, 0, false); val len = u32(b, 4, false)
        if (off <= 0 || len < 64 || off + len > raf.length() || len > MAX_JPEG) return null
        val jpeg = ByteArray(len.toInt())
        raf.seek(off); raf.readFully(jpeg)
        if (jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) return null
        return Result(jpeg, 1)
    }

    private fun tiffPreview(raf: RandomAccessFile, le: Boolean): Result? = runCatching {
        val size = raf.length()
        val candidates = ArrayList<LongArray>()
        val queue = ArrayDeque<Long>()
        val visited = HashSet<Long>()
        var orientation = 1
        queue.add(readU32(raf, 4, le))
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 24) {
            val off = queue.removeFirst()
            if (off <= 0 || off + 2 > size || !visited.add(off)) continue
            val n = readU16(raf, off, le)
            if (n <= 0 || n > 512 || off + 2 + n * 12L + 4 > size) continue
            val entries = ByteArray(n * 12)
            raf.seek(off + 2); raf.readFully(entries)
            var jpegOff = 0L; var jpegLen = 0L
            var stripOff = 0L; var stripLen = 0L; var compression = -1
            for (i in 0 until n) {
                val b = i * 12
                val tag = u16(entries, b, le)
                val type = u16(entries, b + 2, le)
                val count = u32(entries, b + 4, le)
                fun inline(): Long = if (type == 3) u16(entries, b + 8, le).toLong() else u32(entries, b + 8, le)
                fun array(cap: Int): List<Long> {
                    val cnt = count.toInt().coerceIn(1, cap)
                    val fits = (type == 3 && cnt <= 2) || (type == 4 && cnt <= 1)
                    val base = if (fits) -1L else u32(entries, b + 8, le)
                    return (0 until cnt).map { k ->
                        when {
                            fits && type == 3 -> u16(entries, b + 8 + k * 2, le).toLong()
                            fits -> u32(entries, b + 8 + k * 4, le)
                            type == 3 -> readU16(raf, base + k * 2L, le).toLong()
                            else -> readU32(raf, base + k * 4L, le)
                        }
                    }
                }
                when (tag) {
                    274 -> if (orientation == 1) orientation = inline().toInt().coerceIn(1, 8)
                    513 -> jpegOff = inline()
                    514 -> jpegLen = inline()
                    273 -> stripOff = array(4096).firstOrNull() ?: 0L
                    279 -> stripLen = array(4096).sum()
                    259 -> compression = inline().toInt()
                    330 -> array(8).forEach { queue.add(it) }
                }
            }
            if (jpegOff > 0 && jpegLen > 0) candidates.add(longArrayOf(jpegOff, jpegLen))
            if (compression == 6 && stripOff > 0 && stripLen > 0) candidates.add(longArrayOf(stripOff, stripLen))
            val next = readU32(raf, off + 2 + n * 12L, le)
            if (next > 0) queue.add(next)
        }
        pickBest(raf, candidates)?.let { Result(it, orientation) }
    }.getOrNull()

    private fun scanPreview(raf: RandomAccessFile): Result? = runCatching {
        val size = raf.length()
        val limit = minOf(size, 96L * 1024 * 1024)
        val buf = ByteArray(1 shl 20)
        val offs = ArrayList<Long>()
        var pos = 0L
        while (pos < limit && offs.size < 24) {
            raf.seek(pos)
            val n = raf.read(buf, 0, minOf(buf.size.toLong(), limit - pos).toInt())
            if (n < 3) break
            for (i in 0..n - 3) {
                if (buf[i] == 0xFF.toByte() && buf[i + 1] == 0xD8.toByte() && buf[i + 2] == 0xFF.toByte()) {
                    offs.add(pos + i)
                    if (offs.size >= 24) break
                }
            }
            pos += n - 2
        }
        pickBest(raf, offs.map { longArrayOf(it, minOf(size - it, MAX_JPEG)) })?.let { Result(it, 1) }
    }.getOrNull()

    private fun pickBest(raf: RandomAccessFile, cands: List<LongArray>): ByteArray? {
        val size = raf.length()
        var bestOff = -1L; var bestLen = 0L; var bestArea = 0L
        for ((i, c) in cands.withIndex()) {
            if (i >= MAX_CANDIDATES) break
            val off = c[0]; val len = c[1]
            if (off < 0 || len < 64 || off + len > size || len > MAX_JPEG) continue
            val head = ByteArray(minOf(len, 512L * 1024).toInt())
            raf.seek(off); raf.readFully(head)
            if (head[0] != 0xFF.toByte() || head[1] != 0xD8.toByte()) continue
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(head, 0, head.size, o)
            val area = o.outWidth.toLong() * o.outHeight
            if (o.outWidth > 0 && area > bestArea) { bestArea = area; bestOff = off; bestLen = len }
        }
        if (bestOff < 0) return null
        val full = ByteArray(bestLen.toInt())
        raf.seek(bestOff); raf.readFully(full)
        return full
    }

    private fun u16(b: ByteArray, o: Int, le: Boolean): Int =
        if (le) (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8)
        else ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)

    private fun u32(b: ByteArray, o: Int, le: Boolean): Long =
        if (le) u16(b, o, true).toLong() or (u16(b, o + 2, true).toLong() shl 16)
        else (u16(b, o, false).toLong() shl 16) or u16(b, o + 2, false).toLong()

    private fun readU16(raf: RandomAccessFile, off: Long, le: Boolean): Int {
        val b = ByteArray(2); raf.seek(off); raf.readFully(b); return u16(b, 0, le)
    }

    private fun readU32(raf: RandomAccessFile, off: Long, le: Boolean): Long {
        val b = ByteArray(4); raf.seek(off); raf.readFully(b); return u32(b, 0, le)
    }
}
