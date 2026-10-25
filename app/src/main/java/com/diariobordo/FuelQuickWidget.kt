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

/** Botão no ecrã inicial: um toque abre o formulário de abastecimento do carro escolhido. */
class FuelQuickWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { val v = build(ctx); ids.forEach { mgr.updateAppWidget(it, v) } } catch (_: Exception) {} finally { pending.finish() }
        }
    }

    companion object {
        private suspend fun build(ctx: Context): RemoteViews {
            Lang.load(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.widget_fuel)
            val dao = AppDb.get(ctx).dao()
            val cars = dao.vehicles().first()
            val sel = ctx.getSharedPreferences("diario", 0).getLong("sel", 0)
            val car = cars.firstOrNull { it.id == sel } ?: cars.firstOrNull()
            v.setTextViewText(R.id.f_title, if (car != null && isEv(car.fuelType)) t("Carregar", "Charge") else t("Abastecer", "Refuel"))
            v.setTextViewText(R.id.f_sub, car?.let { "${it.name} · ${fint(it.km)} km" } ?: t("Adiciona um carro na app", "Add a car in the app"))
            v.setOnClickPendingIntent(
                R.id.f_root,
                PendingIntent.getActivity(
                    ctx, 21, Intent(ctx, MainActivity::class.java).putExtra("go", "fuel").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            return v
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, FuelQuickWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { try { val v = build(ctx); ids.forEach { mgr.updateAppWidget(it, v) } } catch (_: Exception) {} }
        }
    }
}
