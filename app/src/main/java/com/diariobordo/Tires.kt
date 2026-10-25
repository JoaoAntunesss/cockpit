package com.diariobordo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

val TIRE_KINDS = listOf("Verão", "Inverno", "Todas as estações")
private fun kindLabel(k: String) = when (k) {
    "Verão" -> t("Verão", "Summer"); "Inverno" -> t("Inverno", "Winter"); else -> t("Todas as estações", "All-season")
}

fun tireKm(car: Vehicle, x: Tire): Int = ((if (x.active) car.km else x.removedKm) - x.mountKm).coerceAtLeast(0)

/** Avisos simples sobre um pneu montado: desgaste por km, idade e rotação. */
fun tireWarnings(car: Vehicle, x: Tire): List<String> {
    if (!x.active) return emptyList()
    val out = ArrayList<String>()
    val km = tireKm(car, x)
    if (km >= 40000) out += t("Já fizeram ${fint(km)} km: verifica o desgaste (mínimo legal 1,6 mm).", "Already ${fint(km)} km: check the wear (legal minimum 1.6 mm).")
    val years = ChronoUnit.YEARS.between(LocalDate.ofEpochDay(x.mountDate), LocalDate.now())
    if (years >= 6) out += t("Montados há $years anos: com mais de 6 anos convém verificar a borracha.", "Fitted $years years ago: rubber older than 6 years should be checked.")
    if (car.km - maxOf(x.rotationKm, x.mountKm) >= 10000) out += t("Rotação recomendada (cada cerca de 10 000 km).", "Rotation recommended (about every 10,000 km).")
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiresSheet(car: Vehicle, tires: List<Tire>, onNew: () -> Unit, onEdit: (Tire) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Pneus", "Tyres"), style = MaterialTheme.typography.headlineSmall)
            Text(
                t("Regista os pneus montados e os de inverno guardados. A app conta os km de cada jogo e avisa de desgaste, idade e rotação.", "Log the fitted tyres and any stored winter set. The app counts each set's km and warns about wear, age and rotation."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tires.sortedWith(compareByDescending<Tire> { it.active }.thenByDescending { it.mountDate }).forEach { x ->
                    val warns = tireWarnings(car, x)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { onEdit(x) },
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${x.brand} ${x.size}".trim(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(if (x.active) t("Montados", "Fitted") else t("Guardados", "Stored"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "${kindLabel(x.kind)} · ${t("desde", "since")} ${docDate(x.mountDate)} · ${fint(tireKm(car, x))} km",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            warns.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF5B73B)) }
                        }
                    }
                }
            }
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(t("Novo jogo de pneus", "New set of tyres")) }
        }
    }
}

@Composable
fun TireForm(car: Vehicle, item: Tire?, onSave: (Tire) -> Unit, onDelete: (Tire) -> Unit, onClose: () -> Unit) {
    var brand by remember { mutableStateOf(item?.brand ?: "") }
    var size by remember { mutableStateOf(item?.size ?: "") }
    var kind by remember { mutableStateOf(item?.kind ?: TIRE_KINDS[0]) }
    var date by remember { mutableStateOf(item?.let { LocalDate.ofEpochDay(it.mountDate) } ?: LocalDate.now()) }
    var mountKm by remember { mutableStateOf((item?.mountKm ?: car.km).toString()) }
    var rot by remember { mutableStateOf(item?.rotationKm?.takeIf { it > 0 }?.toString() ?: "") }
    var active by remember { mutableStateOf(item?.active ?: true) }
    var removed by remember { mutableStateOf(item?.removedKm?.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember { mutableStateOf(item?.notes ?: "") }
    var tried by remember { mutableStateOf(false) }
    val mk = mountKm.toIntOrNull()

    FormSheet(
        title = if (item == null) t("Novo jogo de pneus", "New set of tyres") else t("Editar pneus", "Edit tyres"),
        onClose = onClose,
        onDelete = item?.let { { onDelete(it) } },
        onSave = {
            tried = true
            if (brand.isNotBlank() && mk != null && mk >= 0) {
                onSave(
                    Tire(
                        item?.id ?: 0, car.id, brand.trim(), size.trim(), kind, date.toEpochDay(), mk,
                        rot.toIntOrNull() ?: 0, active, if (active) 0 else (removed.toIntOrNull() ?: car.km), notes.trim(),
                    )
                )
            }
        },
    ) {
        FormSection(t("Pneus", "Tyres")) {
            TxtField(t("Marca e modelo", "Brand and model"), brand, { brand = it }, isError = tried && brand.isBlank())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TxtField(t("Medida (205/55 R16)", "Size (205/55 R16)"), size, { size = it }, Modifier.weight(1f))
                PickField(t("Tipo", "Type"), kind, TIRE_KINDS, Modifier.weight(1f)) { kind = it }
            }
        }
        FormSection(t("Montagem", "Fitting")) {
            DateField(t("Montados em", "Fitted on"), date) { date = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumField(t("Km na montagem", "Km when fitted"), mountKm, { mountKm = it.filter(Char::isDigit) }, Modifier.weight(1f), isError = tried && mk == null)
                NumField(t("Km da rotação", "Km at rotation"), rot, { rot = it.filter(Char::isDigit) }, Modifier.weight(1f))
            }
            FormSwitch(t("Montados agora no carro", "Fitted on the car now"), null, active) { active = it }
            if (!active) NumField(t("Km do carro quando saíram", "Car km when removed"), removed, { removed = it.filter(Char::isDigit) }, hint = t("Atuais: ${fint(car.km)} km", "Current: ${fint(car.km)} km"))
            TxtField(t("Notas (opcional)", "Notes (optional)"), notes, { notes = it })
        }
    }
}
