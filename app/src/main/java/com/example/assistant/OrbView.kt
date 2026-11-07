package com.example.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// Rainbow particles ka 3D gola (pure black par). Awaaz ke hisaab se thoda phoolta aur chamakta hai.
class OrbView(ctx: Context) : View(ctx) {
    // 0 chup, 1 sun raha, 2 soch raha, 3 bol raha
    @Volatile var mode = 0
    @Volatile var level = 0f          // mic ki awaaz 0..1

    private val cnt = 24000
    private val px = FloatArray(cnt)
    private val py = FloatArray(cnt)
    private val pz = FloatArray(cnt)
    private val hb = FloatArray(cnt)
    private val a1 = FloatArray(cnt)
    private val a2 = FloatArray(cnt)
    private val a3 = FloatArray(cnt)
    private val ts = FloatArray(cnt)
    private val tp = FloatArray(cnt)
    private val sp = FloatArray(cnt)
    private val ib = FloatArray(cnt)

    private val lut = FloatArray(1024 * 3)

    private var side = 0
    private var rad = 1f
    private var gain = 3.2f
    private var cx = 0f
    private var cy = 0f
    private var drawSize = 0f
    private var acc = FloatArray(0)
    private var pixels = IntArray(0)
    @Volatile private var front: Bitmap? = null
    private var bufs: Array<Bitmap>? = null
    private var bi = 0
    private val swapLock = Any()
    @Volatile private var running = false
    private var worker: Thread? = null
    private val dst = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private var t = 0f
    private var smooth = 0f
    private var last = 0L

