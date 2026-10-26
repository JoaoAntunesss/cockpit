package com.diariobordo

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// ---------- Onde deixei o carro ----------

object ParkingStore {
    class Spot(val lat: Double, val lng: Double, val at: Long, val note: String)

    private fun p(ctx: Context) = ctx.getSharedPreferences("parking", 0)

    fun read(ctx: Context, carId: Long): Spot? {
        val a = (p(ctx).getString("car_$carId", null) ?: return null).split(";")
        if (a.size != 3) return null
        val lat = a[0].toDoubleOrNull() ?: return null
        val lng = a[1].toDoubleOrNull() ?: return null
        return Spot(lat, lng, a[2].toLongOrNull() ?: 0L, p(ctx).getString("note_$carId", "") ?: "")
    }

    fun save(ctx: Context, carId: Long, lat: Double, lng: Double) {
        p(ctx).edit().putString("car_$carId", "$lat;$lng;${System.currentTimeMillis()}").remove("note_$carId").apply()
    }

    fun setNote(ctx: Context, carId: Long, note: String) { p(ctx).edit().putString("note_$carId", note).apply() }

    fun clear(ctx: Context, carId: Long) { p(ctx).edit().remove("car_$carId").remove("note_$carId").apply() }
}

// ---------- Parques de estacionamento (OpenStreetMap, sem chave) ----------

class ParkLot(
    val name: String, val lat: Double, val lng: Double,
    val kind: String, val fee: Boolean?, val capacity: Int?, val hours: String, val distKm: Double,
)

object ParkCache {
    var here: Pair<Double, Double>? = null
    var lots: List<ParkLot> = emptyList()
    private var at = 0L
    fun set(h: Pair<Double, Double>, l: List<ParkLot>) { here = h; lots = l; at = System.currentTimeMillis() }
    fun fresh() = lots.isNotEmpty() && System.currentTimeMillis() - at < 5 * 60_000
}

object Parks {
    suspend fun download(lat: Double, lng: Double, radiusM: Int = 1500): List<ParkLot> {
        val q = "[out:json][timeout:25];nwr(around:$radiusM,$lat,$lng)[amenity=parking]" +
            "[access!~\"^(private|customers|permit|delivery)\$\"][parking!~\"^(street_side|lane)\$\"];out center tags qt 300;"
        val arr = Overpass.query(q)
        return (0 until arr.length()).mapNotNull { i -> toLot(arr.getJSONObject(i), lat, lng) }.sortedBy { it.distKm }
    }

    private fun toLot(o: JSONObject, lat0: Double, lng0: Double): ParkLot? {
        val center = o.optJSONObject("center")
        val lat = if (o.has("lat")) o.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
        val lng = if (o.has("lon")) o.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
        if (lat.isNaN() || lng.isNaN()) return null
        val t = o.optJSONObject("tags") ?: JSONObject()
        val kind = t.optString("parking")
        if (kind == "street_side" || kind == "lane") return null     // estacionamento ao longo da rua: ruído
        val fee = when (t.optString("fee")) { "yes" -> true; "no" -> false; else -> null }
        val name = t.optString("name").ifBlank { t.optString("operator") }
        return ParkLot(
            name = name, lat = lat, lng = lng, kind = kind, fee = fee,
            capacity = t.optString("capacity").toIntOrNull(), hours = t.optString("opening_hours"),
            distKm = distKm(lat0, lng0, lat, lng),
        )
    }
}

private fun ago(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60000
    return when {
        m < 1 -> t("agora mesmo", "just now")
        m < 60 -> t("há $m min", "$m min ago")
        m < 1440 -> t("há ${m / 60} h", "${m / 60} h ago")
        else -> t("há ${m / 1440} dias", "${m / 1440} days ago")
    }
}

private fun distText(km: Double): String = if (km < 1) "${(km * 1000).roundToInt()} m" else fmt(km) + " km"

private fun kindText(k: String): String = when (k) {
    "underground" -> t("Subterrâneo", "Underground")
    "multi-storey" -> t("Silo (vários pisos)", "Multi-storey")
    "surface" -> t("À superfície", "Surface")
    else -> t("Parque", "Car park")
}

private fun isGarage(l: ParkLot) = l.kind == "underground" || l.kind == "multi-storey"

// ---------- Aba "Parque" ----------

