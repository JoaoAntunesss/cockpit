package com.diariobordo

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

val PT: Locale get() = if (Lang.en()) Locale.UK else Locale("pt", "PT")

fun fmt(d: Double, dec: Int = 1): String = String.format(PT, "%.${dec}f", d)
fun fint(n: Int): String = String.format(PT, "%,d", n)
fun eur(d: Double): String = fmt(d, 2) + " €"
fun num(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
fun dec(d: Double): String = if (d == 0.0) "" else d.toString().replace('.', if (Lang.en()) '.' else ',')
fun dateLabel(epochDay: Long): String {
    val d = LocalDate.ofEpochDay(epochDay)
    val m = d.month.getDisplayName(TextStyle.SHORT, PT).replace(".", "")
    return "${d.dayOfMonth} $m" + if (d.year != LocalDate.now().year) " ${d.year}" else ""
}

data class CarData(
    val vehicle: Vehicle,
    val fuelings: List<Fueling>,
    val services: List<Service>,
    val expenses: List<Expense>,
    val reminders: List<Reminder>,
    val docs: List<Doc> = emptyList(),
)

// ---------- Consumo ----------

data class FuelRow(val f: Fueling, val cons: Double?, val kmIv: Int, val lIv: Double)

/**
 * Consumo entre abastecimentos, como as pessoas o contam: "pus 14,8 L e fiz 225 km".
 * Os litros de um abastecimento contam para os km feitos até ao abastecimento seguinte.
 * Quando os dois abastecimentos são de depósito cheio (ou o anterior é o ponto de partida),
 * usa-se o método exato: os litros do segundo repõem o que se gastou.
 * Troços muito curtos juntam-se até MIN_KM (evita valores absurdos com 5 L e 40 km).
 * O consumo fica no abastecimento que fecha o troço.
 */
private const val MIN_KM = 150

fun fuelRows(list: List<Fueling>): List<FuelRow> {
    val s = list.sortedWith(compareBy({ it.km }, { it.date }))
    val out = ArrayList<FuelRow>(s.size)
    var accL = 0.0
    var accKm = 0
    s.forEachIndexed { i, cur ->
        if (i == 0) { out += FuelRow(cur, null, 0, 0.0); return@forEachIndexed }
        val prev = s[i - 1]
        val dk = cur.km - prev.km
        if (dk <= 0) { out += FuelRow(cur, null, 0, 0.0); return@forEachIndexed }
        val exact = (prev.full && cur.full) || prev.liters == 0.0
        accL += if (exact) cur.liters else prev.liters
        accKm += dk
        if (exact || cur.full || accKm >= MIN_KM) {
            out += FuelRow(cur, accL / accKm * 100, accKm, accL)
            accL = 0.0
            accKm = 0
        } else out += FuelRow(cur, null, 0, 0.0)
    }
    return out
}

fun weighted(rows: List<FuelRow>): Double? {
    val km = rows.sumOf { it.kmIv }
    return if (km > 0) rows.sumOf { it.lIv } / km * 100 else null
}

data class FuelStats(
    val rows: List<FuelRow>,
    val measured: List<FuelRow>,
    val avg: Double?,
    val recent: Double?,
    val prior: Double?,
    val trendPct: Double?,
    val pricePerL: Double?,
)

fun fuelStats(list: List<Fueling>): FuelStats {
    val rows = fuelRows(list)
    val m = rows.filter { it.cons != null }
    val avg = weighted(m.takeLast(6))
    var recent: Double? = null
    var prior: Double? = null
    var pct: Double? = null
    if (m.size >= 6) {
        recent = weighted(m.takeLast(3))
        prior = weighted(m.dropLast(3).takeLast(5))
        if (recent != null && prior != null) pct = (recent - prior) / prior * 100
    }
    val last = rows.takeLast(6)
    val l = last.sumOf { it.f.liters }
    val t = last.sumOf { it.f.total }
    return FuelStats(rows, m, avg, recent, prior, pct, if (l > 0) t / l else null)
}

// ---------- Avisos de manutenção ----------

enum class Level { OK, SOON, LATE }
data class RemInfo(val progress: Double, val text: String, val level: Level)

fun remInfo(r: Reminder, carKm: Int, today: LocalDate = LocalDate.now()): RemInfo {
    val days = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(r.lastDate), today)
    val pk = if (r.everyKm > 0) (carKm - r.lastKm).toDouble() / r.everyKm else 0.0
    val pt = if (r.everyMonths > 0) days / (r.everyMonths * 30.4375) else 0.0
    val text = if (r.everyKm > 0 && pk >= pt) {
        val left = r.everyKm - (carKm - r.lastKm)
        if (left >= 0) t("Faltam ${fint(left)} km", "${fint(left)} km left") else t("Ultrapassado em ${fint(-left)} km", "${fint(-left)} km overdue")
    } else {
        val left = Math.round(r.everyMonths * 30.4375 - days).toInt()
        if (left >= 0) t("Faltam $left dias", "$left days left") else t("Atrasado há ${-left} dias", "${-left} days overdue")
    }
    val p = max(max(pk, pt), 0.0)
    return RemInfo(p, text, if (p >= 1) Level.LATE else if (p >= 0.85) Level.SOON else Level.OK)
}

// ---------- Gastos por mês ----------

data class MonthSum(val label: String, val fuel: Double, val mech: Double, val fixed: Double) {
    val total get() = fuel + mech + fixed
}