    init {
        // lal, crimson, narangi, peela, hara, cyan, neela, baingani, magenta, wapas lal
        val stops = arrayOf(
            intArrayOf(255, 24, 24), intArrayOf(225, 20, 72), intArrayOf(255, 128, 0),
            intArrayOf(255, 225, 0), intArrayOf(40, 235, 60), intArrayOf(0, 222, 232),
            intArrayOf(30, 90, 255), intArrayOf(150, 60, 255), intArrayOf(255, 40, 205),
            intArrayOf(255, 24, 24))
        for (i in 0 until 1024) {
            val p = i / 1023f * (stops.size - 1)
            val k = minOf(floor(p).toInt(), stops.size - 2)
            val f = p - k
            for (c in 0 until 3) {
                lut[i * 3 + c] = (stops[k][c] + (stops[k + 1][c] - stops[k][c]) * f) / 255f
            }
        }
        val rnd = Random(7)
        val twoPi = (PI * 2).toFloat()
        for (i in 0 until cnt) {
            val u = rnd.nextFloat() * 2f - 1f
            val ph = rnd.nextFloat() * twoPi
            val s = sqrt(1f - u * u)
            val dx = s * cos(ph)
            val dz = s * sin(ph)
            // zyada tar kinare ke paas (gola bhara dikhe), thode andar bhi
            val r = 1f - 0.5f * rnd.nextFloat().pow(2f) + (rnd.nextFloat() - 0.5f) * 0.02f
            px[i] = dx * r
            py[i] = u * r
            pz[i] = dz * r
            hb[i] = 2f * (atan2(dz, dx) / twoPi + 0.5f) + 0.3f * u
            a1[i] = rnd.nextFloat() * twoPi
            a2[i] = rnd.nextFloat() * twoPi
            a3[i] = rnd.nextFloat() * twoPi
            ts[i] = 0.8f + rnd.nextFloat() * 5.5f
            tp[i] = rnd.nextFloat() * twoPi
            sp[i] = if (rnd.nextFloat() < 0.04f) 0.6f + rnd.nextFloat() * 1.6f else 0f
            ib[i] = 0.7f + rnd.nextFloat() * 0.6f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        val rScreen = minOf(w, h) * 0.253f
        val sScreen = rScreen * 2f * 1.35f
        val rr = minOf(1f, 640f / sScreen)
        side = (sScreen * rr).toInt().coerceAtLeast(16)
        rad = rScreen * rr
        drawSize = sScreen
        cx = w / 2f
        cy = h * 0.51f
        val sd = side
        acc = FloatArray(sd * sd * 3)
        pixels = IntArray(sd * sd)
        synchronized(swapLock) {
            bufs = Array(3) { Bitmap.createBitmap(sd, sd, Bitmap.Config.ARGB_8888) }
            bi = 0
            front = null
        }
        val density = cnt / (PI.toFloat() * rad * rad)
        gain = 3.2f * 0.218f / density
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        worker = Thread {
            while (running) {
                val t0 = System.nanoTime()
                val arr = bufs
                val nxt = (bi + 1) % 3
                val b = arr?.get(nxt)
                if (b != null && !b.isRecycled) {
                    try {
                        render(b)
                        synchronized(swapLock) {
                            if (bufs === arr) { bi = nxt; front = b }
                        }
                        postInvalidateOnAnimation()
                    } catch (e: Exception) { }
                }
                val spent = (System.nanoTime() - t0) / 1_000_000L
                if (spent < 14) try { Thread.sleep(14 - spent) } catch (e: InterruptedException) { }
            }
        }.also { it.priority = Thread.NORM_PRIORITY - 1; it.start() }
    }

    override fun onDetachedFromWindow() {
        running = false
        worker?.interrupt()
        worker = null
        super.onDetachedFromWindow()
    }

    // touch gole ke andar hai ya nahi (bahar ka touch peeche ki list ko milega)
    fun hit(x: Float, y: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        val r = rad * 1.3f
        return dx * dx + dy * dy <= r * r
    }

    override fun onDraw(c: Canvas) {
        val b = front ?: return
        val hs = drawSize / 2f
        dst.set(cx - hs, cy - hs, cx + hs, cy + hs)
        c.drawBitmap(b, null, dst, paint)
    }

    private fun render(b: Bitmap) {
        val side = this.side
        val acc = this.acc
        val pixels = this.pixels
        if (acc.size < side * side * 3 || pixels.size < side * side || b.width != side) return
        val now = System.nanoTime()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceAtMost(0.05f)
        last = now
        t += dt

        // abhi kitni "awaaz" hai
        val tg = when (mode) {
            1 -> level.coerceIn(0f, 1f)
            3 -> 0.3f + 0.3f * (0.5f + 0.5f * sin(t * 7f)) * (0.6f + 0.4f * sin(t * 3.1f))
            2 -> 0.1f + 0.05f * sin(t * 4f)
            else -> 0.02f
        }
        smooth += (tg - smooth) * (if (tg > smooth) 0.35f else 0.12f)

        val breath = 1f + 0.03f * sin(t * 1.2f) + 0.012f * sin(t * 2.9f) + 0.12f * smooth
        val r = rad * breath
        val pulse = 0.88f + 0.12f * sin(t * 1.7f + 1f) + 0.05f * sin(t * 4.1f) + 0.5f * smooth
        val yaw = t * 0.22f
        val tilt = 0.38f + 0.08f * sin(t * 0.3f)
        val cY = cos(yaw)
        val sY = sin(yaw)
        val cT = cos(tilt)
        val sT = sin(tilt)
        val flow = t * 0.035f
        val half = side / 2f
        val o = 0.024f
        val inten0 = 3.2f * (0.218f / (cnt / (PI.toFloat() * rad * rad)))
        val s2 = side - 2
        acc.fill(0f)

        for (i in 0 until cnt) {
            val x = px[i] + o * sin(t * 0.8f + a1[i])
            val y = py[i] + o * sin(t * 0.95f + a2[i])
            val z = pz[i] + o * sin(t * 0.7f + a3[i])
            val x1 = x * cY + z * sY
            val z1 = -x * sY + z * cY
            val y2 = y * cT - z1 * sT
            val z2 = y * sT + z1 * cT
            val pr = 1f / (1f - z2 * 0.16f)
            val sx = half + x1 * r * pr
            val sy = half + y2 * r * pr
            if (sx < 1f || sy < 1f || sx > s2 || sy > s2) continue

            val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * ts[i] + tp[i]))
            var g = 0f
            if (sp[i] > 0f) {
                val q = sin(t * sp[i] + tp[i] * 3f)
                if (q > 0f) g = q.pow(12f) * 2.4f
            }
            val inten = ((0.4f + 0.3f * (z2 + 1f)) * tw * pulse + g) * ib[i] * inten0

            var hh = 0.5f + x1 * 0.46f + y2 * 0.12f + 0.08f * hb[i] + flow
            hh -= floor(hh)
            val li = (hh * 1023f).toInt() * 3
            val ix = sx.toInt()
            val iy = sy.toInt()
            val fx = sx - ix
            val fy = sy - iy
            val w00 = (1f - fx) * (1f - fy) * inten
            val w10 = fx * (1f - fy) * inten
            val w01 = (1f - fx) * fy * inten
            val w11 = fx * fy * inten
            val cr = lut[li]
            val cg = lut[li + 1]
            val cb = lut[li + 2]
            val p0 = (iy * side + ix) * 3
            val p1 = p0 + 3
            val p2 = p0 + side * 3
            val p3 = p2 + 3
            acc[p0] += cr * w00; acc[p0 + 1] += cg * w00; acc[p0 + 2] += cb * w00
            acc[p1] += cr * w10; acc[p1 + 1] += cg * w10; acc[p1 + 2] += cb * w10
            acc[p2] += cr * w01; acc[p2 + 1] += cg * w01; acc[p2 + 2] += cb * w01
            acc[p3] += cr * w11; acc[p3 + 1] += cg * w11; acc[p3 + 2] += cb * w11
        }

        // roshni ko 1 tak dabao, rang ka anupaat bacha kar
        val gTone = 3.2f
        val total = side * side
        var a = 0
        for (j in 0 until total) {
            val rr = acc[a]
            val gg = acc[a + 1]
            val bb = acc[a + 2]
            a += 3
            val m = if (rr > gg) (if (rr > bb) rr else bb) else (if (gg > bb) gg else bb)
            if (m < 0.0015f) {
                pixels[j] = 0
            } else {
                val k = 255f * gTone / (1f + gTone * m)
                var ri = (rr * k).toInt(); if (ri > 255) ri = 255
                var gi = (gg * k).toInt(); if (gi > 255) gi = 255
                var bi = (bb * k).toInt(); if (bi > 255) bi = 255
                val mx = maxOf(ri, maxOf(gi, bi)).coerceAtLeast(1)
                // alpha = chamak; rang poori tarah chamakdar. Kaale par pehle jaisa hi dikhega
                val rf = ri * 255 / mx
                val gf = gi * 255 / mx
                val bf = bi * 255 / mx
                pixels[j] = (mx shl 24) or (rf shl 16) or (gf shl 8) or bf
            }
        }
        b.setPixels(pixels, 0, side, 0, 0, side, side)
    }
}
