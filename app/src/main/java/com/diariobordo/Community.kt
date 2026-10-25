package com.diariobordo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Send
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Só o que a pessoa escolhe mostrar: marca, modelo, ano, combustível, km (exatos ou em intervalo) e consumo médio. */
data class PubCar(
    val vid: Long = 0, val brand: String = "", val model: String = "", val year: Int = 0, val fuel: String = "",
    val km: Int = 0, val kmRange: Boolean = false, val avg: Double = 0.0,
)

data class PubProfile(val uid: String, val nick: String, val cars: List<PubCar>, val bio: String = "")

class NickTaken : Exception()
class RateLimited : Exception()

/** Limites para travar abuso: máximo de ações por janela de tempo, guardado no telemóvel. */
private object Throttle {
    private fun prefs() = com.google.firebase.FirebaseApp.getInstance().applicationContext.getSharedPreferences("throttle", 0)

    fun check(key: String, max: Int, windowMs: Long) {
        val p = prefs()
        val now = System.currentTimeMillis()
        val times = (p.getString(key, "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < windowMs }
        if (times.size >= max) throw RateLimited()
        p.edit().putString(key, (times + now).joinToString(",")).apply()
    }
}

class Report(val id: String, val by: String, val about: String, val reason: String, val ts: Long, val last: List<Pair<String, String>>)

object Community {
    val NICK = Regex("^[a-z0-9_.]{3,20}$")
    private val db get() = FirebaseDatabase.getInstance()
    fun uid(): String? = try { FirebaseAuth.getInstance().currentUser?.uid } catch (e: Throwable) { null }

    private fun parse(uid: String, s: DataSnapshot): PubProfile? {
        val nick = s.child("nick").getValue(String::class.java) ?: return null
        val cars = s.child("cars").children.map { c ->
            PubCar(
                vid = c.child("vid").getValue(Long::class.java) ?: 0,
                brand = c.child("brand").getValue(String::class.java) ?: "",
                model = c.child("model").getValue(String::class.java) ?: "",
                year = (c.child("year").getValue(Long::class.java) ?: 0).toInt(),
                fuel = c.child("fuel").getValue(String::class.java) ?: "",
                km = (c.child("km").getValue(Long::class.java) ?: 0).toInt(),
                kmRange = c.child("kmRange").getValue(Boolean::class.java) ?: false,
                avg = c.child("avg").getValue(Double::class.java) ?: 0.0,
            )
        }
        return PubProfile(uid, nick, cars, s.child("bio").getValue(String::class.java) ?: "")
    }

    suspend fun mine(): PubProfile? {
        val u = uid() ?: return null
        return parse(u, db.getReference("profiles/$u").get().await())
    }

    suspend fun list(prefix: String): List<PubProfile> {
        var q = db.getReference("profiles").orderByChild("nick")
        q = if (prefix.isBlank()) q.limitToFirst(60) else q.startAt(prefix).endAt(prefix + "").limitToFirst(60)
        val me = uid()
        return q.get().await().children.mapNotNull { parse(it.key ?: "", it) }.filter { it.uid != me && it.cars.isNotEmpty() }
    }

    /** Guarda o perfil. Se o nickname já for de outra pessoa, as regras da base de dados recusam e lança NickTaken. */
    suspend fun publish(old: String?, nick: String, cars: List<PubCar>, bio: String = "") {
        val u = uid() ?: error("not signed in")
        if (old != nick) {
            try { db.getReference("usernames/$nick").setValue(u).await() }
            catch (e: Exception) { throw NickTaken() }
        }
        val carMap = cars.mapIndexed { i, c ->
            i.toString() to mapOf(
                "vid" to c.vid, "brand" to c.brand, "model" to c.model, "year" to c.year, "fuel" to c.fuel,
                "km" to c.km, "kmRange" to c.kmRange, "avg" to c.avg,
            )
        }.toMap()
        db.getReference("profiles/$u").setValue(
            mapOf("nick" to nick, "adult" to true, "updatedAt" to ServerValue.TIMESTAMP, "cars" to carMap, "bio" to bio.take(140))
        ).await()
        if (old != null && old != nick) try { db.getReference("usernames/$old").removeValue().await() } catch (_: Exception) {}
    }

    suspend fun delete(nick: String) {
        val u = uid() ?: return
        db.getReference("profiles/$u").removeValue().await()
        db.getReference("usernames/$nick").removeValue().await()
    }

    // ---------- Pedidos, conversas, bloqueios e denúncias ----------
    class Req(val from: String, val nick: String, val status: String)
    class ChatRef(val cid: String, val with: String, val nick: String)
    class ChatMsg(val id: String, val from: String, val text: String, val ts: Long)

    suspend fun requestStatus(to: String): String? {
        val u = uid() ?: return null
        return db.getReference("requests/$to/$u/status").get().await().getValue(String::class.java)
    }

    suspend fun sendRequest(to: String, myNick: String) {
        val u = uid() ?: return
        Throttle.check("req", 15, 24 * 3600_000L)
        db.getReference("requests/$to/$u").setValue(mapOf("nick" to myNick, "status" to "pending", "ts" to ServerValue.TIMESTAMP)).await()
    }

    suspend fun inbox(): List<Req> {
        val u = uid() ?: return emptyList()
        return db.getReference("requests/$u").get().await().children.mapNotNull {
            val st = it.child("status").getValue(String::class.java)
            if (st != "pending") null else Req(it.key ?: "", it.child("nick").getValue(String::class.java) ?: "?", st)
        }
    }

    suspend fun accept(r: Req, myNick: String) {
        val u = uid() ?: return
        val cid = "${r.from}_$u"
        db.getReference("chats/$cid").setValue(mapOf("a" to r.from, "b" to u)).await()
        db.getReference("userChats/$u/$cid").setValue(mapOf("with" to r.from, "nick" to r.nick)).await()
        db.getReference("userChats/${r.from}/$cid").setValue(mapOf("with" to u, "nick" to myNick)).await()
        db.getReference("requests/$u/${r.from}/status").setValue("accepted").await()
    }

    suspend fun decline(r: Req) {
        val u = uid() ?: return
        db.getReference("requests/$u/${r.from}/status").setValue("declined").await()
    }

    suspend fun chats(): List<ChatRef> {
        val u = uid() ?: return emptyList()
        return db.getReference("userChats/$u").get().await().children.map {
            ChatRef(it.key ?: "", it.child("with").getValue(String::class.java) ?: "", it.child("nick").getValue(String::class.java) ?: "?")
        }
    }

    /** Ouve as mensagens em tempo real. Devolve a função que pára de ouvir. */
    fun listen(cid: String, onMsgs: (List<ChatMsg>) -> Unit, onError: () -> Unit): () -> Unit {
        val q = db.getReference("chats/$cid/messages").orderByKey().limitToLast(200)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                onMsgs(s.children.map {
                    ChatMsg(
                        it.key ?: "", it.child("from").getValue(String::class.java) ?: "",
                        it.child("text").getValue(String::class.java) ?: "", it.child("ts").getValue(Long::class.java) ?: 0L,
                    )
                })
            }
            override fun onCancelled(e: DatabaseError) { onError() }
        }
        q.addValueEventListener(l)
        return { q.removeEventListener(l) }
    }

