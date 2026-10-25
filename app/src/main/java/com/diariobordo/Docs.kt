package com.diariobordo

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

val DOC_TYPES = listOf("Inspeção", "Seguro", "IUC", "Livrete e documentos", "Outro")

fun docIcon(type: String): ImageVector = when (type) {
    "Inspeção" -> Icons.Filled.FactCheck
    "Seguro" -> Icons.Filled.Shield
    "IUC" -> Icons.Filled.Receipt
    "Livrete e documentos" -> Icons.Filled.Description
    else -> Icons.Filled.Event
}

fun docDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

data class DocInfo(val days: Int, val level: Level, val text: String)

fun docInfo(d: Doc, today: LocalDate = LocalDate.now()): DocInfo {
    val days = ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(d.expiry)).toInt()
    return when {
        days < 0 -> DocInfo(days, Level.LATE, t("Expirou há ${-days} ${if (-days == 1) "dia" else "dias"}", "Expired ${-days} ${if (-days == 1) "day" else "days"} ago"))
        days == 0 -> DocInfo(days, Level.LATE, tr("Expira hoje"))
        days <= d.warnDays -> DocInfo(days, Level.SOON, t("Faltam $days ${if (days == 1) "dia" else "dias"}", "$days ${if (days == 1) "day" else "days"} left"))
        else -> DocInfo(days, Level.OK, t("Faltam $days dias", "$days days left"))
    }
}

/** Copia a imagem escolhida para a memória da app, para continuar a existir mesmo que a apagues da galeria. */
private suspend fun copyIntoApp(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val dir = File(ctx.filesDir, "docs").apply { mkdirs() }
        val out = File(dir, "doc_${System.currentTimeMillis()}.jpg")
        ctx.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return@withContext null
        out.absolutePath
    } catch (e: Exception) {
        null
    }
}

fun deleteFile(path: String) {
    if (path.isNotBlank()) try { File(path).delete() } catch (_: Exception) {}
}

@Composable
fun DocForm(car: Vehicle, item: Doc?, onSave: (Doc) -> Unit, onDelete: (Doc) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(item?.type ?: "Seguro") }
    var title by remember { mutableStateOf(item?.title ?: "") }
    var number by remember { mutableStateOf(item?.number ?: "") }
    var expiry by remember { mutableStateOf(item?.let { LocalDate.ofEpochDay(it.expiry) } ?: LocalDate.now().plusYears(1)) }
    var cost by remember { mutableStateOf(item?.cost?.takeIf { it > 0 }?.let { dec(it) } ?: "") }
    var warn by remember { mutableStateOf((item?.warnDays ?: 30).toString()) }
    var notes by remember { mutableStateOf(item?.notes ?: "") }
    var image by remember { mutableStateOf(item?.image ?: "") }
    var big by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            copyIntoApp(ctx, uri)?.let { p ->
                if (image != (item?.image ?: "")) deleteFile(image)   // descarta uma escolha anterior ainda não guardada
                image = p
            }
        }
    }

    // Sair sem guardar apaga a imagem nova que ainda não foi guardada
    val leave = { if (image != (item?.image ?: "")) deleteFile(image); onClose() }

    FormSheet(
        title = if (item == null) tr("Novo documento ou prazo") else tr("Editar documento"),
        onClose = leave,
        onDelete = item?.let { { onDelete(it) } },
        onSave = {
            if (item != null && item.image != image) deleteFile(item.image)
            onSave(
                Doc(
                    item?.id ?: 0, car.id, type, title.trim(), number.trim(), expiry.toEpochDay(),
                    num(cost) ?: 0.0, notes.trim(), image, (warn.toIntOrNull() ?: 30).coerceIn(1, 365),
                )
            )
        },
    ) {
        PickField(tr("Tipo"), type, DOC_TYPES) { type = it }
        TxtField(if (type == "Seguro") tr("Seguradora") else tr("Entidade ou descrição"), title, { title = it })
        TxtField(if (type == "Seguro") tr("Nº da apólice") else tr("Nº ou referência"), number, { number = it })
        DateField(tr("Válido até"), expiry) { expiry = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumField(tr("Custo (€)"), cost, { cost = it }, Modifier.weight(1f), decimal = true)
            NumField(tr("Avisar com (dias)"), warn, { warn = it.filter(Char::isDigit) }, Modifier.weight(1f))
        }
        TxtField(tr("Notas (contactos, condições…)"), notes, { notes = it })

        Text(tr("Print ou foto"), style = MaterialTheme.typography.titleSmall)
        if (image.isNotBlank()) {
            AsyncImage(
                model = File(image), contentDescription = tr("Documento"),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)).clickable { big = true },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text(tr("Trocar")) }
                OutlinedButton(onClick = { if (image != (item?.image ?: "")) deleteFile(image); image = "" }, modifier = Modifier.weight(1f)) { Text(tr("Remover")) }
            }
        } else {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(tr("Anexar print ou foto"))
            }
        }
        Text(
            tr("A imagem fica guardada só no teu telemóvel."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (big && image.isNotBlank()) {
        Dialog(onDismissRequest = { big = false }) {
            AsyncImage(
                model = File(image), contentDescription = tr("Documento"), contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { big = false },
            )
        }
    }
}

/** Atalhos para criar avisos de manutenção sem escrever tudo. */
data class ReminderPreset(val name: String, val category: String, val km: Int, val months: Int)

val REMINDER_PRESETS = listOf(
    ReminderPreset("Óleo e filtro", "Óleo e filtros", 15000, 12),
    ReminderPreset("Filtros (ar, habitáculo)", "Óleo e filtros", 20000, 12),
    ReminderPreset("Correia de distribuição", "Motor", 120000, 60),
    ReminderPreset("Travões (pastilhas)", "Travões", 40000, 24),
    ReminderPreset("Líquido dos travões", "Travões", 0, 24),
    ReminderPreset("Pneus", "Pneus", 40000, 48),
    ReminderPreset("Líquido de arrefecimento", "Motor", 60000, 48),
    ReminderPreset("Bateria", "Elétrico", 0, 60),
)


/** Foto ou fatura: escolhe da galeria, fica guardada no telemóvel. */
@Composable
fun PhotoField(label: String, image: String, original: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var big by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            copyIntoApp(ctx, uri)?.let { p ->
                if (image != original) deleteFile(image)
                onChange(p)
            }
        }
    }
    Text(label, style = MaterialTheme.typography.titleSmall)
    if (image.isNotBlank()) {
        AsyncImage(
            model = File(image), contentDescription = label, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).clickable { big = true },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text(tr("Trocar")) }
            OutlinedButton(onClick = { if (image != original) deleteFile(image); onChange("") }, modifier = Modifier.weight(1f)) { Text(tr("Remover")) }
        }
    } else {
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text(tr("Anexar foto ou fatura")) }
    }
    if (big && image.isNotBlank()) {
        Dialog(onDismissRequest = { big = false }) {
            AsyncImage(
                model = File(image), contentDescription = label, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { big = false },
            )
        }
    }
}
