package com.example.assistant

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Base64
import kotlin.math.max
import kotlin.math.sqrt

// Photo ko phone par hi bada aur saaf karna (jab Gemini ka image model free key par nahi chalta):
// bada karo (Full HD / 2K / 4K), phir dhaar (sharpen) aur halka rang-sudhar.
object Enhance {
    fun run(att: Pair<String, String>, prompt: String): Pair<Media.Out, String>? {
        val raw = Base64.decode(att.second, Base64.DEFAULT)
        val src = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        val low = prompt.lowercase()
        val (target, label) = when {
            Regex("\\b(4k|uhd|2160)").containsMatchIn(low) || low.contains("8k") -> 3840 to "4K"
            Regex("\\b(2k|1440)").containsMatchIn(low) -> 2560 to "2K"
            else -> 1920 to "Full HD"
        }
        val long = max(src.width, src.height).toFloat()
        var s = max(1f, target / long)
        val maxPx = 8_300_000f
        val px = src.width * s * src.height * s
        if (px > maxPx) s = sqrt(maxPx / (src.width.toFloat() * src.height))
        val w = (src.width * s).toInt().coerceAtLeast(src.width)
        val h = (src.height * s).toInt().coerceAtLeast(src.height)

        // 2x se zyada ek baar mein bada karne par dhundhla hota hai, isliye kadam-kadam se
        var cur = src
        while (cur.width * 2 < w && cur.height * 2 < h) {
            val n = Bitmap.createScaledBitmap(cur, cur.width * 2, cur.height * 2, true)
            if (cur !== src) cur.recycle()
            cur = n
        }
        // aakhri kadam: poora naap + halka contrast aur rang
        val cm = ColorMatrix().apply {
            val sat = ColorMatrix().apply { setSaturation(1.08f) }
            val c = 1.05f
            val t = 128f * (1f - c)
            val con = ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
            postConcat(sat); postConcat(con)
        }
        val big = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(big).drawBitmap(cur, null, android.graphics.Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
        if (cur !== src) cur.recycle()
        src.recycle()

        val out = sharpen(big, 0.38f)
        val bo = java.io.ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 95, bo)
        return Pair(Media.Out(bo.toByteArray(), "image/jpeg"), "$label (${w}x$h)")
    }

    // 3x3 dhaar badhane wala filter, ek-ek line karke (kam memory)
    private fun sharpen(b: Bitmap, a: Float): Bitmap {
        val w = b.width
        val h = b.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var prev = IntArray(w)
        var cur = IntArray(w)
        var next = IntArray(w)
        val res = IntArray(w)
        b.getPixels(cur, 0, w, 0, 0, w, 1)
        System.arraycopy(cur, 0, prev, 0, w)
        val k = 1f + 4f * a
        for (y in 0 until h) {
            if (y + 1 < h) b.getPixels(next, 0, w, 0, y + 1, w, 1) else System.arraycopy(cur, 0, next, 0, w)
            for (x in 0 until w) {
                val l = cur[if (x > 0) x - 1 else x]
                val r = cur[if (x + 1 < w) x + 1 else x]
                val c = cur[x]
                val u = prev[x]
                val d = next[x]
                var o = c and 0xFF000000.toInt()
                var sh = 16
                while (sh >= 0) {
                    val v = k * ((c shr sh) and 0xFF) - a * (((l shr sh) and 0xFF) + ((r shr sh) and 0xFF) + ((u shr sh) and 0xFF) + ((d shr sh) and 0xFF))
                    o = o or (v.toInt().coerceIn(0, 255) shl sh)
                    sh -= 8
                }
                res[x] = o
            }
            out.setPixels(res, 0, w, 0, y, w, 1)
            val t = prev; prev = cur; cur = next; next = t
        }
        b.recycle()
        return out
    }
}
