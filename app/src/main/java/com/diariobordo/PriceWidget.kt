package com.diariobordo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 7 segmentos desenhados em bitmap (o widget não aceita Canvas do Compose). Mesma geometria do Led.kt. */
object LedDraw {
    private val SEG = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
    )

    private fun hexH(x0: Float, x1: Float, yc: Float, t: Float) = Path().apply {
        moveTo(x0, yc); lineTo(x0 + t / 2, yc - t / 2); lineTo(x1 - t / 2, yc - t / 2)
        lineTo(x1, yc); lineTo(x1 - t / 2, yc + t / 2); lineTo(x0 + t / 2, yc + t / 2); close()
    }

    private fun hexV(y0: Float, y1: Float, xc: Float, t: Float) = Path().apply {
        moveTo(xc, y0); lineTo(xc + t / 2, y0 + t / 2); lineTo(xc + t / 2, y1 - t / 2)
        lineTo(xc, y1); lineTo(xc - t / 2, y1 - t / 2); lineTo(xc - t / 2, y0 + t / 2); close()
    }

    fun widthOf(text: String, h: Float): Float {
        val t = h * 0.15f; val w = h * 0.58f; val gap = h * 0.16f; val dotW = h * 0.22f
        return text.sumOf { if (it.isDigit()) (w + gap).toDouble() else (dotW + gap).toDouble() }.toFloat() - gap + t
    }

    /** Desenha [text] com o canto superior esquerdo em ([x], [yTop]) e altura [h]. Devolve a largura usada. */
    fun draw(c: Canvas, text: String, color: Int, x0: Float, yTop: Float, h: Float): Float {
        val t = h * 0.15f; val w = h * 0.58f; val gap = h * 0.16f; val dotW = h * 0.22f
        val lit = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(50, Color.red(color), Color.green(color), Color.blue(color))
            style = Paint.Style.STROKE; strokeWidth = t * 0.4f; strokeJoin = Paint.Join.ROUND
        }
        val off = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(18, Color.red(color), Color.green(color), Color.blue(color)); style = Paint.Style.FILL }
        fun seg(p: Path, on: Boolean) { if (on) { c.drawPath(p, halo); c.drawPath(p, lit) } else c.drawPath(p, off) }
        var x = x0 + t / 2
        val top = yTop + t / 2
        val g = t * 0.12f
        for (ch in text) {
            if (!ch.isDigit()) {
                val s = t * 1.05f
                c.drawRect(x + dotW / 2 - s / 2, top + h - s * 1.1f, x + dotW / 2 + s / 2, top + h - s * 0.1f, lit)
                x += dotW + gap
                continue
            }
            val on = SEG[ch] ?: ""
            val mid = top + h / 2
            val xl = x + t / 2
            val xr = x + w - t / 2
            val bot = top + h - t / 2
            seg(hexH(x + t / 2 + g, x + w - t / 2 - g, top + t / 2, t), 'a' in on)
            seg(hexH(x + t / 2 + g, x + w - t / 2 - g, mid, t), 'g' in on)
            seg(hexH(x + t / 2 + g, x + w - t / 2 - g, bot, t), 'd' in on)
            seg(hexV(top + t / 2 + g, mid - g, xr, t), 'b' in on)
            seg(hexV(mid + g, bot - g, xr, t), 'c' in on)
            seg(hexV(mid + g, bot - g, xl, t), 'e' in on)
            seg(hexV(top + t / 2 + g, mid - g, xl, t), 'f' in on)
            x += w + gap
        }
        return widthOf(text, h)
    }
}