    suspend fun send(cid: String, text: String) {
        val u = uid() ?: return
        Throttle.check("msg", 20, 60_000L)
        db.getReference("chats/$cid/messages").push().setValue(mapOf("from" to u, "text" to text.take(1000), "ts" to ServerValue.TIMESTAMP)).await()
    }

    suspend fun isBlocked(other: String): Boolean {
        val u = uid() ?: return false
        return db.getReference("blocks/$u/$other").get().await().exists()
    }

    suspend fun setBlocked(other: String, on: Boolean) {
        val u = uid() ?: return
        val r = db.getReference("blocks/$u/$other")
        if (on) r.setValue(true).await() else r.removeValue().await()
    }

    suspend fun report(cid: String, other: String, reason: String, msgs: List<ChatMsg>) {
        val u = uid() ?: return
        Throttle.check("report", 5, 24 * 3600_000L)
        db.getReference("reports").push().setValue(
            mapOf(
                "by" to u, "about" to other, "cid" to cid, "reason" to reason, "ts" to ServerValue.TIMESTAMP,
                "last" to msgs.takeLast(10).map { mapOf("from" to it.from, "text" to it.text) },
            )
        ).await()
    }

    // ---------- Moderação (só quem estiver em admins/<uid> na base de dados) ----------
    suspend fun isAdmin(): Boolean {
        val u = uid() ?: return false
        return try { db.getReference("admins/$u").get().await().getValue(Boolean::class.java) == true } catch (e: Exception) { false }
    }

