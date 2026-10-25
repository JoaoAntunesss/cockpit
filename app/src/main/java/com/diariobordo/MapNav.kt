package com.diariobordo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

// ---------- Abrir navegação noutra app (Waze, Google Maps...) ----------

object Nav {
    private fun tryStart(ctx: Context, i: Intent): Boolean =
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); true } catch (e: Exception) { false }

    fun waze(ctx: Context, lat: Double, lng: Double) {
        val uri = Uri.parse("https://waze.com/ul?ll=$lat,$lng&navigate=yes")
        if (!tryStart(ctx, Intent(Intent.ACTION_VIEW, uri).setPackage("com.waze"))) tryStart(ctx, Intent(Intent.ACTION_VIEW, uri))
    }

    fun googleMaps(ctx: Context, lat: Double, lng: Double) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng")).setPackage("com.google.android.apps.maps")
        if (!tryStart(ctx, i)) other(ctx, lat, lng, "")
    }

    fun other(ctx: Context, lat: Double, lng: Double, label: String) {
        tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")))
    }
}

@Composable
fun NavDialog(lat: Double, lng: Double, label: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Navegar até aqui")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { Nav.waze(ctx, lat, lng); onClose() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Waze") }
                OutlinedButton(onClick = { Nav.googleMaps(ctx, lat, lng); onClose() }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Google Maps") }
                TextButton(onClick = { Nav.other(ctx, lat, lng, label); onClose() }, modifier = Modifier.fillMaxWidth()) { Text(tr("Outra app")) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancelar")) } },
    )
}

// ---------- Mapa dentro da app (OpenStreetMap, sem chave) ----------

fun dot(color: Int, sizePx: Int = 42): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
    setStroke(5, android.graphics.Color.WHITE)
    setSize(sizePx, sizePx)
}

private class Parking(val lat: Double, val lng: Double, val at: Long)

private fun readParking(ctx: Context, carId: Long): Parking? {
    val s = ctx.getSharedPreferences("parking", 0).getString("car_$carId", null) ?: return null
    val p = s.split(";")
    return if (p.size == 3) Parking(p[0].toDouble(), p[1].toDouble(), p[2].toLong()) else null
}

@Composable
fun MapDialog(car: Vehicle, evMode: Boolean, st: NearbyState, onSearch: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    var here by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selected by remember { mutableStateOf<Station?>(null) }
    var navTo by remember { mutableStateOf<Triple<Double, Double, String>?>(null) }
    var parking by remember { mutableStateOf(readParking(ctx, car.id)) }
    var msg by remember { mutableStateOf<String?>(null) }
    var centered by remember { mutableStateOf(false) }

    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)   // mapa escuro
            controller.setZoom(13.0)
            controller.setCenter(GeoPoint(39.6, -8.0))
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) map.onResume() else if (e == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); map.onDetach() }
    }

    suspend fun locate() {
        here = Loc.current(ctx)
        if (here == null) msg = tr("Não consegui obter a tua localização. Liga a localização do telemóvel.")
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) { onSearch() } else msg = tr("Sem acesso à localização.")
    }
    LaunchedEffect(Unit) {
        if (Loc.hasPermission(ctx)) { onSearch() }
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(st.done, Loc.hasPermission(ctx)) { if (Loc.hasPermission(ctx)) locate() }

    // Desenha tudo no mapa
    LaunchedEffect(st, here, parking) {
        map.overlays.clear()
        val list = st.stations.sortedBy { it.distKm }.take(80)
        val cheapest = if (evMode) list.filter { it.kw >= 50 }.take(8) else list.take(0) + (list.filter { it.distKm <= 10 }.sortedBy { it.price }.take(3))
        list.forEach { s ->
            val m = Marker(map)
            m.position = GeoPoint(s.lat, s.lng)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            m.icon = dot(if (s in cheapest) 0xFF22C55E.toInt() else primary.toArgb(), if (s in cheapest) 54 else 38)
            m.setOnMarkerClickListener { _, _ -> selected = s; true }
            map.overlays.add(m)
        }
        parking?.let { p ->
            val m = Marker(map)
            m.position = GeoPoint(p.lat, p.lng)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            m.icon = dot(0xFFFFB020.toInt(), 54)
            m.setOnMarkerClickListener { _, _ -> navTo = Triple(p.lat, p.lng, tr("O teu carro")); true }
            map.overlays.add(m)
        }
        here?.let { h ->
            val m = Marker(map)
            m.position = GeoPoint(h.first, h.second)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            m.icon = dot(0xFF3B82F6.toInt(), 46)
            m.setInfoWindow(null)
            map.overlays.add(m)
            if (!centered) { map.controller.setZoom(14.0); map.controller.setCenter(GeoPoint(h.first, h.second)); centered = true }
        }
        map.invalidate()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())

                // Topo
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledIconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Fechar")) }
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f), modifier = Modifier.weight(1f)) {
                        Text(
                            if (evMode) t("Elétrico · verde = carregador rápido (≥ 50 kW)", "Electric · green = fast charger (≥ 50 kW)") else "${car.fuelType} · " + t("verde = mais barato perto de ti", "green = cheapest near you"),
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    FilledIconButton(onClick = { here?.let { map.controller.animateTo(GeoPoint(it.first, it.second)); map.controller.setZoom(15.0) } }) {
                        Icon(Icons.Filled.MyLocation, tr("Centrar em mim"))
                    }
                }

                // Fundo
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    msg?.let {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    selected?.let { s ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (!s.ev) Text("${fmt(s.price, 3)} €/L", fontWeight = FontWeight.Black, color = primary) else if (s.kw > 0) Text("${if (s.kw % 1.0 == 0.0) s.kw.toInt() else s.kw} kW", fontWeight = FontWeight.Black, color = primary)
                                }
                                Text("${fmt(s.distKm)} km · " + (if (s.ev) listOf(s.info, s.locality).filter { it.isNotBlank() }.joinToString(" · ") else "${s.address}, ${s.locality}"), style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = { navTo = Triple(s.lat, s.lng, "${s.name}, ${s.locality}") },
                                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp),
                                ) { Icon(Icons.Filled.Navigation, null); Spacer(Modifier.width(8.dp)); Text(tr("Navegar")) }
                            }
                        }
                    }
                    if (!st.done && st.error) Text(tr("Não foi possível obter os postos."), style = MaterialTheme.typography.bodySmall)
                    if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }

    navTo?.let { (la, ln, lb) -> NavDialog(la, ln, lb) { navTo = null } }
}
