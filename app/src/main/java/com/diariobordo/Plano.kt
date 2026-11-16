package com.diariobordo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Plano: 24 horas grátis, depois 1 €/mês ou 10 €/ano.
 * O servidor da AW (appEstado) confirma a conta Google e procura o pagamento no Stripe.
 * Sem internet vale a última resposta guardada (o teste acaba à hora certa; o pago tem 7 dias de folga).
 * Só se aplica quando a app tem Firebase e a pessoa entrou com a conta Google.
 */
object Plano {
    private const val API = "https://europe-west1-aw-sites-e31c3.cloudfunctions.net/"

    /** Muda sempre que a app volta ao ecrã (por exemplo, depois de pagar no navegador). */
    val retomar = mutableIntStateOf(0)

    data class Estado(
        val estado: String, val fimTeste: Long = 0L, val ate: Long = 0L,
        val link: String = "", val linkAno: String = "", val email: String = "",
        val cancelada: Boolean = false,
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("plano", Context.MODE_PRIVATE)

    private fun inicio(ctx: Context): Long {
        val p = prefs(ctx)
        val v = p.getLong("inicio", 0L)
        if (v > 0L) return v
        val agora = System.currentTimeMillis()
        p.edit().putLong("inicio", agora).apply()
        return agora
    }

    private fun deJson(j: JSONObject) = Estado(
        j.optString("estado"), j.optLong("fimTeste"), j.optLong("ate"),
        j.optString("link"), j.optString("linkAno"), j.optString("email"), j.optBoolean("cancelada"),
    )

    fun guardado(ctx: Context, email: String?): Estado? {
        val s = prefs(ctx).getString("ultimo", null) ?: return null
        val p = runCatching { deJson(JSONObject(s)) }.getOrNull() ?: return null
        return if (email != null && p.email == email) p else null
    }

    suspend fun pedir(ctx: Context, pago: Boolean): Estado? = withContext(Dispatchers.IO) {
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null
            val tok = user.getIdToken(false).await().token ?: return@withContext null
            val url = URL(API + "appEstado?app=cockpit&inicio=" + inicio(ctx) + (if (pago) "&pago=1" else ""))
            val c = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 25_000
                setRequestProperty("Authorization", "Bearer $tok")
            }
            try {
                if (c.responseCode != 200) return@withContext null
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                j.put("email", user.email ?: "")
                prefs(ctx).edit().putString("ultimo", j.toString()).apply()
                deJson(j)
            } finally { c.disconnect() }
        } catch (e: Exception) { null }
    }

    /** Link do portal do Stripe para gerir ou cancelar (ou a mensagem de erro). */
    suspend fun gerir(): Pair<String?, String?> = withContext(Dispatchers.IO) {
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return@withContext null to t("Entra com a tua conta Google.", "Sign in with your Google account.")
            val tok = user.getIdToken(false).await().token ?: return@withContext null to t("Entra com a tua conta Google.", "Sign in with your Google account.")
            val c = (URL(API + "appGerir?app=cockpit").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 25_000
                setRequestProperty("Authorization", "Bearer $tok")
            }
            try {
                val ok = c.responseCode == 200
                val corpo = (if (ok) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
                val j = runCatching { JSONObject(corpo) }.getOrNull()
                if (ok) j?.optString("url")?.takeIf { it.isNotBlank() } to null
                else null to (j?.optString("erro")?.takeIf { it.isNotBlank() } ?: t("Não foi possível abrir a gestão da subscrição.", "Could not open subscription management."))
            } finally { c.disconnect() }
        } catch (e: Exception) { null to t("Sem ligação. Tenta outra vez daqui a pouco.", "No connection. Try again in a moment.") }
    }

    fun bloqueia(p: Estado?): Boolean {
        if (p == null) return false
        val agora = System.currentTimeMillis()
        return when (p.estado) {
            "pagar" -> true
            "teste" -> p.fimTeste in 1 until agora
            "pago" -> p.ate > 0L && agora > p.ate + 7 * 86_400_000L
            else -> false
        }
    }
}

/** Ecrã por cima da app quando o dia grátis acabou e ainda não há pagamento. */
@Composable
fun PlanoGate(email: String, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    var plano by remember(email) { mutableStateOf(Plano.guardado(ctx, email)) }
    val retomar by Plano.retomar
    LaunchedEffect(email, retomar) { Plano.pedir(ctx, false)?.let { plano = it } }
    val p = plano
    if (p != null && Plano.bloqueia(p)) PlanoPagar(p, onSignOut) { plano = it }
}