    suspend fun reports(): List<Report> =
        db.getReference("reports").orderByChild("ts").limitToLast(50).get().await().children.map { r ->
            Report(
                r.key ?: "", r.child("by").getValue(String::class.java) ?: "", r.child("about").getValue(String::class.java) ?: "",
                r.child("reason").getValue(String::class.java) ?: "", r.child("ts").getValue(Long::class.java) ?: 0L,
                r.child("last").children.map { (it.child("from").getValue(String::class.java) ?: "") to (it.child("text").getValue(String::class.java) ?: "") },
            )
        }.reversed()

    suspend fun nickOf(uid: String): String =
        db.getReference("profiles/$uid/nick").get().await().getValue(String::class.java) ?: uid.take(6)

    suspend fun dismiss(id: String) { db.getReference("reports/$id").removeValue().await() }

    /** Tira o perfil do ar e impede-o de voltar a publicar. */
    suspend fun ban(uid: String) {
        db.getReference("bans/$uid").setValue(true).await()
        try { db.getReference("profiles/$uid").removeValue().await() } catch (_: Exception) {}
    }

    suspend fun deleteAll(nick: String) {
        val u = uid() ?: return
        try { db.getReference("userChats/$u").removeValue().await() } catch (_: Exception) {}
        delete(nick)
    }
}

fun kmText(c: PubCar): String =
    if (c.kmRange) "${c.km / 1000}–${c.km / 1000 + 10}" + t(" mil km", "k km") else fint(c.km) + " km"

