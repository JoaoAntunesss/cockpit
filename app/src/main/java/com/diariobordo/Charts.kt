package com.diariobordo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Consumo em L/100 km por abastecimento, com linha tracejada na média. */
@Composable
fun ConsumptionChart(values: List<Double>, avg: Double, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onSurface
    val tm = rememberTextMeasurer()
    val small = TextStyle(fontSize = 12.sp, color = text)

    Canvas(modifier.fillMaxWidth().height(190.dp)) {
        val lo = floor(values.min() - 0.4)
        var hi = ceil(values.max() + 0.4)
        val step = if (hi - lo > 5) 2.0 else 1.0
        hi = lo + ceil((hi - lo) / step) * step
        val padL = 36.dp.toPx(); val padR = 14.dp.toPx()
        val padT = 26.dp.toPx(); val padB = 8.dp.toPx()
        fun y(v: Double) = padT + ((hi - v) / (hi - lo)).toFloat() * (size.height - padT - padB)
        fun x(i: Int) = padL + if (values.size == 1) 0f else i * (size.width - padL - padR) / (values.size - 1)

        var v = lo
        while (v <= hi + 1e-9) {
            drawLine(grid, Offset(padL, y(v)), Offset(size.width - padR, y(v)), 1.dp.toPx())
            drawText(tm, v.toInt().toString(), Offset(2.dp.toPx(), y(v) - 7.dp.toPx()), small)
            v += step
        }
        drawLine(
            text, Offset(padL, y(avg)), Offset(size.width - padR, y(avg)), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
        )
        val path = Path()
        values.forEachIndexed { i, d -> if (i == 0) path.moveTo(x(i), y(d)) else path.lineTo(x(i), y(d)) }
        val area = Path().apply {
            addPath(path)
            lineTo(x(values.size - 1), y(lo)); lineTo(x(0), y(lo)); close()
        }
        drawPath(area, line.copy(alpha = 0.16f))
        drawPath(path, line.copy(alpha = 0.30f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        values.forEachIndexed { i, d ->
            val last = i == values.size - 1
            drawCircle(line, if (last) 5.dp.toPx() else 3.dp.toPx(), Offset(x(i), y(d)))
        }
        // valores escritos nos pontos (poucos pontos) ou só no último
        values.forEachIndexed { i, d ->
            val last = i == values.size - 1
            if (last || values.size <= 6) {
                drawText(
                    tm, fmt(d, 1), Offset(x(i) - 14.dp.toPx(), y(d) - 24.dp.toPx()),
                    TextStyle(fontSize = if (last) 15.sp else 12.sp, color = if (last) line else strong),
                )
            }
        }
    }
}

/** Colunas LED por mês: combustível, mecânica, fixas. O total vem escrito por cima. */
@Composable
fun CostBars(months: List<MonthSum>, colors: ChartColors, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val text = MaterialTheme.colorScheme.onSurfaceVariant
    val strong = MaterialTheme.colorScheme.onSurface
    val tm = rememberTextMeasurer()
    val small = TextStyle(fontSize = 12.sp, color = text)
    var sel by remember(months) { mutableStateOf<Int?>(null) }

    Column {
    Text(
        sel?.let { months.getOrNull(it) }?.let { m ->
            "${m.label}: ${eur(m.total)} · " + t("comb. ", "fuel ") + eur(m.fuel) + " · " + t("mec. ", "mech. ") + eur(m.mech) + " · " + t("fixas ", "fixed ") + eur(m.fixed)
        } ?: t("Toca numa coluna para ver o detalhe do mês.", "Tap a column to see the month's detail."),
        style = MaterialTheme.typography.bodySmall, color = text,
    )
    Canvas(
        modifier.fillMaxWidth().height(230.dp).pointerInput(months) {
            detectTapGestures { off ->
                val padL = 42.dp.toPx(); val padR = 4.dp.toPx()
                val slot = (size.width - padL - padR) / months.size
                val i = ((off.x - padL) / slot).toInt()
                sel = if (off.x >= padL && i in months.indices && sel != i) i else null
            }
        },
    ) {
        val top = months.maxOf { it.total }.coerceAtLeast(1.0)
        val p = 10.0.pow(floor(log10(top)))
        val mx = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * p }.first { it >= top }
        val padL = 42.dp.toPx(); val padR = 4.dp.toPx()
        val padT = 22.dp.toPx(); val padB = 24.dp.toPx()
        val plotH = size.height - padT - padB
        fun y(v: Double) = padT + (1 - (v / mx)).toFloat() * plotH

        listOf(0.0, 0.5, 1.0).forEach { f ->
            val v = mx * f
            drawLine(grid, Offset(padL, y(v)), Offset(size.width - padR, y(v)), 1.dp.toPx())
            drawText(tm, fint(v.toInt()), Offset(2.dp.toPx(), y(v) - 8.dp.toPx()), small)
        }
        val slot = (size.width - padL - padR) / months.size
        val bw = minOf(26.dp.toPx(), slot * 0.70f)
        val segH = 7.dp.toPx()
        val gap = 2.dp.toPx()
        val unit = segH + gap
        val nSeg = (plotH / unit).toInt()
        months.forEachIndexed { i, m ->
            val cx = padL + slot * i + slot / 2
            // quantos segmentos acesos por categoria (pelo menos 1 se houver gasto)
            val parts = listOf(m.fuel to colors.fuel, m.mech to colors.mech, m.fixed to colors.fixed)
            var used = 0
            val dim = if (sel == null || sel == i) 1f else 0.3f
            parts.forEach { (v, c0) ->
                val c = c0.copy(alpha = dim)
                if (v > 0) {
                    val cnt = Math.max(1, Math.round(v / mx * nSeg).toInt())
                    for (k in 0 until cnt) {
                        val yTop = size.height - padB - (used + k + 1) * unit + gap
                        drawRect(c, Offset(cx - bw / 2, yTop), Size(bw, segH))
                    }
                    used += cnt
                }
            }
            if ((months.size <= 6 || sel == i) && m.total > 0) {
                val t = fint(Math.round(m.total).toInt())
                drawText(tm, t, Offset(cx - 11.dp.toPx(), size.height - padB - used * unit - 18.dp.toPx()), TextStyle(fontSize = 12.sp, color = strong))
            }
            drawText(tm, m.label, Offset(cx - 10.dp.toPx(), size.height - padB + 5.dp.toPx()), small)
        }
    }
    }
}
