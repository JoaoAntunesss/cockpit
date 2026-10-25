package com.diariobordo

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Mês e total do IUC guardados no telemóvel. */
object IucPrefs {
    private fun p(c: Context) = c.getSharedPreferences("iuc", 0)
    fun month(c: Context, carId: Long): Int = p(c).getInt("m_$carId", 0)
    fun setMonth(c: Context, carId: Long, m: Int) = p(c).edit().putInt("m_$carId", m).apply()
    fun total(c: Context): Double = p(c).getFloat("total", 0f).toDouble()
    fun setTotal(c: Context, v: Double) = p(c).edit().putFloat("total", v.toFloat()).apply()
}

/**
 * Meses de pagamento do IUC em cada ano, segundo o calendário novo (valores de fontes secundárias, sem dias exatos):
 * até 2026 paga-se no mês da matrícula; em 2027 transitório; a partir de 2028 por escalões.
 */
fun iucMonths(year: Int, total: Double, regMonth: Int): List<Int> = when {
    year <= 2026 -> listOf(regMonth)
    year == 2027 -> if (total <= 500) listOf(10) else listOf(7, 10)
    total <= 100 -> listOf(4)
    total <= 500 -> listOf(4, 10)
    else -> listOf(4, 7, 10)
}

data class IucNext(val ym: YearMonth, val days: Int, val parts: Int)

fun iucNext(total: Double, regMonth: Int, today: LocalDate = LocalDate.now()): IucNext? {
    if (regMonth !in 1..12) return null
    for (y in today.year..today.year + 2) {
        val ms = iucMonths(y, total, regMonth)
        for (m in ms) {
            val ym = YearMonth.of(y, m)
            val end = ym.atEndOfMonth()
            if (!end.isBefore(today)) return IucNext(ym, ChronoUnit.DAYS.between(today, end).toInt(), ms.size)
        }
    }
    return null
}

private val MONTHS_PT = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
private val MONTHS_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
private fun monthName(m: Int) = if (Lang.en()) MONTHS_EN[m - 1] else MONTHS_PT[m - 1]

@Composable
fun IucCard(car: Vehicle, onAddDoc: () -> Unit) {
    val ctx = LocalContext.current
    var month by remember(car.id) { mutableIntStateOf(IucPrefs.month(ctx, car.id)) }
    var totalTxt by remember { mutableStateOf(IucPrefs.total(ctx).takeIf { it > 0 }?.let { dec(it) } ?: "") }
    val total = num(totalTxt) ?: 0.0
    val nxt = iucNext(total, month)
    val monthNames = (1..12).map { monthName(it) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t("IUC: quando pagar", "Road tax (IUC): when to pay"), style = MaterialTheme.typography.titleMedium)
            PickField(t("Mês da matrícula", "Registration month"), if (month in 1..12) monthName(month) else "", monthNames) {
                month = monthNames.indexOf(it) + 1; IucPrefs.setMonth(ctx, car.id, month)
            }
            NumField(
                t("IUC anual de todos os teus carros (€)", "Yearly IUC of all your cars (€)"), totalTxt,
                { totalTxt = it; IucPrefs.setTotal(ctx, num(it) ?: 0.0) }, decimal = true,
                hint = t("A partir de 2027 o prazo depende do total por contribuinte.", "From 2027 the schedule depends on the total per taxpayer."),
            )
            if (nxt != null) {
                Text(
                    t("Próximo pagamento: ${monthName(nxt.ym.monthValue)} de ${nxt.ym.year}", "Next payment: ${monthName(nxt.ym.monthValue)} ${nxt.ym.year}") +
                        (if (nxt.parts > 1) t(" (1 de ${nxt.parts} prestações no ano)", " (1 of ${nxt.parts} instalments this year)") else ""),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (nxt.days <= 31) t("Faltam até ${nxt.days} dias para o fim desse mês.", "Up to ${nxt.days} days to the end of that month.")
                    else t("Faltam cerca de ${nxt.days / 30} meses.", "About ${nxt.days / 30} months to go."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onAddDoc) { Text(t("Criar prazo nos documentos", "Add a deadline to documents")) }
            } else Text(t("Escolhe o mês da matrícula para ver o próximo prazo.", "Pick the registration month to see the next deadline."), style = MaterialTheme.typography.bodySmall)
            Text(
                t(
                    "Regras: em 2026 paga-se no mês da matrícula. Em 2027 (transição): até 500 € numa prestação em outubro; acima de 500 € em julho e outubro. Desde 2028: até 100 € em abril; 100 a 500 € em abril e outubro; acima de 500 € em abril, julho e outubro. Valores de fontes secundárias, sem os dias exatos: confirma no Portal das Finanças.",
                    "Rules: in 2026 you pay in the registration month. In 2027 (transition): up to €500 in one instalment in October; above €500 in July and October. From 2028: up to €100 in April; €100–500 in April and October; above €500 in April, July and October. Figures come from secondary sources and exact days are unknown: confirm on the Portuguese Tax Portal.",
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
