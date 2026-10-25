package com.diariobordo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Widget com o que está mais perto de acabar: avisos de manutenção e prazos (seguro, inspeção, IUC...). */
class ReminderWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { val v = build(ctx); ids.forEach { mgr.updateAppWidget(it, v) } } catch (_: Exception) {} finally { pending.finish() }
        }
    }

    companion object {
        private class Item(val text: String, val level: Level, val rank: Double)

        private suspend fun build(ctx: Context): RemoteViews {
            Lang.load(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_rem)
            val dao = AppDb.get(ctx).dao()
            val cars = dao.vehicles().first()
            val sel = ctx.getSharedPreferences("diario", 0).getLong("sel", 0)
            val car = cars.firstOrNull { it.id == sel } ?: cars.firstOrNull()
            val ids = intArrayOf(R.id.r1, R.id.r2, R.id.r3, R.id.r4, R.id.r5)
            if (car == null) {
                v.setTextViewText(R.id.r_title, "Cockpit")
                ids.forEachIndexed { i, id -> v.setTextViewText(id, if (i == 0) t("Adiciona um carro na app", "Add a car in the app") else "") }
            } else {
                val data = CarData(car, dao.fuelings(car.id).first(), dao.services(car.id).first(), dao.expenses(car.id).first(), dao.reminders(car.id).first())
                val km = if (KmPrefs.on(ctx)) estimateKm(data, KmPrefs.day(ctx, car.id))?.km ?: car.km else car.km
                val today = LocalDate.now()
                val items = data.reminders.map { r -> remInfo(r, km, today).let { Item("${r.name} · ${it.text}", it.level, it.progress) } } +
                    dao.docs(car.id).first().map { d -> docInfo(d, today).let { Item("${d.type} · ${it.text}", it.level, 1.2 - it.days.coerceIn(-30, 400) / 400.0) } }
                val top = items.sortedByDescending { it.rank }.take(5)
                v.setTextViewText(R.id.r_title, "${car.name} · " + t("a seguir", "coming up"))
                ids.forEachIndexed { i, id ->
                    val it = top.getOrNull(i)
                    if (it == null) v.setTextViewText(id, if (i == 0) t("Sem avisos nem prazos.", "No reminders or deadlines.") else "")
                    else {
                        v.setTextViewText(id, "● " + it.text)
                        v.setTextColor(id, when (it.level) { Level.LATE -> Color.parseColor("#FF5C5C"); Level.SOON -> Color.parseColor("#FFB020"); Level.OK -> Color.parseColor("#D5DAE3") })
                    }
                }
            }
            v.setOnClickPendingIntent(R.id.r_root, PendingIntent.getActivity(ctx, 31, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            return v
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ReminderWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { try { val v = build(ctx); ids.forEach { mgr.updateAppWidget(it, v) } } catch (_: Exception) {} }
        }
    }
}
