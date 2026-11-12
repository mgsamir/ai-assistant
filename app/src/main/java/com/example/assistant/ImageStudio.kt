package com.example.assistant

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.*

// Image viewer ke tools: Edit (AI), Markup (draw), Resize, Remove (AI)
object Studio {
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun dlg(c: Context): Dialog {
        val d = Dialog(c, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        d.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return d
    }

    fun load(f: File, max: Int = 1600): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (max(o.outWidth, o.outHeight) / s > max) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (e: Throwable) { null }

    fun write(dir: File, b: Bitmap): File {
        dir.mkdirs()
        val f = File(dir, "img_" + System.currentTimeMillis() + ".jpg")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 93, it) }
        return f
    }

    private fun b64(b: Bitmap): String {
        val s = min(1.0f, 1280f / max(b.width, b.height))
        val t = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
        val o = java.io.ByteArrayOutputStream()
        t.compress(Bitmap.CompressFormat.JPEG, 90, o)
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    private fun oval(c: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
    private fun pill(c: Context, t: String, bg: String = "#1A1A1A", onC: () -> Unit) = TextView(c).apply {
        text = t; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10))
        background = GradientDrawable().apply { cornerRadius = dp(c, 24).toFloat(); setColor(Color.parseColor(bg)) }
        setOnClickListener { onC() }
    }
    private fun backBtn(c: Context, onC: () -> Unit) = IconView(c).apply { kind = 23; background = oval(Color.parseColor("#1A1A1A")); setOnClickListener { onC() } }

    // AI se photo badalna: photo + likha hua kaam bhejta hai
    private fun ai(act: Activity, bmp: Bitmap, prompt: String, dir: File, done: (File?, String) -> Unit) {
        val sp = act.getSharedPreferences("p", Context.MODE_PRIVATE)
        val key = Keys.get(sp)
        if (key.isEmpty()) { done(null, "API key nahi mili"); return }
        thread {
            val out = try { Media.image(key, prompt, Pair("image/jpeg", b64(bmp)), Models.sel(sp, "photo")) } catch (e: Throwable) { null }
            val err = Media.lastErr
            var f: File? = null
            if (out != null) {
                try {
                    val ext = if (out.mime.contains("png")) "png" else if (out.mime.contains("webp")) "webp" else "jpg"
                    dir.mkdirs()
                    f = File(dir, "img_" + System.currentTimeMillis() + "." + ext).also { it.writeBytes(out.bytes) }
                } catch (e: Exception) { }
            }
            act.runOnUiThread { done(f, if (f == null) err.ifBlank { "Image nahi bani, dobara try karo" } else "") }
        }
    }

    private fun busyOverlay(c: Context, root: FrameLayout): View {
        val o = FrameLayout(c)
        o.setBackgroundColor(Color.parseColor("#CC000000"))
        o.isClickable = true
        val col = LinearLayout(c); col.orientation = LinearLayout.VERTICAL; col.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(ProgressBar(c))
        col.addView(TextView(c).apply { text = "Creating image..."; textSize = 16f; setTextColor(Color.WHITE); setPadding(0, dp(c, 12), 0, 0) })
        o.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        o.visibility = View.GONE
        root.addView(o, FrameLayout.LayoutParams(-1, -1))
        return o
    }

    // ---------- Resize: photo ko naye ratio mein (peeche ka hissa dhundhla karke bharta hai) ----------
    fun resize(f: File, ratio: Float, dir: File): File? {
        val b = load(f, 2400) ?: return null
        val bw = b.width.toFloat(); val bh = b.height.toFloat()
        val W: Int; val H: Int
        if (bw / bh < ratio) { H = bh.toInt(); W = (bh * ratio).toInt() } else { W = bw.toInt(); H = (bw / ratio).toInt() }
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val cover = max(W / bw, H / bh)
        val small = Bitmap.createScaledBitmap(b, max(1, (bw * cover / 24).toInt()), max(1, (bh * cover / 24).toInt()), true)
        val bigW = (bw * cover); val bigH = (bh * cover)
        c.drawBitmap(small, null, RectF((W - bigW) / 2, (H - bigH) / 2, (W + bigW) / 2, (H + bigH) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        c.drawColor(Color.parseColor("#55000000"))
        val s = min(W / bw, H / bh)
        val w2 = bw * s; val h2 = bh * s
        c.drawBitmap(b, null, RectF((W - w2) / 2, (H - h2) / 2, (W + w2) / 2, (H + h2) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        return write(dir, out)
    }

    // ---------- Edit: general likho + photo par jagah chhoo kar khaas notes ----------
    fun edit(act: Activity, f: File, dir: File, done: (File) -> Unit) {
        val bmp = load(f, 1600) ?: return
        val d = dlg(act)
        val root = FrameLayout(act)
        root.setBackgroundColor(Color.BLACK)
        val col = LinearLayout(act); col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(act, 16), dp(act, 44), dp(act, 16), dp(act, 16))
        val top = FrameLayout(act)
        top.addView(backBtn(act) { d.dismiss() }, FrameLayout.LayoutParams(dp(act, 44), dp(act, 44), Gravity.START))
        top.addView(TextView(act).apply { text = "Edit image"; textSize = 19f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        col.addView(top, LinearLayout.LayoutParams(-1, dp(act, 48)))
        val box = FrameLayout(act)
        val iv = ImageView(act); iv.scaleType = ImageView.ScaleType.FIT_CENTER; iv.setImageBitmap(bmp)
        val pins = FrameLayout(act)
        box.addView(iv, FrameLayout.LayoutParams(-1, -1)); box.addView(pins, FrameLayout.LayoutParams(-1, -1))
        col.addView(box, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(act, 10) })
        col.addView(TextView(act).apply { text = "Photo par chhoo kar khaas edit note likho"; textSize = 14f; setTextColor(Color.parseColor("#B0B0B8")); gravity = Gravity.CENTER; setPadding(0, dp(act, 8), 0, dp(act, 8)) })
        val notes = ArrayList<Triple<Float, Float, String>>()
        fun redraw() {
            pins.removeAllViews()
            val vw = iv.width.toFloat(); val vh = iv.height.toFloat()
            val s = min(vw / bmp.width, vh / bmp.height)
            val dx = (vw - bmp.width * s) / 2; val dy = (vh - bmp.height * s) / 2
            notes.forEachIndexed { i, n ->
                pins.addView(TextView(act).apply {
                    text = "${i + 1}"; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = oval(Color.parseColor("#2F6BFF"))
                }, FrameLayout.LayoutParams(dp(act, 26), dp(act, 26)).apply { leftMargin = (dx + n.first * bmp.width * s).toInt() - dp(act, 13); topMargin = (dy + n.second * bmp.height * s).toInt() - dp(act, 13) })
            }
        }
        iv.setOnTouchListener { v, ev ->
            if (ev.action == MotionEvent.ACTION_UP) {
                val vw = v.width.toFloat(); val vh = v.height.toFloat()
                val s = min(vw / bmp.width, vh / bmp.height)
                val dx = (vw - bmp.width * s) / 2; val dy = (vh - bmp.height * s) / 2
                val px = (ev.x - dx) / (bmp.width * s); val py = (ev.y - dy) / (bmp.height * s)
                if (px in 0f..1f && py in 0f..1f) {
                    val et = EditText(act); et.hint = "Yahan kya badalna hai?"
                    android.app.AlertDialog.Builder(act).setTitle("Note ${notes.size + 1}").setView(et)
                        .setPositiveButton("Add") { _, _ -> val t = et.text.toString().trim(); if (t.isNotEmpty()) { notes.add(Triple(px, py, t)); redraw() } }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            true
        }
        val et = EditText(act).apply {
            hint = "Describe general edits"; setHintTextColor(Color.parseColor("#A8A8B0")); setTextColor(Color.WHITE); textSize = 17f; background = null; maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val busy = busyOverlay(act, root)
        val send = IconView(act).apply { kind = 4; tint = Color.WHITE; background = oval(Color.parseColor("#2F6BFF")) }
        send.setOnClickListener {
            val g = et.text.toString().trim()
            if (g.isEmpty() && notes.isEmpty()) { Toast.makeText(act, "Pehle likho kya badalna hai", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val sb = StringBuilder("Edit this image as asked and keep everything else unchanged. Return only the edited image.")
            if (g.isNotEmpty()) sb.append("\nGeneral edit: ").append(g)
            if (notes.isNotEmpty()) {
                sb.append("\nSpecific edits (position is percent from the left and from the top of the image):")
                notes.forEachIndexed { i, n -> sb.append("\n${i + 1}) at (${(n.first * 100).toInt()}%, ${(n.second * 100).toInt()}%): ${n.third}") }
            }
            busy.visibility = View.VISIBLE
            ai(act, bmp, sb.toString(), dir) { nf, err ->
                busy.visibility = View.GONE
                if (nf != null) { d.dismiss(); done(nf) } else Toast.makeText(act, err, Toast.LENGTH_LONG).show()
            }
        }
        val bar = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(act, 18), dp(act, 6), dp(act, 8), dp(act, 6))
            background = GradientDrawable().apply { cornerRadius = dp(act, 30).toFloat(); setColor(Color.parseColor("#1A1A1A")); setStroke(dp(act, 1), Color.parseColor("#262626")) }
            addView(et, LinearLayout.LayoutParams(0, -2, 1f)); addView(send, LinearLayout.LayoutParams(dp(act, 42), dp(act, 42)))
        }
        col.addView(bar, LinearLayout.LayoutParams(-1, -2))
        root.addView(col, 0, FrameLayout.LayoutParams(-1, -1))
        d.setContentView(root); d.show()
    }

    // ---------- Remove: jis cheez ko mitana hai us par brush, phir AI mita deta hai ----------
    fun remove(act: Activity, f: File, dir: File, done: (File) -> Unit) {
        val bmp = load(f, 1600) ?: return
        val d = dlg(act)
        val root = FrameLayout(act)
        val dv = DrawView(act, bmp, true)
        val col = LinearLayout(act); col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(act, 16), dp(act, 44), dp(act, 16), dp(act, 20))
        val top = FrameLayout(act)
        top.addView(backBtn(act) { d.dismiss() }, FrameLayout.LayoutParams(dp(act, 44), dp(act, 44), Gravity.START))
        top.addView(TextView(act).apply { text = "Remove"; textSize = 19f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        col.addView(top, LinearLayout.LayoutParams(-1, dp(act, 48)))
        col.addView(dv, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(act, 10) })
        col.addView(TextView(act).apply { text = "Jo cheez mitani hai uske upar ungli phero"; textSize = 14f; setTextColor(Color.parseColor("#B0B0B8")); gravity = Gravity.CENTER; setPadding(0, dp(act, 8), 0, dp(act, 8)) })
        val busy = busyOverlay(act, root)
        val row = LinearLayout(act); row.gravity = Gravity.CENTER_VERTICAL
        row.addView(IconView(act).apply { kind = 36; background = oval(Color.parseColor("#1A1A1A")); setOnClickListener { dv.undo() } }, LinearLayout.LayoutParams(dp(act, 48), dp(act, 48)))
        row.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(pill(act, "Remove", "#2F6BFF") {
            if (dv.strokes.isEmpty()) { Toast.makeText(act, "Pehle mitane wali cheez par brush phero", Toast.LENGTH_SHORT).show(); return@pill }
            busy.visibility = View.VISIBLE
            ai(act, dv.render(), "The areas painted in red mark objects or people to remove. Remove everything under the red paint and fill the area naturally so it matches the surrounding background. Do not leave any red color. Keep everything else in the image exactly unchanged. Return only the edited image.", dir) { nf, err ->
                busy.visibility = View.GONE
                if (nf != null) { d.dismiss(); done(nf) } else Toast.makeText(act, err, Toast.LENGTH_LONG).show()
            }
        })
        col.addView(row, LinearLayout.LayoutParams(-1, -2))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        d.setContentView(root); d.show()
    }

    // ---------- Markup: pen, shapes, eraser, rang, undo, save ----------
    fun markup(act: Activity, f: File, dir: File, done: (File) -> Unit) {
        val bmp = load(f, 2000) ?: return
        markupBmp(act, bmp, "Save") { nb -> done(write(dir, nb)) }
    }

    // koi bhi bitmap par pen / shapes / text; Done par naya bitmap lautata hai
    fun markupBmp(act: Activity, bmp: Bitmap, doneLabel: String, done: (Bitmap) -> Unit) {
        val d = dlg(act)
        val root = FrameLayout(act)
        val dv = DrawView(act, bmp, false)
        val col = LinearLayout(act); col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(act, 16), dp(act, 44), dp(act, 16), dp(act, 20))
        val top = FrameLayout(act)
        top.addView(backBtn(act) { d.dismiss() }, FrameLayout.LayoutParams(dp(act, 44), dp(act, 44), Gravity.START))
        val tools = LinearLayout(act); tools.gravity = Gravity.CENTER
        tools.background = GradientDrawable().apply { cornerRadius = dp(act, 30).toFloat(); setColor(Color.parseColor("#1A1A1A")) }
        fun tb(kind: Int, tool: String): IconView = IconView(act).apply { this.kind = kind }
        val pen = tb(32, "pen"); val shp = tb(37, "shape"); val ers = tb(34, "eraser"); val txt = tb(41, "text")
        fun sel(v: IconView?) { for (x in listOf(pen, shp, txt, ers)) x.background = if (x === v) oval(Color.parseColor("#4A4A4A")) else null }
        sel(pen)
        pen.setOnClickListener { dv.tool = "pen"; sel(pen) }
        txt.setOnClickListener { dv.tool = "text"; sel(txt) }
        dv.onTextTap = { x, y ->
            val et = android.widget.EditText(act)
            et.hint = "Text likho"
            et.setTextColor(Color.WHITE); et.setHintTextColor(Color.parseColor("#9A9AA2"))
            android.app.AlertDialog.Builder(act).setView(et)
                .setPositiveButton("OK") { _, _ -> val tx = et.text.toString().trim(); if (tx.isNotEmpty()) dv.addText(tx, x, y) }
                .setNegativeButton("Cancel", null).show()
        }
        ers.setOnClickListener { dv.tool = "eraser"; sel(ers) }
        shp.setOnClickListener {
            val names = listOf("line", "arrow", "rect", "circle", "triangle", "diamond", "star", "heart")
            val glyph = listOf("╱", "↗", "▭", "◯", "△", "◇", "☆", "♡")
            val grid = GridLayout(act); grid.columnCount = 4
            grid.background = GradientDrawable().apply { cornerRadius = dp(act, 20).toFloat(); setColor(Color.parseColor("#1A1A1A")) }
            grid.setPadding(dp(act, 8), dp(act, 8), dp(act, 8), dp(act, 8))
            val pw = PopupWindow(grid, dp(act, 300), -2, true)
            names.forEachIndexed { i, n ->
                grid.addView(TextView(act).apply {
                    text = glyph[i]; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                    setOnClickListener { dv.tool = n; sel(shp); pw.dismiss() }
                }, GridLayout.LayoutParams().apply { width = dp(act, 70); height = dp(act, 62) })
            }
            pw.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            pw.showAsDropDown(tools, 0, dp(act, 8))
        }
        for (x in listOf(pen, shp, txt, ers)) tools.addView(x, LinearLayout.LayoutParams(dp(act, 52), dp(act, 48)))
        top.addView(tools, FrameLayout.LayoutParams(-2, dp(act, 48), Gravity.CENTER))
        top.addView(pill(act, doneLabel) {
            if (dv.strokes.isEmpty()) { d.dismiss(); return@pill }
            val nb = dv.render(); d.dismiss(); done(nb)
        }, FrameLayout.LayoutParams(-2, dp(act, 44), Gravity.END))
        col.addView(top, LinearLayout.LayoutParams(-1, dp(act, 50)))
        col.addView(dv, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(act, 10); bottomMargin = dp(act, 10) })
        val row = LinearLayout(act); row.gravity = Gravity.CENTER_VERTICAL
        val colors = listOf("#FFFFFF", "#000000", "#9A9AA0", "#FF3B30", "#FF9500")
        val dots = ArrayList<View>()
        fun mark() { dots.forEach { v -> val cc = v.tag as Int; (v.background as GradientDrawable).setStroke(if (dv.color == cc) dp(act, 3) else 0, Color.parseColor("#8FB0FF")) } }
        val wheel = View(act)
        wheel.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA)).apply { shape = GradientDrawable.OVAL }
        wheel.setOnClickListener {
            val sb = SeekBar(act); sb.max = 360
            android.app.AlertDialog.Builder(act).setTitle("Rang chuno").setView(sb)
                .setPositiveButton("OK") { _, _ -> dv.color = Color.HSVToColor(floatArrayOf(sb.progress.toFloat(), 1f, 1f)); mark() }
                .setNegativeButton("Cancel", null).show()
        }
        row.addView(wheel, LinearLayout.LayoutParams(dp(act, 44), dp(act, 44)).apply { rightMargin = dp(act, 10) })
        for (c in colors) {
            val v = View(act)
            v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(c)) }
            v.setTag(Color.parseColor(c)); dots.add(v)
            v.setOnClickListener { dv.color = Color.parseColor(c); mark() }
            row.addView(v, LinearLayout.LayoutParams(dp(act, 44), dp(act, 44)).apply { rightMargin = dp(act, 10) })
        }
        row.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(IconView(act).apply { kind = 36; background = oval(Color.parseColor("#1A1A1A")); setOnClickListener { dv.undo() } }, LinearLayout.LayoutParams(dp(act, 48), dp(act, 48)))
        dv.color = Color.parseColor("#FF3B30"); mark()
        col.addView(row, LinearLayout.LayoutParams(-1, -2))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        d.setContentView(root); d.show()
    }
}

