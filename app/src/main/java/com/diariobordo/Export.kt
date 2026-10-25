package com.diariobordo

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File

/** Exporta o histórico do carro em PDF (para mostrar a um comprador) ou CSV (abre no Excel). */
object Export {
    private fun dir(ctx: Context) = File(ctx.cacheDir, "export").apply { mkdirs() }
    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private fun n2(x: Double) = String.format(PT, "%.2f", x)
    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    fun csv(ctx: Context, d: CarData): File {
        class R(val day: Long, val line: String)
        val rows = mutableListOf<R>()
        d.fuelings.forEach { rows += R(it.date, "${docDate(it.date)};${tr("Abastecimento")};${it.km};${q(tr("Abastecimento"))};${n2(it.liters)};${n2(it.total)};${q(it.station)}") }
        d.services.forEach {
            rows += R(it.date, "${docDate(it.date)};${tr("Intervenção")};${it.km};${q(it.title)};;${n2(it.parts + it.labor)};${q(it.category + if (it.byMe) t(" · feito por mim", " · done by me") else if (it.shop.isNotBlank()) " · " + it.shop else "")}")
        }
        d.expenses.forEach { rows += R(it.date, "${docDate(it.date)};${tr("Despesa")};;${q(it.type)};;${n2(it.amount)};") }
        val sb = StringBuilder("﻿")
        sb.append(t("Data;Tipo;Km;Descrição;Litros/kWh;Custo (€);Notas\n", "Date;Type;Km;Description;Litres/kWh;Cost (€);Notes\n"))
        rows.sortedBy { it.day }.forEach { sb.append(it.line).append('\n') }
        val f = File(dir(ctx), "cockpit_${safe(d.vehicle.name)}.csv")
        f.writeText(sb.toString())
        return f
    }

    fun pdf(ctx: Context, d: CarData): File {
        val doc = PdfDocument()
        val paint = Paint().apply { isAntiAlias = true }
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        var pageNo = 0
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            canvas = page!!.canvas
            y = 56f
        }
        fun line(left: String, right: String = "", size: Float = 11f, bold: Boolean = false, color: Int = Color.BLACK) {
            if (page == null || y > 790f) newPage()
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.color = color
            paint.textAlign = Paint.Align.LEFT
            canvas!!.drawText(if (left.length > 78) left.take(77) + "…" else left, 40f, y, paint)
            if (right.isNotBlank()) {
                paint.textAlign = Paint.Align.RIGHT
                canvas!!.drawText(right, 555f, y, paint)
            }
            y += size + 7f
        }
        fun gap(h: Float = 8f) { y += h }

        val v = d.vehicle
        newPage()
        line(tr("Histórico do veículo"), size = 22f, bold = true)
        line(v.name, size = 15f, bold = true)
        line(listOf(v.brand.replaceFirstChar { it.uppercase() }, v.model, if (v.year > 0) v.year.toString() else "", v.engine, v.fuelType)
            .filter { it.isNotBlank() }.joinToString(" · "), color = Color.DKGRAY)
        line(t("Matrícula", "Plate") + ": ${v.plate.ifBlank { "—" }} · ${fint(v.km)} km", color = Color.DKGRAY)
        gap(6f)

        val months = monthly(d, 12)
        val total = months.sumOf { it.total }
        val st = fuelStats(d.liquid())
        val span = kmSpan(d, 12)
        line(tr("Resumo (últimos 12 meses)"), size = 13f, bold = true)
        line(tr("Gasto total"), eur(total))
        line(tr("Combustível"), eur(months.sumOf { it.fuel }))
        line(tr("Mecânica"), eur(months.sumOf { it.mech }))
        line(tr("Despesas fixas"), eur(months.sumOf { it.fixed }))
        line(tr("Consumo médio"), st.avg?.let { fmt(it) + " " + consUnit(v.fuelType) + " km" } ?: "—")
        line(tr("Custo por km"), if (span >= 50) fmt(total / span, 3) + " €" else "—")
        gap()

        line(t("Intervenções", "Services") + " (${d.services.size})", size = 13f, bold = true)
        if (d.services.isEmpty()) line(tr("Sem registos."))
        d.services.sortedBy { it.date }.forEach {
            line("${docDate(it.date)} · ${fint(it.km)} km · ${it.title}" + if (it.byMe) t(" (feito por mim)", " (done by me)") else if (it.shop.isNotBlank()) " (${it.shop})" else "", eur(it.parts + it.labor))
        }
        gap()

        line(tr("Documentos e prazos"), size = 13f, bold = true)
        if (d.docs.isEmpty()) line(tr("Sem registos."))
        d.docs.forEach { line("${it.type}${if (it.title.isNotBlank()) " · " + it.title else ""}", t("até ", "until ") + docDate(it.expiry)) }
        gap()

        line(t("Despesas", "Expenses") + " (${d.expenses.size})", size = 13f, bold = true)
        if (d.expenses.isEmpty()) line(tr("Sem registos."))
        d.expenses.sortedBy { it.date }.forEach { line("${docDate(it.date)} · ${it.type}", eur(it.amount)) }
        gap()

        line(tr("Últimos abastecimentos"), size = 13f, bold = true)
        if (d.fuelings.isEmpty()) line(tr("Sem registos."))
        d.fuelings.sortedByDescending { it.date }.take(20).forEach {
            line("${docDate(it.date)} · ${fint(it.km)} km · ${fmt(it.liters)} ${unitOf(it, d.vehicle.fuelType)}", eur(it.total))
        }
        gap(12f)
        line(tr("Gerado pela app Cockpit."), size = 9f, color = Color.GRAY)

        page?.let { doc.finishPage(it) }
        val f = File(dir(ctx), "cockpit_${safe(v.name)}.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun share(ctx: Context, f: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, tr("Partilhar histórico")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