/** Mostra o perfil de alguém (só leitura). */
@Composable
private fun PublicCars(p: PubProfile) {
    p.cars.forEach { c ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val b = Brand.of(c.brand)
                BrandMark(b, 44.dp, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("${b.label} ${c.model}".trim(), fontWeight = FontWeight.Bold)
                    Text(listOf(if (c.year > 0) c.year.toString() else "", c.fuel).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(kmText(c), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    if (c.avg > 0) Text(fmt(c.avg) + " L/100 km", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitySheet(cars: List<CarSummary>, signedIn: Boolean, onAccount: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<PubProfile?>(null) }
    var screen by remember { mutableStateOf("list") }   // list | edit | view
    var viewing by remember { mutableStateOf<PubProfile?>(null) }
    var q by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<PubProfile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<List<Community.Req>>(emptyList()) }
    var chatList by remember { mutableStateOf<List<Community.ChatRef>>(emptyList()) }
    var chat by remember { mutableStateOf<Community.ChatRef?>(null) }
    var admin by remember { mutableStateOf(false) }

    fun reloadSocial() {
        scope.launch {
            try { pending = Community.inbox() } catch (_: Exception) {}
            try { chatList = Community.chats() } catch (_: Exception) {}
        }
    }

    fun reload() {
        scope.launch {
            busy = true
            try { users = Community.list(q.trim().lowercase()); msg = null }
            catch (e: Exception) { msg = t("Não foi possível carregar a lista.", "Could not load the list.") }
            busy = false
        }
    }
    LaunchedEffect(signedIn) {
        if (signedIn) {
            try { me = Community.mine() } catch (_: Exception) {}
            reload()
            reloadSocial()
            admin = Community.isAdmin()
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                !signedIn -> {
                    Text(t("Comunidade", "Community"), style = MaterialTheme.typography.headlineSmall)
                    Text(t("Entra com a tua conta Google para veres outros condutores e criares o teu perfil.", "Sign in with your Google account to see other drivers and create your profile."))
                    Button(onClick = { onClose(); onAccount() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(tr("Entrar com Google")) }
                }
                screen == "view" && viewing != null -> {
                    val p = viewing!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { screen = "list" }) { Icon(Icons.Filled.ArrowBack, t("Voltar", "Back")) }
                        Text("@${p.nick}", style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (p.bio.isNotBlank()) Text(p.bio, style = MaterialTheme.typography.bodyMedium)
                        PublicCars(p)
                        ContactButton(p, me, chatList, onOpen = { chat = it; screen = "chat" }, onChanged = { reloadSocial() })
                        Text(
                            t("Os dados são declarados pelo dono e não estão verificados.", "Data is declared by the owner and not verified."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                screen == "inbox" -> InboxScreen(
                    pending, me, onBack = { screen = "list" },
                    onChanged = { reloadSocial() },
                    onOpen = { chat = it; screen = "chat" },
                )
                screen == "chats" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { screen = "list" }) { Icon(Icons.Filled.ArrowBack, t("Voltar", "Back")) }
                        Text(t("Conversas", "Chats"), style = MaterialTheme.typography.headlineSmall)
                    }
                    if (chatList.isEmpty()) Text(t("Ainda não tens conversas. Abre um perfil e pede para falar.", "You have no chats yet. Open a profile and ask to talk."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    chatList.forEach { c ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { chat = c; screen = "chat" },
                        ) { Text("@${c.nick}", Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }
                    }
                }
                screen == "admin" -> AdminScreen(onBack = { screen = "list" })
                screen == "chat" && chat != null -> ChatScreen(chat!!, onBack = { screen = "chats" })
                screen == "edit" -> key(cars.size) {
                    ProfileEditor(
                        cars = cars, me = me,
                        onCancel = { screen = "list" },
                        onSaved = { p -> me = p; screen = "list"; reload() },
                        onDeleted = { me = null; screen = "list"; reload() },
                    )
                }
                else -> {
                    Text(t("Comunidade", "Community"), style = MaterialTheme.typography.headlineSmall)
                    if (me == null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(t("Cria o teu perfil para apareceres aqui. Escolhes um nickname e que carros mostras. Só se vê o que quiseres: marca, modelo, ano, combustível, km e consumo médio.", "Create your profile to appear here. You pick a nickname and which cars to show. Only what you choose is visible: brand, model, year, fuel, km and average consumption."))
                                Button(onClick = { screen = "edit" }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(t("Criar perfil", "Create profile")) }
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { screen = "edit" }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(t("O meu perfil", "My profile") + " (@${me!!.nick})") }
                    }
                    if (me != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { reloadSocial(); screen = "inbox" }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(t("Pedidos", "Requests") + if (pending.isNotEmpty()) " (${pending.size})" else "")
                        }
                        OutlinedButton(onClick = { reloadSocial(); screen = "chats" }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(t("Conversas", "Chats") + if (chatList.isNotEmpty()) " (${chatList.size})" else "")
                        }
                    }
                    if (admin) OutlinedButton(onClick = { screen = "admin" }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(t("Painel de denúncias", "Reports panel")) }
                    OutlinedTextField(
                        value = q, onValueChange = { q = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }; reload() },
                        label = { Text(t("Procurar nickname", "Search nickname")) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (!busy && users.isEmpty() && msg == null) Text(t("Ainda ninguém por aqui. Quando outras pessoas criarem perfil, aparecem nesta lista.", "Nobody here yet. When other people create a profile they show up in this list."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(users, key = { it.uid }) { u ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clickable { viewing = u; screen = "view" },
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("@${u.nick}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(
                                        u.cars.joinToString(" · ") { "${Brand.of(it.brand).label} ${it.model}".trim() },
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(cars: List<CarSummary>, me: PubProfile?, onCancel: () -> Unit, onSaved: (PubProfile) -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nick by remember { mutableStateOf(me?.nick ?: "") }
    var adult by remember { mutableStateOf(me != null) }
    var bio by remember { mutableStateOf(me?.bio ?: "") }
    val share = remember { mutableStateMapOf<Long, Boolean>().apply { cars.forEach { put(it.v.id, me?.cars?.any { m -> m.vid == it.v.id } ?: true) } } }
    val range = remember { mutableStateMapOf<Long, Boolean>().apply { cars.forEach { put(it.v.id, me?.cars?.firstOrNull { m -> m.vid == it.v.id }?.kmRange ?: false) } } }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    val okNick = Community.NICK.matches(nick)

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (me == null) t("Criar perfil", "Create profile") else t("O meu perfil", "My profile"), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = nick, onValueChange = { nick = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }.take(20); err = null },
            label = { Text("Nickname") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = nick.isNotEmpty() && !okNick,
            supportingText = { Text(t("3 a 20 caracteres: letras, números, _ e .", "3 to 20 characters: letters, numbers, _ and .")) },
        )
        OutlinedTextField(
            value = bio, onValueChange = { bio = it.take(140) }, label = { Text(t("Sobre ti (opcional)", "About you (optional)")) },
            supportingText = { Text(t("Visível a todos. Não ponhas contactos nem dados pessoais. ${bio.length}/140", "Visible to everyone. Do not add contact or personal details. ${bio.length}/140")) },
            modifier = Modifier.fillMaxWidth(), maxLines = 3,
        )
        Text(t("Carros que queres mostrar", "Cars you want to show"), fontWeight = FontWeight.SemiBold)
        cars.forEach { c ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.v.name, fontWeight = FontWeight.Bold)
                            Text("${Brand.of(c.v.brand).label} ${c.v.model}".trim(), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = share[c.v.id] == true, onCheckedChange = { share[c.v.id] = it })
                    }
                    if (share[c.v.id] == true) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Mostrar os km em intervalo (mais privado)", "Show km as a range (more private)"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = range[c.v.id] == true, onCheckedChange = { range[c.v.id] = it })
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = adult, onCheckedChange = { adult = it })
            Text(t("Tenho 16 anos ou mais e aceito que os dados escolhidos sejam visíveis a outros utilizadores.", "I am 16 or older and accept that the data I chose is visible to other users."), style = MaterialTheme.typography.bodySmall)
        }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (me != null) OutlinedButton(
                onClick = {
                    if (!armed) armed = true
                    else scope.launch { busy = true; try { Community.deleteAll(me.nick); onDeleted() } catch (e: Exception) { err = t("Não foi possível apagar.", "Could not delete."); busy = false } }
                },
                enabled = !busy, modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(if (armed) tr("Confirmar: apagar") else tr("Apagar")) }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) { Text(tr("Cancelar")) }
            Button(
                enabled = okNick && adult && !busy && share.values.any { it },
                onClick = {
                    scope.launch {
                        busy = true; err = null
                        val list = cars.filter { share[it.v.id] == true }.map { c ->
                            val r = range[c.v.id] == true
                            PubCar(c.v.id, c.v.brand, c.v.model, c.v.year, c.v.fuelType, if (r) c.v.km / 10000 * 10000 else c.v.km, r, c.avg ?: 0.0)
                        }
                        try { Community.publish(me?.nick, nick, list, bio.trim()); onSaved(PubProfile(Community.uid() ?: "", nick, list, bio.trim())) }
                        catch (e: NickTaken) { err = t("Esse nickname já está a ser usado.", "That nickname is already taken.") }
                        catch (e: Exception) { err = t("Não foi possível guardar. Verifica a ligação.", "Could not save. Check your connection.") }
                        busy = false
                    }
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(tr("Guardar")) }
        }
    }
}

@Composable
private fun ContactButton(p: PubProfile, me: PubProfile?, chats: List<Community.ChatRef>, onOpen: (Community.ChatRef) -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember(p.uid) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(p.uid) { status = try { Community.requestStatus(p.uid) } catch (_: Exception) { null } }
    val existing = chats.firstOrNull { it.with == p.uid }
    when {
        me == null -> Text(t("Cria o teu perfil para poderes pedir para falar.", "Create your profile to be able to ask to talk."), style = MaterialTheme.typography.bodySmall)
        existing != null -> Button(onClick = { onOpen(existing) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(t("Abrir conversa", "Open chat")) }
        status == "pending" -> Text(t("Pedido enviado. À espera de resposta.", "Request sent. Waiting for an answer."), fontWeight = FontWeight.SemiBold)
        status == "declined" -> Text(t("O pedido não foi aceite.", "The request was not accepted."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Button(
            enabled = !busy,
            onClick = {
                scope.launch {
                    busy = true; err = null
                    try { Community.sendRequest(p.uid, me.nick); status = "pending"; onChanged() }
                    catch (e: RateLimited) { err = t("Já enviaste muitos pedidos hoje. Tenta amanhã.", "You have sent many requests today. Try again tomorrow.") }
                    catch (e: Exception) { err = t("Não foi possível enviar o pedido.", "Could not send the request.") }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text(t("Pedir para falar", "Ask to talk")) }
    }
    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun InboxScreen(pending: List<Community.Req>, me: PubProfile?, onBack: () -> Unit, onChanged: () -> Unit, onOpen: (Community.ChatRef) -> Unit) {
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, t("Voltar", "Back")) }
        Text(t("Pedidos", "Requests"), style = MaterialTheme.typography.headlineSmall)
    }
    if (pending.isEmpty()) Text(t("Não tens pedidos novos.", "You have no new requests."), color = MaterialTheme.colorScheme.onSurfaceVariant)
    pending.forEach { r ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("@${r.nick}", fontWeight = FontWeight.Bold)
                Text(t("quer falar contigo.", "wants to talk to you."), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { scope.launch { try { Community.decline(r); onChanged() } catch (e: Exception) { err = t("Não foi possível responder.", "Could not respond.") } } },
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) { Text(t("Recusar", "Decline")) }
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    Community.accept(r, me?.nick ?: "")
                                    onChanged()
                                    onOpen(Community.ChatRef("${r.from}_${Community.uid()}", r.from, r.nick))
                                } catch (e: Exception) { err = t("Não foi possível aceitar.", "Could not accept.") }
                            }
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) { Text(t("Aceitar", "Accept")) }
                }
            }
        }
    }
    err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun ChatScreen(c: Community.ChatRef, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val me = Community.uid()
    var msgs by remember { mutableStateOf<List<Community.ChatMsg>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var blocked by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf(false) }
    val list = rememberLazyListState()

    LaunchedEffect(c.cid) { blocked = try { Community.isBlocked(c.with) } catch (_: Exception) { false } }
    DisposableEffect(c.cid) {
        val stop = Community.listen(c.cid, { msgs = it }, { info = t("Sem acesso a esta conversa.", "No access to this chat.") })
        onDispose { stop() }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, t("Voltar", "Back")) }
        Text("@${c.nick}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            scope.launch {
                try { Community.setBlocked(c.with, !blocked); blocked = !blocked } catch (_: Exception) { info = t("Não foi possível alterar o bloqueio.", "Could not change the block.") }
            }
        }) { Text(if (blocked) t("Desbloquear", "Unblock") else t("Bloquear", "Block")) }
        TextButton(onClick = { reporting = true }) { Text(t("Denunciar", "Report")) }
    }
    LazyColumn(Modifier.fillMaxWidth().height(340.dp), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(msgs, key = { it.id }) { m ->
            val mine = m.from == me
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { Text(m.text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            }
        }
    }
    info?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (blocked) Text(t("Bloqueaste esta pessoa. Desbloqueia para voltar a conversar.", "You blocked this person. Unblock to chat again."), style = MaterialTheme.typography.bodySmall)
    else Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(1000) }, modifier = Modifier.weight(1f),
            placeholder = { Text(t("Mensagem", "Message")) }, maxLines = 3,
        )
        IconButton(
            enabled = text.isNotBlank(),
            onClick = {
                val send = text.trim(); text = ""
                scope.launch {
                    try { Community.send(c.cid, send); info = null }
                    catch (e: RateLimited) { info = t("Estás a enviar depressa demais. Espera um pouco.", "You are sending too fast. Wait a moment."); text = send }
                    catch (e: Exception) { info = t("Não foi possível enviar.", "Could not send."); text = send }
                }
            },
        ) { Icon(Icons.Filled.Send, t("Enviar", "Send")) }
    }
    Text(
        t("Só texto. Nunca partilhes passwords nem dados de pagamento.", "Text only. Never share passwords or payment details."),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (reporting) AlertDialog(
        onDismissRequest = { reporting = false },
        title = { Text(t("Denunciar esta conversa", "Report this chat")) },
        text = { Text(t("Enviamos as últimas mensagens para análise. Escolhe o motivo.", "We send the latest messages for review. Pick a reason.")) },
        confirmButton = {
            Column {
                listOf(t("Spam ou publicidade", "Spam or advertising"), t("Assédio ou abuso", "Harassment or abuse"), t("Outro motivo", "Other reason")).forEach { r ->
                    TextButton(onClick = {
                        reporting = false
                        scope.launch {
                            try { Community.report(c.cid, c.with, r, msgs); info = t("Denúncia enviada. Obrigado.", "Report sent. Thank you.") }
                            catch (_: RateLimited) { info = t("Já fizeste várias denúncias hoje.", "You have made several reports today.") }
                            catch (_: Exception) { info = t("Não foi possível denunciar.", "Could not report.") }
                        }
                    }) { Text(r) }
                }
            }
        },
        dismissButton = { TextButton(onClick = { reporting = false }) { Text(tr("Cancelar")) } },
    )
}


@Composable
private fun AdminScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Report>>(emptyList()) }
    var nicks by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var busy by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    fun load() {
        scope.launch {
            busy = true
            try {
                list = Community.reports()
                nicks = (list.map { it.about } + list.map { it.by }).distinct().associateWith { try { Community.nickOf(it) } catch (e: Exception) { it.take(6) } }
                err = null
            } catch (e: Exception) { err = t("Não foi possível carregar.", "Could not load.") }
            busy = false
        }
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, t("Voltar", "Back")) }
            Text(t("Painel de denúncias", "Reports panel"), style = MaterialTheme.typography.headlineSmall)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!busy && list.isEmpty() && err == null) Text(t("Sem denúncias.", "No reports."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { r ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("@${nicks[r.about] ?: "?"}  ←  @${nicks[r.by] ?: "?"}", fontWeight = FontWeight.Bold)
                    Text(r.reason, style = MaterialTheme.typography.bodyMedium)
                    r.last.forEach { (from, text) ->
                        Text((if (from == r.about) "@${nicks[r.about] ?: "?"}: " else "@${nicks[r.by] ?: "?"}: ") + text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { scope.launch { try { Community.dismiss(r.id); load() } catch (_: Exception) {} } }, modifier = Modifier.weight(1f)) { Text(t("Dispensar", "Dismiss")) }
                        Button(onClick = { scope.launch { try { Community.ban(r.about); Community.dismiss(r.id); load() } catch (_: Exception) { err = t("Falhou. Confirma as regras.", "Failed. Check the rules.") } } }, modifier = Modifier.weight(1f)) { Text(t("Banir", "Ban")) }
                    }
                }
            }
        }
    }
}
