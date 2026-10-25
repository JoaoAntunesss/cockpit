package com.diariobordo

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Bloqueio opcional: pede o PIN, o padrão ou a impressão digital do próprio telemóvel. */
object AppLock {
    var locked by mutableStateOf(false)
    var prompting = false
    var stoppedAt = 0L

    private fun sp(c: Context) = c.getSharedPreferences("diario", 0)
    fun enabled(c: Context) = sp(c).getBoolean("lock_on", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("lock_on", v).apply()
    fun secure(c: Context): Boolean = (c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    fun initial(c: Context) { locked = enabled(c) && secure(c) }

    /** Ao voltar à app: se esteve mais de 30 segundos fora, volta a pedir. */
    fun onReturn(c: Context) {
        if (enabled(c) && secure(c) && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 30_000) locked = true
    }
}

@Composable
fun LockScreen() {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        AppLock.prompting = false
        if (r.resultCode == Activity.RESULT_OK) { AppLock.locked = false; AppLock.stoppedAt = 0L }
    }
    fun prompt() {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val i = km.createConfirmDeviceCredentialIntent(t("Cockpit bloqueado", "Cockpit locked"), t("Confirma que és tu para abrir a app.", "Confirm it's you to open the app."))
        if (i == null) { AppLock.locked = false; return }
        AppLock.prompting = true
        try { launcher.launch(i) } catch (_: Exception) { AppLock.prompting = false }
    }
    LaunchedEffect(Unit) { prompt() }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Cockpit", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(t("A app está bloqueada.", "The app is locked."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { prompt() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(t("Desbloquear", "Unlock")) }
            }
        }
    }
}
