package com.diariobordo

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Estado da conta, para o ecrã. */
data class AccountState(
    val configured: Boolean = false,
    val email: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val auto: Boolean = false,
)

class DocImg(val docId: Long = 0, val b64: String = "", val kind: String = "doc")

/** Cópia completa dos teus dados. Fica em diarioBordo/<o teu utilizador> na Realtime Database. */
class Snapshot(
    val v: Int = 1,
    val vehicles: List<Vehicle> = emptyList(),
    val fuelings: List<Fueling> = emptyList(),
    val services: List<Service> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val docs: List<Doc> = emptyList(),
    val images: List<DocImg> = emptyList(),
    val cards: List<FuelCard> = emptyList(),
    val tires: List<Tire> = emptyList(),
)

object Cloud {
    /** Só funciona se puseres o google-services.json na pasta app (ver LEIAME). */
    fun configured(ctx: Context): Boolean = try { FirebaseApp.getApps(ctx).isNotEmpty() } catch (e: Throwable) { false }

    private fun webClientId(ctx: Context): String? {
        val id = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        return if (id == 0) null else ctx.getString(id)
    }

    fun email(ctx: Context): String? = if (configured(ctx)) FirebaseAuth.getInstance().currentUser?.email else null

    suspend fun signIn(activity: Activity) {
        val web = webClientId(activity) ?: error(tr("Falta o cliente web. Ativa o login Google na Firebase e volta a descarregar o google-services.json."))
        val option = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(web).build()
        val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val res = CredentialManager.create(activity).getCredential(activity, req)
        val cred = res.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
        } else error(tr("Resposta de login inesperada."))
    }

    suspend fun signOut(ctx: Context) {
        FirebaseAuth.getInstance().signOut()
        try { CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest()) } catch (_: Exception) {}
    }

    /** Apaga a cópia na nuvem, o perfil público, os nicknames e a conta. As mensagens já enviadas continuam visíveis para quem as recebeu. */
    suspend fun deleteAccount(ctx: Context) = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: error(tr("Sem sessão iniciada."))
        val uid = user.uid
        val db = FirebaseDatabase.getInstance()
        val nick = try { db.getReference("profiles/$uid/nick").get().await().getValue(String::class.java) } catch (_: Exception) { null }
        for (path in listOf("diarioBordo/$uid", "userChats/$uid", "profiles/$uid", "blocks/$uid") + listOfNotNull(nick?.let { "usernames/$it" })) {
            try { db.getReference(path).removeValue().await() } catch (_: Exception) {}
        }
        user.delete().await()
        try { CredentialManager.create(ctx).clearCredentialState(ClearCredentialStateRequest()) } catch (_: Exception) {}
    }

    private fun ref(): com.google.firebase.database.DatabaseReference {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error(tr("Sem sessão iniciada."))
        return FirebaseDatabase.getInstance().getReference("diarioBordo/$uid")
    }

    private fun encodeImage(path: String): String? {
        if (path.isBlank() || !File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var s = 1
        while (bounds.outWidth / s > 1400 || bounds.outHeight / s > 1400) s *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 72, bos)
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    /** Todos os dados (e as fotos) em JSON. Serve para a nuvem e para a cópia em ficheiro. */
    suspend fun snapshotJson(ctx: Context): String = withContext(Dispatchers.IO) {
        val dao = AppDb.get(ctx).dao()
        val docs = dao.allDocs()
        val svcs = dao.allServices()
        val exps = dao.allExpenses()
        val imgs = docs.mapNotNull { d -> encodeImage(d.image)?.let { DocImg(d.id, it, "doc") } } +
            svcs.mapNotNull { s -> encodeImage(s.image)?.let { DocImg(s.id, it, "svc") } } +
            exps.mapNotNull { e -> encodeImage(e.image)?.let { DocImg(e.id, it, "exp") } }
        val snap = Snapshot(
            1, dao.allVehicles(), dao.allFuel(), svcs.map { it.copy(image = "") }, exps.map { it.copy(image = "") }, dao.allReminders(),
            docs.map { it.copy(image = "") }, imgs, dao.allCards(), dao.allTires(),
        )
        Gson().toJson(snap)
    }

    suspend fun upload(ctx: Context) = withContext(Dispatchers.IO) {
        ref().setValue(mapOf("snapshot" to snapshotJson(ctx), "updatedAt" to ServerValue.TIMESTAMP)).await()
    }

    /** Substitui TUDO o que está neste telemóvel pela cópia da nuvem. Devolve false se não houver cópia. */
    suspend fun restore(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val json = ref().get().await().child("snapshot").getValue(String::class.java) ?: return@withContext false
        restoreJson(ctx, json)
    }

    /** Repõe uma cópia (nuvem ou ficheiro). Devolve false se o conteúdo não for uma cópia válida. */
    suspend fun restoreJson(ctx: Context, json: String): Boolean = withContext(Dispatchers.IO) {
        val snap = try { Gson().fromJson(json, Snapshot::class.java) } catch (e: Exception) { null } ?: return@withContext false
        if (snap.vehicles.isNullOrEmpty()) return@withContext false
        val db = AppDb.get(ctx)
        db.clearAllTables()
        val dao = db.dao()
        snap.vehicles.forEach { dao.saveVehicle(it) }
        snap.fuelings.forEach { dao.saveFueling(it) }
        val dir = File(ctx.filesDir, "docs").apply { mkdirs() }
        fun pathFor(kind: String, id: Long): String {
            val img = snap.images.firstOrNull { it.kind == kind && it.docId == id } ?: return ""
            return File(dir, "restored_${kind}_${id}_${System.currentTimeMillis()}.jpg")
                .also { it.writeBytes(Base64.decode(img.b64, Base64.NO_WRAP)) }.absolutePath
        }
        snap.services.forEach { dao.saveService(it.copy(image = pathFor("svc", it.id))) }
        snap.expenses.forEach { dao.saveExpense(it.copy(image = pathFor("exp", it.id))) }
        snap.reminders.forEach { dao.saveReminder(it) }
        snap.docs.forEach { dao.saveDoc(it.copy(image = pathFor("doc", it.id))) }
        snap.cards.orEmpty().forEach { dao.saveCard(it) }
        snap.tires.orEmpty().forEach { dao.saveTire(it) }
        true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    st: AccountState,
    onSignIn: () -> Unit, onSignOut: () -> Unit, onUpload: () -> Unit, onRestore: () -> Unit, onAuto: (Boolean) -> Unit, onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        var armed by remember { mutableStateOf(false) }
        var delArmed by remember { mutableStateOf(false) }
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tr("Conta e cópia na nuvem"), style = MaterialTheme.typography.headlineSmall)
            when {
                !st.configured -> {
                    Text(
                        tr("O login com Google ainda não está ligado. Falta pôr o ficheiro google-services.json na pasta app do projeto. Os passos estão no LEIAME.md. Sem isto, os teus dados ficam só neste telemóvel."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                st.email == null -> {
                    Text(tr("Entra com a tua conta Google para guardar os dados na nuvem e recuperá-los noutro telemóvel."), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onSignIn, enabled = !st.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Filled.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text(tr("Entrar com Google"))
                    }
                }
                else -> {
                    Text("Sessão iniciada: ${st.email}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Guardar sozinho"))
                            Text(tr("Envia uma cópia pouco depois de cada alteração."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = st.auto, onCheckedChange = onAuto)
                    }
                    Button(onClick = onUpload, enabled = !st.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text(tr("Guardar agora na nuvem"))
                    }
                    OutlinedButton(
                        onClick = { if (armed) { armed = false; onRestore() } else armed = true },
                        enabled = !st.busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(8.dp))
                        Text(if (armed) tr("Confirmar: substituir os dados deste telemóvel") else tr("Restaurar da nuvem"))
                    }
                    TextButton(onClick = onSignOut, enabled = !st.busy) { Text(tr("Terminar sessão")) }
                    TextButton(onClick = { if (delArmed) { delArmed = false; onDelete() } else delArmed = true }, enabled = !st.busy) {
                        Text(
                            if (delArmed) t("Confirmar: apagar conta e dados da nuvem", "Confirm: delete account and cloud data")
                            else t("Apagar conta e dados da nuvem", "Delete account and cloud data"),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (st.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            st.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
