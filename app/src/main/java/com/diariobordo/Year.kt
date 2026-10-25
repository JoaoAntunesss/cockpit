package com.diariobordo

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File

class YearSummary(
    val year: Int, val total: Double, val fuel: Double, val mech: Double, val fixed: Double, val km: Int,
    val refuels: Int, val qty: Double, val avg: Double?, val costliest: Pair<YearMonth, Double>?, val costPerKm: Double?,
)

fun yearSummary(d: CarData, year: Int): YearSummary {
    fun y(day: Long) = LocalDate.ofEpochDay(day).year == year
    val f = d.fuelings.filter { y(it.date) }
    val s = d.services.filter { y(it.date) }
    val e = d.expenses.filter { y(it.date) }
    val fuel = f.sumOf { it.total }
    val mech = s.sumOf { it.parts + it.labor }
    val fixed = e.sumOf { it.amount }
    val total = fuel + mech + fixed
    val kms = f.map { it.km } + s.map { it.km }
    val km = if (kms.size < 2) 0 else kms.max() - kms.min()
    val rows = fuelStats(d.liquid()).rows.filter { y(it.f.date) && it.cons != null }
    val perMonth = HashMap<YearMonth, Double>()
    f.forEach { perMonth.merge(YearMonth.from(LocalDate.ofEpochDay(it.date)), it.total, Double::plus) }
    s.forEach { perMonth.merge(YearMonth.from(LocalDate.ofEpochDay(it.date)), it.parts + it.labor, Double::plus) }
    e.forEach { perMonth.merge(YearMonth.from(LocalDate.ofEpochDay(it.date)), it.amount, Double::plus) }
    return YearSummary(
        year, total, fuel, mech, fixed, km, f.size, f.sumOf { it.liters }, weighted(rows),
        perMonth.maxByOrNull { it.value }?.toPair(), if (km >= 50 && total > 0) total / km else null,
    )
}

