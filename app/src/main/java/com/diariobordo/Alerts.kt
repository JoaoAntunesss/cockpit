package com.diariobordo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Corre de 12 em 12 horas, mesmo com a app fechada, e avisa de prazos (seguro, inspeção...)
 * e de manutenções que estão a chegar ou já passaram. Cada aviso só toca uma vez (os atrasados voltam a tocar de semana a semana).
 */
class AlertWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {

    private class Msg(val key: String, val title: String, val text: String)

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        if (!KmPrefs.notifOn(ctx)) return Result.success()
        val dao = AppDb.get(ctx).dao()
        val prefs = ctx.getSharedPreferences("alerts", 0)
        val today = LocalDate.now()
        val week = today.toEpochDay() / 7
        val out = mutableListOf<Msg>()

        for (v in dao.vehicles().first()) {
            for (dc in dao.docs(v.id).first()) {
                val i = docInfo(dc, today)
                val what = dc.type + if (dc.title.isNotBlank()) " (${dc.title})" else ""
                when {
                    i.days < 0 -> out += Msg("doc${dc.id}_${dc.expiry}_late_$week", "${v.name}: $what " + tr("expirou"), "${i.text}. " + tr("Renova e atualiza a data na app."))
                    i.days <= 1 -> out += Msg("doc${dc.id}_${dc.expiry}_d1", "${v.name}: $what", if (i.days == 0) tr("Acaba hoje.") else tr("Acaba amanhã."))
                    i.days <= 7 -> out += Msg("doc${dc.id}_${dc.expiry}_d7", "${v.name}: $what", "${i.text} (" + t("até ", "until ") + docDate(dc.expiry) + ").")
                    i.level == Level.SOON -> out += Msg("doc${dc.id}_${dc.expiry}_w", "${v.name}: $what", "${i.text} (" + t("até ", "until ") + docDate(dc.expiry) + ").")
                    else -> {}
                }
            }
            val iucM = IucPrefs.month(ctx, v.id)
            if (iucM in 1..12 && !isEv(v.fuelType)) {
                iucNext(IucPrefs.total(ctx), iucM, today)?.let { n ->
                    val monthTxt = n.ym.monthValue.toString().padStart(2, '0') + "/" + n.ym.year
                    when {
                        n.days <= 7 -> out += Msg("iuc${v.id}_${n.ym}_w", "${v.name}: IUC", t("O prazo do IUC (mês $monthTxt) acaba esta semana. Confirma o valor e a data no Portal das Finanças.", "The IUC deadline (month $monthTxt) ends this week. Confirm the amount and date on the Tax Portal."))
                        n.days <= 31 -> out += Msg("iuc${v.id}_${n.ym}_m", "${v.name}: IUC", t("Há um pagamento de IUC previsto em $monthTxt. Confirma o valor e a data no Portal das Finanças.", "An IUC payment is due in $monthTxt. Confirm the amount and date on the Tax Portal."))
                    }
                }
            }
            val reminders = dao.reminders(v.id).first()
            val data = CarData(v, dao.fuelings(v.id).first(), dao.services(v.id).first(), dao.expenses(v.id).first(), reminders)
            if (!isEv(v.fuelType)) {
                val stc = fuelStats(data.liquid())
                val pct = stc.trendPct
                if (pct != null && pct >= 15) {
                    val lastId = data.liquid().maxByOrNull { it.date }?.id ?: 0L
                    out += Msg("cons${v.id}_$lastId", "${v.name}: " + t("consumo anormal", "unusual consumption"),
                        t("Nos últimos 3 abastecimentos gastaste ${fmt(pct, 0)}% mais. Vê a pressão dos pneus, os filtros e se há alguma fuga.", "Over the last 3 refuels you used ${fmt(pct, 0)}% more. Check tyre pressure, filters and any leaks."))
                }
            }
            if (v.monthlyBudget > 0) {
                val spent = monthSpend(data)
                val ym = java.time.YearMonth.now()
                when {
                    spent > v.monthlyBudget -> out += Msg("bud${v.id}_${ym}_100", "${v.name}: " + t("orçamento ultrapassado", "budget exceeded"), t("Gastaste ${eur(spent)} este mês, o orçamento era ${eur(v.monthlyBudget)}.", "You spent ${eur(spent)} this month against a ${eur(v.monthlyBudget)} budget."))
                    spent >= v.monthlyBudget * 0.8 -> out += Msg("bud${v.id}_${ym}_80", "${v.name}: " + t("80% do orçamento", "80% of the budget"), t("Já gastaste ${eur(spent)} de ${eur(v.monthlyBudget)} este mês.", "You have spent ${eur(spent)} of ${eur(v.monthlyBudget)} this month."))
                }
            }
            val km = if (KmPrefs.on(ctx)) estimateKm(data, KmPrefs.day(ctx, v.id))?.km ?: v.km else v.km
            for (r in reminders) {
                val ri = remInfo(r, km, today)
                val base = "rem${r.id}_${r.lastKm}_${r.lastDate}"
                when (ri.level) {
                    Level.LATE -> out += Msg("${base}_late_$week", "${v.name}: ${r.name}", ri.text)
                    Level.SOON -> out += Msg("${base}_soon", "${v.name}: ${r.name}", ri.text)
                    Level.OK -> {}
                }
            }
        }

        val fresh = out.filter { !prefs.getBoolean(it.key, false) }
        if (fresh.isEmpty()) return Result.success()

        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val nm = NotificationManagerCompat.from(ctx)
        val edit = prefs.edit()
        fresh.forEach { m ->
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(m.title)
                .setContentText(m.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(m.text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try { nm.notify(m.key.hashCode(), n); edit.putBoolean(m.key, true) } catch (_: SecurityException) {}
        }
        edit.apply()
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "prazos"

        private fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Prazos e manutenção"), NotificationManager.IMPORTANCE_DEFAULT))
            }
        }

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<AlertWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("alertas", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
