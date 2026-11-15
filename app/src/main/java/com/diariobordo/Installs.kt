package com.diariobordo

import android.content.Context
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.UUID

/**
 * Contagem anónima de instalações: um identificador aleatório (gerado aqui, não ligado à conta nem ao
 * telemóvel), a versão da app e a data do último uso. No máximo uma escrita por dia. Serve só para
 * sabermos quantas pessoas usam a app; não envia carros, registos nem localização.
 */
object Installs {
    private const val PREFS = "installs"
    private const val DAY = 24 * 60 * 60 * 1000L

    fun ping(ctx: Context) {
        if (!Cloud.configured(ctx)) return
        try {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - p.getLong("last", 0L) < DAY) return
            var id = p.getString("id", null)
            val first = id == null
            if (id == null) {
                id = UUID.randomUUID().toString()
                p.edit().putString("id", id).apply()
            }
            val data = mutableMapOf<String, Any>("last" to ServerValue.TIMESTAMP, "v" to BuildConfig.VERSION_NAME)
            if (first) data["first"] = ServerValue.TIMESTAMP
            FirebaseDatabase.getInstance().getReference("installs/$id").updateChildren(data)
                .addOnSuccessListener { p.edit().putLong("last", now).apply() }
        } catch (_: Throwable) {
        }
    }
}
