package com.diariobordo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Preço médio nacional de um combustível (API Aberta: GET /v1/fuel/prices). */
data class FuelPrice(
    val slug: String,
    val name: String,
    val avg: Double,
    val min: Double,
    val max: Double,
    val stations: Int,
    val date: String,
)

data class PricesState(
    val map: Map<String, FuelPrice> = emptyMap(),
    val loading: Boolean = false,
    val error: Boolean = false,
)

object FuelPrices {
    private const val URL_PRICES = "https://api.apiaberta.pt/v1/fuel/prices"

    /** Tipo de combustível do carro -> identificador da API. */
    fun slugFor(ft: String): String? { val fuelType = baseFuel(ft); return slugForBase(fuelType) }
    private fun slugForBase(fuelType: String): String? = when {
        fuelType.startsWith("Gasóleo") -> "diesel"
        fuelType == "Gasolina 95" -> "gasoline_95"
        fuelType == "Gasolina 98" -> "gasoline_98"
        fuelType == "GPL" -> "gpl_auto"
        else -> null
    }

    suspend fun download(): String = withContext(Dispatchers.IO) {
        val c = URL(URL_PRICES).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun parse(body: String?): List<FuelPrice> {
        if (body.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONObject(body).getJSONArray("data")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FuelPrice(
                    slug = o.getString("fuel_slug"),
                    name = o.getString("fuel_name"),
                    avg = o.getDouble("avg_price_eur"),
                    min = o.getDouble("min_price_eur"),
                    max = o.getDouble("max_price_eur"),
                    stations = o.getInt("station_count"),
                    date = o.getString("date"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
