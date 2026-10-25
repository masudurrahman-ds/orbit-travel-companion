package com.crowncouncil.orbit.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.crowncouncil.orbit.data.Destination
import com.crowncouncil.orbit.domain.BangladeshMap
import com.crowncouncil.orbit.domain.Badge

/** Draws the shareable "My Bangladesh" passport image: gold pins for visited districts, stats and earned badges. */
object PassportCard {
    private const val W = 1080
    private const val H = 1440

    fun render(destinations: List<Destination>, visitedIds: Set<Long>, badges: List<Badge>, name: String?): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), 0xFF12355B.toInt(), 0xFF0B1F3A.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, serif: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
            paint.style = Paint.Style.FILL
            paint.color = color; paint.textSize = size; paint.textAlign = align
            paint.typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            c.drawText(s, x, y, paint)
        }

        text("My Bangladesh", 70f, 130f, 84f, 0xFFFFFFFF.toInt(), bold = true, serif = true)
        text(if (name.isNullOrBlank()) "ORBIT travel passport" else "$name's travel passport", 70f, 190f, 40f, 0xFFC8962E.toInt())

        val byDistrict = destinations.groupBy { it.district }
        val done = byDistrict.filter { (_, l) -> l.any { it.id in visitedIds } }.keys
        val box = RectF(70f, 240f, 70f + 640f, 240f + 640f / BangladeshMap.ASPECT)
        val outline = Path()
        BangladeshMap.outline.forEachIndexed { i, (lon, lat) ->
            val (nx, ny) = BangladeshMap.project(lon, lat)
            val x = box.left + nx * box.width(); val y = box.top + ny * box.height()
            if (i == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
        }
        outline.close()
        paint.style = Paint.Style.FILL; paint.color = 0x33FFFFFF; c.drawPath(outline, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = 0xFF8FC1F0.toInt(); c.drawPath(outline, paint)
        paint.style = Paint.Style.FILL
        for ((district, list) in byDistrict) {
            val (nx, ny) = BangladeshMap.project(list.first().longitude, list.first().latitude)
            val x = box.left + nx * box.width(); val y = box.top + ny * box.height()
            if (district in done) {
                paint.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, 15f, paint)
                paint.color = 0xFFC8962E.toInt(); c.drawCircle(x, y, 11f, paint)
            } else {
                paint.color = 0x66FFFFFF; c.drawCircle(x, y, 4f, paint)
            }
        }

        val spots = destinations.count { it.id in visitedIds }
        text("${done.size}", 760f, 400f, 150f, 0xFFC8962E.toInt(), bold = true, serif = true)
        text("of ${byDistrict.size} districts", 760f, 450f, 40f, 0xFFFFFFFF.toInt())
        text("$spots", 760f, 590f, 110f, 0xFFFFFFFF.toInt(), bold = true, serif = true)
        text("of ${destinations.size} spots", 760f, 640f, 40f, 0xFFFFFFFF.toInt())
        text("${badges.count { it.earned }}", 760f, 770f, 110f, 0xFFFFFFFF.toInt(), bold = true, serif = true)
        text("badges earned", 760f, 820f, 40f, 0xFFFFFFFF.toInt())

        val earned = badges.filter { it.earned }.take(8)
        text("BADGES", 70f, box.bottom + 90f, 34f, 0xFFC8962E.toInt(), bold = true)
        var x = 70f; var y = box.bottom + 150f
        if (earned.isEmpty()) text("Visit a spot to earn your first badge.", 70f, y, 38f, 0xCCFFFFFF.toInt())
        for (b in earned) {
            paint.textSize = 36f; paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            val w = paint.measureText(b.title) + 56f
            if (x + w > W - 70f) { x = 70f; y += 84f }
            paint.style = Paint.Style.FILL; paint.color = 0xFFC8962E.toInt(); c.drawRoundRect(RectF(x, y - 48f, x + w, y + 20f), 34f, 34f, paint)
            text(b.title, x + 28f, y, 36f, 0xFF12355B.toInt(), bold = true)
            x += w + 16f
        }
        text("Plan smarter. Travel better.  |  ORBIT", W / 2f, H - 60f, 34f, 0xCCFFFFFF.toInt(), align = Paint.Align.CENTER)
        return bmp
    }
}
