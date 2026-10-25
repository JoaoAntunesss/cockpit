package com.diariobordo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Widget do ecrã inicial: nome do carro, km e o aviso mais urgente. Toca para abrir a app. */
class CarWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { ids.forEach { mgr.updateAppWidget(it, build(ctx)) } } catch (_: Exception) {} finally { pending.finish() }
        }
    }

    companion object {
        private fun go(ctx: Context, code: Int, what: String): PendingIntent =
            PendingIntent.getActivity(
                ctx, code, Intent(ctx, MainActivity::class.java).putExtra("go", what).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private suspend fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_car)
            val dao = AppDb.get(ctx).dao()
            val cars = dao.vehicles().first()
            val sel = ctx.getSharedPreferences("diario", 0).getLong("sel", 0)
            val car = cars.firstOrNull { it.id == sel } ?: cars.firstOrNull()
            if (car == null) {
                v.setTextViewText(R.id.w_name, "Cockpit")
                v.setTextViewText(R.id.w_km, "—")
                v.setTextViewText(R.id.w_stats, "")
                v.setTextViewText(R.id.w_next, t("Adiciona um carro", "Add a car"))
                v.setTextViewText(R.id.w_doc, "")
            } else {
                val data = CarData(car, dao.fuelings(car.id).first(), dao.services(car.id).first(), dao.expenses(car.id).first(), dao.reminders(car.id).first())
                val km = if (KmPrefs.on(ctx)) estimateKm(data, KmPrefs.day(ctx, car.id))?.km ?: car.km else car.km
                val avg = fuelStats(data.liquid()).avg
                val now = java.time.YearMonth.now()
                fun inMonth(day: Long) = java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(day)) == now
                val month = data.fuelings.filter { inMonth(it.date) }.sumOf { it.total } +
                    data.services.filter { inMonth(it.date) }.sumOf { it.parts + it.labor } +
                    data.expenses.filter { inMonth(it.date) }.sumOf { it.amount }
                v.setTextViewText(R.id.w_name, "${car.name} · ${car.fuelType}")
                v.setTextViewText(R.id.w_km, fint(km) + " km")
                v.setTextViewText(
                    R.id.w_stats,
                    (avg?.let { t("Consumo ", "Avg ") + fmt(it) + " " + consUnit(car.fuelType) } ?: t("Sem consumo ainda", "No consumption yet")) +
                        "  ·  " + t("Mês ", "Month ") + eur(month),
                )
                val top = data.reminders.map { it to remInfo(it, km) }.maxByOrNull { it.second.progress }
                v.setTextViewText(R.id.w_next, top?.let { "🔧 ${it.first.name} · ${it.second.text}" } ?: t("Sem avisos de manutenção", "No maintenance reminders"))
                val doc = dao.docs(car.id).first().minByOrNull { it.expiry }
                v.setTextViewText(R.id.w_doc, doc?.let { "📄 ${it.type} · ${docInfo(it, java.time.LocalDate.now()).text}" } ?: "")
            }
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            v.setOnClickPendingIntent(R.id.w_b_fuel, go(ctx, 11, "fuel"))
            v.setOnClickPendingIntent(R.id.w_b_serv, go(ctx, 12, "serv"))
            v.setOnClickPendingIntent(R.id.w_b_exp, go(ctx, 13, "exp"))
            return v
        }

        /** Chama quando os dados mudam, para o widget não ficar desatualizado. */
        fun refresh(ctx: Context) {
            FuelQuickWidget.refresh(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, CarWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { try { ids.forEach { mgr.updateAppWidget(it, build(ctx)) } } catch (_: Exception) {} }
        }
    }
}
