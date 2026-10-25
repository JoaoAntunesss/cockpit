package com.diariobordo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// a b c d e f g
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

private fun DrawScope.seg(p: Path, lit: Boolean, color: Color, glow: Float) {
    if (lit) {
        // halo curto e discreto: o segmento fica nítido, com um brilho fino à volta
        drawPath(p, color.copy(alpha = 0.20f), style = Stroke(width = glow * 0.7f, join = StrokeJoin.Round))
        drawPath(p, color)
    } else drawPath(p, color.copy(alpha = 0.07f))
}

/** Números em displays de 7 segmentos desenhados à mão (nítidos em qualquer ecrã). Os segmentos apagados ficam ténues. */
@Composable
fun LedNumber(text: String, color: Color, height: Dp, modifier: Modifier = Modifier) {
    val d = LocalDensity.current
    val h = with(d) { height.toPx() }
    val t = h * 0.15f
    val w = h * 0.58f
    val gap = h * 0.16f
    val dotW = h * 0.22f
    val total = text.sumOf { if (it.isDigit()) (w + gap).toDouble() else (dotW + gap).toDouble() }.toFloat() - gap
    Canvas(modifier.semantics { contentDescription = text }.size(with(d) { (total + t).toDp() }, with(d) { (h + t).toDp() })) {
        var x = t / 2 + 0f
        val top = t / 2
        val g = t * 0.12f
        for (ch in text) {
            if (!ch.isDigit()) {
                val s = t * 1.05f
                drawRect(color.copy(alpha = 0.20f), Offset(x + dotW / 2 - s * 0.75f, top + h - s * 1.35f), androidx.compose.ui.geometry.Size(s * 1.5f, s * 1.5f))
                drawRect(color, Offset(x + dotW / 2 - s / 2, top + h - s * 1.1f), androidx.compose.ui.geometry.Size(s, s))
                x += dotW + gap
                continue
            }
            val on = SEG[ch] ?: ""
            val mid = top + h / 2
            val xl = x + t / 2
            val xr = x + w - t / 2
            val bot = top + h - t / 2
            val glow = t * 0.55f
            seg(hexH(x + t / 2 + g, x + w - t / 2 - g, top + t / 2, t), 'a' in on, color, glow)
            seg(hexH(x + t / 2 + g, x + w - t / 2 - g, mid, t), 'g' in on, color, glow)
            seg(hexH(x + t / 2 + g, x + w - t / 2 - g, bot, t), 'd' in on, color, glow)
            seg(hexV(top + t / 2 + g, mid - g, xr, t), 'b' in on, color, glow)
            seg(hexV(mid + g, bot - g, xr, t), 'c' in on, color, glow)
            seg(hexV(mid + g, bot - g, xl, t), 'e' in on, color, glow)
            seg(hexV(top + t / 2 + g, mid - g, xl, t), 'f' in on, color, glow)
            x += w + gap
        }
    }
}