/** Widget com o totem de preços: mesmo aspeto do separador Combustível. */
class PriceWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // tenta preços frescos; se não houver rede, usa os últimos guardados
                val prefs = ctx.getSharedPreferences("diario", 0)
                try {
                    val body = FuelPrices.download()
                    if (FuelPrices.parse(body).isNotEmpty()) prefs.edit().putString("prices_json", body).apply()
                } catch (_: Exception) {}
                val v = build(ctx)
                ids.forEach { mgr.updateAppWidget(it, v) }
            } catch (_: Exception) {} finally { pending.finish() }
        }
    }

    companion object {
        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_prices)
            val prefs = ctx.getSharedPreferences("diario", 0)
            UiPrefs.load(ctx); Lang.load(ctx)
            val list = FuelPrices.parse(prefs.getString("prices_json", null)).filter { isRoadFuel(it) }.sortedBy { fuelOrder(it) }.take(6)
            v.setImageViewBitmap(R.id.p_img, render(list, UiPrefs.fav, UiPrefs.led))
            v.setOnClickPendingIntent(R.id.p_root, PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }

        private fun render(list: List<FuelPrice>, fav: String, ledArgb: Int): Bitmap {
            val W = 720
            val head = 78
            val rowH = 124
            val foot = 16
            val rows = maxOf(list.size, 1)
            val H = head + rows * rowH + foot
            val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
            if (list.isEmpty()) {
                text.color = Color.parseColor("#9AA3B2"); text.textSize = 34f; text.textAlign = Paint.Align.CENTER
                c.drawText(t("Abre a app para carregar os preços", "Open the app to load prices"), W / 2f, H / 2f, text)
                return bmp
            }
            val amber = Color.parseColor("#FFB020")
            val color = if (ledArgb != 0) ledArgb else amber
            // cabeçalho
            text.color = Color.parseColor("#8A93A3"); text.textSize = 26f; text.textAlign = Paint.Align.LEFT
            text.letterSpacing = 0.18f
            c.drawText(t("COMBUSTÍVEIS", "FUEL PRICES"), 28f, 50f, text)
            text.letterSpacing = 0f; text.typeface = Typeface.DEFAULT; text.textAlign = Paint.Align.RIGHT
            c.drawText("€/L · " + t("média nacional", "national avg") + (list.firstOrNull()?.date?.let { " · ${it.takeLast(5)}" } ?: ""), W - 28f, 50f, text)
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor("#22262E"); strokeWidth = 2f }
            c.drawLine(28f, head - 8f, W - 28f, head - 8f, line)
            val cheapest = list.minByOrNull { it.avg }
            list.forEachIndexed { i, f ->
                val top = head + i * rowH
                val (label, col) = fuelBadge(f)
                val cy = top + rowH / 2f
                if (f.slug == fav) {
                    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor("#14181F") }
                    c.drawRoundRect(RectF(12f, top + 8f, W - 12f, top + rowH - 8f), 24f, 24f, bg)
                    val bd = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)); style = Paint.Style.STROKE; strokeWidth = 3f }
                    c.drawRoundRect(RectF(12f, top + 8f, W - 12f, top + rowH - 8f), 24f, 24f, bd)
                }
                // emblema
                val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = col.toArgb() }
                c.drawCircle(76f, cy, 38f, circle)
                text.typeface = Typeface.DEFAULT_BOLD; text.color = Color.WHITE; text.textSize = if (label.length > 2) 28f else 36f; text.textAlign = Paint.Align.CENTER
                c.drawText(label, 76f, cy + 12f, text)
                // nome e intervalo de preços
                text.textAlign = Paint.Align.LEFT; text.color = Color.parseColor("#E7EAF0"); text.textSize = 30f
                val nm = fuelLabel(f).let { if (it.length > 18) it.take(17) + "…" else it }
                c.drawText(nm, 138f, cy - 4f, text)
                text.typeface = Typeface.DEFAULT; text.color = Color.parseColor("#7C8594"); text.textSize = 24f
                c.drawText(String.format(PT, "%.2f – %.2f €", f.min, f.max), 138f, cy + 30f, text)
                if (f === cheapest && list.size > 1) {
                    val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor("#1B3B26") }
                    c.drawRoundRect(RectF(138f, cy - 52f, 138f + 118f, cy - 28f), 12f, 12f, chip)
                    text.typeface = Typeface.DEFAULT_BOLD; text.color = Color.parseColor("#4ADE80"); text.textSize = 18f
                    c.drawText(t("MAIS BARATO", "CHEAPEST"), 148f, cy - 33f, text)
                }
                // preço em LED
                val txt = String.format(PT, "%.3f", f.avg)
                val big = 70f; val small = 40f
                val wBig = LedDraw.widthOf(txt.dropLast(1), big)
                val wSmall = LedDraw.widthOf(txt.takeLast(1), small)
                val total = wBig + 10f + wSmall
                val x = W - 34f - total
                LedDraw.draw(c, txt.dropLast(1), color, x, cy - big / 2 - 2f, big)
                LedDraw.draw(c, txt.takeLast(1), color, x + wBig + 10f, cy + big / 2 - small - 2f + 4f, small)
                if (i < list.lastIndex && f.slug != fav) c.drawLine(28f, top + rowH.toFloat(), W - 28f, top + rowH.toFloat(), line)
            }
            return bmp
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PriceWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { try { val v = build(ctx); ids.forEach { mgr.updateAppWidget(it, v) } } catch (_: Exception) {} }
        }
    }
}
