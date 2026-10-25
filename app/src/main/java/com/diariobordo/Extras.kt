package com.diariobordo

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---------- Km estimados ----------

@Composable
fun KmCard(est: Int, current: Int, onYes: () -> Unit, onFix: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("QUILÓMETROS ESTIMADOS"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("~${fint(est)} km", fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
            Text(
                t("Pelo teu ritmo, o carro já deve ir nos ${fint(est)} km. O último valor que tenho é ${fint(current)} km. Os avisos já usam a estimativa. Está certo?", "At your pace the car should be around ${fint(est)} km by now. The last value I have is ${fint(current)} km. Alerts already use the estimate. Is that right?"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onYes, modifier = Modifier.weight(1f).height(48.dp)) { Text(tr("Está certo")) }
                OutlinedButton(onClick = onFix, modifier = Modifier.weight(1f).height(48.dp)) { Text(tr("Corrigir")) }
            }
        }
    }
}

@Composable
fun KmDialog(current: Int, onSave: (Int) -> Unit, onClose: () -> Unit) {
    var km by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Quilómetros atuais")) },
        text = {
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            var note by remember { mutableStateOf<String?>(null) }
            val shoot = rememberCameraLauncher { uri ->
                scope.launch {
                    busy = true
                    note = try {
                        val v = Ocr.parseOdometer(Ocr.read(ctx, uri), current)
                        if (v != null) { km = v.toString(); t("Li $v km. Confirma antes de guardar.", "Read $v km. Check before saving.") }
                        else t("Não consegui ler os números. Tenta com mais luz.", "Could not read the digits. Try with more light.")
                    } catch (e: Exception) { t("Não consegui ler a foto.", "Could not read the photo.") }
                    busy = false
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField(tr("Km no conta-quilómetros"), km, { km = it.filter(Char::isDigit) })
                OutlinedButton(onClick = shoot, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text(if (busy) t("A ler...", "Reading...") else t("Ler da foto do painel", "Read from dashboard photo"))
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { km.toIntOrNull()?.let { if (it > 0) onSave(it) } }) { Text(tr("Guardar")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancelar")) } },
    )
}

// ---------- Definições ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(hasCar: Boolean, onPdf: () -> Unit, onCsv: () -> Unit, onImport: () -> Unit, onSale: () -> Unit, onBackup: () -> Unit, onRestoreFile: () -> Unit, onCompare: () -> Unit, onShared: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var notif by remember { mutableStateOf(KmPrefs.notifOn(ctx)) }
    var est by remember { mutableStateOf(KmPrefs.on(ctx)) }
    var priceOn by remember { mutableStateOf(KmPrefs.priceOn(ctx)) }
    var lockOn by remember { mutableStateOf(AppLock.enabled(ctx)) }
    var restoreArmed by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr("Definições"), style = MaterialTheme.typography.headlineSmall)
            SettingSwitch(tr("Notificações de prazos e manutenção"), tr("Avisa mesmo com a app fechada."), notif) { notif = it; KmPrefs.set(ctx, "notif_on", it) }
            SettingSwitch(tr("Estimar os quilómetros"), tr("Usa o teu ritmo médio para os avisos por km ficarem certos."), est) { est = it; KmPrefs.set(ctx, "est_on", it) }
            HorizontalDivider()
            Text(tr("Aspeto"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(tr("Sistema") to 0, tr("Escuro") to 1, tr("Claro") to 2).forEach { (l, m) ->
                    FilterChip(selected = UiPrefs.mode == m, onClick = { UiPrefs.setMode(ctx, m) }, label = { Text(l) })
                }
            }
            Text(tr("Língua"), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(tr("Sistema") to 0, "Português" to 1, "English" to 2).forEach { (l, m) ->
                    FilterChip(selected = Lang.mode == m, onClick = { Lang.set(ctx, m) }, label = { Text(l) })
                }
            }
            Text(tr("Cor dos dígitos do painel de preços"), fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    0xFFFF2A2A.toInt() to tr("Vermelho"), 0xFFFFA000.toInt() to tr("Âmbar"), 0xFF39FF14.toInt() to tr("Verde"),
                    0xFF00E5FF.toInt() to tr("Azul gelo"), 0xFFFFFFFF.toInt() to tr("Branco"), 0xFFFF4FD8.toInt() to tr("Rosa"), 0 to tr("Marca"),
                ).forEach { (c, name) ->
                    val sel = UiPrefs.led == c
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { UiPrefs.setLed(ctx, c) }) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (c == 0) MaterialTheme.colorScheme.primary else Color(c))
                                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                        Text(name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (hasCar) {
                HorizontalDivider()
                Text(tr("Histórico do carro"), style = MaterialTheme.typography.titleSmall)
                Button(onClick = onPdf, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text(tr("Exportar PDF"))
                }
                OutlinedButton(onClick = onCsv, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.TableChart, null); Spacer(Modifier.width(8.dp)); Text(tr("Exportar para Excel (CSV)"))
                }
                OutlinedButton(onClick = onSale, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Sell, null); Spacer(Modifier.width(8.dp)); Text(t("Relatório para venda (PDF)", "Sale report (PDF)"))
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text(t("Importar histórico (CSV)", "Import history (CSV)"))
                }
                Text(
                    t("Usa o mesmo formato do «Exportar para Excel»: Data;Tipo;Km;Descrição;Litros;Custo;Notas. Tipo: Abastecimento, Intervenção ou Despesa. Exporta primeiro para teres o modelo.", "Uses the same format as the Excel export: Date;Type;Km;Description;Litres;Cost;Notes. Type: Refuel, Service or Expense. Export first to get the template."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onCompare, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Balance, null); Spacer(Modifier.width(8.dp)); Text(tr("Comparar carros"))
                }
                OutlinedButton(
                    onClick = {
                        try {
                            if (androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
                                val info = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, "pin_fuel")
                                    .setShortLabel(t("Abastecer", "Refuel"))
                                    .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
                                    .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("go", "fuel"))
                                    .build()
                                androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
                            }
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Filled.LocalGasStation, null); Spacer(Modifier.width(8.dp)); Text(t("Atalho no ecrã: registar abastecimento", "Home-screen shortcut: log a refuel"))
                }
                OutlinedButton(onClick = onShared, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Group, null); Spacer(Modifier.width(8.dp)); Text(t("Carro partilhado", "Shared car"))
                }
            }
            SettingSwitch(
                t("Avisar quando o combustível baixar", "Alert me when fuel gets cheaper"),
                t("Compara o preço médio nacional do teu combustível algumas vezes por dia e avisa se descer 1 cêntimo ou mais.", "Checks the national average price of your fuel a few times a day and alerts you if it drops by 1 cent or more."),
                priceOn,
            ) { priceOn = it; KmPrefs.set(ctx, "price_on", it) }
            SettingSwitch(
                t("Bloquear a app", "Lock the app"),
                t("Pede o PIN, o padrão ou a impressão digital do telemóvel ao abrir, e depois de 30 segundos fora da app. Precisa de um bloqueio de ecrã definido no telemóvel.", "Asks for your phone's PIN, pattern or fingerprint when opening, and after 30 seconds away. Needs a screen lock set on your phone."),
                lockOn,
            ) {
                if (it && !AppLock.secure(ctx)) android.widget.Toast.makeText(ctx, t("Define primeiro um bloqueio de ecrã nas definições do telemóvel.", "Set a screen lock in your phone settings first."), android.widget.Toast.LENGTH_LONG).show()
                else { lockOn = it; AppLock.setEnabled(ctx, it) }
            }
            HorizontalDivider()
            Text(t("Cópia de segurança no telemóvel", "Backup on this phone"), style = MaterialTheme.typography.titleSmall)
            Text(
                t("Guarda tudo (carros, registos, fotos e cartões) num ficheiro que podes pôr no Drive ou enviar para ti. Não precisa de conta.", "Saves everything (cars, records, photos and cards) in one file you can put on Drive or send to yourself. No account needed."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text(t("Guardar cópia em ficheiro", "Save backup to file"))
            }
            OutlinedButton(
                onClick = { if (restoreArmed) { restoreArmed = false; onRestoreFile() } else restoreArmed = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Filled.Restore, null); Spacer(Modifier.width(8.dp))
                Text(if (restoreArmed) t("Confirmar: substitui tudo o que está aqui", "Confirm: replaces everything here") else t("Restaurar de um ficheiro", "Restore from a file"))
            }
            HorizontalDivider()
            UpdateSection()
            HorizontalDivider()
            Text(tr("Créditos"), style = MaterialTheme.typography.titleSmall)
            Text(t("Versão ", "Version ") + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                tr("Mapas © OpenStreetMap contributors. Preços dos combustíveis: DGEG. Símbolos das marcas: Simple Icons e Logo.dev. ") +
                    tr("Os teus dados ficam neste telemóvel e, se entrares com Google, na tua cópia privada na nuvem."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingSwitch(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

// ---------- Comparar carros ----------

class CarSummary(val v: Vehicle, val avg: Double?, val costPerKm: Double?, val monthly: Double, val year: Double, val km12: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareSheet(list: List<CarSummary>, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tr("Comparar carros"), style = MaterialTheme.typography.headlineSmall)
            if (list.isEmpty()) {
                ShimmerRows(3)
            } else {
                if (list.size < 2) {
                    Text(tr("Só tens um carro. Quando adicionares outro, a comparação fica completa."), style = MaterialTheme.typography.bodySmall)
                }
                CompareBars(tr("Consumo médio"), if (list.any { isEv(it.v.fuelType) }) " L|kWh/100" else " L/100", list.map { it.v.name to it.avg }, 1)
                CompareBars(tr("Custo por km"), " €", list.map { it.v.name to it.costPerKm }, 3)
                CompareBars(tr("Gasto médio por mês"), " €", list.map { it.v.name to it.monthly.takeIf { m -> m > 0 } }, 0)
                CompareBars(tr("Gasto nos últimos 12 meses"), " €", list.map { it.v.name to it.year.takeIf { m -> m > 0 } }, 0)
                CompareBars(tr("Km percorridos (12 meses)"), " km", list.map { it.v.name to it.km12.toDouble().takeIf { m -> m > 0 } }, 0)
            }
        }
    }
}

@Composable
private fun CompareBars(title: String, unit: String, items: List<Pair<String, Double?>>, digits: Int) {
    val mx = items.mapNotNull { it.second }.maxOrNull() ?: 0.0
    val best = items.filter { it.second != null && it.second!! > 0 }.minByOrNull { it.second!! }?.first
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { (name, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, modifier = Modifier.width(92.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                Box(Modifier.weight(1f).height(14.dp)) {
                    if (v != null && mx > 0) {
                        Box(
                            Modifier.fillMaxWidth((v / mx).toFloat().coerceIn(0.04f, 1f)).fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Text(
                    if (v == null) "—" else fmt(v, digits) + unit, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    textAlign = TextAlign.End, modifier = Modifier.width(92.dp),
                )
            }
        }
    }
}
