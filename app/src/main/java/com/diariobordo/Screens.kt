package com.diariobordo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

class Actions(
    val fuel: (Fueling) -> Unit,
    val serv: (Service) -> Unit,
    val exp: (Expense) -> Unit,
    val remDone: (Reminder) -> Unit,
    val remEdit: (Reminder) -> Unit,
    val remNew: () -> Unit,
    val goTab: (Tab) -> Unit,
    val nearby: () -> Unit,
    val nearbyEv: () -> Unit,
    val doc: (Doc?) -> Unit,
    val confirmKm: (Int) -> Unit,
    val fixKm: () -> Unit,
    val tires: () -> Unit,
    val create: (String) -> Unit = {},
    val del: (Any) -> Unit = {},
    val editCar: () -> Unit = {},
)

private val Gap = Arrangement.spacedBy(12.dp)

@Composable
private fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

@Composable
private fun cardBorder() = BorderStroke(
    1.dp,
    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.42f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))),
)

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 16.dp).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))))
        Spacer(Modifier.width(10.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
}

@Composable
private fun Row3(icon: ImageVector, title: String, sub: String, value: String, tag: String? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconBadge(icon) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(value, fontWeight = FontWeight.SemiBold)
                if (tag != null) Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun Empty(icon: ImageVector, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (action != null) FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun DocRows(docs: List<Doc>, a: Actions) {
    Column {
        docs.forEach { dc ->
            val i = docInfo(dc)
            val c = levelColor(i.level)
            ListItem(
                modifier = Modifier.clickable { a.doc(dc) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { IconBadge(docIcon(dc.type)) },
                headlineContent = { Text(dc.type + if (dc.title.isNotBlank()) " · ${dc.title}" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                supportingContent = { Text(t("Até ", "Until ") + docDate(dc.expiry) + if (dc.image.isNotBlank()) t(" · com imagem", " · with image") else "", maxLines = 1) },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(c))
                            Spacer(Modifier.width(6.dp))
                            Text(i.text, color = c, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                    }
                },
            )
        }
    }
}

// ---------- Início ----------

@Composable
private fun BudgetBar(spent: Double, budget: Double) {
    val ratio = (spent / budget).coerceIn(0.0, 1.0).toFloat()
    val over = spent > budget
    val color = if (over) Color(0xFFFF6B6B) else if (spent >= budget * 0.8) Color(0xFFF5B73B) else MaterialTheme.colorScheme.primary
    Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(t("Orçamento do mês", "Monthly budget"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${eur(spent)} / ${eur(budget)}", color = color, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { ratio }, color = color, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
            if (over) Text(t("Ultrapassaste o orçamento em ${eur(spent - budget)}.", "You are ${eur(spent - budget)} over budget."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Anéis de estado, como os indicadores de serviço de um carro moderno. */
@Composable
private fun ServiceRings(rems: List<Reminder>, curKm: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(vertical = 18.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        rems.forEach { r ->
            val i = remInfo(r, curKm)
            val c = levelColor(i.level)
            val target = i.progress.coerceIn(0.0, 1.0).toFloat()
            val frac = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(target) { frac.animateTo(target, androidx.compose.animation.core.tween(900)) }
            Column(Modifier.weight(1f).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val st = 7.dp.toPx()
                        val dia = size.minDimension - st * 1.8f
                        val tl = Offset((size.width - dia) / 2f, (size.height - dia) / 2f)
                        drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, tl, Size(dia, dia), style = Stroke(st, cap = StrokeCap.Round))
                        if (frac.value > 0.001f) {
                            drawArc(c.copy(alpha = 0.25f), 135f, 270f * frac.value, false, tl, Size(dia, dia), style = Stroke(st * 1.8f, cap = StrokeCap.Round))
                            drawArc(c, 135f, 270f * frac.value, false, tl, Size(dia, dia), style = Stroke(st, cap = StrokeCap.Round))
                        }
                    }
                    Text("${Math.round(target * 100)}%", fontWeight = FontWeight.Black, fontSize = 15.sp, fontFamily = FontFamily.Monospace, color = c)
                }
                Text(r.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(i.text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** O que importa agora: o prazo ou aviso mais urgente, em grande. */
@Composable
private fun FocusCard(d: CarData, curKm: Int, a: Actions) {
    class Focus(val title: String, val big: String, val level: Level, val score: Double, val open: () -> Unit)
    fun score(level: Level, p: Double) = when (level) { Level.LATE -> 3.0 + p; Level.SOON -> 2.0 + p; Level.OK -> p }
    val all = buildList {
        d.docs.forEach { dc ->
            val i = docInfo(dc)
            val closeness = (1.0 - (i.days.coerceIn(0, 365) / 365.0)).coerceIn(0.0, 1.0)
            val big = when {
                i.days < 0 -> t("Expirou há ${-i.days} d", "Expired ${-i.days} d ago")
                i.days == 0 -> t("Expira hoje", "Expires today")
                else -> t("Faltam ${i.days} ${if (i.days == 1) "dia" else "dias"}", "${i.days} ${if (i.days == 1) "day" else "days"} left")
            }
            add(Focus(dc.type + if (dc.title.isNotBlank()) " · ${dc.title}" else "", big, i.level, score(i.level, closeness)) { a.doc(dc) })
        }
        d.reminders.forEach { r ->
            val ri = remInfo(r, curKm)
            add(Focus(r.name, ri.text, ri.level, score(ri.level, ri.progress.coerceIn(0.0, 1.0))) { a.goTab(Tab.MAINT) })
        }
    }
    val f = all.maxByOrNull { it.score } ?: return
    val c = levelColor(f.level)
    val shape = RoundedCornerShape(16.dp)
    val base = MaterialTheme.colorScheme.surfaceContainer
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (f.level == Level.OK) base else c.copy(alpha = 0.16f).compositeOver(base))
            .clickable(onClick = f.open).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when (f.level) {
                    Level.OK -> t("TUDO EM DIA · A SEGUIR", "ALL GOOD · NEXT UP")
                    Level.SOON -> t("A PRECISAR DE ATENÇÃO", "NEEDS ATTENTION")
                    Level.LATE -> t("EM ATRASO", "OVERDUE")
                },
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, color = c,
            )
            Text(f.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(f.big, fontSize = 26.sp, fontWeight = FontWeight.Black, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HomeScreen(d: CarData, a: Actions, pad: PaddingValues) {
    val st = remember(d.fuelings) { fuelStats(d.liquid()) }
    val monthKey = LocalDate.now().let { it.year * 100 + it.monthValue }
    fun key(day: Long) = LocalDate.ofEpochDay(day).let { it.year * 100 + it.monthValue }
    val monthSpend = d.fuelings.filter { key(it.date) == monthKey }.sumOf { it.total } +
        d.services.filter { key(it.date) == monthKey }.sumOf { it.parts + it.labor } +
        d.expenses.filter { key(it.date) == monthKey }.sumOf { it.amount }
    val kmCtx = LocalContext.current
    val kmEst = if (KmPrefs.on(kmCtx)) estimateKm(d, KmPrefs.day(kmCtx, d.vehicle.id)) else null
    val curKm = kmEst?.km ?: d.vehicle.km
    val rems = d.reminders.sortedByDescending { remInfo(it, curKm).progress }.take(4)

    class Recent(val date: Long, val icon: ImageVector, val title: String, val sub: String, val value: Double, val open: () -> Unit)
    val recents = (
        d.fuelings.map { f -> Recent(f.date, Icons.Filled.LocalGasStation, tr("Abastecimento"), "${dateLabel(f.date)} · ${fmt(f.liters)} ${unitOf(f, d.vehicle.fuelType)}", f.total) { a.fuel(f) } } +
        d.services.map { s -> Recent(s.date, Icons.Filled.Build, s.title, "${dateLabel(s.date)} · ${s.category}", s.parts + s.labor) { a.serv(s) } } +
        d.expenses.map { e -> Recent(e.date, Icons.Filled.Receipt, e.type, "${dateLabel(e.date)} · " + tr("Despesa"), e.amount) { a.exp(e) } }
    ).sortedByDescending { it.date }.take(5)

    LazyColumn(
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 4.dp, 16.dp, pad.calculateBottomPadding() + 88.dp),
        verticalArrangement = Gap,
    ) {
        if (d.vehicle.monthlyBudget > 0) item { BudgetBar(monthSpend, d.vehicle.monthlyBudget) }
        item { Appear(0) { Hero(d.vehicle, st.avg, monthSpend, a.fixKm, a.editCar) } }
        item { Appear(1) { FocusCard(d, curKm, a) } }
        item { StartCard(d, a) }
        item { Appear(1) { InsightsCard(d, monthSpend) } }
        kmEst?.let { e ->
            item { KmCard(e.km, d.vehicle.km, onYes = { a.confirmKm(e.km) }, onFix = a.fixKm) }
        }
        st.trendPct?.let { pct ->
            item {
                val up = pct > 5
                val down = pct < -5
                Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(if (up) Icons.Filled.Warning else Icons.Filled.CheckCircle, null)
                        Text(
                            when {
                                up -> t((if (pct >= 15) "Consumo anormal. " else "Consumo a subir. ") + "Nos últimos 3 abastecimentos gastaste ${fmt(pct, 0)}% mais (${fmt(st.prior ?: 0.0)} → ${fmt(st.recent ?: 0.0)} ${consUnit(d.vehicle.fuelType)} km). Vale a pena ver filtros, pneus e injetores.", (if (pct >= 15) "Unusual consumption. " else "Consumption is rising. ") + "Over the last 3 refuels you used ${fmt(pct, 0)}% more (${fmt(st.prior ?: 0.0)} → ${fmt(st.recent ?: 0.0)} ${consUnit(d.vehicle.fuelType)} km). Worth checking filters, tyres and injectors.")
                                down -> t("Consumo a descer. Menos ${fmt(-pct, 0)}% nos últimos 3 abastecimentos (${fmt(st.prior ?: 0.0)} → ${fmt(st.recent ?: 0.0)} ${consUnit(d.vehicle.fuelType)} km).", "Consumption is falling. ${fmt(-pct, 0)}% less over the last 3 refuels (${fmt(st.prior ?: 0.0)} → ${fmt(st.recent ?: 0.0)} ${consUnit(d.vehicle.fuelType)} km).")
                                else -> t("Consumo estável. Os últimos 3 abastecimentos ficaram perto de ${fmt(st.recent ?: 0.0)} L/100 km.", "Consumption is stable. The last 3 refuels were around ${fmt(st.recent ?: 0.0)} ${consUnit(d.vehicle.fuelType)} km.")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        val dueDocs = d.docs.filter { docInfo(it).level != Level.OK }.take(3)
        if (dueDocs.isNotEmpty()) {
            item { SectionTitle(tr("Prazos a atenção"), tr("Ver todos")) { a.goTab(Tab.MAINT) } }
            item {
                Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { DocRows(dueDocs, a) }
            }
        }
        item { SectionTitle(t("Estado do carro", "Car status"), tr("Ver todos")) { a.goTab(Tab.MAINT) } }
        item {
            if (rems.isEmpty()) Empty(Icons.Filled.Build, tr("Ainda não tens avisos. Cria um na secção Manutenção, por exemplo para o óleo."), tr("Criar aviso")) { a.remNew() }
            else Appear(2) { ServiceRings(rems, curKm) { a.goTab(Tab.MAINT) } }
        }
        item { SectionTitle(tr("Últimos registos")) }
        item {
            if (recents.isEmpty()) Empty(Icons.Filled.LocalGasStation, tr("Sem registos ainda. Usa o botão Registar para o primeiro abastecimento ou intervenção."))
            else Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column { recents.forEach { r -> Row3(r.icon, r.title, r.sub, eur(r.value), onClick = r.open) } }
            }
        }
    }
}

@Composable
private fun Hero(v: Vehicle, avg: Double?, monthSpend: Double, onKm: () -> Unit = {}, onPlate: () -> Unit = {}) {
    val shownSpend by androidx.compose.animation.core.animateFloatAsState(monthSpend.toFloat(), androidx.compose.animation.core.tween(700), label = "spend")
    val accent = MaterialTheme.colorScheme.primary
    val dark = Color(0xFF0B0C0F)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = dark, contentColor = Color.White),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.6f), Brand.of(v.brand).second.copy(alpha = 0.35f)))),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent, Brand.of(v.brand).third.copy(alpha = 0.13f)))
            )
        ) {
            // Marca de água com o modelo
            Text(
                v.model.uppercase(), fontSize = if (v.model.length <= 4) 110.sp else 64.sp, fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic, color = Color.White.copy(alpha = 0.05f), maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 18.dp),
            )
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandMark(Brand.of(v.brand), 34.dp, Color.White.copy(alpha = 0.10f), Color.White)
                    Column(Modifier.weight(1f)) {
                        Text(v.name.uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${v.fuelType} · ${v.engine} · ${v.year}".trim(' ', '·'),
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Plate(v.plate, onPlate)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                        Gauge(avg, if (avg != null) maxOf(10.0, Math.ceil(avg * 1.5)) else 10.0, accent, Modifier.fillMaxSize(), Brand.of(v.brand).second, Brand.of(v.brand).third)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(avg?.let { fmt(it) } ?: "--.-", fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = accent)
                            Text(consUnit(v.fuelType).uppercase() + " KM", fontSize = 10.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.8f))
                            Text(tr("MÉDIA"), fontSize = 9.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.65f))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(t("ODÓMETRO · toca para alterar", "ODOMETER · tap to change"), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        val kmAnim = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(v.km) { kmAnim.animateTo(v.km.toFloat(), androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onKm)) { Odometer(kmAnim.value.toInt(), accent) }
                        HeroStat(tr("Gasto este mês"), eur(shownSpend.toDouble()), Modifier.fillMaxWidth())
                    }
                }
                if (avg == null) Text(
                    t("Regista o 1.º abastecimento (podes dizer quantos km fizeste com o depósito anterior) para ver já a média.", "Log your first refuel (you can enter the km driven on the previous tank) to see the average right away."),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Conta-quilómetros: um quadrado por dígito. */
@Composable
private fun Odometer(km: Int, accent: Color) {
    val digits = km.coerceAtLeast(0).toString().padStart(6, '0')
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        digits.forEach { c ->
            Box(
                Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black)
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) { Text(c.toString(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        }
    }
}

/** Quadrante digital: arco de segmentos LED, como o painel de instrumentos de um carro moderno. */
@Composable
private fun Gauge(value: Double?, max: Double, accent: Color, modifier: Modifier = Modifier, second: Color = Color.White, third: Color = accent) {
    val frac0 = if (value == null) 0f else (value / max).coerceIn(0.0, 1.0).toFloat()
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(frac0) { anim.animateTo(frac0, androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    Canvas(modifier) {
        val n = 40
        val sweep = 270f
        val gap = 1.6f
        val seg = sweep / n - gap
        val stroke = size.minDimension * 0.085f
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        val arcSize = Size(d, d)
        val lit = Math.round(anim.value * n)
        for (i in 0 until n) {
            val on = i < lit
            // do verde/cor da marca até ao vermelho nos últimos segmentos
            val tip = on && i == lit - 1
            val tone = if (!on) Color.White.copy(alpha = 0.09f)
                else if (i >= n * 0.85f) Color(0xFFFF4D4D) else if (tip) androidx.compose.ui.graphics.lerp(accent, Color.White, 0.55f) else accent
            val start = 135f + i * (seg + gap)
            if (on) drawArc(tone.copy(alpha = if (tip) 0.45f else 0.25f), start, seg, false, topLeft, arcSize, style = Stroke(stroke * (if (tip) 2.1f else 1.7f)))
            drawArc(tone, start, seg, false, topLeft, arcSize, style = Stroke(stroke))
        }
        // ponteiro: marca o valor atual, do anel interior até à ponta do arco
        run {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = d / 2f
            val ang = Math.toRadians((135f + sweep * anim.value).toDouble())
            val ux = Math.cos(ang).toFloat()
            val uy = Math.sin(ang).toFloat()
            val p0 = Offset(cx + ux * r * 0.72f, cy + uy * r * 0.72f)
            val p1 = Offset(cx + ux * (r + stroke * 0.3f), cy + uy * (r + stroke * 0.3f))
            drawLine(second.copy(alpha = 0.28f), p0, p1, strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
            drawLine(second, p0, p1, strokeWidth = stroke * 0.38f, cap = StrokeCap.Round)
            drawCircle(third, radius = stroke * 0.34f, center = p1)
        }
        // anel interior fino
        val inner = d - stroke * 2.4f
        drawArc(
            Color.White.copy(alpha = 0.12f), 135f, 270f, false,
            Offset((size.width - inner) / 2f, (size.height - inner) / 2f), Size(inner, inner), style = Stroke(2f),
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.07f)).padding(12.dp, 8.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Matrícula portuguesa: faixa azul com P e campo branco. */
@Composable
private fun Plate(text: String, onClick: () -> Unit = {}) {
    Row(
        Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.background(Color(0xFF003399)).padding(horizontal = 5.dp, vertical = 5.dp)) {
            Text("P", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text.ifBlank { t("Adicionar matrícula", "Add plate") }, color = if (text.isBlank()) Color(0xFF6B7280) else Color(0xFF111111), fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = if (text.isBlank()) 12.sp else 14.sp, modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun levelColor(l: Level): Color = when (l) {
    Level.OK -> Color(0xFF2FD05A)
    Level.SOON -> Color(0xFFFAB219)
    Level.LATE -> Color(0xFFFF5C5C)
}

@Composable
private fun ReminderBlock(r: Reminder, carKm: Int, a: Actions?, est: Double? = null) {
    val i = remInfo(r, carKm)
    val c = levelColor(i.level)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // luz de painel
            Box(Modifier.size(10.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(6.dp))
            Text(
                when (i.level) { Level.OK -> tr("EM DIA"); Level.SOON -> tr("EM BREVE"); Level.LATE -> tr("ATRASADO") },
                color = c, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.2.sp,
            )
        }
        SegBar(i.progress.coerceIn(0.0, 1.0).toFloat(), c, Modifier.fillMaxWidth().height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(i.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                if (est != null) Text(t("Custo estimado: ~${eur(est)}", "Estimated cost: ~${eur(est)}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (a != null) {
                TextButton(onClick = { a.remDone(r) }) { Text(tr("Feito agora")) }
                TextButton(onClick = { a.remEdit(r) }) { Text(tr("Editar")) }
            }
        }
    }
}

/** Barra de segmentos LED. */
@Composable
private fun SegBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = 24
        val gap = 3f
        val w = (size.width - gap * (n - 1)) / n
        val lit = Math.round(progress * n)
        for (k in 0 until n) {
            val x = k * (w + gap)
            val on = k < lit
            if (on) drawRect(color.copy(alpha = 0.22f), Offset(x - 1f, -1f), Size(w + 2f, size.height + 2f))
            drawRect(if (on) color else Color.White.copy(alpha = 0.08f), Offset(x, 0f), Size(w, size.height))
        }
    }
}

// ---------- Combustível ----------

@Composable
fun FuelScreen(d: CarData, a: Actions, pad: PaddingValues, prices: PricesState, onRefresh: () -> Unit) {
    val st = remember(d.fuelings) { fuelStats(d.liquid()) }
    val chart = st.measured.takeLast(14)
    LazyColumn(
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 4.dp, 16.dp, pad.calculateBottomPadding() + 88.dp),
        verticalArrangement = Gap,
    ) {
        item {
            Text(tr("Combustível"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Text(if (isEv(d.vehicle.fuelType)) "${d.vehicle.name} · " + t("bateria de ${d.vehicle.tankL} kWh", "${d.vehicle.tankL} kWh battery") else "${d.vehicle.name} · depósito de ${d.vehicle.tankL} L", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isEv(d.vehicle.fuelType)) PriceCard(d.vehicle.fuelType, prices, onRefresh, a.nearby)
                if (isEv(d.vehicle.fuelType) || isPhev(d.vehicle.fuelType)) ChargerCard(a.nearbyEv)
            }
        }
        if (d.fuelings.isEmpty()) {
            item { Empty(Icons.Filled.LocalGasStation, tr("Ainda sem abastecimentos. Regista o primeiro e o consumo aparece depois de uns 200 km."), tr("Registar abastecimento")) { a.create("fuel") } }
            return@LazyColumn
        }
        item {
            val tp = st.trendPct
            if (tp != null) {
                val worse = tp > 3
                val better = tp < -3
                Text(
                    when {
                        worse -> t("▲ Estás a gastar ${fmt(tp, 0)}% mais do que antes.", "▲ You are using ${fmt(tp, 0)}% more than before.")
                        better -> t("▼ Estás a gastar ${fmt(-tp, 0)}% menos do que antes.", "▼ You are using ${fmt(-tp, 0)}% less than before.")
                        else -> tr("● O consumo mantém-se estável.")
                    },
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = if (worse) Color(0xFFFF6B6B) else if (better) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(tr("Consumo médio"), st.avg?.let { fmt(it) + " " + consUnit(d.vehicle.fuelType) } ?: "—", Modifier.weight(1f))
                Tile(tr("Preço médio"), st.pricePerL?.let { fmt(it, 3) + " €/" + qtyUnit(d.vehicle.fuelType) } ?: "—", Modifier.weight(1f))
                Tile(tr("Último"), dateLabel(d.fuelings.first().date), Modifier.weight(1f))
            }
        }
        if (isPhev(d.vehicle.fuelType)) item {
            val el = d.fuelings.filter { it.electric }
            Text(
                t("Eletricidade: ${fmt(el.sumOf { it.liters })} kWh em ${el.size} cargas · ${eur(el.sumOf { it.total })}", "Electricity: ${fmt(el.sumOf { it.liters })} kWh over ${el.size} charges · ${eur(el.sumOf { it.total })}"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { SectionTitle(tr("Consumo ao longo do tempo")) }
        item {
            Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (chart.size >= 2 && st.avg != null) {
                        Text((if (isEv(d.vehicle.fuelType)) t("kWh/100 km por carga · últimas ${chart.size}", "kWh/100 km per charge · last ${chart.size}") else t("L/100 km por abastecimento · últimos ${chart.size}", "L/100 km per refuel · last ${chart.size}")), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        ConsumptionChart(chart.map { it.cons!! }, st.avg)
                    } else Text(tr("Com dois ou três abastecimentos e uns 200 km já aparece o gráfico do consumo."))
                }
            }
        }
        item { SectionTitle(tr("Histórico")) }
        item {
            Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    val byId = st.rows.associateBy { it.f.id }
                    val all = (st.rows.map { it.f } + d.fuelings.filter { it.electric }).sortedWith(compareBy({ it.date }, { it.km })).reversed()
                    all.forEach { e ->
                        val r = byId[e.id]
                        val uq = unitOf(e, d.vehicle.fuelType)
                        val high = r?.cons != null && st.avg != null && r.cons > st.avg * 1.08
                        key(e.id) { SwipeDelete({ a.del(e) }) { Row3(
                            if (e.electric) Icons.Filled.EvStation else Icons.Filled.LocalGasStation,
                            "${fmt(e.liters)} $uq · ${fmt(e.total / e.liters, 3)} €/$uq",
                            "${dateLabel(e.date)} · ${fint(e.km)} km" + if (e.station.isNotBlank()) " · ${e.station}" else "",
                            eur(e.total),
                            tag = when {
                                e.electric -> "⚡"
                                r?.cons != null -> (if (high) "⚠ " else "") + fmt(r.cons) + " " + consUnit(d.vehicle.fuelType)
                                e.full -> tr("referência")
                                else -> "parcial"
                            },
                        ) { a.fuel(e) } } }
                    }
                }
            }
        }
    }
}

fun ledColor(): Color = if (UiPrefs.led == 0) Color.Unspecified else Color(UiPrefs.led)

fun fuelBadge(f: FuelPrice): Pair<String, Color> {
    val k = (f.slug + " " + f.name).lowercase()
    val plus = if (listOf("plus", "especial", "special", "premium", "simples+").any { k.contains(it) }) "+" else ""
    return when {
        k.contains("diesel") || k.contains("gasóleo") || k.contains("gasoleo") -> (if (k.contains("bio") || k.contains("b15")) "B15" else "D$plus") to (if (k.contains("bio") || k.contains("b15")) Color(0xFF14B8A6) else Color(0xFFF5B400))
        k.contains("98") -> "98$plus" to Color(0xFF1E88E5)
        k.contains("95") -> "95$plus" to Color(0xFF2EAD4B)
        k.contains("gpl") -> "GPL" to Color(0xFF8E5BD6)
        else -> f.name.take(2).uppercase() to Color(0xFF7D8590)
    }
}

/** Fora da lista: gasóleo colorido e de aquecimento não são para carros. */
fun isRoadFuel(f: FuelPrice): Boolean {
    val k = (f.slug + " " + f.name).lowercase()
    return listOf("aquecimento", "colorido", "heating", "colou", "dyed").none { k.contains(it) }
}

fun fuelLabel(f: FuelPrice): String {
    val k = (f.slug + " " + f.name).lowercase()
    return if (k.contains("biodiesel") || k.contains("b15")) t("Gasóleo B15 (biodiesel)", "Diesel B15 (biodiesel)") else f.name
}

fun fuelOrder(f: FuelPrice): Int {
    val k = (f.slug + " " + f.name).lowercase()
    val base = when {
        k.contains("gpl") -> 40
        k.contains("diesel") || k.contains("gasóleo") || k.contains("gasoleo") -> 10
        k.contains("95") -> 20
        k.contains("98") -> 30
        else -> 50
    }
    return base + if (k.contains("plus") || k.contains("especial") || k.contains("special")) 1 else if (k.contains("bio") || k.contains("b15")) 2 else 0
}

/** Elétricos: em vez do totem de preços, atalho para os postos de carregamento. */
@Composable
private fun ChargerCard(onNearby: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050506)),
        border = BorderStroke(2.dp, Color(0xFF2A2D33)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.EvStation, null, tint = MaterialTheme.colorScheme.primary)
                Text(tr("Postos de carregamento"), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(
                t("Vê no mapa os carregadores perto de ti, com a potência e as fichas. Dados do OpenStreetMap.", "See chargers near you on the map, with power and plug types. Data from OpenStreetMap."),
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f),
            )
            Button(onClick = onNearby, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(tr("Ver carregadores perto de mim")) }
        }
    }
}

/** Totem de posto: fundo preto, dígitos LED (a cor é escolhida nas Definições), último dígito mais pequeno. */
@Composable
private fun PriceCard(fuelType: String, ps: PricesState, onRefresh: () -> Unit, onNearby: () -> Unit) {
    val ctx = LocalContext.current
    val mine = (UiPrefs.fav.takeIf { f -> ps.map[f]?.let { isRoadFuel(it) } == true } ?: FuelPrices.slugFor(fuelType)) ?: ""
    val led = ledColor().let { if (it == Color.Unspecified) MaterialTheme.colorScheme.primary else it }
    val rows = ps.map.values.filter { isRoadFuel(it) }.sortedBy { fuelOrder(it) }.take(6)
    val frame = Color(0xFF2A2D33)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF050506))
            .border(2.dp, frame, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("PREÇOS MÉDIOS · PORTUGAL"), color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
            )
            if (ps.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = led)
            else IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, tr("Atualizar preços"), tint = Color.White.copy(alpha = 0.7f)) }
        }
        if (rows.isEmpty() && ps.loading) ShimmerRows(3, Modifier.padding(vertical = 6.dp), Color.White)
        else if (rows.isEmpty()) {
            Text(
                if (ps.error) tr("Não foi possível obter os preços. Tenta outra vez quando tiveres rede.") else tr("A obter preços…"),
                color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
            )
        }
        rows.forEach { f ->
            val (letter, tint) = fuelBadge(f)
            val isMine = f.slug == mine
            val txt = fmt(f.avg, 3)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable { UiPrefs.setFav(ctx, f.slug) }
                    .background(if (isMine) Color.White.copy(alpha = 0.07f) else Color.Transparent)
                    .border(1.dp, if (isMine) led.copy(alpha = 0.7f) else frame, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.width(52.dp).clip(RoundedCornerShape(8.dp)).background(tint).padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, color = onColor(tint), fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(fuelLabel(f), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (isMine) tr("O TEU COMBUSTÍVEL") else "${fmt(f.min, 3)} – ${fmt(f.max, 3)}",
                        color = if (isMine) led else Color.White.copy(alpha = 0.45f), fontSize = 10.sp, letterSpacing = 0.8.sp,
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    LedNumber(txt.dropLast(1), led, 30.dp)
                    Spacer(Modifier.width(3.dp))
                    LedNumber(txt.takeLast(1), led, 17.dp)
                }
            }
        }
        if (rows.isNotEmpty()) Text(tr("Toca num combustível para o escolher"), color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodySmall)
        rows.firstOrNull()?.let {
            Text(
                t("€/L · atualizado a ${it.date}", "€/L · updated ${it.date}") + if (ps.error) tr(" (sem rede, valor guardado)") else "",
                color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.bodySmall,
            )
        }
        FilledTonalButton(onClick = onNearby, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Place, null); Spacer(Modifier.width(8.dp)); Text(tr("Postos perto de mim"))
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp, 12.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- Manutenção ----------

@Composable
fun MaintScreen(d: CarData, a: Actions, pad: PaddingValues) {
    val kmCtx = LocalContext.current
    val kmEst = if (KmPrefs.on(kmCtx)) estimateKm(d, KmPrefs.day(kmCtx, d.vehicle.id)) else null
    val curKm = kmEst?.km ?: d.vehicle.km
    val rems = d.reminders.sortedByDescending { remInfo(it, curKm).progress }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 4.dp, 16.dp, pad.calculateBottomPadding() + 88.dp),
        verticalArrangement = Gap,
    ) {
        item {
            Text(tr("Manutenção"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Text("${d.vehicle.name} · ${fint(d.vehicle.km)} km", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionTitle(tr("Documentos e prazos"), tr("Novo")) { a.doc(null) } }
        item {
            if (d.docs.isEmpty()) Empty(Icons.Filled.Shield, tr("Guarda aqui o seguro, a inspeção e o IUC com a data de validade e um print. A app avisa-te antes de acabar."), tr("Adicionar documento")) { a.doc(null) }
            else Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { DocRows(d.docs, a) }
        }
        item { IucCard(d.vehicle) { a.doc(null) } }
        item {
            OutlinedButton(onClick = a.tires, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.TireRepair, null); Spacer(Modifier.width(8.dp)); Text(t("Pneus", "Tyres"))
            }
        }
        item { SectionTitle(tr("Avisos de manutenção"), tr("Novo aviso")) { a.remNew() } }
        item {
            if (rems.isEmpty()) Empty(Icons.Filled.Build, tr("Sem avisos. Cria um para saberes quando mudar o óleo, os filtros ou a correia."), tr("Criar aviso")) { a.remNew() }
            else Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    rems.forEach { ReminderBlock(it, curKm, a, serviceEstimate(d.services, it.category)) }
                }
            }
        }
        item { SectionTitle(tr("Diário de intervenções")) }
        item {
            if (d.services.isEmpty()) Empty(Icons.Filled.Build, tr("Ainda sem intervenções. Regista a primeira com o botão Intervenção."), tr("Registar intervenção")) { a.create("serv") }
            else Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column {
                    d.services.forEach { s ->
                        key(s.id) { SwipeDelete({ a.del(s) }) { Row3(
                            Icons.Filled.Build, s.title,
                            "${dateLabel(s.date)} · ${fint(s.km)} km · " + if (s.byMe) tr("Feito por mim") else s.shop.ifBlank { tr("Oficina") },
                            eur(s.parts + s.labor), tag = s.category,
                        ) { a.serv(s) } } }
                    }
                }
            }
        }
    }
}

// ---------- Gastos ----------

@Composable
private fun OwnershipCard(d: CarData) {
    val v = d.vehicle
    val spent = d.fuelings.sumOf { it.total } + d.services.sumOf { it.parts + it.labor } + d.expenses.sumOf { it.amount }
    val dep = if (v.currentValue > 0) (v.purchasePrice - v.currentValue).coerceAtLeast(0.0) else null
    val totalCost = spent + (dep ?: 0.0)
    val kmDone = if (v.purchaseKm > 0) v.km - v.purchaseKm else 0
    Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Custo real do carro", "True cost of ownership"), style = MaterialTheme.typography.titleSmall)
            fun row(k: String, vv: String) = k to vv
            listOf(
                row(t("Gasto registado (tudo)", "Logged spending (all time)"), eur(spent)),
                row(t("Depreciação", "Depreciation"), dep?.let { eur(it) } ?: t("indica o valor atual no carro", "enter the current value on the car")),
                row(t("Custo total", "Total cost"), eur(totalCost)),
                row(t("Custo por km desde a compra", "Cost per km since purchase"), if (kmDone >= 500) fmt(totalCost / kmDone, 3) + " €" else "—"),
            ).forEach { (k, vv) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(k, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(vv, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(t("O preço de compra, os km e o valor atual editam-se em Carro e aspeto.", "Purchase price, km and current value are edited under Car and look."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CostsScreen(d: CarData, pad: PaddingValues, openYear: () -> Unit, delExp: (Expense) -> Unit, openExp: (Expense) -> Unit) {
    var period by rememberSaveable { mutableIntStateOf(6) }
    val months = remember(d, period) { monthly(d, period) }
    val fuel = months.sumOf { it.fuel }
    val mech = months.sumOf { it.mech }
    val fixed = months.sumOf { it.fixed }
    val total = fuel + mech + fixed
    val span = remember(d, period) { kmSpan(d, period) }
    val colors = chartColors()

    LazyColumn(
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 4.dp, 16.dp, pad.calculateBottomPadding() + 88.dp),
        verticalArrangement = Gap,
    ) {
        item {
            Text(tr("Gastos"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Text(d.vehicle.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 6, 12).forEach { p ->
                    FilterChip(selected = period == p, onClick = { period = p }, label = { Text(t("$p meses", "$p months")) })
                }
            }
        }
        item {
            OutlinedButton(onClick = openYear, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.Celebration, null); Spacer(Modifier.width(8.dp)); Text(t("Resumo do ano", "Year in review"))
            }
        }
        if (d.vehicle.purchasePrice > 0) item { OwnershipCard(d) }
        if (total <= 0) {
            item { Empty(Icons.Filled.BarChart, tr("Sem gastos neste período. Regista abastecimentos, intervenções e despesas para veres o resumo.")) }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(tr("Total"), eur(total), Modifier.weight(1f))
                Tile(tr("Custo por km"), if (span >= 50) fmt(total / span, 3) + " €" else "—", Modifier.weight(1f))
                Tile(tr("Percorridos"), if (span > 0) fint(span) + " km" else "—", Modifier.weight(1f))
            }
        }
        item { SectionTitle(tr("Por mês")) }
        item {
            Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendRow(colors.fuel, tr("Combustível"), fuel, total)
                    LegendRow(colors.mech, tr("Mecânica (peças e mão de obra)"), mech, total)
                    LegendRow(colors.fixed, tr("Fixas e outras (seguro, IUC, inspeção…)"), fixed, total)
                    Spacer(Modifier.height(4.dp))
                    CostBars(months, colors)
                }
            }
        }
        item { SectionTitle(tr("Despesas fixas")) }
        item {
            if (d.expenses.isEmpty()) Empty(Icons.Filled.Receipt, tr("Sem despesas registadas."))
            else Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column { d.expenses.take(8).forEach { e -> key(e.id) { SwipeDelete({ delExp(e) }) { Row3(Icons.Filled.Receipt, e.type, dateLabel(e.date), eur(e.amount)) { openExp(e) } } } } }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: Double, total: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(eur(value), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        Text("${fmt(value / total * 100, 0)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
    }
}

// ---------- Garagem ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageSheet(
    cars: List<Vehicle>, selected: Long?, onPick: (Vehicle) -> Unit, onAdd: () -> Unit, onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("Garagem"), style = MaterialTheme.typography.headlineSmall)
            cars.forEach { c ->
                val b = Brand.of(c.brand)
                val seed = b.seed
                ListItem(
                    modifier = Modifier.clickable { onPick(c) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        BrandMark(b, 44.dp, seed, onColor(seed))
                    },
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text("${c.fuelType} · ${fint(c.km)} km" + if (c.id == selected) t(" · selecionado", " · selected") else "") },
                )
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("Adicionar carro"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSheet(onFuel: () -> Unit, onServ: () -> Unit, onExp: () -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Novo registo"), style = MaterialTheme.typography.headlineSmall)
            @Composable fun opt(icon: ImageVector, t: String, s: String, f: () -> Unit) {
                Card(onClick = f, colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { IconBadge(icon) },
                        headlineContent = { Text(t) },
                        supportingContent = { Text(s) },
                    )
                }
            }
            opt(Icons.Filled.LocalGasStation, tr("Abastecimento"), tr("Litros, total pago e quilómetros"), onFuel)
            opt(Icons.Filled.Build, tr("Intervenção"), tr("Óleo, travões, pneus, reparações"), onServ)
            opt(Icons.Filled.Receipt, tr("Despesa"), tr("Seguro, IUC, inspeção, portagens"), onExp)
        }
    }
}

// ---------- Postos perto de mim ----------

private fun openMaps(ctx: Context, s: Station) {
    val label = Uri.encode((s.brand + " " + s.locality).trim())
    val uri = Uri.parse("geo:${s.lat},${s.lng}?q=${s.lat},${s.lng}($label)")
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { }
}

@Composable
private fun NearMsg(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, textAlign = TextAlign.Center)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbySheet(car: Vehicle, evMode: Boolean, st: NearbyState, nationalAvg: Double?, onSearch: () -> Unit, onMap: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var navTo by remember { mutableStateOf<Station?>(null) }
    var denied by remember { mutableStateOf(false) }
    var radius by rememberSaveable { mutableIntStateOf(10) }
    var byPrice by rememberSaveable { mutableStateOf(true) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) { denied = false; onSearch() } else denied = true
    }
    fun ask() {
        if (Loc.hasPermission(ctx)) { denied = false; onSearch() }
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(Unit) { ask() }

    navTo?.let { n -> NavDialog(n.lat, n.lng, "${n.name}, ${n.locality}") { navTo = null } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            val ev = evMode
            Text(if (ev) tr("Postos de carregamento") else tr("Postos perto de ti"), style = MaterialTheme.typography.headlineSmall)
            Text(if (ev) t("Elétrico · dados do OpenStreetMap", "Electric · OpenStreetMap data") else "${car.fuelType} · " + t("preços da DGEG", "DGEG prices"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            when {
                denied -> NearMsg(tr("Sem acesso à localização. Permite-o para ver os postos mais próximos."), tr("Permitir")) { ask() }
                st.unsupported -> NearMsg(tr("Não há preços por posto para este tipo de combustível."))
                st.locError -> NearMsg(tr("Não consegui obter a tua localização. Liga a localização do telemóvel e tenta outra vez."), tr("Tentar outra vez")) { ask() }
                st.error -> NearMsg(tr("Não foi possível obter os preços. Verifica a ligação e tenta outra vez."), tr("Tentar outra vez")) { ask() }
                !st.done -> ShimmerRows(5, Modifier.padding(16.dp))
                else -> {
                    val inRange = st.stations.filter { it.distKm <= radius }
                    if (ev) {
                        var fastOnly by rememberSaveable { mutableStateOf(false) }
                        val list = inRange.filter { !fastOnly || it.kw >= 50 }.take(30)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf(5, 10, 20).forEach { r -> FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text("$r km") }) }
                            Spacer(Modifier.width(4.dp))
                            FilterChip(selected = fastOnly, onClick = { fastOnly = !fastOnly }, label = { Text(t("Rápidos (≥ 50 kW)", "Fast (≥ 50 kW)")) })
                        }
                        FilledTonalButton(onClick = onMap, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Icon(Icons.Filled.Map, null); Spacer(Modifier.width(8.dp)); Text(tr("Ver no mapa"))
                        }
                        Spacer(Modifier.height(8.dp))
                        if (list.isEmpty()) NearMsg(t("Sem carregadores neste raio. Aumenta o raio.", "No chargers in this radius. Increase it."))
                        else Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                            list.forEach { s ->
                                ListItem(
                                    modifier = Modifier.clickable { navTo = s },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    leadingContent = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                                            Text(fmt(s.distKm), fontWeight = FontWeight.Bold); Text("km", style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(listOf(s.info, s.locality).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { t("sem detalhes", "no details") }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                        Text(tr("Toca num posto para abrir no mapa."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        return@Column
                    }
                    val shown = (if (byPrice) inRange.sortedWith(compareBy({ it.price }, { it.distKm })) else inRange.sortedBy { it.distKm }).take(15)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(5, 10, 20).forEach { r -> FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text("$r km") }) }
                        Spacer(Modifier.width(4.dp))
                        FilterChip(selected = byPrice, onClick = { byPrice = true }, label = { Text(tr("Mais barato")) })
                        FilterChip(selected = !byPrice, onClick = { byPrice = false }, label = { Text(tr("Mais perto")) })
                    }
                    Spacer(Modifier.height(8.dp))
                    if (shown.isEmpty()) {
                        NearMsg(t("Sem postos num raio de $radius km. Aumenta o raio.", "No stations within $radius km. Increase the radius."))
                    } else {
                        val cheapest = inRange.minBy { it.price }
                        val dearest = inRange.maxBy { it.price }
                        Text(
                            t("O mais barato está a ${fmt(cheapest.distKm)} km, a ${fmt(cheapest.price, 3)} €/L. ", "The cheapest is ${fmt(cheapest.distKm)} km away, at ${fmt(cheapest.price, 3)} €/L. ") +
                                t("Entre o mais barato e o mais caro num raio de $radius km, a diferença num depósito de ${car.tankL} L é de ${eur((dearest.price - cheapest.price) * car.tankL)}.", "Between the cheapest and the most expensive within $radius km, the difference on a ${car.tankL} L tank is ${eur((dearest.price - cheapest.price) * car.tankL)}."),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(onClick = onMap, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Icon(Icons.Filled.Map, null); Spacer(Modifier.width(8.dp)); Text(tr("Ver no mapa"))
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                            shown.forEach { s ->
                                val day = if (s.updated.length >= 10) s.updated.substring(8, 10) + "/" + s.updated.substring(5, 7) else ""
                                val vs = nationalAvg?.let { s.price - it }
                                ListItem(
                                    modifier = Modifier.clickable { navTo = s },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    leadingContent = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
                                            Text(fmt(s.distKm), fontWeight = FontWeight.Bold)
                                            Text("km", style = MaterialTheme.typography.labelSmall)
                                        }
                                    },
                                    headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        Text(
                                            listOf(s.brand, s.locality, if (day.isNotEmpty()) t("preço de $day", "price from $day") else "").filter { it.isNotBlank() }.joinToString(" · "),
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingContent = {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(fmt(s.price, 3) + " €/L", fontWeight = FontWeight.SemiBold)
                                            if (vs != null) Text(
                                                (if (vs <= 0) "−" else "+") + fmt(kotlin.math.abs(vs), 3) + tr(" vs média"),
                                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        Text(tr("Toca num posto para abrir no mapa."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}


/** Custo por km (últimos 3 meses) e previsão do gasto até ao fim do mês. */
@Composable
private fun InsightsCard(d: CarData, monthSpend: Double) {
    val total3 = monthly(d, 3).sumOf { it.total }
    val span = kmSpan(d, 3)
    val perKm = if (span >= 50 && total3 > 0) total3 / span else null
    val today = LocalDate.now()
    val forecast = if (today.dayOfMonth >= 5 && monthSpend > 0) monthSpend / today.dayOfMonth * today.lengthOfMonth() else null
    if (perKm == null && forecast == null) return
    Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t("Custo por km", "Cost per km"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(perKm?.let { fmt(it, 2) + " €" } ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text(t("últimos 3 meses", "last 3 months"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(t("Previsão do mês", "Month forecast"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(forecast?.let { eur(it) } ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text(
                    if (forecast != null && d.vehicle.monthlyBudget > 0) (if (forecast > d.vehicle.monthlyBudget) t("acima do orçamento", "over budget") else t("dentro do orçamento", "within budget"))
                    else t("ao ritmo atual", "at the current pace"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


/** Primeiros passos: lista curta que se vai riscando sozinha e que se pode fechar. */
@Composable
private fun StartCard(d: CarData, a: Actions) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("diario", 0) }
    var hidden by remember { mutableStateOf(sp.getBoolean("tips_off", false)) }
    val steps = listOf(
        Triple(t("Regista o primeiro abastecimento", "Log your first refuel"), d.fuelings.isNotEmpty()) { a.create("fuel") },
        Triple(t("Cria um aviso de manutenção (óleo, correia...)", "Create a maintenance reminder (oil, belt...)"), d.reminders.isNotEmpty()) { a.remNew() },
        Triple(t("Guarda o seguro ou a inspeção com a data", "Save insurance or inspection with its date"), d.docs.isNotEmpty()) { a.doc(null) },
        Triple(t("Define o IUC no separador Manutenção", "Set up your road tax in the Maintenance tab"), IucPrefs.month(ctx, d.vehicle.id) in 1..12) { a.goTab(Tab.MAINT) },
    )
    val done = steps.count { it.second }
    if (hidden || done == steps.size) return
    Card(colors = cardColors(), border = cardBorder(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Primeiros passos ($done/${steps.size})", "Getting started ($done/${steps.size})"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { hidden = true; sp.edit().putBoolean("tips_off", true).apply() }) { Text(t("Fechar", "Close")) }
            }
            LinearProgressIndicator(progress = { done / steps.size.toFloat() }, modifier = Modifier.fillMaxWidth())
            steps.forEach { (label, ok, go) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !ok, onClick = go).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null, tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (ok) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                }
            }
        }
    }
}