fun monthly(d: CarData, n: Int): List<MonthSum> {
    val now = YearMonth.now()
    fun ym(day: Long) = YearMonth.from(LocalDate.ofEpochDay(day))
    return (n - 1 downTo 0).map { i ->
        val m = now.minusMonths(i.toLong())
        MonthSum(
            label = m.month.getDisplayName(TextStyle.SHORT, PT).replace(".", ""),
            fuel = d.fuelings.filter { ym(it.date) == m }.sumOf { it.total },
            mech = d.services.filter { ym(it.date) == m }.sumOf { it.parts + it.labor },
            fixed = d.expenses.filter { ym(it.date) == m }.sumOf { it.amount },
        )
    }
}

/** Quilómetros percorridos no período, a partir dos registos com km. */
fun kmSpan(d: CarData, months: Int): Int {
    val start = YearMonth.now().minusMonths((months - 1).toLong()).atDay(1).toEpochDay()
    val kms = d.fuelings.filter { it.date >= start }.map { it.km } + d.services.filter { it.date >= start }.map { it.km }
    return if (kms.size < 2) 0 else kms.max() - kms.min()
}

val SERVICE_CATEGORIES = listOf("Óleo e filtros", "Travões", "Pneus", "Motor", "Elétrico", "Suspensão", "Carroçaria", "Outros")
val EXPENSE_TYPES = listOf("Seguro", "IUC", "Inspeção", "Portagem", "Estacionamento", "Outro")


// ---------- Km estimados pelo teu ritmo ----------

class KmEstimate(val km: Int, val pace: Double)

/** Estima os km de hoje pelo ritmo do último ano. Devolve null se faltarem dados ou se os km foram atualizados há menos de 7 dias. */
fun estimateKm(d: CarData, confirmDay: Long = 0, today: LocalDate = LocalDate.now()): KmEstimate? {
    val pts = (d.fuelings.map { it.date to it.km } + d.services.map { it.date to it.km }).sortedBy { it.first }
    if (pts.size < 2) return null
    val recent = pts.filter { it.first >= today.toEpochDay() - 365 }.let { if (it.size >= 2) it else pts }
    val a = recent.first()
    val b = recent.last()
    val days = b.first - a.first
    if (days < 14 || b.second <= a.second) return null
    val pace = (b.second - a.second).toDouble() / days
    val since = (today.toEpochDay() - maxOf(b.first, confirmDay)).coerceAtMost(180)
    if (since < 7) return null
    return KmEstimate(d.vehicle.km + Math.round(pace * since).toInt(), pace)
}

object KmPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("diario", 0)
    fun day(ctx: Context, id: Long): Long = p(ctx).getLong("kmday_$id", 0)
    fun setDay(ctx: Context, id: Long) = p(ctx).edit().putLong("kmday_$id", LocalDate.now().toEpochDay()).apply()
    fun on(ctx: Context): Boolean = p(ctx).getBoolean("est_on", true)
    fun notifOn(ctx: Context): Boolean = p(ctx).getBoolean("notif_on", true)
    fun priceOn(ctx: Context): Boolean = p(ctx).getBoolean("price_on", false)
    fun set(ctx: Context, key: String, v: Boolean) = p(ctx).edit().putBoolean(key, v).apply()
}

// ---------- Elétricos: mesma estrutura, outra unidade ----------
fun isEv(fuelType: String) = fuelType == "Elétrico"
/** Híbrido simples ou plug-in: «Híbrido (Gasolina 95)», «Híbrido plug-in (Gasóleo)». */
fun isHybrid(fuelType: String) = fuelType.startsWith("Híbrido")
fun isPhev(fuelType: String) = fuelType.startsWith("Híbrido plug-in")
/** O combustível líquido de um híbrido (para preços e postos). */
fun baseFuel(fuelType: String): String = if (isHybrid(fuelType)) fuelType.substringAfter("(").substringBefore(")") else fuelType
/** Unidade de um registo: kWh nas cargas elétricas, L nos abastecimentos. */
fun unitOf(f: Fueling, fuelType: String) = if (f.electric || isEv(fuelType)) "kWh" else "L"
/** Só os abastecimentos de combustível (as cargas de um plug-in contam à parte). */
fun CarData.liquid() = fuelings.filter { !it.electric }
/** Unidade da quantidade abastecida: kWh nos elétricos, L nos outros. */
fun qtyUnit(fuelType: String) = if (isEv(fuelType)) "kWh" else "L"
/** Unidade do consumo médio. */
fun consUnit(fuelType: String) = if (isEv(fuelType)) "kWh/100" else "L/100"

/** Gasto total de um mês (combustível, intervenções e despesas). */
fun monthSpend(d: CarData, m: java.time.YearMonth = java.time.YearMonth.now()): Double {
    fun inM(day: Long) = java.time.YearMonth.from(LocalDate.ofEpochDay(day)) == m
    return d.fuelings.filter { inM(it.date) }.sumOf { it.total } +
        d.services.filter { inM(it.date) }.sumOf { it.parts + it.labor } +
        d.expenses.filter { inM(it.date) }.sumOf { it.amount }
}

/** Custo provável da próxima intervenção desta categoria: média das últimas 3 (peças + mão de obra). */
fun serviceEstimate(services: List<Service>, category: String): Double? {
    val last = services.filter { it.category == category && it.parts + it.labor > 0 }.sortedByDescending { it.date }.take(3)
    return if (last.isEmpty()) null else last.sumOf { it.parts + it.labor } / last.size
}
