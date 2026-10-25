package com.diariobordo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

/** Atualização pela própria app: lê a última Release do GitHub e instala o APK. */
object Updater {
    private const val REPO = "JoaoAntunesss/cockpit"

    private fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun newer(remote: String, local: String): Boolean {
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Devolve a versão nova, ou null se já estás na última. Lança erro se não houver rede. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode == 404) return@withContext null
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val tag = j.optString("tag_name")
            val assets = j.optJSONArray("assets") ?: return@withContext null
            var apk = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
            }
            if (apk.isBlank() || !newer(tag, BuildConfig.VERSION_NAME)) null
            else UpdateInfo(tag.removePrefix("v"), apk, j.optString("body"))
        } finally { c.disconnect() }
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "cockpit.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        try {
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(32 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; onProgress(p) } }
                    }
                }
            }
        } finally { c.disconnect() }
        out
    }

    /** Abre o instalador. Se a permissão de instalar não estiver dada, abre a definição certa e devolve false. */
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}

@Composable
fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(-1) }
    var file by remember { mutableStateOf<File?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Atualizações", "Updates"), style = MaterialTheme.typography.titleSmall)
        Text(
            t("Versão instalada: ${BuildConfig.VERSION_NAME}. A app procura a versão mais recente no GitHub e instala-a por cima, sem perderes dados.",
                "Installed version: ${BuildConfig.VERSION_NAME}. The app looks for the latest version on GitHub and installs it over this one, keeping your data."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val i = info
        if (i == null) OutlinedButton(
            onClick = {
                busy = true; status = ""
                scope.launch {
                    try {
                        val r = Updater.check()
                        info = r
                        status = if (r == null) t("Já tens a versão mais recente.", "You already have the latest version.") else ""
                    } catch (e: Exception) { status = t("Sem ligação ou GitHub indisponível.", "No connection or GitHub unavailable.") }
                    busy = false
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(t("Procurar atualizações", "Check for updates")) }
        else {
            Text(t("Versão nova: ${i.version}", "New version: ${i.version}"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            if (i.notes.isNotBlank()) Text(i.notes.take(400), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = {
                    busy = true; status = ""; progress = 0
                    scope.launch {
                        try {
                            val f = file ?: Updater.download(ctx, i.apkUrl) { progress = it }.also { file = it }
                            val ok = Updater.install(ctx, f)
                            status = if (ok) "" else t("Dá licença a esta app para instalar e volta a tocar em Instalar.", "Allow this app to install, then tap Install again.")
                        } catch (e: Exception) { status = t("Falhou o descarregamento. Tenta outra vez.", "Download failed. Try again.") }
                        busy = false; progress = -1
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (file != null) t("Instalar", "Install") else t("Descarregar e instalar", "Download and install")) }
            if (progress >= 0) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        if (busy && progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}