class DrawView(ctx: Context, val bmp: Bitmap, val mask: Boolean) : View(ctx) {
    class S(val kind: String, val color: Int, val w: Float) {
        val pts = ArrayList<PointF>(); var x1 = 0f; var y1 = 0f; var x2 = 0f; var y2 = 0f; var text = ""
    }
    val strokes = ArrayList<S>()
    var onTextTap: ((Float, Float) -> Unit)? = null
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; typeface = Typeface.DEFAULT_BOLD }
    private fun textSize() = max(bmp.width, bmp.height) * 0.045f

    fun addText(text: String, x: Float, y: Float) {
        tp.textSize = textSize()
        val st = S("text", color, 0f)
        st.text = text
        st.x1 = x; st.y1 = y - textSize(); st.x2 = x + tp.measureText(text); st.y2 = y
        strokes.add(st); invalidate()
    }
    var tool = "pen"
    var color = Color.RED
    private var cur: S? = null
    private val m = Matrix(); private val inv = Matrix()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)

    fun undo() { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.size - 1); invalidate() } }

    private fun lay() {
        val s = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        m.setScale(s, s); m.postTranslate((width - bmp.width * s) / 2, (height - bmp.height * s) / 2); m.invert(inv)
    }
    private fun base() = max(bmp.width, bmp.height) * (if (mask) 0.05f else 0.008f)

    private fun drawS(c: Canvas, s: S) {
        if (s.kind == "text") { tp.color = s.color; tp.textSize = textSize(); c.drawText(s.text, s.x1, s.y2, tp); return }
        p.color = s.color; p.strokeWidth = s.w
        if (s.kind == "pen") {
            if (s.pts.size == 1) { c.drawPoint(s.pts[0].x, s.pts[0].y, p); return }
            val path = Path(); path.moveTo(s.pts[0].x, s.pts[0].y)
            for (i in 1 until s.pts.size) path.lineTo(s.pts[i].x, s.pts[i].y)
            c.drawPath(path, p); return
        }
        val l = min(s.x1, s.x2); val r = max(s.x1, s.x2); val t = min(s.y1, s.y2); val b = max(s.y1, s.y2)
        val cx = (l + r) / 2; val cy = (t + b) / 2; val w = r - l; val h = b - t
        when (s.kind) {
            "line" -> c.drawLine(s.x1, s.y1, s.x2, s.y2, p)
            "arrow" -> {
                c.drawLine(s.x1, s.y1, s.x2, s.y2, p)
                val a = atan2(s.y2 - s.y1, s.x2 - s.x1); val len = s.w * 5
                for (da in listOf(2.6f, -2.6f)) c.drawLine(s.x2, s.y2, s.x2 + len * cos(a + da), s.y2 + len * sin(a + da), p)
            }
            "rect" -> c.drawRect(l, t, r, b, p)
            "circle" -> c.drawOval(RectF(l, t, r, b), p)
            "triangle" -> c.drawPath(Path().apply { moveTo(cx, t); lineTo(r, b); lineTo(l, b); close() }, p)
            "diamond" -> c.drawPath(Path().apply { moveTo(cx, t); lineTo(r, cy); lineTo(cx, b); lineTo(l, cy); close() }, p)
            "star" -> {
                val R = min(w, h) / 2; val path = Path()
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) R else R * 0.42f; val a = -PI / 2 + i * PI / 5
                    val x = cx + (rad * cos(a)).toFloat(); val y = cy + (rad * sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close(); c.drawPath(path, p)
            }
            "heart" -> c.drawPath(Path().apply {
                moveTo(cx, t + h * 0.3f); cubicTo(cx, t, l, t, l, t + h * 0.35f); cubicTo(l, t + h * 0.65f, cx, t + h * 0.8f, cx, b)
                cubicTo(cx, t + h * 0.8f, r, t + h * 0.65f, r, t + h * 0.35f); cubicTo(r, t, cx, t, cx, t + h * 0.3f)
            }, p)
        }
    }

    override fun onDraw(c: Canvas) {
        lay(); c.drawBitmap(bmp, m, bp)
        c.save(); c.concat(m)
        for (s in strokes) drawS(c, s)
        cur?.let { drawS(c, it) }
        c.restore()
    }

    fun render(): Bitmap {
        val o = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(o)
        for (s in strokes) drawS(c, s)
        return o
    }

    private fun hit(s: S, x: Float, y: Float, tol: Float): Boolean {
        if (s.kind == "pen") return s.pts.any { hypot(it.x - x, it.y - y) < tol }
        return x >= min(s.x1, s.x2) - tol && x <= max(s.x1, s.x2) + tol && y >= min(s.y1, s.y2) - tol && y <= max(s.y1, s.y2) + tol
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pt = floatArrayOf(e.x, e.y); inv.mapPoints(pt)
        val x = pt[0]; val y = pt[1]
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (tool == "eraser" && !mask) {
                    val i = strokes.indexOfLast { hit(it, x, y, base() * 3) }
                    if (i >= 0) { strokes.removeAt(i); invalidate() }
                    return true
                }
                if (tool == "text" && !mask) {
                    if (e.action == MotionEvent.ACTION_DOWN) onTextTap?.invoke(x, y)
                    return true
                }
                if (e.action == MotionEvent.ACTION_DOWN) {
                    val kind = if (mask) "pen" else tool
                    val col = if (mask) Color.argb(170, 255, 40, 40) else color
                    cur = S(kind, col, base()).also { it.pts.add(PointF(x, y)); it.x1 = x; it.y1 = y; it.x2 = x; it.y2 = y }
                } else cur?.let { it.x2 = x; it.y2 = y; if (it.kind == "pen") it.pts.add(PointF(x, y)) }
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { cur?.let { strokes.add(it) }; cur = null; invalidate() }
        }
        return true
    }
}