@Composable
private fun PlanoPagar(p: Plano.Estado, onSignOut: () -> Unit, onNovo: (Plano.Estado) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var aConfirmar by remember { mutableStateOf(false) }
    fun abrir(url: String) {
        if (url.isBlank()) { Toast.makeText(ctx, t("Sem ligação. Tenta outra vez daqui a pouco.", "No connection. Try again in a moment."), Toast.LENGTH_LONG).show(); return }
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }
    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(132.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.DirectionsCar, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(32.dp))
            Text(t("O teu dia grátis acabou", "Your free day is over"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                t("O Cockpit custa 1 € por mês, ou 10 € por ano (dois meses grátis). Cancelas quando quiseres. Os teus registos ficam guardados.",
                    "Cockpit costs €1 a month, or €10 a year (two months free). Cancel whenever you want. Your records stay saved."),
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = { abrir(p.link) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(t("Pagar 1 € por mês", "Pay €1 a month"))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { abrir(p.linkAno) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(t("10 € por ano", "€10 a year"))
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                enabled = !aConfirmar,
                onClick = {
                    aConfirmar = true
                    scope.launch {
                        val r = Plano.pedir(ctx, true)
                        aConfirmar = false
                        if (r != null) onNovo(r)
                        if (r == null || Plano.bloqueia(r))
                            Toast.makeText(ctx, t("Ainda não encontrámos o pagamento. Se acabaste de pagar, espera um minuto e tenta outra vez.",
                                "Payment not found yet. If you just paid, wait a minute and try again."), Toast.LENGTH_LONG).show()
                    }
                },
            ) { Text(if (aConfirmar) t("A confirmar…", "Checking…") else t("Já paguei", "I have paid")) }
            TextButton(onClick = onSignOut) { Text(t("Entrar com outra conta", "Use another account")) }
        }
    }
}

/** Secção "Subscrição" na folha da conta: estado do plano e botões para pagar, gerir ou cancelar. */
@Composable
fun PlanoSecao(email: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var plano by remember(email) { mutableStateOf(Plano.guardado(ctx, email)) }
    var aAbrir by remember { mutableStateOf(false) }
    LaunchedEffect(email) { Plano.pedir(ctx, false)?.let { plano = it } }
    val fmt = remember { java.text.SimpleDateFormat("d/MM/yyyy HH:mm", java.util.Locale.getDefault()) }
    fun abrir(url: String) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {} }
    val p = plano
    val texto = when (p?.estado) {
        "pago" -> if (p.ate > 0L) {
            if (p.cancelada) t("Cancelada. Tens acesso até ", "Cancelled. You have access until ") + fmt.format(java.util.Date(p.ate)) + "."
            else t("Paga. Renova a ", "Paid. Renews on ") + fmt.format(java.util.Date(p.ate)) + "."
        } else t("Paga.", "Paid.")
        "teste" -> t("Período grátis até ", "Free until ") + fmt.format(java.util.Date(p.fimTeste)) + t(". Depois 1 € por mês ou 10 € por ano.", ". Then €1 a month or €10 a year.")
        "gratis" -> t("Grátis nesta conta.", "Free on this account.")
        "pagar" -> t("Sem subscrição ativa.", "No active subscription.")
        else -> t("A confirmar…", "Checking…")
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Subscrição", "Subscription"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (p?.estado == "teste" || p?.estado == "pagar") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = p.link.isNotBlank(), onClick = { abrir(p.link) }) { Text(t("1 € por mês", "€1 a month")) }
                OutlinedButton(enabled = p.linkAno.isNotBlank(), onClick = { abrir(p.linkAno) }) { Text(t("10 € por ano", "€10 a year")) }
            }
        }
        if (p?.estado == "pago") {
            FilledTonalButton(
                enabled = !aAbrir,
                onClick = {
                    aAbrir = true
                    scope.launch {
                        val (url, erro) = Plano.gerir()
                        aAbrir = false
                        if (url != null) abrir(url) else Toast.makeText(ctx, erro, Toast.LENGTH_LONG).show()
                    }
                },
            ) { Text(if (aAbrir) t("A abrir…", "Opening…") else t("Gerir ou cancelar", "Manage or cancel")) }
        }
    }
}
