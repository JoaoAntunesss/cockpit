package com.diariobordo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Pedidos ao Overpass (OpenStreetMap). Os servidores públicos falham muitas vezes (limites, sobrecarga),
 * por isso consultamos vários ao mesmo tempo e ficamos com o primeiro que responder bem.
 */
object Overpass {
    private val ENDPOINTS = listOf(
        "https://overpass.openstreetmap.fr/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    /** Motivo da última falha (para mostrar no ecrã e perceber o que se passa). */
    @Volatile var lastError: String = ""

    private fun fetch(endpoint: String, query: String): JSONArray {
        val c = URL(endpoint).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 8000
            c.readTimeout = 25000
            c.setRequestProperty("User-Agent", "Cockpit-Android/2.0 (github.com/JoaoAntunesss/cockpit)")
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            c.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val arr = o.optJSONArray("elements") ?: throw IllegalStateException("resposta sem dados")
            val remark = o.optString("remark")
            if (arr.length() == 0 && remark.contains("error", ignoreCase = true)) throw IllegalStateException(remark.take(80))
            return arr
        } finally {
            c.disconnect()
        }
    }

    /**
     * Pergunta a todos os servidores ao mesmo tempo. Fica com a primeira resposta com dados;
     * uma resposta vazia só é aceite se nenhum outro servidor trouxer dados em poucos segundos
     * (alguns espelhos respondem logo, mas vazios ou desatualizados).
     */
    suspend fun query(query: String): JSONArray {
        val results = Channel<Result<JSONArray>>(ENDPOINTS.size)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            ENDPOINTS.forEach { ep -> scope.launch { results.send(runCatching { fetch(ep, query) }) } }
            val errors = mutableListOf<String>()
            var empty: JSONArray? = null
            var emptyAt = 0L
            var got = 0
            while (got < ENDPOINTS.size) {
                val r = if (empty == null) results.receive()
                        else kotlinx.coroutines.withTimeoutOrNull(maxOf(1L, 6000 - (System.currentTimeMillis() - emptyAt))) { results.receive() } ?: break
                got++
                if (r.isSuccess) {
                    val arr = r.getOrThrow()
                    if (arr.length() > 0) { lastError = ""; return arr }
                    if (empty == null) { empty = arr; emptyAt = System.currentTimeMillis() }
                } else errors.add(r.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "erro")
            }
            if (empty != null) { lastError = ""; return empty }
            lastError = errors.distinct().joinToString(" · ")
            throw IllegalStateException(lastError)
        } finally {
            scope.cancel()
        }
    }
}
