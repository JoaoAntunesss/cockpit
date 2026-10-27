package com.diariobordo

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.PhotoLibrary
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

// ---------- Marcas de gasolineiras e lojas ----------

class StationBrand(val key: String, val label: String, val color: Color, val domain: String?, val mono: String)

val STATION_BRANDS = listOf(
    StationBrand("galp", "Galp", Color(0xFFF26B00), "galp.com", "G"),
    StationBrand("bp", "BP", Color(0xFF009B3A), "bp.com", "BP"),
    StationBrand("repsol", "Repsol", Color(0xFFFF8200), "repsol.com", "R"),
    StationBrand("moeve", "Moeve (Cepsa)", Color(0xFF00A0DF), "moeve.com", "M"),
    StationBrand("prio", "Prio", Color(0xFF00A859), "prio.pt", "P"),
    StationBrand("petroprix", "Petroprix", Color(0xFF2B3A3F), "petroprix.pt", "P"),
    StationBrand("shell", "Shell", Color(0xFFFBCE07), null, "S"),
    StationBrand("esso", "Esso", Color(0xFF1E4EA0), "esso.pt", "E"),
    StationBrand("continente", "Continente", Color(0xFFE2001A), null, "C"),
    StationBrand("auchan", "Auchan", Color(0xFFE30613), null, "A"),
    StationBrand("intermarche", "Intermarché", Color(0xFFE2001A), null, "I"),
    StationBrand("outra", "Outro", Color(0xFF3B4252), null, ""),
)

fun stationBrand(key: String): StationBrand = STATION_BRANDS.firstOrNull { it.key == key } ?: STATION_BRANDS.last()

