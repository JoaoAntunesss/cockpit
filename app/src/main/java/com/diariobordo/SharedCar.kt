package com.diariobordo

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SharedData(
    val fuelings: List<Fueling> = emptyList(),
    val services: List<Service> = emptyList(),
    val expenses: List<Expense> = emptyList(),
)

/**
 * Carro partilhado entre contas. Quem tem o código vê e junta registos (abastecimentos, intervenções, despesas).
 * A junção é por união: o que não existe aqui é acrescentado, o que já existe não se repete.
 * Apagar um registo NÃO se propaga aos outros.
 */
object SharedCar {
    private fun prefs(c: Context) = c.getSharedPreferences("shared", 0)
    fun code(c: Context, carId: Long): String = prefs(c).getString("car_$carId", "") ?: ""
    fun setCode(c: Context, carId: Long, code: String) = prefs(c).edit().putString("car_$carId", code).apply()

    private fun uid() = FirebaseAuth.getInstance().currentUser?.uid ?: error(tr("Sem sessão iniciada."))
    private fun ref(code: String) = FirebaseDatabase.getInstance().getReference("shared/$code")

    private fun newCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        return (1..8).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun create(ctx: Context, car: Vehicle): String = withContext(Dispatchers.IO) {
        val u = uid()
        val code = newCode()
        ref(code).child("name").setValue(car.name).await()
        ref(code).child("members/$u").setValue(true).await()
        setCode(ctx, car.id, code)
        sync(ctx, car.id)
        code
    }

    suspend fun join(ctx: Context, car: Vehicle, codeIn: String): Int = withContext(Dispatchers.IO) {
        val code = codeIn.trim().uppercase()
        require(code.length == 8) { t("O código tem 8 letras e números.", "The code has 8 letters and numbers.") }
        ref(code).child("members/${uid()}").setValue(true).await()
        setCode(ctx, car.id, code)
        sync(ctx, car.id)
    }

    fun leave(ctx: Context, carId: Long) = setCode(ctx, carId, "")

    /** Junta o que há na nuvem com o que há aqui e devolve quantos registos novos chegaram. */
    suspend fun sync(ctx: Context, carId: Long): Int = withContext(Dispatchers.IO) {
        val code = code(ctx, carId)
        require(code.isNotBlank()) { t("Este carro não está partilhado.", "This car is not shared.") }
        val dao = AppDb.get(ctx).dao()
        val remote = ref(code).child("data").get().await().getValue(String::class.java)
            ?.let { try { Gson().fromJson(it, SharedData::class.java) } catch (_: Exception) { null } } ?: SharedData()
        val lf = dao.allFuel().filter { it.vehicleId == carId }
        val ls = dao.allServices().filter { it.vehicleId == carId }
        val le = dao.allExpenses().filter { it.vehicleId == carId }
        val nf = remote.fuelings.orEmpty().filter { n -> lf.none { it.date == n.date && it.km == n.km && it.total == n.total } }
        val ns = remote.services.orEmpty().filter { n -> ls.none { it.date == n.date && it.title == n.title && it.parts == n.parts } }
        val ne = remote.expenses.orEmpty().filter { n -> le.none { it.date == n.date && it.type == n.type && it.amount == n.amount } }
        nf.forEach { dao.saveFueling(it.copy(id = 0, vehicleId = carId)) }
        ns.forEach { dao.saveService(it.copy(id = 0, vehicleId = carId, image = "")) }
        ne.forEach { dao.saveExpense(it.copy(id = 0, vehicleId = carId, image = "")) }
        val top = (nf.maxOfOrNull { it.km } ?: 0).coerceAtLeast(ns.maxOfOrNull { it.km } ?: 0)
        if (top > 0) dao.bumpKm(carId, top)
        val union = SharedData(
            lf.map { it.copy(id = 0, vehicleId = 0) } + nf.map { it.copy(id = 0, vehicleId = 0) },
            ls.map { it.copy(id = 0, vehicleId = 0, image = "") } + ns.map { it.copy(id = 0, vehicleId = 0, image = "") },
            le.map { it.copy(id = 0, vehicleId = 0, image = "") } + ne.map { it.copy(id = 0, vehicleId = 0, image = "") },
        )
        ref(code).child("data").setValue(Gson().toJson(union)).await()
        ref(code).child("updatedAt").setValue(ServerValue.TIMESTAMP).await()
        nf.size + ns.size + ne.size
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSheet(car: Vehicle, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(SharedCar.code(ctx, car.id)) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val signed = Cloud.configured(ctx) && Cloud.email(ctx) != null
    fun run(block: suspend () -> String) {
        busy = true; msg = null
        scope.launch {
            msg = try { block() } catch (e: Exception) { t("Não foi possível: ", "Could not: ") + (e.message ?: "") }
            busy = false
        }
    }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Carro partilhado", "Shared car"), style = MaterialTheme.typography.headlineSmall)
            Text(
                t("Partilha ${car.name} com outra pessoa (família, parceiro). Cada um regista no seu telemóvel e carrega em Sincronizar para juntar tudo. Apagar um registo não se propaga ao outro.",
                    "Share ${car.name} with someone else (family, partner). Each logs on their own phone and taps Sync to merge. Deleting a record does not carry over."),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!signed) Text(t("Primeiro entra na tua conta (menu Conta).", "Sign in to your account first (Account menu)."), color = MaterialTheme.colorScheme.error)
            else if (code.isBlank()) {
                Button(onClick = { run { code = SharedCar.create(ctx, car); t("Carro partilhado. Dá este código à outra pessoa: $code", "Car shared. Give this code to the other person: $code") } },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(t("Partilhar este carro", "Share this car")) }
                Text(t("Ou entra num carro que já foi partilhado contigo:", "Or join a car already shared with you:"), style = MaterialTheme.typography.bodySmall)
                TxtField(t("Código (8 caracteres)", "Code (8 characters)"), input, { input = it.uppercase().take(8) })
                OutlinedButton(onClick = { run { val n = SharedCar.join(ctx, car, input); code = SharedCar.code(ctx, car.id); t("Ligado. Chegaram $n registos novos.", "Joined. $n new records arrived.") } },
                    enabled = !busy && input.length == 8, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(t("Entrar com código", "Join with code")) }
                Text(t("Atenção: os registos do carro que escolheres aqui juntam-se aos que já existem.", "Note: the records merge into the car you have open."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(t("Código: $code", "Code: $code"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Button(onClick = { run { val n = SharedCar.sync(ctx, car.id); t("Sincronizado. Chegaram $n registos novos.", "Synced. $n new records arrived.") } },
                    enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(t("Sincronizar agora", "Sync now")) }
                TextButton(onClick = { SharedCar.leave(ctx, car.id); code = "" }) { Text(t("Deixar de partilhar neste telemóvel", "Stop sharing on this phone")) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
