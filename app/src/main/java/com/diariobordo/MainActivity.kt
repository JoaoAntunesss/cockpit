package com.diariobordo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Tab(val label: String, val icon: ImageVector, val fab: String) {
    HOME("Início", Icons.Filled.Home, "Registar"),
    FUEL("Combustível", Icons.Filled.LocalGasStation, "Abastecer"),
    MAINT("Manutenção", Icons.Filled.Build, "Intervenção"),
    COSTS("Gastos", Icons.Filled.BarChart, "Despesa"),
    PARK("Parque", Icons.Filled.LocalParking, "Parque"),
    CARDS("Cartões", Icons.Filled.CreditCard, "Novo cartão"),
}

sealed interface Dlg {
    data object None : Dlg
    data object Garage : Dlg
    data object Account : Dlg
    data object KmD : Dlg
    data object Settings : Dlg
    data object Compare : Dlg
    data object Shared : Dlg
    data object Comm : Dlg
    data object Tires : Dlg
    data class TireF(val item: Tire?) : Dlg
    data object Year : Dlg
    data object Sale : Dlg
    data object Nearby : Dlg
    data object MapD : Dlg
    data object New : Dlg
    data class Fuel(val item: Fueling?) : Dlg
    data class Serv(val item: Service?) : Dlg
    data class Exp(val item: Expense?) : Dlg
    data class Rem(val item: Reminder?) : Dlg
    data class DocD(val item: Doc?) : Dlg
    data class Car(val item: Vehicle?) : Dlg
    data class CardV(val item: FuelCard) : Dlg
    data class CardF(val item: FuelCard?) : Dlg
}

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()
    private val launchAction = mutableStateOf<String?>(null)

    override fun onStart() {
        super.onStart()
        AppLock.onReturn(this)
    }

    override fun onStop() {
        super.onStop()
        if (!AppLock.prompting) AppLock.stoppedAt = System.currentTimeMillis()
        CarWidget.refresh(this)
        PriceWidget.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchAction.value = intent.getStringExtra("go")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        UiPrefs.load(this)
        Lang.load(this)
        AlertWorker.schedule(this)
        PriceWorker.schedule(this)
        launchAction.value = intent?.getStringExtra("go")
        AppLock.initial(this)
        setContent { if (AppLock.locked) LockScreen() else AppRoot(vm, launchAction.value) { launchAction.value = null } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainVm, go: String?, onGoDone: () -> Unit) {
    val cars by vm.vehicles.collectAsStateWithLifecycle()
    val fuelCards by vm.cards.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val pr by vm.prices.collectAsStateWithLifecycle()
    val near by vm.nearby.collectAsStateWithLifecycle()
    val tires by vm.tires.collectAsStateWithLifecycle()
    val acc by vm.account.collectAsStateWithLifecycle()
    val cmp by vm.compare.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var dlg by remember { mutableStateOf<Dlg>(Dlg.None) }
    var formKey by remember { mutableIntStateOf(0) }
    var evMode by remember { mutableStateOf(false) }
    var onboarded by remember { mutableStateOf(ctx.getSharedPreferences("diario", 0).getBoolean("onboarded", false)) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        val sp = ctx.getSharedPreferences("diario", 0)
        val today = java.time.LocalDate.now().toEpochDay()
        if (sp.getLong("upd_day", 0) != today) {
            sp.edit().putLong("upd_day", today).apply()
            try {
                Updater.check()?.let {
                    if (snack.showSnackbar(t("Há uma versão nova: ${it.version}", "New version available: ${it.version}"), t("Ver", "View"), duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) dlg = Dlg.Settings
                }
            } catch (_: Exception) {}
        }
    }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val msg = try {
                val json = Cloud.snapshotJson(ctx)
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                t("Cópia guardada.", "Backup saved.")
            } catch (e: Exception) { t("Não consegui guardar a cópia.", "Could not save the backup.") }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    val backupLoader = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val msg = try {
                val txt = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }
                if (Cloud.restoreJson(ctx, txt)) t("Cópia restaurada.", "Backup restored.") else t("Este ficheiro não é uma cópia do Cockpit.", "This file is not a Cockpit backup.")
            } catch (e: Exception) { t("Não consegui ler o ficheiro.", "Could not read the file.") }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cur = vm.data.value
        if (uri != null && cur != null) scope.launch {
            val msg = try {
                val txt = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "" }
                vm.importCsv(cur, txt)
            } catch (e: Exception) { t("Não consegui ler o ficheiro.", "Could not read the file.") }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    val d = data

    // Atalhos do ícone da app (carregar muito tempo no ícone)
    LaunchedEffect(go, d != null) {
        if (go != null && d != null) {
            dlg = when (go) {
                "fuel" -> Dlg.Fuel(null)
                "serv" -> Dlg.Serv(null)
                "exp" -> Dlg.Exp(null)
                "map" -> { evMode = d?.let { isEv(it.vehicle.fuelType) } ?: false; Dlg.MapD }
                else -> Dlg.None
            }
            onGoDone()
        }
    }

    // Pede licença para notificações (Android 13+) quando já há um carro
    LaunchedEffect(d != null) {
        if (d != null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Cópia automática na nuvem, 4 segundos depois da última alteração
    LaunchedEffect(d, cars, acc.email, acc.auto) {
        if (acc.email != null && acc.auto && cars.isNotEmpty()) { delay(4000); vm.autoUpload() }
    }

    // A marca do carro escolhido define as cores de toda a app
    GarageTheme(Brand.of(d?.vehicle?.brand)) {
        fun savedNote() { scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(t("Guardado", "Saved"), duration = SnackbarDuration.Short) } }
        fun savedFuel(f: Fueling, prev: List<Fueling>, type: String) {
            val m = fuelNote(f, prev, type)
            if (m == null) savedNote() else scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m, duration = SnackbarDuration.Short) }
        }
        fun undoable(msg: String, undo: () -> Unit, expire: () -> Unit = {}) {
            scope.launch {
                snack.currentSnackbarData?.dismiss()
                val r = snack.showSnackbar(msg, actionLabel = t("Desfazer", "Undo"), duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) undo() else expire()
            }
        }
        fun removeWithUndo(x: Any) {
            val km = d?.vehicle?.km ?: 0
            val msg = t("Registo apagado", "Record deleted")
            when (x) {
                is Fueling -> { vm.softDelete(x); undoable(msg, { vm.saveFueling(x) }) }
                is Service -> { vm.softDelete(x); undoable(msg, { vm.saveService(x, false) }, { deleteFile(x.image) }) }
                is Expense -> { vm.softDelete(x); undoable(msg, { vm.saveExpense(x, km, false) }, { deleteFile(x.image) }) }
            }
        }
        val a = Actions(
            fuel = { dlg = Dlg.Fuel(it) }, serv = { dlg = Dlg.Serv(it) }, exp = { dlg = Dlg.Exp(it) },
            remDone = { r -> d?.let {
                vm.markDone(r, it.vehicle.km)
                undoable(t("«${r.name}» marcado como feito aos ${fint(it.vehicle.km)} km, hoje.", "“${r.name}” marked as done at ${fint(it.vehicle.km)} km, today."), { vm.saveReminder(r) })
            } },
            remEdit = { dlg = Dlg.Rem(it) }, remNew = { dlg = Dlg.Rem(null) },
            goTab = { tab = it },
            nearby = { evMode = false; dlg = Dlg.Nearby },
            nearbyEv = { evMode = true; dlg = Dlg.Nearby },
            doc = { dlg = Dlg.DocD(it) },
            confirmKm = { km -> d?.let { vm.setKm(it.vehicle, km) } },
            fixKm = { dlg = Dlg.KmD },
            tires = { dlg = Dlg.Tires },
            create = { k -> dlg = when (k) { "fuel" -> Dlg.Fuel(null); "serv" -> Dlg.Serv(null); else -> Dlg.Exp(null) } },
            del = { removeWithUndo(it) },
            editCar = { d?.let { dlg = Dlg.Car(it.vehicle) } },
        )
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = {
                        if (d != null) {
                            val b = Brand.of(d.vehicle.brand)
                            Row(
                                Modifier.clip(RoundedCornerShape(28.dp)).clickable { dlg = Dlg.Garage }
                                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(6.dp, 6.dp, 12.dp, 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                BrandMark(b, 36.dp, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                                Text(d.vehicle.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                        } else Text("Cockpit")
                    },
                    actions = {
                        IconButton(onClick = { vm.loadCompare(); dlg = Dlg.Comm }) { Icon(Icons.Filled.Groups, t("Comunidade", "Community")) }
                        if (d != null) IconButton(onClick = { evMode = isEv(d.vehicle.fuelType); dlg = Dlg.MapD }) { Icon(Icons.Filled.Map, tr("Mapa")) }
                        IconButton(onClick = { dlg = Dlg.Account }) {
                            Icon(if (acc.email != null) Icons.Filled.CloudDone else Icons.Filled.AccountCircle, tr("Conta e nuvem"))
                        }
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, tr("Mais")) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (d != null) DropdownMenuItem(
                                    text = { Text(tr("Aspeto do carro")) }, leadingIcon = { Icon(Icons.Filled.Palette, null) },
                                    onClick = { menu = false; dlg = Dlg.Car(d.vehicle) },
                                )
                                if (d != null) DropdownMenuItem(
                                    text = { Text(tr("Comparar carros")) }, leadingIcon = { Icon(Icons.Filled.Balance, null) },
                                    onClick = { menu = false; vm.loadCompare(); dlg = Dlg.Compare },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Definições e exportar")) }, leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    onClick = { menu = false; dlg = Dlg.Settings },
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                if (d != null) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, null) }, label = { Text(tr(t.label), maxLines = 1) }, alwaysShowLabel = false)
                    }
                }
            },
            floatingActionButton = {
                if (d != null && tab != Tab.PARK) FloatingActionButton(
                    onClick = {
                        dlg = when (tab) {
                            Tab.HOME -> Dlg.New
                            Tab.FUEL -> Dlg.Fuel(null)
                            Tab.MAINT -> Dlg.Serv(null)
                            Tab.COSTS -> Dlg.Exp(null)
                            Tab.PARK -> Dlg.None
                            Tab.CARDS -> Dlg.CardF(null)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, tr(tab.fab)) }
            },
        ) { pad ->
            if (d == null) {
                Column(
                    Modifier.fillMaxSize().padding(pad).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.DirectionsCar, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text(tr("A tua garagem está vazia"), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(tr("Adiciona o primeiro carro. A app ganha as cores da marca."), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { dlg = Dlg.Car(null) }, modifier = Modifier.height(52.dp)) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("Adicionar carro"))
                    }
                }
            } else Box(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.11f), Color.Transparent), endY = 1300f)),
                contentAlignment = Alignment.TopCenter,
            ) { AnimatedContent(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxHeight(),
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(280, delayMillis = 70)) + slideInHorizontally(tween(340)) { it / 12 * dir }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(280)) { -it / 12 * dir })
                },
                label = "tab",
            ) { tb ->
                when (tb) {
                    Tab.HOME -> HomeScreen(d, a, pad)
                    Tab.FUEL -> FuelScreen(d, a, pad, pr, vm::refreshPrices)
                    Tab.MAINT -> MaintScreen(d, a, pad)
                    Tab.COSTS -> CostsScreen(d, pad, { dlg = Dlg.Year }, { removeWithUndo(it) }) { dlg = Dlg.Exp(it) }
                    Tab.PARK -> ParkScreen(d.vehicle, pad)
                    Tab.CARDS -> CardsScreen(fuelCards, pad) { dlg = Dlg.CardV(it) }
                }
            } }
        }

        val close = { EntryMemo.date = null; dlg = Dlg.None }
        when (val x = dlg) {
            Dlg.None -> {}
            Dlg.KmD -> d?.let { dd ->
                KmDialog(dd.vehicle.km, { km -> vm.setKm(dd.vehicle, km); close() }, close)
            }
            Dlg.Settings -> SettingsSheet(
                hasCar = d != null,
                onPdf = { d?.let { dd -> try { Export.share(ctx, Export.pdf(ctx, dd), "application/pdf") } catch (_: Exception) {} } },
                onCsv = { d?.let { dd -> try { Export.share(ctx, Export.csv(ctx, dd), "text/csv") } catch (_: Exception) {} } },
                onImport = { importPicker.launch("*/*") },
                onSale = { dlg = Dlg.Sale },
                onBackup = { backupSaver.launch("cockpit_backup_${java.time.LocalDate.now()}.json") },
                onRestoreFile = { backupLoader.launch("*/*") },
                onCompare = { vm.loadCompare(); dlg = Dlg.Compare },
                onShared = { dlg = Dlg.Shared },
                onClose = close,
            )
            Dlg.Compare -> CompareSheet(cmp, close)
            Dlg.Shared -> d?.let { SharedSheet(it.vehicle, close) }
            Dlg.Account -> {
                val act = ctx as? Activity
                AccountSheet(
                    acc, onSignIn = { act?.let { vm.signIn(it) } }, onSignOut = vm::signOut, onUpload = vm::uploadNow,
                    onRestore = vm::restoreNow, onAuto = vm::setAuto, onDelete = vm::deleteAccount, onClose = close,
                )
            }
            Dlg.Garage -> GarageSheet(cars, d?.vehicle?.id, onPick = { vm.select(it.id); tab = Tab.HOME; close() }, onAdd = { dlg = Dlg.Car(null) }, onClose = close)
            Dlg.New -> NewSheet({ dlg = Dlg.Fuel(null) }, { dlg = Dlg.Serv(null) }, { dlg = Dlg.Exp(null) }, close)
            Dlg.Tires -> d?.let { TiresSheet(it.vehicle, tires, { dlg = Dlg.TireF(null) }, { tx -> dlg = Dlg.TireF(tx) }, close) }
            is Dlg.TireF -> d?.let { TireForm(it.vehicle, x.item, { tx -> vm.saveTire(tx); dlg = Dlg.Tires }, { tx -> vm.delete(tx); dlg = Dlg.Tires }, { dlg = Dlg.Tires }) }
            Dlg.Year -> d?.let { YearSheet(it, close) }
            Dlg.Sale -> d?.let { SaleDialog(it, tires, close) }
            Dlg.Comm -> CommunitySheet(cmp, acc.email != null, { dlg = Dlg.Account }, close)
            Dlg.MapD -> d?.let { MapDialog(it.vehicle, evMode, near, { vm.findNearby(it.vehicle.fuelType, evMode) }, close) }
            Dlg.Nearby -> d?.let {
                NearbySheet(it.vehicle, evMode, near, FuelPrices.slugFor(it.vehicle.fuelType)?.let { s -> pr.map[s]?.avg }, { vm.findNearby(it.vehicle.fuelType, evMode) }, { dlg = Dlg.MapD }, close)
            }
            is Dlg.Car -> VehicleForm(
                x.item, canDelete = cars.size > 1,
                onSave = { vm.saveVehicle(it); if (x.item == null) tab = Tab.HOME; close() },
                onDelete = { vm.deleteVehicle(it); close() }, onClose = close,
            )
            is Dlg.Fuel -> d?.let { key(formKey) { FuelForm(it.vehicle, x.item, { f -> vm.saveFueling(f); close(); savedFuel(f, it.fuelings, it.vehicle.fuelType) }, { f -> removeWithUndo(f); close() }, close, FuelPrices.slugFor(it.vehicle.fuelType)?.let { s -> pr.map[s]?.avg }, onSaveMore = { f -> vm.saveFueling(f); formKey++; savedFuel(f, it.fuelings, it.vehicle.fuelType) }, lastKm = it.fuelings.maxOfOrNull { f -> f.km }, onAnchor = { f -> vm.saveFueling(f) }, others = it.fuelings) } }
            is Dlg.Serv -> d?.let { key(formKey) { ServiceForm(it.vehicle, x.item, { s -> vm.saveService(s, x.item == null); close(); savedNote() }, { s -> removeWithUndo(s); close() }, close, onSaveMore = { s -> vm.saveService(s, true); formKey++; savedNote() }) } }
            is Dlg.Exp -> d?.let { key(formKey) { ExpenseForm(it.vehicle, x.item, { e -> vm.saveExpense(e, it.vehicle.km, x.item == null); close(); savedNote() }, { e -> removeWithUndo(e); close() }, close, onSaveMore = { e -> vm.saveExpense(e, it.vehicle.km, true); formKey++; savedNote() }) } }
            is Dlg.CardV -> CardView(x.item, { dlg = Dlg.CardF(x.item) }, close)
            is Dlg.CardF -> CardForm(x.item, { vm.saveCard(it); close() }, { vm.delete(it); close() }, close)
            is Dlg.DocD -> d?.let { DocForm(it.vehicle, x.item, { dc -> vm.saveDoc(dc); close() }, { dc -> vm.delete(dc); close() }, close) }
            is Dlg.Rem -> d?.let { ReminderForm(it.vehicle, x.item, { r -> vm.saveReminder(r); close() }, { r -> vm.delete(r); close() }, close) }
        }
        AnimatedVisibility(!onboarded, enter = fadeIn(), exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 6 }) {
            Onboarding { ctx.getSharedPreferences("diario", 0).edit().putBoolean("onboarded", true).apply(); onboarded = true }
        }
    }
}


/** Momento de recompensa ao guardar: diz o consumo deste abastecimento e como se compara com a tua média. */
private fun fuelNote(f: Fueling, prev: List<Fueling>, type: String): String? {
    if (f.electric) return null
    val before = fuelStats(prev.filter { it.id != f.id }.filter { !it.electric })
    val after = fuelStats((prev.filter { it.id != f.id } + f).filter { !it.electric })
    val row = after.rows.firstOrNull { it.f.date == f.date && it.f.km == f.km && it.f.total == f.total && it.cons != null } ?: return null
    val cons = row.cons ?: return null
    val base = t("Guardado · ", "Saved · ") + fmt(cons) + " " + consUnit(type) + " km"
    val avg = before.avg ?: return base
    return when {
        cons < avg * 0.97 -> base + t(" · melhor que a tua média 🎉", " · better than your average 🎉")
        cons > avg * 1.08 -> base + t(" · acima da tua média", " · above your average")
        else -> base + t(" · na tua média", " · right on your average")
    }
}