@Composable
fun ParkScreen(car: Vehicle, pad: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var spot by remember(car.id) { mutableStateOf(ParkingStore.read(ctx, car.id)) }
    var here by remember { mutableStateOf(ParkCache.here) }
    var lots by remember { mutableStateOf(ParkCache.lots) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(0) }       // 0 todos, 1 grátis, 2 garagem
    var nav by remember { mutableStateOf<Triple<Double, Double, String>?>(null) }
    var showMap by remember { mutableStateOf(false) }
    var note by remember(spot?.at) { mutableStateOf(spot?.note ?: "") }

    fun search() {
        scope.launch {
            loading = true; error = false
            val h = kotlinx.coroutines.withTimeoutOrNull(15_000L) { Loc.current(ctx) }
            if (h == null) { loading = false; error = true; detail = t("sem localização", "no location"); return@launch }
            here = h
            try {
                var l = kotlinx.coroutines.withTimeout(40_000L) { Parks.download(h.first, h.second) }
                if (l.isEmpty()) l = kotlinx.coroutines.withTimeout(40_000L) { Parks.download(h.first, h.second, 3000) }
                lots = l
                ParkCache.set(h, l)
                detail = ""
            } catch (e: Exception) { error = true; detail = Overpass.lastError.ifBlank { e.message ?: "" } }
            loading = false
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) { denied = false; search() } else denied = true
    }
    fun ask() {
        if (Loc.hasPermission(ctx)) { denied = false; search() }
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    LaunchedEffect(Unit) {
        if (ParkCache.fresh()) { here = ParkCache.here; lots = ParkCache.lots }
        else if (Loc.hasPermission(ctx)) search()
    }
    fun saveHere() {
        if (!Loc.hasPermission(ctx)) { ask(); return }
        scope.launch {
            val h = kotlinx.coroutines.withTimeoutOrNull(15_000L) { Loc.current(ctx) }
            if (h == null) msg = t("Não consegui obter a tua localização. Liga a localização do telemóvel.", "Could not get your location. Turn on location on your phone.")
            else {
                ParkingStore.save(ctx, car.id, h.first, h.second)
                spot = ParkingStore.read(ctx, car.id)
                here = h
                msg = t("Local guardado.", "Spot saved.")
            }
        }
    }

    val shown = lots.filter { when (filter) { 1 -> it.fee == false; 2 -> isGarage(it); else -> true } }.take(40)
    val card = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    LazyColumn(
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 4.dp, 16.dp, pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = card, border = border, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.LocalParking, null, tint = MaterialTheme.colorScheme.primary)
                        Text(t("Onde deixei o carro", "Where I parked"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    val s = spot
                    if (s == null) {
                        Text(t("Ainda não guardaste onde estacionaste. Toca no botão quando saíres do carro.", "You have not saved where you parked yet. Tap the button when you leave the car."), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val dist = here?.let { distKm(it.first, it.second, s.lat, s.lng) }
                        Text(
                            t("Guardado ", "Saved ") + ago(s.at) + (dist?.let { " · " + t("a ", "") + distText(it) + t(" de ti", " away") } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedTextField(
                            value = note, onValueChange = { note = it.take(60); ParkingStore.setNote(ctx, car.id, note) },
                            label = { Text(t("Nota (piso, lugar...)", "Note (level, space...)")) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { saveHere() }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(if (s == null) t("Guardar aqui", "Save here") else t("Atualizar local", "Update spot"))
                        }
                        if (s != null) OutlinedButton(
                            onClick = { nav = Triple(s.lat, s.lng, t("O teu carro", "Your car")) },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Icon(Icons.Filled.DirectionsWalk, null); Spacer(Modifier.width(6.dp)); Text(t("Levar-me ao carro", "Take me to the car")) }
                    }
                    if (s != null) TextButton(onClick = { ParkingStore.clear(ctx, car.id); spot = null; note = ""; msg = t("Local apagado.", "Spot cleared.") }) {
                        Text(t("Já apanhei o carro (apagar)", "Got the car (clear)"))
                    }
                    msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("Parques perto de mim", "Car parks near me").uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { ask() }, enabled = !loading) { Icon(Icons.Filled.Refresh, t("Procurar outra vez", "Search again")) }
                if (lots.isNotEmpty()) TextButton(onClick = { showMap = true }) { Icon(Icons.Filled.Map, null); Spacer(Modifier.width(6.dp)); Text(t("Mapa", "Map")) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(t("Todos", "All"), t("Grátis", "Free"), t("Garagem", "Garage")).forEachIndexed { i, l ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(l) })
                }
            }
        }
        when {
            loading && lots.isEmpty() -> item { ShimmerRows(5, Modifier.padding(vertical = 8.dp)) }
            denied -> item { ParkMsg(t("Sem acesso à localização. Permite-o para ver os parques perto de ti.", "No location access. Allow it to see car parks near you."), t("Permitir", "Allow")) { ask() } }
            error && lots.isEmpty() -> item { ParkMsg(t("Não consegui procurar parques. Verifica a localização e a ligação.", "Could not search car parks. Check location and connection.") + (if (detail.isNotBlank()) "\n($detail)" else ""), t("Tentar outra vez", "Try again")) { ask() } }
            lots.isEmpty() && ParkCache.here != null && !loading -> item { ParkMsg(t("Não encontrei parques públicos num raio de 3 km. Os dados do OpenStreetMap podem estar incompletos nesta zona.", "No public car parks found within 3 km. OpenStreetMap data may be incomplete here."), t("Procurar outra vez", "Search again")) { ask() } }
            lots.isEmpty() -> item { ParkMsg(t("Procura os parques de estacionamento num raio de 1,5 km.", "Search car parks within 1.5 km."), t("Procurar", "Search")) { ask() } }
            shown.isEmpty() -> item { Text(t("Nenhum parque com este filtro.", "No car parks match this filter."), style = MaterialTheme.typography.bodyMedium) }
            else -> {
                item {
                    Card(colors = card, border = border, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            shown.forEach { l ->
                                ListItem(
                                    modifier = Modifier.clickable { nav = Triple(l.lat, l.lng, l.name.ifBlank { t("Parque de estacionamento", "Car park") }) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    leadingContent = { Icon(if (isGarage(l)) Icons.Filled.LocalParking else Icons.Filled.DirectionsCar, null, tint = MaterialTheme.colorScheme.primary) },
                                    headlineContent = { Text(l.name.ifBlank { t("Parque sem nome", "Unnamed car park") }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = {
                                        Text(
                                            listOfNotNull(
                                                kindText(l.kind),
                                                l.fee?.let { if (it) t("Pago", "Paid") else t("Grátis", "Free") },
                                                l.capacity?.let { t("$it lugares", "$it spaces") },
                                                l.hours.ifBlank { null },
                                            ).joinToString(" · "),
                                            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                                        )
                                    },
                                    trailingContent = { Text(distText(l.distKm), fontWeight = FontWeight.SemiBold) },
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        t("Dados do OpenStreetMap. Preços, horários e lugares livres podem estar desatualizados ou em falta.", "OpenStreetMap data. Prices, opening hours and free spaces may be out of date or missing."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showMap) ParkMapDialog(spot, here, shown, { la, ln, lb -> nav = Triple(la, ln, lb) }) { showMap = false }
    nav?.let { (la, ln, lb) -> NavDialog(la, ln, lb) { nav = null } }
}

@Composable
private fun ParkMsg(text: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun ParkMapDialog(spot: ParkingStore.Spot?, here: Pair<Double, Double>?, lots: List<ParkLot>, onNav: (Double, Double, String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
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
            overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(here?.first ?: 39.6, here?.second ?: -8.0))
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
    LaunchedEffect(lots, spot, here) {
        map.overlays.clear()
        lots.forEach { l ->
            val m = Marker(map)
            m.position = GeoPoint(l.lat, l.lng)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            m.icon = dot(if (l.fee == false) 0xFF22C55E.toInt() else primary.toArgb(), 40)
            m.setOnMarkerClickListener { _, _ -> onNav(l.lat, l.lng, l.name.ifBlank { t("Parque de estacionamento", "Car park") }); true }
            map.overlays.add(m)
        }
        spot?.let { s ->
            val m = Marker(map)
            m.position = GeoPoint(s.lat, s.lng)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            m.icon = dot(0xFFFFB020.toInt(), 54)
            m.setOnMarkerClickListener { _, _ -> onNav(s.lat, s.lng, t("O teu carro", "Your car")); true }
            map.overlays.add(m)
        }
        here?.let { h ->
            val m = Marker(map)
            m.position = GeoPoint(h.first, h.second)
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            m.icon = dot(0xFF3B82F6.toInt(), 46)
            m.setInfoWindow(null)
            map.overlays.add(m)
        }
        map.invalidate()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledIconButton(onClick = onClose) { Icon(Icons.Filled.Close, t("Fechar", "Close")) }
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f), modifier = Modifier.weight(1f)) {
                        Text(
                            t("Verde = grátis · amarelo = o teu carro · toca num ponto para navegar", "Green = free · yellow = your car · tap a point to navigate"),
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    FilledIconButton(onClick = { here?.let { map.controller.animateTo(GeoPoint(it.first, it.second)); map.controller.setZoom(16.0) } }) {
                        Icon(Icons.Filled.MyLocation, t("Centrar em mim", "Center on me"))
                    }
                }
            }
        }
    }
}
