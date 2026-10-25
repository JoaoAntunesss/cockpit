package com.diariobordo

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Importa histórico de um CSV (o mesmo formato do «Exportar para Excel»): Data;Tipo;Km;Descrição;Litros/kWh;Custo;Notas */
object Importer {
    class Parsed(val fuel: List<Fueling>, val serv: List<Service>, val exp: List<Expense>, val skipped: Int)

    private fun plain(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun split(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQ && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQ = !inQ
                c == sep && !inQ -> { out.add(sb.toString().trim()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }

    private val FORMATS = listOf("dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "d-M-yyyy", "dd.MM.yyyy").map { DateTimeFormatter.ofPattern(it) }
    private fun date(s: String): LocalDate? {
        val x = s.trim()
        for (f in FORMATS) try { return LocalDate.parse(x, f) } catch (_: Exception) {}
        return null
    }
    private fun number(s: String): Double? = s.replace("€", "").replace(" ", "").replace(',', '.').toDoubleOrNull()

    fun parse(text: String, vehicleId: Long): Parsed {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        val sep = if (lines.firstOrNull()?.count { it == ';' } ?: 0 >= (lines.firstOrNull()?.count { it == ',' } ?: 0)) ';' else ','
        val fuel = ArrayList<Fueling>(); val serv = ArrayList<Service>(); val exp = ArrayList<Expense>()
        var skipped = 0
        for ((idx, line) in lines.withIndex()) {
            val c = split(line, sep)
            val day = c.getOrNull(0)?.let { date(it) }
            if (day == null) { if (idx > 0) skipped++; continue }   // cabeçalho
            val type = plain(c.getOrNull(1) ?: "")
            val km = c.getOrNull(2)?.filter(Char::isDigit)?.toIntOrNull() ?: 0
            val desc = c.getOrNull(3) ?: ""
            val qty = c.getOrNull(4)?.let { number(it) }
            val cost = c.getOrNull(5)?.let { number(it) } ?: 0.0
            val notes = c.getOrNull(6) ?: ""
            val d = day.toEpochDay()
            when {
                type.startsWith("abast") || type.startsWith("fuel") || type.startsWith("refuel") || type.startsWith("carga") || type.startsWith("charg") -> {
                    if (km > 0 && qty != null && qty > 0) fuel += Fueling(0, vehicleId, d, km, qty, cost, false, notes) else skipped++
                }
                type.startsWith("interv") || type.startsWith("serv") || type.startsWith("manut") || type.startsWith("maint") -> {
                    if (desc.isBlank()) { skipped++; continue }
                    val head = notes.substringBefore(" · ").trim()
                    val p = plain(desc + " " + notes)
                    val cat = SERVICE_CATEGORIES.firstOrNull { it == head } ?: when {
                        "pneu" in p || "tyre" in p || "tire" in p -> "Pneus"
                        "trav" in p || "pastilha" in p || "disco" in p || "brake" in p -> "Travões"
                        "oleo" in p || "filtro" in p || "oil" in p -> "Óleo e filtros"
                        "suspens" in p || "amortec" in p -> "Suspensão"
                        else -> "Outros"
                    }
                    val byMe = "feito por mim" in plain(notes) || "done by me" in plain(notes)
                    val shop = if (byMe) "" else notes.substringAfter(" · ", "").trim()
                    serv += Service(0, vehicleId, d, km, desc, cat, byMe, shop, cost, 0.0, "", "")
                }
                type.startsWith("desp") || type.startsWith("exp") -> {
                    if (cost <= 0) { skipped++; continue }
                    val ty = EXPENSE_TYPES.firstOrNull { plain(it) == plain(desc) } ?: "Outro"
                    exp += Expense(0, vehicleId, d, ty, cost)
                }
                else -> skipped++
            }
        }
        return Parsed(fuel, serv, exp, skipped)
    }
}
