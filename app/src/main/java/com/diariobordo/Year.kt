package com.diariobordo

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
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
        }
    }
}