fun yearText(d: CarData, y: YearSummary): String = buildString {
    appendLine(t("O meu ${y.year} com ${d.vehicle.name} (Cockpit)", "My ${y.year} with ${d.vehicle.name} (Cockpit)"))
    appendLine(t("Gasto total: ", "Total spent: ") + eur(y.total))
    appendLine(t("Combustível: ", "Fuel: ") + eur(y.fuel) + " · " + t("Mecânica: ", "Maintenance: ") + eur(y.mech) + " · " + t("Despesas: ", "Expenses: ") + eur(y.fixed))
    if (y.km > 0) appendLine(t("Quilómetros: ", "Kilometres: ") + fint(y.km) + " km")
    y.avg?.let { appendLine(t("Consumo médio: ", "Average consumption: ") + fmt(it) + " " + consUnit(d.vehicle.fuelType) + " km") }
    y.costPerKm?.let { appendLine(t("Custo por km: ", "Cost per km: ") + fmt(it, 3) + " €") }
    y.costliest?.let { appendLine(t("Mês mais caro: ", "Costliest month: ") + it.first.month.getDisplayName(java.time.format.TextStyle.FULL, PT) + " (" + eur(it.second) + ")") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearSheet(d: CarData, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val years = remember(d) {
        (d.fuelings.map { it.date } + d.services.map { it.date } + d.expenses.map { it.date })
            .map { LocalDate.ofEpochDay(it).year }.distinct().sortedDescending().ifEmpty { listOf(LocalDate.now().year) }
    }
    var year by remember { mutableIntStateOf(years.first()) }
    val y = remember(d, year) { yearSummary(d, year) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Resumo do ano", "Year in review"), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                years.take(5).forEach { yr -> FilterChip(selected = year == yr, onClick = { year = yr }, label = { Text(yr.toString()) }) }
            }
            Text(eur(y.total), fontSize = 40.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(t("gastos em ${y.year} com ${d.vehicle.name}", "spent in ${y.year} on ${d.vehicle.name}"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val lines = buildList {
                add(t("Combustível", "Fuel") to eur(y.fuel))
                add(t("Mecânica", "Maintenance") to eur(y.mech))
                add(t("Despesas fixas", "Fixed expenses") to eur(y.fixed))
                add(t("Quilómetros", "Kilometres") to if (y.km > 0) fint(y.km) + " km" else "—")
                add(t("Abastecimentos", "Refuels") to "${y.refuels}")
                add(t("Consumo médio", "Average consumption") to (y.avg?.let { fmt(it) + " " + consUnit(d.vehicle.fuelType) } ?: "—"))
                add(t("Custo por km", "Cost per km") to (y.costPerKm?.let { fmt(it, 3) + " €" } ?: "—"))
                add(t("Mês mais caro", "Costliest month") to (y.costliest?.let { it.first.month.getDisplayName(java.time.format.TextStyle.FULL, PT) + " · " + eur(it.second) } ?: "—"))
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lines.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(k, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, yearText(d, y)) }
                    ctx.startActivity(Intent.createChooser(send, t("Partilhar", "Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text(t("Partilhar resumo", "Share summary")) }
            OutlinedButton(
                onClick = { try { Export.share(ctx, YearReport.pdf(ctx, d, year), "application/pdf") } catch (_: Exception) {} },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text(t("Relatório anual (PDF)", "Annual report (PDF)")) }
        }
    }
}


/** Relatório anual em PDF: totais por categoria, mês a mês e lista de despesas e intervenções. */
object YearReport {
    fun pdf(ctx: Context, d: CarData, year: Int): File {
        val y = yearSummary(d, year)
        val v = d.vehicle
        val doc = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var yy = 0f
        var no = 0
        fun newPage() {
            page?.let { doc.finishPage(it) }
            no++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, no).create())
            canvas = page!!.canvas
            yy = 56f
        }
        fun line(left: String, right: String = "", size: Float = 11f, bold: Boolean = false, color: Int = Color.BLACK) {
            if (page == null || yy > 790f) newPage()
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.color = color
            paint.textAlign = Paint.Align.LEFT
            canvas!!.drawText(if (left.length > 70) left.take(69) + "…" else left, 40f, yy, paint)
            if (right.isNotBlank()) { paint.textAlign = Paint.Align.RIGHT; canvas!!.drawText(right, 555f, yy, paint) }
            yy += size + 7f
        }
        newPage()
        line(t("Relatório anual $year", "Annual report $year"), size = 22f, bold = true)
        line(listOf(v.name, Brand.of(v.brand).label, v.model).filter { it.isNotBlank() }.distinct().joinToString(" · "), size = 14f, bold = true)
        line(t("Gerado em ", "Generated on ") + docDate(LocalDate.now().toEpochDay()), size = 9f, color = Color.GRAY)
        yy += 8f
        line(t("Resumo", "Summary"), size = 13f, bold = true)
        line(t("Total gasto", "Total spent"), eur(y.total), bold = true)
        line(t("Combustível", "Fuel"), eur(y.fuel))
        line(t("Mecânica", "Maintenance"), eur(y.mech))
        line(t("Despesas fixas", "Fixed expenses"), eur(y.fixed))
        if (y.km > 0) line(t("Quilómetros no ano", "Kilometres in the year"), fint(y.km) + " km")
        y.avg?.let { line(t("Consumo médio", "Average consumption"), fmt(it) + " " + consUnit(v.fuelType) + " km") }
        y.costPerKm?.let { line(t("Custo por km", "Cost per km"), fmt(it, 3) + " €") }
        yy += 8f
        line(t("Mês a mês", "Month by month"), size = 13f, bold = true)
        for (m in 1..12) {
            val ym = YearMonth.of(year, m)
            fun inM(day: Long) = YearMonth.from(LocalDate.ofEpochDay(day)) == ym
            val tot = d.fuelings.filter { inM(it.date) }.sumOf { it.total } + d.services.filter { inM(it.date) }.sumOf { it.parts + it.labor } + d.expenses.filter { inM(it.date) }.sumOf { it.amount }
            if (tot > 0) line(ym.month.getDisplayName(java.time.format.TextStyle.FULL, PT).replaceFirstChar { it.uppercase() }, eur(tot))
        }
        yy += 8f
        val svc = d.services.filter { LocalDate.ofEpochDay(it.date).year == year }.sortedBy { it.date }
        line(t("Intervenções", "Services") + " (${svc.size})", size = 13f, bold = true)
        if (svc.isEmpty()) line(t("Sem registos.", "No records."))
        svc.forEach { line("${docDate(it.date)} · ${it.title}", eur(it.parts + it.labor)) }
        yy += 8f
        val exp = d.expenses.filter { LocalDate.ofEpochDay(it.date).year == year }.sortedBy { it.date }
        line(t("Despesas", "Expenses") + " (${exp.size})", size = 13f, bold = true)
        if (exp.isEmpty()) line(t("Sem registos.", "No records."))
        exp.forEach { line("${docDate(it.date)} · ${it.type}", eur(it.amount)) }
        yy += 8f
        val fu = d.fuelings.filter { LocalDate.ofEpochDay(it.date).year == year }.sortedBy { it.date }
        line(t("Abastecimentos", "Refuels") + " (${fu.size})", size = 13f, bold = true)
        fu.forEach { line("${docDate(it.date)} · ${fmt(it.liters)} ${unitOf(it, v.fuelType)} · ${fint(it.km)} km", eur(it.total)) }
        yy += 10f
        line(t("Gerado pela app Cockpit a partir dos registos do proprietário. Não substitui as faturas.", "Generated by the Cockpit app from the owner's records. It does not replace invoices."), size = 9f, color = Color.GRAY)
        page?.let { doc.finishPage(it) }
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val f = File(dir, "cockpit_${year}_${v.name.replace(Regex("[^A-Za-z0-9]+"), "_")}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
