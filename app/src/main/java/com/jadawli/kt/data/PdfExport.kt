package com.jadawli.kt.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri

/** تصدير الجدول إلى PDF بنص حقيقي في صفحة واحدة (A3 أفقية، أو A2 إن كثرت الأسطر)، بنفس تخطيط جداولي. */
object PdfExport {
    private val AR = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]")
    private val labelRe = Regex("^\\d{4}-\\d{2}-(\\d{2}) \\((.+)\\)$")
    private const val M = 30f
    private const val TOP = 46f
    private const val PAD = 1.3f

    fun shortLabel(r: String): String {
        val m = labelRe.find(r)
        return if (m != null) m.groupValues[1] + " " + m.groupValues[2] else r
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)

    fun write(ctx: Context, uri: Uri, title: String, subtitle: String, g: GridUi, today: String) {
        val cols = g.cols.list
        val rows = g.rows
        val hasG = cols.any { it.group.isNotEmpty() }
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val reg = Typeface.DEFAULT
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        fun isAr(t: String) = AR.containsMatchIn(t)

        /** عرض النص بوحدة «em» (يُضرب في الحجم). */
        fun w(t: String, isBold: Boolean): Float {
            if (t.isEmpty()) return 0f
            p.typeface = if (isBold) bold else reg
            p.textSize = 100f
            val ww = if (isAr(t)) p.getRunAdvance(t, 0, t.length, 0, t.length, true, t.length) else p.measureText(t)
            return ww / 100f
        }

        /** يرسم النص بحيث تكون حافته اليمنى عند right. */
        fun draw(c: Canvas, t: String, right: Float, baseline: Float, size: Float, isBold: Boolean, color: Int) {
            if (t.isEmpty()) return
            p.typeface = if (isBold) bold else reg
            p.textSize = size
            p.color = color
            val x = right - w(t, isBold) * size
            if (isAr(t)) c.drawTextRun(t, 0, t.length, 0, t.length, x, baseline, true, p)
            else c.drawText(t, x, baseline, p)
        }

        fun drawC(c: Canvas, t: String, cx: Float, baseline: Float, size: Float, isBold: Boolean, color: Int) =
            draw(c, t, cx + w(t, isBold) * size / 2f, baseline, size, isBold, color)

        fun wrap2(name: String): List<String> {
            val ws = name.split(" ").filter { it.isNotEmpty() }
            if (ws.size < 2) return listOf(name)
            var best: List<String>? = null
            var bm = Float.MAX_VALUE
            for (i in 1 until ws.size) {
                val a = ws.subList(0, i).joinToString(" ")
                val b = ws.subList(i, ws.size).joinToString(" ")
                val m = maxOf(w(a, true), w(b, true))
                if (m < bm) { bm = m; best = listOf(a, b) }
            }
            return best ?: listOf(name)
        }

        val labels = rows.map { shortLabel(it.name) }
        val cells = rows.map { r -> cols.mapIndexed { i, c -> showVal(c, r.vals[i]) } }
        val hdr = cols.map { wrap2(it.name) }
        val u = cols.mapIndexed { i, c ->
            var m = hdr[i].maxOf { w(it, true) }
            if (c.type == "checkbox") {
                maxOf(m, 1.4f)
            } else {
                for (r in cells) { val v = r[i]; if (v.isNotEmpty()) m = maxOf(m, minOf(w(v, false), 18f)) }
                maxOf(m, 2.2f)
            }
        }
        val uL = maxOf(w("#", true), labels.maxOfOrNull { w(it, true) } ?: 1f)
        val sumU = uL + PAD + u.sumOf { (it + PAD).toDouble() }.toFloat()
        val two = hdr.any { it.size > 1 }
        val hdrU = if (two) 3.6f else 2.4f
        val gU = if (hasG) 2.1f else 0f
        val rowU = 2.1f

        fun fit(W: Float, H: Float): Float =
            minOf((W - 2 * M) / sumU, (H - 2 * M - TOP - 14f) / (hdrU + gU + rowU * maxOf(rows.size, 1)), 11f)

        var W = 1191f
        var H = 842f
        var s = fit(W, H)
        if (s < 5.5f) { W = 1684f; H = 1191f; s = fit(W, H) }

        val pri = rgb(37, 99, 235)
        val dark = rgb((37 * .62).toInt(), (99 * .62).toInt(), (235 * .62).toInt())
        val line = rgb(204, 213, 224)
        val alt = rgb(248, 250, 252)
        val todayC = rgb(209, 250, 229)
        val ok = rgb(16, 185, 129)
        val txt = rgb(31, 41, 56)
        val mute = rgb(102, 115, 140)
        val white = Color.WHITE

        fun mix(argb: Int): Int {          // اللون على أبيض بنسبة 38% كما في جداولي
            val k = 0.38f
            fun f(c: Int) = (c * k + 255 * (1 - k)).toInt()
            return rgb(f(Color.red(argb)), f(Color.green(argb)), f(Color.blue(argb)))
        }

        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
            val c = page.canvas
            val fillP = Paint()
            val strokeP = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.4f }

            fun box(x1: Float, x2: Float, yt: Float, h: Float, fill: Int, border: Int) {
                fillP.color = fill
                c.drawRect(x2, yt, x1, yt + h, fillP)
                strokeP.color = border
                c.drawRect(x2, yt, x1, yt + h, strokeP)
            }

            val right = W - M
            val wL = (uL + PAD) * s
            val cw = u.map { (it + PAD) * s }
            val rh = rowU * s
            val hh = hdrU * s
            val gh = gU * s
            draw(c, title, right, M + 16f, 16f, true, pri)
            if (subtitle.isNotEmpty()) draw(c, subtitle, right, M + 33f, 9f, false, mute)
            val top = M + TOP

            val xr = FloatArray(cols.size)
            val xl = FloatArray(cols.size)
            var cur = right - wL
            cw.forEachIndexed { i, ww -> xr[i] = cur; xl[i] = cur - ww; cur -= ww }

            if (hasG) {
                var i = 0
                while (i < cols.size) {
                    var j = i
                    while (j + 1 < cols.size && cols[j + 1].group == cols[i].group) j++
                    if (cols[i].group.isNotEmpty()) {
                        box(xr[i], xl[j], top, gh, dark, dark)
                        drawC(c, cols[i].group, (xr[i] + xl[j]) / 2f, top + gh / 2f + s * .35f, s, true, white)
                    }
                    i = j + 1
                }
            }
            val hy = top + gh
            box(right, right - wL, hy, hh, pri, pri)
            drawC(c, "#", right - wL / 2f, hy + hh / 2f + s * .35f, s, true, white)
            cols.forEachIndexed { i, _ ->
                box(xr[i], xl[i], hy, hh, pri, pri)
                val L = hdr[i]
                val lh = s * 1.15f
                val y0 = hy + hh / 2f - (L.size - 1) * lh / 2f + s * .35f
                L.forEachIndexed { k, l -> drawC(c, l, (xr[i] + xl[i]) / 2f, y0 + k * lh, s, true, white) }
            }
            rows.forEachIndexed { ri, r ->
                val yt = hy + hh + ri * rh
                val fill = if (r.name.contains(today)) todayC else if (ri % 2 == 1) alt else white
                box(right, right - wL, yt, rh, fill, line)
                draw(c, labels[ri], right - .5f * s, yt + rh / 2f + s * .35f, s, true, txt)
                cols.forEachIndexed { i, col ->
                    val tint = col.tints[r.vals[i]]
                    box(xr[i], xl[i], yt, rh, if (tint != null) mix(tint) else fill, line)
                    val v = cells[ri][i]
                    if (v.isEmpty()) return@forEachIndexed
                    val cx = (xr[i] + xl[i]) / 2f
                    val cy = yt + rh / 2f
                    if (col.type == "checkbox") {
                        if (v == "1") {
                            val k = s * .3f
                            val tp = Paint(Paint.ANTI_ALIAS_FLAG)
                            tp.style = Paint.Style.STROKE
                            tp.strokeWidth = maxOf(1f, s * .13f)
                            tp.strokeCap = Paint.Cap.ROUND
                            tp.color = ok
                            c.drawLine(cx - k, cy, cx - k * .35f, cy + k * .75f, tp)
                            c.drawLine(cx - k * .35f, cy + k * .75f, cx + k * 1.1f, cy - k * .8f, tp)
                        }
                    } else {
                        c.save()
                        c.clipRect(xl[i], yt, xr[i], yt + rh)
                        drawC(c, v, cx, cy + s * .35f, s, false, txt)
                        c.restore()
                    }
                }
            }
            doc.finishPage(page)
            val os = ctx.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("تعذّر الكتابة في الملف")
            os.use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }
}
