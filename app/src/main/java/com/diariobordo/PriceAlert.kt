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
import java.util.concurrent.TimeUnit

/**
 * De 6 em 6 horas compara o preço médio nacional do combustível do carro escolhido.
 * Se descer 1 cêntimo ou mais desde a última referência, avisa. Só corre se a opção estiver ligada nas Definições.
 */
class PriceWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!KmPrefs.priceOn(ctx)) return Result.success()
        val dao = AppDb.get(ctx).dao()
        val cars = dao.vehicles().first()
        val sel = ctx.getSharedPreferences("diario", 0).getLong("sel", 0)
        val car = cars.firstOrNull { it.id == sel } ?: cars.firstOrNull() ?: return Result.success()
        val slug = UiPrefs.run { load(ctx); fav }.ifBlank { FuelPrices.slugFor(car.fuelType) ?: "" }
        if (slug.isBlank()) return Result.success()
        val now = try { FuelPrices.parse(FuelPrices.download()).firstOrNull { it.slug == slug } } catch (e: Exception) { null }
            ?: return Result.retry()

        val p = ctx.getSharedPreferences("pricealert", 0)
        val ref = p.getFloat("ref_$slug", 0f).toDouble()
        if (ref <= 0.0 || now.avg > ref) { p.edit().putFloat("ref_$slug", now.avg.toFloat()).apply(); return Result.success() }
        if (ref - now.avg >= 0.0099) {
            p.edit().putFloat("ref_$slug", now.avg.toFloat()).apply()
            notify(ctx, now, ref - now.avg)
        }
        return Result.success()
    }

    private fun notify(ctx: Context, f: FuelPrice, drop: Double) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT >= 26) {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel("precos", t("Preço do combustível", "Fuel price"), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cents = String.format(PT, "%.1f", drop * 100)
        val n = NotificationCompat.Builder(ctx, "precos")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(t("${f.name} baixou", "${f.name} dropped"))
            .setContentText(t("Menos $cents cêntimos: média de ${String.format(PT, "%.3f", f.avg)} €/L", "Down $cents cents: average ${String.format(PT, "%.3f", f.avg)} €/L"))
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(7001, n) } catch (_: SecurityException) {}
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<PriceWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("preco", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
