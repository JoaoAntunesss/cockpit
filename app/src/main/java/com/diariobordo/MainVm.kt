package com.diariobordo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    private val prefs = app.getSharedPreferences("diario", 0)

    val vehicles: StateFlow<List<Vehicle>> =
        dao.vehicles().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cards: StateFlow<List<FuelCard>> = dao.cards().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun saveCard(x: FuelCard) = viewModelScope.launch { dao.saveCard(x) }
    fun delete(x: FuelCard) = viewModelScope.launch { dao.delete(x) }

    private val selId = MutableStateFlow(prefs.getLong("sel", -1L))

    private val current: StateFlow<Vehicle?> =
        combine(vehicles, selId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val data: StateFlow<CarData?> = current.flatMapLatest { v ->
        if (v == null) flowOf(null)
        else combine(dao.fuelings(v.id), dao.services(v.id), dao.expenses(v.id), dao.reminders(v.id), dao.docs(v.id)) { f, s, e, r, dc ->
            CarData(v, f, s, e, r, dc)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tires: StateFlow<List<Tire>> = current.flatMapLatest { v ->
        if (v == null) flowOf(emptyList()) else dao.tires(v.id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun saveTire(x: Tire) = viewModelScope.launch { dao.saveTire(x) }
    fun delete(x: Tire) = viewModelScope.launch { dao.delete(x) }

    private val _prices = MutableStateFlow(
        PricesState(map = FuelPrices.parse(prefs.getString("prices_json", null)).associateBy { it.slug })
    )
    val prices: StateFlow<PricesState> = _prices

    fun refreshPrices() {
        viewModelScope.launch {
            _prices.value = _prices.value.copy(loading = true, error = false)
            try {
                val body = FuelPrices.download()
                val list = FuelPrices.parse(body)
                if (list.isEmpty()) error("resposta vazia")
                prefs.edit().putString("prices_json", body).apply()
                _prices.value = PricesState(list.associateBy { it.slug })
                PriceWidget.refresh(getApplication())
            } catch (e: Exception) {
                _prices.value = _prices.value.copy(loading = false, error = true)
            }
        }
    }

    init { refreshPrices() }

    // ---------- Conta Google e cópia na nuvem ----------
    private val ctxApp = app
    private val _account = MutableStateFlow(
        AccountState(
            configured = Cloud.configured(app), email = Cloud.email(app), auto = prefs.getBoolean("cloud_auto", false),
        )
    )
    val account: StateFlow<AccountState> = _account

    private fun cloudJob(ok: String? = null, block: suspend () -> String?) {
        viewModelScope.launch {
            _account.value = _account.value.copy(busy = true, message = null)
            try {
                val msg = block()
                _account.value = _account.value.copy(busy = false, message = msg ?: ok, email = Cloud.email(ctxApp))
            } catch (e: Exception) {
                _account.value = _account.value.copy(busy = false, message = t("Não correu bem: ", "Something went wrong: ") + (e.message ?: t("erro desconhecido", "unknown error")), email = Cloud.email(ctxApp))
            }
        }
    }

    fun signIn(activity: android.app.Activity) = cloudJob {
        Cloud.signIn(activity)
        // Telemóvel novo: se ainda não há carros e existe cópia na nuvem, traz tudo.
        if (vehicles.value.isEmpty() && Cloud.restore(ctxApp)) tr("Sessão iniciada. Dados recuperados da nuvem.") else tr("Sessão iniciada.")
    }

    fun signOut() = cloudJob(tr("Sessão terminada.")) { Cloud.signOut(ctxApp); null }
    fun deleteAccount() = cloudJob(t("Conta e dados da nuvem apagados. Os dados deste telemóvel ficam cá.", "Account and cloud data deleted. The data on this phone stays here.")) {
        try { Cloud.deleteAccount(ctxApp); null }
        catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            t("Por segurança, termina a sessão, entra outra vez e repete.", "For security, sign out, sign in again and repeat.")
        }
    }
    fun uploadNow() = cloudJob(tr("Cópia guardada na nuvem.")) { Cloud.upload(ctxApp); null }
    fun restoreNow() = cloudJob { if (Cloud.restore(ctxApp)) tr("Dados restaurados da nuvem.") else tr("Ainda não há cópia na nuvem.") }

    fun setAuto(on: Boolean) {
        prefs.edit().putBoolean("cloud_auto", on).apply()
        _account.value = _account.value.copy(auto = on)
    }

    /** Envio em silêncio quando a opção «Guardar sozinho» está ligada. */
    fun autoUpload() {
        viewModelScope.launch { try { Cloud.upload(ctxApp) } catch (_: Exception) {} }
    }

    // ---------- Postos perto de mim ----------
    private val _nearby = MutableStateFlow(NearbyState())
    val nearby: StateFlow<NearbyState> = _nearby
    private val stationCache = HashMap<Int, Pair<Long, List<Station>>>()

    fun findNearby(fuelType: String, ev: Boolean = isEv(fuelType)) {
        viewModelScope.launch {
            if (ev) {
                _nearby.value = NearbyState(loading = true)
                val here = kotlinx.coroutines.withTimeoutOrNull(15_000L) { Loc.current(getApplication()) }
                if (here == null) { _nearby.value = NearbyState(locError = true); return@launch }
                try {
                    val list = kotlinx.coroutines.withTimeout(45_000L) { Chargers.download(here.first, here.second)
                        .map { it.copy(distKm = distKm(here.first, here.second, it.lat, it.lng)) }.sortedBy { it.distKm } }
                    _nearby.value = NearbyState(stations = list, done = true)
                } catch (e: Exception) { _nearby.value = NearbyState(error = true) }
                return@launch
            }
            val fid = Stations.fuelIdFor(fuelType)
            if (fid == null) { _nearby.value = NearbyState(unsupported = true); return@launch }
            _nearby.value = NearbyState(loading = true)
            val here = Loc.current(getApplication())
            if (here == null) { _nearby.value = NearbyState(locError = true); return@launch }
            try {
                val cached = stationCache[fid]
                val list = if (cached != null && System.currentTimeMillis() - cached.first < 30 * 60_000L) cached.second
                else Stations.download(fid).also { stationCache[fid] = System.currentTimeMillis() to it }
                val near = list.map { it.copy(distKm = distKm(here.first, here.second, it.lat, it.lng)) }.filter { it.distKm <= 50 }
                _nearby.value = NearbyState(stations = near, done = true)
            } catch (e: Exception) {
                _nearby.value = NearbyState(error = true)
            }
        }
    }

    fun select(id: Long) {
        prefs.edit().putLong("sel", id).apply()
        selId.value = id
    }

    fun saveVehicle(v: Vehicle) = viewModelScope.launch {
        val id = dao.saveVehicle(v)
        if (v.id == 0L) {
            // Carro novo: cria logo um aviso de óleo para a app já ter algo útil
            dao.saveReminder(
                Reminder(
                    vehicleId = id, name = "Óleo e filtro", category = "Óleo e filtros",
                    everyKm = 15000, everyMonths = 12, lastKm = v.km, lastDate = LocalDate.now().toEpochDay(),
                )
            )
            select(id)
        }
    }

    fun deleteVehicle(v: Vehicle) = viewModelScope.launch {
        dao.deleteVehicle(v.id)
        vehicles.value.firstOrNull { it.id != v.id }?.let { select(it.id) }
    }

    fun saveFueling(x: Fueling) = viewModelScope.launch {
        dao.saveFueling(x)
        dao.bumpKm(x.vehicleId, x.km)
        CarWidget.refresh(ctxApp)
    }

    fun saveService(x: Service, isNew: Boolean) = viewModelScope.launch {
        dao.saveService(x)
        dao.bumpKm(x.vehicleId, x.km)
        if (isNew) dao.resetReminders(x.vehicleId, x.category, x.km, x.date)
        CarWidget.refresh(ctxApp)
    }

    fun saveExpense(x: Expense, carKm: Int, isNew: Boolean) = viewModelScope.launch {
        dao.saveExpense(x)
        if (isNew) dao.resetReminders(x.vehicleId, x.type, carKm, x.date)
        CarWidget.refresh(ctxApp)
    }

    /** Junta o histórico de um CSV, sem repetir o que já existe. Devolve o texto para mostrar ao utilizador. */
    suspend fun importCsv(d: CarData, text: String): String {
        val p = Importer.parse(text, d.vehicle.id)
        val f = p.fuel.filter { n -> d.fuelings.none { it.date == n.date && it.km == n.km && it.total == n.total } }
        val s = p.serv.filter { n -> d.services.none { it.date == n.date && it.title == n.title && it.parts == n.parts } }
        val e = p.exp.filter { n -> d.expenses.none { it.date == n.date && it.type == n.type && it.amount == n.amount } }
        f.forEach { dao.saveFueling(it) }
        s.forEach { dao.saveService(it) }
        e.forEach { dao.saveExpense(it) }
        val top = (f.maxOfOrNull { it.km } ?: 0).coerceAtLeast(s.maxOfOrNull { it.km } ?: 0)
        if (top > 0) dao.bumpKm(d.vehicle.id, top)
        val dup = (p.fuel.size - f.size) + (p.serv.size - s.size) + (p.exp.size - e.size)
        return t("Importados: ${f.size} abastecimentos, ${s.size} intervenções, ${e.size} despesas.", "Imported: ${f.size} refuels, ${s.size} services, ${e.size} expenses.") +
            (if (dup > 0) t(" Repetidos ignorados: $dup.", " Duplicates skipped: $dup.") else "") +
            (if (p.skipped > 0) t(" Linhas ignoradas: ${p.skipped}.", " Rows skipped: ${p.skipped}.") else "")
    }

    fun saveReminder(x: Reminder) = viewModelScope.launch { dao.saveReminder(x) }

    fun markDone(r: Reminder, carKm: Int) = viewModelScope.launch {
        dao.saveReminder(r.copy(lastKm = carKm, lastDate = LocalDate.now().toEpochDay()))
    }

    fun saveDoc(x: Doc) = viewModelScope.launch { dao.saveDoc(x) }
    fun delete(x: Doc) = viewModelScope.launch {
        dao.delete(x)
        if (x.image.isNotBlank()) try { java.io.File(x.image).delete() } catch (_: Exception) {}
    }

    /** Apaga só a linha (sem apagar a foto), para o "Desfazer" poder repor tudo. */
    fun softDelete(x: Fueling) = viewModelScope.launch { dao.delete(x) }
    fun softDelete(x: Service) = viewModelScope.launch { dao.delete(x) }
    fun softDelete(x: Expense) = viewModelScope.launch { dao.delete(x) }

    fun delete(x: Fueling) = viewModelScope.launch { dao.delete(x) }
    fun delete(x: Service) = viewModelScope.launch { dao.delete(x); deleteFile(x.image) }
    fun delete(x: Expense) = viewModelScope.launch { dao.delete(x); deleteFile(x.image) }
    fun delete(x: Reminder) = viewModelScope.launch { dao.delete(x) }

    // ---------- Km ----------
    fun setKm(v: Vehicle, km: Int) = viewModelScope.launch {
        dao.saveVehicle(v.copy(km = km))
        CarWidget.refresh(ctxApp)
        KmPrefs.setDay(ctxApp, v.id)
    }

    // ---------- Comparar carros ----------
    private val _compare = MutableStateFlow<List<CarSummary>>(emptyList())
    val compare: StateFlow<List<CarSummary>> = _compare

    fun loadCompare() = viewModelScope.launch {
        _compare.value = emptyList()
        val vs = dao.allVehicles()
        val f = dao.allFuel(); val s = dao.allServices(); val e = dao.allExpenses()
        _compare.value = vs.map { v ->
            val d = CarData(v, f.filter { it.vehicleId == v.id }, s.filter { it.vehicleId == v.id }, e.filter { it.vehicleId == v.id }, emptyList())
            val total = monthly(d, 12).sumOf { it.total }
            val span = kmSpan(d, 12)
            CarSummary(v, fuelStats(d.liquid()).avg, if (span >= 50) total / span else null, total / 12, total, span)
        }
    }
}
