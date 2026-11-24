package com.gt7.telemetry

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/** Cockpit layout: shift lights on top, pedals left, gear and speed centre, tyres and fuel right, lap strip bottom. */
class DashboardView(ctx: Context, private val tracker: LapTracker) : View(ctx) {
    @Volatile var data: Telemetry? = null
    @Volatile var lastPacketNs: Long = 0L
    @Volatile var statusText: String = "Connecting…"

    private val dark =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val cBg = if (dark) 0xFF0B0D10.toInt() else 0xFFF5F5F2.toInt()
    private val cText = if (dark) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
    private val cDim = if (dark) 0xFF262B32.toInt() else 0xFFD6D6D0.toInt()
    private val cSub = if (dark) 0xFF8A9099.toInt() else 0xFF666666.toInt()
    private val cGreen = 0xFF3FB950.toInt()
    private val cAmber = 0xFFF0A020.toInt()
    private val cRed = 0xFFE5484D.toInt()
    private val cBlue = 0xFF3B8BEB.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun rr(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, rad: Float) {
        paint.style = Paint.Style.FILL
        paint.color = color
        rect.set(l, t, r, b)
        c.drawRoundRect(rect, rad, rad, paint)
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align, bold: Boolean = false, mono: Boolean = false) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = if (mono) Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
        else Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(s, x, y, paint)
    }

    private fun fmt(ms: Int): String =
        if (ms <= 0) "-:--.---" else String.format("%d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000)

    private fun tyreColor(t: Float): Int = when {
        t < 60f -> cBlue
        t < 95f -> cGreen
        t < 110f -> cAmber
        else -> cRed
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawColor(cBg)
        val t = data
        val now = System.nanoTime()
        if (t == null || now - lastPacketNs > 2_000_000_000L) {
            txt(c, statusText, w / 2, h / 2, h * 0.07f, cSub, Paint.Align.CENTER)
            return
        }
        val u = min(w, h)

        // Shift lights
        val n = 16
        val maxR = if (t.maxAlertRpm > 0) t.maxAlertRpm.toFloat() else 8000f
        val minR = if (t.minAlertRpm > 0) t.minAlertRpm.toFloat() else maxR * 0.75f
        val start = minR * 0.8f
        val lit = (((t.rpm - start) / (maxR - start)) * n).toInt().coerceIn(0, n)
        val flash = t.revLimiter || t.rpm >= maxR * 0.99f
        val flashOn = (now / 90_000_000L) % 2L == 0L
        val gx = w * 0.04f
        val gw = (w * 0.92f - (n - 1) * 6f) / n
        for (i in 0 until n) {
            val col = if (i < 10) cGreen else if (i < 13) cAmber else cRed
            val on = if (flash) flashOn else i < lit
            val l = gx + i * (gw + 6f)
            rr(c, l, h * 0.04f, l + gw, h * 0.15f, if (on) (if (flash) cRed else col) else cDim, 6f)
        }

        // Pedals
        val top = h * 0.24f
        val bot = h * 0.72f
        val bw = w * 0.04f
        rr(c, w * 0.03f, top, w * 0.03f + bw, bot, cDim, 6f)
        rr(c, w * 0.03f, bot - (bot - top) * t.throttle, w * 0.03f + bw, bot, cGreen, 6f)
        rr(c, w * 0.03f + bw + w * 0.015f, top, w * 0.03f + bw * 2 + w * 0.015f, bot, cDim, 6f)
        rr(c, w * 0.03f + bw + w * 0.015f, bot - (bot - top) * t.brake, w * 0.03f + bw * 2 + w * 0.015f, bot, cRed, 6f)
        txt(c, "T", w * 0.03f + bw / 2, bot + u * 0.07f, u * 0.05f, cSub, Paint.Align.CENTER)
        txt(c, "B", w * 0.03f + bw * 1.5f + w * 0.015f, bot + u * 0.07f, u * 0.05f, cSub, Paint.Align.CENTER)

        // Gear and speed
        val gearStr = if (t.gear == 0) "R" else t.gear.toString()
        val sugg = t.suggestedGear
        val gearCol = if (sugg in 1..14 && sugg != t.gear) cAmber else cText
        txt(c, gearStr, w * 0.32f, h * 0.60f, h * 0.50f, gearCol, Paint.Align.CENTER, bold = true)
        txt(c, String.format("%.0f", t.speedKmh), w * 0.62f, h * 0.56f, h * 0.32f, cText, Paint.Align.CENTER, bold = true)
        txt(c, "km/h", w * 0.62f, h * 0.66f, h * 0.06f, cSub, Paint.Align.CENTER)
        txt(c, String.format("%.0f rpm", t.rpm), w * 0.47f, h * 0.22f, h * 0.055f, cSub, Paint.Align.CENTER)
        val lapStr = if (t.laps > 0) "Lap ${t.lap}/${t.laps}" else "Lap ${t.lap}"
        txt(c, lapStr, w * 0.47f, h * 0.72f, h * 0.06f, cSub, Paint.Align.CENTER)

        // Tyres
        val tx = w * 0.78f
        val tw = w * 0.085f
        val th = h * 0.15f
        for (i in 0 until 4) {
            val l = tx + (i % 2) * (tw + w * 0.015f)
            val tp = h * 0.24f + (i / 2) * (th + h * 0.025f)
            rr(c, l, tp, l + tw, tp + th, tyreColor(t.tyres[i]), 8f)
            txt(c, String.format("%.0f", t.tyres[i]), l + tw / 2, tp + th * 0.62f, h * 0.06f, 0xFF0B0D10.toInt(), Paint.Align.CENTER, bold = true)
        }

        // Fuel
        val fp = if (t.fuelCap > 0f) (t.fuel / t.fuelCap).coerceIn(0f, 1f) else 0f
        val fy = h * 0.66f
        rr(c, tx, fy, tx + tw * 2 + w * 0.015f, fy + h * 0.03f, cDim, 6f)
        rr(c, tx, fy, tx + (tw * 2 + w * 0.015f) * fp, fy + h * 0.03f, if (fp < 0.15f) cRed else cBlue, 6f)
        val ll = tracker.lapsLeft
        val fuelStr = String.format("Fuel %.0f%%", fp * 100f) + if (ll >= 0f) String.format("  ~%.1f laps", ll) else ""
        txt(c, fuelStr, tx, fy + h * 0.09f, h * 0.05f, cSub, Paint.Align.LEFT)

        // Lap strip
        val sy = h * 0.80f
        rr(c, w * 0.03f, sy, w * 0.97f, h * 0.97f, if (dark) 0xFF14181D.toInt() else 0xFFE9E9E4.toInt(), 12f)
        val cur = (tracker.curLapSec * 1000).toInt()
        val cols = floatArrayOf(0.05f, 0.24f, 0.43f)
        val labels = arrayOf("Current", "Last", "Best")
        val vals = arrayOf(fmt(cur), fmt(t.lastMs), fmt(t.bestMs))
        for (i in 0 until 3) {
            txt(c, labels[i], w * cols[i], sy + h * 0.06f, h * 0.045f, cSub, Paint.Align.LEFT)
            txt(c, vals[i], w * cols[i], sy + h * 0.145f, h * 0.075f, cText, Paint.Align.LEFT, bold = true, mono = true)
        }
        val d = tracker.deltaSec
        val dStr = if (d == null) "--.--" else String.format("%+.2f", d)
        val dCol = if (d == null) cSub else if (d <= 0) cGreen else cRed
        txt(c, "Delta to best", w * 0.95f, sy + h * 0.06f, h * 0.045f, cSub, Paint.Align.RIGHT)
        txt(c, dStr, w * 0.95f, sy + h * 0.145f, h * 0.10f, dCol, Paint.Align.RIGHT, bold = true, mono = true)

        if (t.paused) txt(c, "PAUSED", w * 0.47f, h * 0.45f, h * 0.08f, cAmber, Paint.Align.CENTER, bold = true)
    }
}
