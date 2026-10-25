package com.diariobordo

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Um posto com o preço de um combustível (DGEG, Preços de Combustíveis Online). */
data class Station(
    val id: Int,
    val name: String,
    val brand: String,
    val address: String,
    val locality: String,
    val price: Double,
    val updated: String,   // "2026-10-21 09:05"
    val lat: Double,
    val lng: Double,
    val distKm: Double = 0.0,
    val ev: Boolean = false,
    val kw: Double = 0.0,      // potência máxima (elétricos)
    val info: String = "",     // ex.: "50 kW · CCS2, Type 2"
)

data class NearbyState(
    val stations: List<Station> = emptyList(),
    val done: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false,
    val locError: Boolean = false,
    val unsupported: Boolean = false,
)

object Chargers {
    private val ENDPOINTS = listOf("https://overpass-api.de/api/interpreter", "https://overpass.private.coffee/api/interpreter", "https://overpass.kumi.systems/api/interpreter")

    private fun kwOf(s: String): Double {
        // "22 kW", "50000" (W), "11;22" -> maior valor
        return s.split(';', ',').mapNotNull { p ->
            val m = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(kw|w)?", RegexOption.IGNORE_CASE).find(p.trim()) ?: return@mapNotNull null
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            val unit = m.groupValues[2].lowercase()
            if (unit == "w" || (unit.isEmpty() && v >= 1000)) v / 1000 else v
        }.maxOrNull() ?: 0.0
    }

    /** Postos de carregamento OpenStreetMap num raio de [radiusM] metros. */
    suspend fun download(lat: Double, lng: Double, radiusM: Int = 10000): List<Station> = withContext(Dispatchers.IO) {
        val q = "[out:json][timeout:15];nwr(around:$radiusM,$lat,$lng)[amenity=charging_station];out center tags 200;"
        var last: Exception? = null
        for (ep in ENDPOINTS) {
            try {
                val c = URL(ep).openConnection() as HttpURLConnection
                try {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.connectTimeout = 6000
                    c.readTimeout = 14000
                    c.setRequestProperty("User-Agent", "Cockpit-Android/1.0")
                    c.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                    if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONObject(body).getJSONArray("elements")
                    return@withContext (0 until arr.length()).mapNotNull { i -> toCharger(arr.getJSONObject(i)) }
                } finally { c.disconnect() }
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException()
    }

    private fun toCharger(o: JSONObject): Station? {
        val center = o.optJSONObject("center")
        val lat = if (o.has("lat")) o.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
        val lng = if (o.has("lon")) o.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
        if (lat.isNaN() || lng.isNaN()) return null
        val t = o.optJSONObject("tags") ?: JSONObject()
        val operator = t.optString("operator").ifBlank { t.optString("network") }.ifBlank { t.optString("brand") }
        val name = t.optString("name").ifBlank { operator }.ifBlank { "Carregador" }
        var kw = kwOf(t.optString("charging_station:output").ifBlank { t.optString("socket:type2:output").ifBlank { t.optString("socket:type2_combo:output") } })
        if (kw == 0.0) kw = kwOf(t.optString("socket:chademo:output"))
        val plugs = buildList {
            if (t.optString("socket:type2_combo") .isNotBlank() && t.optString("socket:type2_combo") != "0") add("CCS")
            if (t.optString("socket:type2").isNotBlank() && t.optString("socket:type2") != "0") add("Type 2")
            if (t.optString("socket:chademo").isNotBlank() && t.optString("socket:chademo") != "0") add("CHAdeMO")
        }
        val cap = t.optString("capacity").toIntOrNull()
        val info = listOfNotNull(
            if (kw > 0) "${if (kw % 1.0 == 0.0) kw.toInt().toString() else kw.toString()} kW" else null,
            plugs.joinToString(", ").ifBlank { null },
            cap?.let { "$it ${if (it == 1) "ponto" else "pontos"}" },
        ).joinToString(" · ")
        return Station(
            id = o.optLong("id").toInt(), name = name, brand = operator,
            address = t.optString("addr:street"), locality = t.optString("addr:city"),
            price = 0.0, updated = "", lat = lat, lng = lng, ev = true, kw = kw, info = info,
        )
    }
}

object Stations {
    private const val BASE = "https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos"

    /** Tipo de combustível do carro -> identificador da DGEG. */
    fun fuelIdFor(ft: String): Int? = fuelIdForBase(baseFuel(ft))
    private fun fuelIdForBase(fuelType: String): Int? = when {
        isEv(fuelType) -> null
        fuelType.startsWith("Gasóleo") -> 2101   // Gasóleo simples
        fuelType == "Gasolina 95" -> 3201        // Gasolina simples 95
        fuelType == "Gasolina 98" -> 3400        // Gasolina 98
        fuelType == "GPL" -> 1120                // GPL Auto
        else -> null
    }

    /** Todos os postos do país para um combustível, já ordenados por preço pela DGEG. */
    suspend fun download(fuelId: Int): List<Station> = withContext(Dispatchers.IO) {
        val all = ArrayList<Station>()
        var page = 1
        var total = Int.MAX_VALUE
        while (all.size < total && page <= 5) {
            val url = "$BASE?idsTiposComb=$fuelId&idMarca=&idTipoPosto=&idDistrito=&idsMunicipios=&qtdPorPagina=4000&pagina=$page"
            val c = URL(url).openConnection() as HttpURLConnection
            val batch: List<Station>
            try {
                c.connectTimeout = 10000
                c.readTimeout = 30000
                c.setRequestProperty("Accept", "application/json")
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(body).getJSONArray("resultado")
                batch = (0 until arr.length()).mapNotNull { i -> toStation(arr.getJSONObject(i)) }
                if (arr.length() > 0) total = arr.getJSONObject(0).optInt("Quantidade", arr.length())
                if (arr.length() == 0) break
            } finally {
                c.disconnect()
            }
            all.addAll(batch)
            page++
        }
        all
    }

    private fun toStation(o: JSONObject): Station? {
        val lat = o.optDouble("Latitude", Double.NaN)
        val lng = o.optDouble("Longitude", Double.NaN)
        val price = o.optString("Preco").replace(Regex("[^0-9,.]"), "").replace(',', '.').toDoubleOrNull()
        if (lat.isNaN() || lng.isNaN() || price == null || price <= 0) return null
        return Station(
            id = o.optInt("Id"),
            name = o.optString("Nome").trim(),
            brand = o.optString("Marca").trim(),
            address = o.optString("Morada").trim(),
            locality = o.optString("Localidade").trim(),
            price = price,
            updated = o.optString("DataAtualizacao"),
            lat = lat,
            lng = lng,
        )
    }
}

fun distKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * 6371.0 * asin(sqrt(a))
}

object Loc {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Posição atual (aproximada chega), ou null se não foi possível obter. */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Pair<Double, Double>? = suspendCancellableCoroutine { cont ->
        if (!hasPermission(ctx)) { cont.resume(null); return@suspendCancellableCoroutine }
        val client = LocationServices.getFusedLocationProviderClient(ctx)
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { l ->
                if (l != null) cont.resume(l.latitude to l.longitude)
                else client.lastLocation
                    .addOnSuccessListener { l2 -> cont.resume(l2?.let { it.latitude to it.longitude }) }
                    .addOnFailureListener { cont.resume(null) }
            }
            .addOnFailureListener { cont.resume(null) }
    }
}
