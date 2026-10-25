package com.diariobordo

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Leitura de texto em fotos (ML Kit, no próprio telemóvel: a foto não sai daqui). */
object Ocr {
    class Receipt(val total: Double?, val liters: Double?, val date: LocalDate?)

    suspend fun read(ctx: Context, uri: Uri): String {
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try { rec.process(InputImage.fromFilePath(ctx, uri)).await().text } finally { rec.close() }
    }

    private fun n(s: String) = s.replace(',', '.').toDoubleOrNull()

    fun parseReceipt(text: String): Receipt {
        val flat = text.replace('\n', ' ')
        val liters = Regex("(\\d{1,3}[.,]\\d{2,3})\\s*(?:l|lt|ltr|litros?)\\b", RegexOption.IGNORE_CASE).find(flat)?.groupValues?.get(1)?.let { n(it) }
            ?: Regex("(?:quantidade|qtd|litros|volume|qty)\\D{0,12}(\\d{1,3}[.,]\\d{2,3})", RegexOption.IGNORE_CASE).find(flat)?.groupValues?.get(1)?.let { n(it) }
        val labelled = Regex("(?:total|a pagar|valor|montante|amount)\\D{0,14}(\\d{1,4}[.,]\\d{2})(?!\\d)", RegexOption.IGNORE_CASE)
            .findAll(flat).mapNotNull { n(it.groupValues[1]) }.maxOrNull()
        val total = labelled ?: Regex("(\\d{1,4}[.,]\\d{2})(?!\\d)").findAll(flat).mapNotNull { n(it.groupValues[1]) }.maxOrNull()
        val d = Regex("(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})").find(flat)?.let {
            try { LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt()) } catch (e: Exception) { null }
        } ?: Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(flat)?.let {
            try { LocalDate.parse(it.value, DateTimeFormatter.ISO_LOCAL_DATE) } catch (e: Exception) { null }
        }
        return Receipt(total, liters, d?.takeIf { !it.isAfter(LocalDate.now()) })
    }

    /** Escolhe o número mais provável de ser o conta-quilómetros (4 a 7 dígitos, o mais perto de [current] sem ser muito menor). */
    fun parseOdometer(text: String, current: Int): Int? {
        val nums = Regex("\\d[\\d .]{2,10}\\d").findAll(text).mapNotNull { m ->
            m.value.replace(Regex("[ .]"), "").takeIf { it.length in 4..7 }?.toIntOrNull()
        }.toList()
        if (nums.isEmpty()) return null
        return nums.filter { it >= current * 0.9 }.minByOrNull { kotlin.math.abs(it - current) } ?: nums.maxOrNull()
    }
}

/** Abre a câmara e devolve o URI da foto tirada. */
@Composable
fun rememberCameraLauncher(onPhoto: (Uri) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pending
        if (ok && u != null) onPhoto(u)
    }
    return {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val f = File.createTempFile("ocr_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        pending = uri
        launcher.launch(uri)
    }
}

/** Botão «Ler fatura»: tira a foto, lê o texto e devolve total, litros e data para preencher o formulário. */
@Composable
fun ReceiptButton(onResult: (Ocr.Receipt) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val shoot = rememberCameraLauncher { uri ->
        scope.launch {
            busy = true
            msg = try {
                val r = Ocr.parseReceipt(Ocr.read(ctx, uri))
                if (r.total == null && r.liters == null && r.date == null) t("Não encontrei valores. Tenta com mais luz e a fatura direita.", "Found nothing. Try again with more light and the receipt flat.")
                else { onResult(r); t("Preenchido. Confirma os valores antes de guardar.", "Filled in. Check the values before saving.") }
            } catch (e: Exception) { t("Não consegui ler a foto.", "Could not read the photo.") }
            busy = false
        }
    }
    OutlinedButton(onClick = shoot, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(8.dp))
        Text(if (busy) t("A ler...", "Reading...") else t("Ler fatura com a câmara", "Scan receipt with camera"))
    }
    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
