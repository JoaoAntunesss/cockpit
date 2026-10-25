package com.diariobordo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDate

/** Relatório para mostrar a um comprador: identificação, manutenção feita, pneus e prazos. Os valores são opcionais. */
object SaleReport {
    fun pdf(ctx: Context, d: CarData, tires: List<Tire>, showPlate: Boolean, showCosts: Boolean): File {
        val doc = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        var pageNo = 0
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            canvas = page!!.canvas
            y = 56f
        }
        fun line(left: String, right: String = "", size: Float = 11f, bold: Boolean = false, color: Int = Color.BLACK) {
            if (page == null || y > 790f) newPage()
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.color = color
            paint.textAlign = Paint.Align.LEFT
            canvas!!.drawText(if (left.length > 82) left.take(81) + "…" else left, 40f, y, paint)
            if (right.isNotBlank()) {
                paint.textAlign = Paint.Align.RIGHT
                canvas!!.drawText(right, 555f, y, paint)
            }
            y += size + 7f
        }
        fun gap(h: Float = 8f) { y += h }

        val v = d.vehicle
        newPage()
        line(t("Relatório do veículo", "Vehicle report"), size = 22f, bold = true)
        line(listOf(Brand.of(v.brand).label, v.model, if (v.year > 0) v.year.toString() else "").filter { it.isNotBlank() }.joinToString(" "), size = 15f, bold = true)
        line(listOf(v.engine, v.fuelType).filter { it.isNotBlank() }.joinToString(" · "), color = Color.DKGRAY)
        line(t("Quilómetros", "Kilometres") + ": ${fint(v.km)} km" + if (showPlate && v.plate.isNotBlank()) " · " + t("Matrícula", "Plate") + ": ${v.plate}" else "", color = Color.DKGRAY)
        line(t("Gerado em ", "Generated on ") + docDate(LocalDate.now().toEpochDay()), color = Color.GRAY, size = 9f)
        gap(8f)

        val st = fuelStats(d.liquid())
        val last = d.services.maxByOrNull { it.date }
        val byMe = d.services.count { it.byMe }
        line(t("Em resumo", "At a glance"), size = 13f, bold = true)
        line(t("Intervenções registadas", "Services logged"), "${d.services.size}")
        if (d.services.isNotEmpty()) line(t("Feitas por oficina / pelo dono", "By a garage / by the owner"), "${d.services.size - byMe} / $byMe")
        last?.let { line(t("Última intervenção", "Latest service"), "${docDate(it.date)} · ${fint(it.km)} km") }
        st.avg?.let { line(t("Consumo médio", "Average consumption"), fmt(it) + " " + consUnit(v.fuelType) + " km") }
        d.docs.filter { it.type.contains("Inspe", ignoreCase = true) }.maxByOrNull { it.expiry }?.let { line(t("Inspeção válida até", "Inspection valid until"), docDate(it.expiry)) }
        gap()

        line(t("Manutenção feita", "Maintenance history") + " (${d.services.size})", size = 13f, bold = true)
        if (d.services.isEmpty()) line(t("Sem registos.", "No records."))
        d.services.sortedBy { it.date }.forEach {
            val who = if (it.byMe) t("dono", "owner") else it.shop.ifBlank { t("oficina", "garage") }
            line("${docDate(it.date)} · ${fint(it.km)} km · ${it.title} · $who", if (showCosts) eur(it.parts + it.labor) else "")
        }
        gap()

        if (tires.isNotEmpty()) {
            line(t("Pneus", "Tyres"), size = 13f, bold = true)
            tires.sortedByDescending { it.active }.forEach {
                line("${it.brand} ${it.size} · ${it.kind}", (if (it.active) t("montados", "fitted") else t("guardados", "stored")) + " · ${fint(tireKm(v, it))} km")
            }
            gap()
        }

        if (d.docs.isNotEmpty()) {
            line(t("Documentos e prazos", "Documents and deadlines"), size = 13f, bold = true)
            d.docs.forEach { line("${it.type}${if (it.title.isNotBlank() && showCosts) " · " + it.title else ""}", t("até ", "until ") + docDate(it.expiry)) }
            gap()
        }

        gap(8f)
        line(t("Informação declarada pelo proprietário e registada na app Cockpit. Não substitui as faturas.", "Information declared by the owner and logged in the Cockpit app. It does not replace invoices."), size = 9f, color = Color.GRAY)

        page?.let { doc.finishPage(it) }
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val f = File(dir, "cockpit_venda_${v.name.replace(Regex("[^A-Za-z0-9]+"), "_")}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }
}

@Composable
fun SaleDialog(d: CarData, tires: List<Tire>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var plate by remember { mutableStateOf(false) }
    var costs by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("Relatório para venda", "Sale report")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    t("Um PDF com o histórico de manutenção, os pneus e os prazos, para mostrar a um comprador. Escolhe o que mostrar.", "A PDF with the maintenance history, tyres and deadlines, to show a buyer. Choose what to include."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Mostrar a matrícula", "Show the plate"), Modifier.weight(1f)); Switch(plate, { plate = it }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Mostrar os valores pagos", "Show amounts paid"), Modifier.weight(1f)); Switch(costs, { costs = it }) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try { Export.share(ctx, SaleReport.pdf(ctx, d, tires, plate, costs), "application/pdf") } catch (_: Exception) {}
                onClose()
            }) { Text(t("Gerar PDF", "Create PDF")) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancelar")) } },
    )
}
