package com.example.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Rainbow particles ka 3D gola: pure black par, dots se bana kinara, ghoomta, jhilmilata,
// awaaz ke saath phoolta. mode: 0 chup, 1 sun raha, 2 soch raha, 3 bol raha. level: mic ki awaaz 0..1
class OrbView(ctx: Context) : View(ctx) {
    var mode = 0
    var level = 0f

    private val n = 26000
    private val px = FloatArray(n)
    private val py = FloatArray(n)
    private val pz = FloatArray(n)
    private val hb = FloatArray(n)
    private val a1 = FloatArray(n)
    private val a2 = FloatArray(n)
    private val a3 = FloatArray(n)
    private val ts = FloatArray(n)
    private val tp = FloatArray(n)
    private val sp = FloatArray(n)
    private val ib = FloatArray(n)
    private val dk = FloatArray(n)
    private val lut = FloatArray(1024 * 3)

    private var bmp: Bitmap? = null
    private var pix = IntArray(0)
    private var acc = FloatArray(0)
    private var s = 0
    private var baseR = 0f
    private var rs = 0.5f          // gola half resolution par banta hai, phir bada hota hai (smooth + tez)
    private val dst = Rect()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glow = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN); alpha = 90 }

    private var t = 0f
    private var smooth = 0f
    private var last = 0L

    init {
        // lal, crimson, narangi, peela, hara, cyan, neela, baingani, magenta, wapas lal
        val stops = arrayOf(
            intArrayOf(255, 24, 24), intArrayOf(225, 20, 72), intArrayOf(255, 128, 0), intArrayOf(255, 225, 0),
            intArrayOf(40, 235, 60), intArrayOf(0, 222, 232), intArrayOf(30, 90, 255), intArrayOf(150, 60, 255),
            intArrayOf(255, 40, 205), intArrayOf(255, 24, 24))
        for (i in 0 until 1024) {
            val p = i / 1023f * (stops.size - 1)
            val k = min(floor(p).toInt(), stops.size - 2)
            val f = p - k
            for (c in 0 until 3) lut[i * 3 + c] = (stops[k][c] + (stops[k + 1][c] - stops[k][c]) * f) / 255f
        }
        val rnd = Random(7)
        for (i in 0 until n) {
            val u = rnd.nextFloat() * 2f - 1f
            val ph = rnd.nextFloat() * 6.2832f
            val sq = sqrt(1f - u * u)
            val dx = sq * cos(ph)
            val dz = sq * sin(ph)
            val rad = 1f - 0.5f * Math.pow(rnd.nextDouble(), 2.0).toFloat() + (rnd.nextFloat() - 0.5f) * 0.02f
            px[i] = dx * rad; py[i] = u * rad; pz[i] = dz * rad
            hb[i] = 2f * (atan2(dz, dx) / 6.2832f + 0.5f) + 0.3f * u
            a1[i] = rnd.nextFloat() * 6.2832f; a2[i] = rnd.nextFloat() * 6.2832f; a3[i] = rnd.nextFloat() * 6.2832f
            ts[i] = 0.8f + rnd.nextFloat() * 5.5f
            tp[i] = rnd.nextFloat() * 6.2832f
            sp[i] = if (rnd.nextFloat() < 0.04f) 0.6f + rnd.nextFloat() * 1.6f else 0f
            ib[i] = 0.7f + rnd.nextFloat() * 0.6f
            dk[i] = rnd.nextFloat()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val m = min(w, h).toFloat()
        baseR = m * 0.34f * rs
        s = max(16, Math.ceil((baseR * 2f * 1.35f).toDouble()).toInt())
        acc = FloatArray(s * s * 3)
        pix = IntArray(s * s)
        bmp?.recycle()
        bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val size = (s / rs).toInt()
        val cx = w / 2
        val cy = (h * 0.44f).toInt()
        dst.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
    }

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else min((now - last) / 1e9f, 0.05f)
        last = now
        t += dt

        val target = when (mode) {
            1 -> level
            3 -> 0.35f + 0.3f * (0.5f + 0.5f * sin(t * 7f)) * (0.6f + 0.4f * sin(t * 3.1f))
            2 -> 0.12f + 0.08f * sin(t * 4f)
            else -> 0.03f
        }
        smooth += (target - smooth) * (if (target > smooth) 0.3f else 0.1f)

        val breath = 1f + 0.03f * sin(t * 1.2f) + 0.012f * sin(t * 2.9f) + smooth * 0.2f
        val rr = baseR * breath
        val pulse = 0.88f + 0.12f * sin(t * 1.7f + 1f) + 0.05f * sin(t * 4.1f) + smooth * 0.45f
        val spin = 0.22f + (if (mode == 2) 0.6f else smooth * 0.7f)
        yaw += dt * spin
        val tilt = 0.38f + 0.08f * sin(t * 0.3f)
        val cY = cos(yaw); val sY = sin(yaw); val cT = cos(tilt); val sT = sin(tilt)
        val flow = t * 0.035f
        val half = s / 2f
        val o = 0.024f
        val sw = s
        val spread = smooth * 0.3f

        java.util.Arrays.fill(acc, 0f)
        for (i in 0 until n) {
            val d = 1f + spread * dk[i]
            val x = (px[i] + o * sin(t * 0.8f + a1[i])) * d
            val y = (py[i] + o * sin(t * 0.95f + a2[i])) * d
            val z = (pz[i] + o * sin(t * 0.7f + a3[i])) * d
            val x1 = x * cY + z * sY
            val z1 = -x * sY + z * cY
            val y2 = y * cT - z1 * sT
            val z2 = y * sT + z1 * cT
            val pr = 1f / (1f - z2 * 0.16f)
            val sx = half + x1 * rr * pr
            val sy = half + y2 * rr * pr
            if (sx < 1f || sy < 1f || sx > s - 2f || sy > s - 2f) continue

            val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * ts[i] + tp[i]))
            var g = 0f
            if (sp[i] > 0f) {
                val q = sin(t * sp[i] + tp[i] * 3f)
                if (q > 0f) { val q2 = q * q; val q4 = q2 * q2; g = q4 * q4 * q4 * 2.4f }
            }
            val inten = ((0.4f + 0.6f * (z2 + 1f) * 0.5f) * tw * pulse + g) * ib[i] * 2.6f

            var hh = 0.5f + x1 * 0.46f + y2 * 0.12f + 0.08f * hb[i] + flow
            hh -= floor(hh)
            val li = ((hh * 1023f).toInt()) * 3
            val ix = sx.toInt(); val iy = sy.toInt()
            val fx = sx - ix; val fy = sy - iy
            val w00 = (1f - fx) * (1f - fy) * inten
            val w10 = fx * (1f - fy) * inten
            val w01 = (1f - fx) * fy * inten
            val w11 = fx * fy * inten
            val r = lut[li]; val gc = lut[li + 1]; val bl = lut[li + 2]
            val p0 = (iy * sw + ix) * 3
            val p1 = p0 + 3
            val p2 = p0 + sw * 3
            val p3 = p2 + 3
            acc[p0] += r * w00; acc[p0 + 1] += gc * w00; acc[p0 + 2] += bl * w00
            acc[p1] += r * w10; acc[p1 + 1] += gc * w10; acc[p1 + 2] += bl * w10
            acc[p2] += r * w01; acc[p2 + 1] += gc * w01; acc[p2 + 2] += bl * w01
            acc[p3] += r * w11; acc[p3 + 1] += gc * w11; acc[p3 + 2] += bl * w11
        }

        // roshni 1 tak dabao, rang ka anupaat bacha kar
        val gain = 3.2f
        val total = s * s
        var a = 0
        for (j in 0 until total) {
            val r = acc[a]; val g = acc[a + 1]; val bl = acc[a + 2]
            a += 3
            val m = if (r > g) (if (r > bl) r else bl) else (if (g > bl) g else bl)
            if (m < 0.0015f) { pix[j] = Color.BLACK; continue }
            val k = 255f * gain / (1f + gain * m)
            val ri = min(255, (r * k).toInt())
            val gi = min(255, (g * k).toInt())
            val bi = min(255, (bl * k).toInt())
            pix[j] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        b.setPixels(pix, 0, s, 0, 0, s, s)

        c.drawColor(Color.BLACK)
        c.drawBitmap(b, null, dst, paint)
        // halka neon glow: wahi tasveer thodi badi karke screen blend
        val gr = Rect(dst.left - dst.width() / 30, dst.top - dst.height() / 30, dst.right + dst.width() / 30, dst.bottom + dst.height() / 30)
        c.drawBitmap(b, null, gr, glow)

        postInvalidateOnAnimation()
    }

    private var yaw = 0f
}
