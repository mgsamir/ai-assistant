package com.example.assistant

import android.content.Context
import android.graphics.*
import android.view.View

// laal tikona ⚠ nishaan
class WarnIcon(ctx: Context) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F01010"); style = Paint.Style.FILL; pathEffect = CornerPathEffect(10f) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD0D0"); style = Paint.Style.STROKE; strokeWidth = 5f; strokeJoin = Paint.Join.ROUND; pathEffect = CornerPathEffect(10f) }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val p = Path().apply { moveTo(w / 2, 4f); lineTo(w - 4f, h - 4f); lineTo(4f, h - 4f); close() }
        c.drawPath(p, fill); c.drawPath(p, edge)
        c.drawRoundRect(RectF(w / 2 - w * 0.055f, h * 0.32f, w / 2 + w * 0.055f, h * 0.67f), 6f, 6f, mark)
        c.drawCircle(w / 2, h * 0.79f, w * 0.065f, mark)
    }
}