/** Logo local (shell, continente, auchan, intermarche), senão Logo.dev, senão a inicial. */
@Composable
fun StationMark(sb: StationBrand, size: Dp) {
    val ctx = LocalContext.current
    val res = remember(sb) { ctx.resources.getIdentifier("station_${sb.key}", "drawable", ctx.packageName) }
    val key = BuildConfig.LOGO_DEV_KEY
    val url = if (res == 0 && sb.domain != null && key.isNotBlank()) "https://img.logo.dev/${sb.domain}?size=128&format=png&token=$key" else null

    @Composable
    fun Mono() {
        Box(Modifier.fillMaxSize().background(sb.color), contentAlignment = Alignment.Center) {
            if (sb.mono.isBlank()) Icon(Icons.Filled.CreditCard, null, Modifier.size(size * 0.5f), tint = onColor(sb.color))
            else Text(sb.mono, color = onColor(sb.color), fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
        }
    }
    Box(Modifier.size(size).clip(CircleShape)) {
        when {
            res != 0 -> Box(Modifier.fillMaxSize().background(Color.White).padding(size * 0.16f)) {
                Image(painterResource(res), sb.label, Modifier.fillMaxSize())
            }
            url != null -> SubcomposeAsyncImage(
                model = url, contentDescription = sb.label, modifier = Modifier.fillMaxSize().background(Color.White).padding(size * 0.14f),
                loading = { Mono() }, error = { Mono() },
            )
            else -> Mono()
        }
    }
}

// ---------- Código de barras ----------

private val TWO_D = listOf(BarcodeFormat.QR_CODE, BarcodeFormat.AZTEC, BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417)

fun zxingName(mlFormat: Int): String = when (mlFormat) {
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    Barcode.FORMAT_CODE_39 -> "CODE_39"
    Barcode.FORMAT_CODE_93 -> "CODE_93"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    Barcode.FORMAT_PDF417 -> "PDF_417"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
    else -> "CODE_128"
}

fun is2D(fmt: String): Boolean = try { BarcodeFormat.valueOf(fmt) in TWO_D } catch (e: Exception) { false }

fun barcodeBitmap(text: String, fmt: String): Bitmap? {
    fun make(f: BarcodeFormat): Bitmap {
        val two = f in TWO_D
        val m = MultiFormatWriter().encode(text, f, 900, if (two) 900 else 300)
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }
    return try { make(BarcodeFormat.valueOf(fmt)) } catch (e: Exception) {
        try { make(BarcodeFormat.QR_CODE) } catch (e2: Exception) { null }
    }
}

/** Abre o leitor de códigos da Google (não precisa de permissão de câmara). */
fun scanCode(ctx: Context, onResult: (String, String) -> Unit) {
    GmsBarcodeScanning.getClient(ctx).startScan()
        .addOnSuccessListener { b ->
            val raw = b.rawValue
            if (raw != null) onResult(raw, zxingName(b.format))
        }
        .addOnFailureListener {
            Toast.makeText(ctx, t("Não foi possível abrir a câmara: ", "Could not open the camera: ") + (it.message ?: ""), Toast.LENGTH_LONG).show()
        }
}

/** Lê o código a partir de uma imagem (foto ou captura de ecrã na galeria). Funciona sem rede. */
fun scanCodeFromImage(ctx: Context, uri: Uri, onResult: (String, String) -> Unit) {
    try {
        BarcodeScanning.getClient().process(InputImage.fromFilePath(ctx, uri))
            .addOnSuccessListener { list ->
                val b = list.firstOrNull { !it.rawValue.isNullOrBlank() }
                if (b?.rawValue != null) onResult(b.rawValue!!, zxingName(b.format))
                else Toast.makeText(ctx, t("Não encontrei nenhum código nesta imagem.", "No code found in this image."), Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(ctx, t("Não consegui ler a imagem.", "Could not read the image."), Toast.LENGTH_LONG).show()
            }
    } catch (e: Exception) {
        Toast.makeText(ctx, t("Não consegui abrir a imagem.", "Could not open the image."), Toast.LENGTH_LONG).show()
    }
}

// ---------- Ecrã ----------

@Composable
fun CardsScreen(cards: List<FuelCard>, pad: PaddingValues, onOpen: (FuelCard) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 4.dp, 16.dp, pad.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(tr("Cartões"), style = MaterialTheme.typography.headlineSmall) }
        if (cards.isEmpty()) item {
            Text(
                tr("Ainda não tens cartões. Toca em «Novo cartão», aponta a câmara ao código de barras do cartão da bomba e fica guardado. No posto, abres aqui e mostras ao leitor."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(cards, key = { it.id }) { c ->
            val sb = stationBrand(c.brand)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(lerp(Color(0xFF0B0C0F), sb.color, 0.55f), Color(0xFF0B0C0F))))
                    .clickable { onOpen(c) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StationMark(sb, 48.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(c.name.ifBlank { sb.label }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sb.label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    Text("•••• " + c.number.takeLast(4), color = Color.White.copy(alpha = 0.85f), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                }
                Icon(Icons.Filled.QrCodeScanner, null, tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

/** Mostra o código grande, com o brilho no máximo, para o leitor do posto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardView(c: FuelCard, onEdit: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val w = (ctx as? Activity)?.window
        val old = w?.attributes?.screenBrightness
        w?.attributes = w?.attributes?.also { it.screenBrightness = 1f }
        onDispose { w?.attributes = w?.attributes?.also { it.screenBrightness = old ?: -1f } }
    }
    val sb = stationBrand(c.brand)
    val bmp = remember(c.number, c.format) { barcodeBitmap(c.number, c.format) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StationMark(sb, 44.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(c.name.ifBlank { sb.label }, style = MaterialTheme.typography.titleLarge)
                    Text(sb.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).padding(16.dp), contentAlignment = Alignment.Center) {
                if (bmp != null) Image(
                    bmp.asImageBitmap(), tr("Código do cartão"), contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().height(if (is2D(c.format)) 260.dp else 130.dp),
                ) else Text(tr("Não consegui desenhar o código. Usa o número abaixo."), color = Color.Black)
            }
            Text(c.number, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            if (c.notes.isNotBlank()) Text(c.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).height(48.dp)) { Text(tr("Editar")) }
                Button(onClick = onClose, modifier = Modifier.weight(1f).height(48.dp)) { Text(tr("Fechar")) }
            }
        }
    }
}

@Composable
fun CardForm(item: FuelCard?, onSave: (FuelCard) -> Unit, onDelete: (FuelCard) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var brand by remember { mutableStateOf(stationBrand(item?.brand ?: "galp").label) }
    var name by remember { mutableStateOf(item?.name ?: "") }
    var number by remember { mutableStateOf(item?.number ?: "") }
    var format by remember { mutableStateOf(item?.format ?: "CODE_128") }
    var notes by remember { mutableStateOf(item?.notes ?: "") }
    var err by remember { mutableStateOf(false) }
    val fromImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scanCodeFromImage(ctx, uri) { raw, fmt -> number = raw; format = fmt; err = false }
    }
    // Ao criar um cartão novo, abre logo o leitor: aponta ao código e já está.
    LaunchedEffect(Unit) {
        if (item == null) scanCode(ctx) { raw, fmt -> number = raw; format = fmt; err = false }
    }
    FormSheet(
        title = if (item == null) tr("Novo cartão") else tr("Editar cartão"),
        onClose = onClose,
        onSave = {
            if (number.isBlank()) err = true
            else onSave(FuelCard(item?.id ?: 0, STATION_BRANDS.first { it.label == brand }.key, name.trim(), number.trim(), format, notes.trim()))
        },
        onDelete = item?.let { { onDelete(it) } },
    ) {
        FormSection(t("Cartão", "Card")) {
            PickField(tr("Marca"), brand, STATION_BRANDS.map { it.label }) { brand = it }
            TxtField(tr("Nome (ex.: Cartão Galp da família)"), name, { name = it })
        }
        FormSection(t("Código", "Code")) {
            Button(
                onClick = { scanCode(ctx) { raw, fmt -> number = raw; format = fmt; err = false } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text(tr("Ler código com a câmara")) }
            OutlinedButton(
                onClick = { fromImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text(t("Ler de uma imagem da galeria", "Read from a gallery image")) }
            Text(
                t("Lê QR, códigos de barras e outros formatos de cartões.", "Reads QR codes, barcodes and other card formats."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TxtField(tr("Número do cartão"), number, { number = it; err = false }, isError = err)
            if (number.isNotBlank()) Text(
                t("Formato: ", "Format: ") + format.replace('_', ' '),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
        }
        FormSection {
            TxtField(tr("Notas (ex.: −6 cêntimos/L)"), notes, { notes = it })
        }
    }
}
