package com.diariobordo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---------- Peças reutilizáveis ----------

/** Data do último «Guardar e adicionar outro», para o histórico antigo ficar na mesma data. */
object EntryMemo { var date: LocalDate? = null; var byTrip: Boolean = false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSaveNext: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        var armed by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().imePadding()) {
            // Cabeçalho fixo
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Fechar")) }
            }
            // Campos: só esta parte desliza
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
            // Ações sempre visíveis
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (onDelete != null) {
                        OutlinedButton(
                            onClick = { if (armed) onDelete() else armed = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) { Text(if (armed) tr("Confirmar: apagar") else tr("Apagar")) }
                    }
                    Button(onClick = onSave, modifier = Modifier.weight(if (onDelete != null) 1.4f else 1f).height(52.dp)) { Text(tr("Guardar")) }
                }
                if (onSaveNext != null) {
                    TextButton(onClick = onSaveNext, modifier = Modifier.fillMaxWidth()) { Text(t("Guardar e adicionar outro", "Save and add another")) }
                }
            }
        }
    }
}

/** Grupo de campos num cartão, com título. Todos os formulários usam o mesmo. */
@Composable
fun FormSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Interruptor com título e explicação curta. */
@Composable
fun FormSwitch(title: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (desc != null) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Campo de escolha (data, lista) com o mesmo aspeto dos campos de texto. */
@Composable
private fun FieldBox(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NumField(
    label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier, decimal: Boolean = false, isError: Boolean = false, hint: String? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        isError = isError, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        supportingText = { if (isError) Text(tr("Confirma este valor")) else if (!hint.isNullOrBlank()) Text(hint) },
    )
}

@Composable
fun TxtField(
    label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier, isError: Boolean = false,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        isError = isError, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        supportingText = { if (isError) Text(tr("Preenche este campo")) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate, modifier: Modifier = Modifier, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FieldBox(label, dateLabel(value.toEpochDay()), Icons.Filled.DateRange, modifier) { open = true }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(tr("Cancelar")) } },
        ) { DatePicker(state = st) }
    }
}

@Composable
fun PickField(label: String, value: String, options: List<String>, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        FieldBox(label, value.ifBlank { tr("Nenhuma") }, Icons.Filled.ArrowDropDown) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o.ifBlank { tr("Nenhuma") }) }, onClick = { onChange(o); open = false })
            }
        }
    }
}

// ---------- Abastecimento ----------

@Composable
fun FuelForm(
    car: Vehicle, item: Fueling?, onSave: (Fueling) -> Unit, onDelete: (Fueling) -> Unit, onClose: () -> Unit,
    todayAvg: Double? = null,
    onSaveMore: ((Fueling) -> Unit)? = null,
    lastKm: Int? = null,
    onAnchor: ((Fueling) -> Unit)? = null,
    others: List<Fueling> = emptyList(),
) {
    var date by remember { mutableStateOf(item?.let { LocalDate.ofEpochDay(it.date) } ?: EntryMemo.date ?: LocalDate.now()) }
    var byTrip by remember { mutableStateOf(item == null && EntryMemo.byTrip) }
    val baseKm = lastKm ?: car.km
    var km by remember { mutableStateOf(item?.km?.toString() ?: "") }
    var liters by remember { mutableStateOf(item?.let { dec(it.liters) } ?: "") }
    var total by remember { mutableStateOf(item?.let { dec(it.total) } ?: "") }
    var station by remember { mutableStateOf(item?.station ?: "") }
    var full by remember { mutableStateOf(item?.full ?: false) }
    var tried by remember { mutableStateOf(false) }
    var since by remember { mutableStateOf("") }
    val firstRef = item == null && onAnchor != null && lastKm == null

    val rawKm = km.toIntOrNull()
    val kmV = if (byTrip) rawKm?.takeIf { it > 0 }?.let { baseKm + it } else rawKm
    val lV = num(liters)
    val tV = num(total)
    val kmBad = tried && (kmV == null || kmV <= 0)
    val phev = isPhev(car.fuelType)
    var elec by remember { mutableStateOf(item?.electric ?: false) }
    val ev = isEv(car.fuelType) || (phev && elec)
    val u = if (ev) "kWh" else "L"
    val maxQ = if (phev && elec) 150.0 else car.tankL * (if (ev) 1.3 else 1.1)
    val lBad = tried && (lV == null || lV <= 0 || lV > maxQ)
    val tBad = tried && (tV == null || tV <= 0)
    val ppl = if (lV != null && tV != null && lV > 0) tV / lV else null

    val doSave: (Boolean) -> Unit = { more ->
        if (more) EntryMemo.date = date
            tried = true
            if (kmV != null && kmV > 0 && lV != null && lV > 0 && lV <= maxQ && tV != null && tV > 0) {
                val sv = since.toIntOrNull()
                if (firstRef && sv != null && sv in 1 until kmV && !(phev && elec)) {
                    onAnchor?.invoke(Fueling(0, car.id, date.toEpochDay(), kmV - sv, 0.0, 0.0, true, t("Ponto de partida", "Starting point"), false))
                }
                (if (more) (onSaveMore ?: onSave) else onSave)(Fueling(item?.id ?: 0, car.id, date.toEpochDay(), kmV, lV, tV, full, station.trim(), phev && elec))
            }
    }
    FormSheet(
        title = if (item == null) tr("Novo abastecimento") else tr("Editar abastecimento"),
        onClose = onClose,
        onDelete = item?.let { { onDelete(it) } },
        onSaveNext = if (item == null && onSaveMore != null) ({ doSave(true) }) else null,
        onSave = { doSave(false) },
    ) {
        if (item == null) ReceiptButton { r ->
            r.liters?.let { liters = dec(it) }
            r.total?.let { total = dec(it) }
            r.date?.let { date = it }
        }
        if (phev) FormSection {
            FormSwitch(
                t("Carga elétrica (kWh)", "Electric charge (kWh)"),
                t("Liga se isto foi uma carga da bateria. Fica fora do consumo de combustível.", "Turn on if this was a battery charge. It stays out of the fuel consumption."),
                elec,
            ) { elec = it }
        }
        FormSection(t("Quando e quilómetros", "When and distance")) {
            DateField(tr("Data"), date) { date = it }
            if (item == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = !byTrip, onClick = { byTrip = false; EntryMemo.byTrip = false }, label = { Text(t("Conta-quilómetros", "Odometer")) })
                FilterChip(selected = byTrip, onClick = { byTrip = true; EntryMemo.byTrip = true }, label = { Text(t("Km desde o último", "Km since last")) })
            }
            if (byTrip) NumField(
                t("Quilómetros desde o último abastecimento", "Kilometres since the last refuel"), km, { km = it.filter(Char::isDigit) }, isError = kmBad,
                hint = if (kmV != null) t("Conta-quilómetros: ${fint(kmV)} km (último registo: ${fint(baseKm)} km)", "Odometer: ${fint(kmV)} km (last entry: ${fint(baseKm)} km)")
                       else t("Último registo: ${fint(baseKm)} km", "Last entry: ${fint(baseKm)} km"),
            ) else NumField(
                t("Quilómetros no conta-quilómetros", "Odometer reading (km)"), km, { km = it.filter(Char::isDigit) }, isError = kmBad,
                hint = t("Atuais: ${fint(car.km)} km", "Current: ${fint(car.km)} km"),
            )
            if (firstRef && !(phev && elec)) NumField(
                t("Km feitos com o depósito anterior (opcional)", "Km driven on the previous tank (optional)"), since, { since = it.filter(Char::isDigit) },
                hint = t("Se souberes, a média aparece já neste abastecimento, sem esperar pelo próximo.", "If you know it, the average shows right away, without waiting for the next refuel."),
            )
        }
        FormSection(t("Quantidade e preço", "Amount and price")) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumField(if (ev) "kWh" else tr("Litros"), liters, { liters = it }, Modifier.weight(1f), decimal = true, isError = lBad,
                    hint = if (phev && elec) "" else if (ev) t("Bateria: ${car.tankL} kWh", "Battery: ${car.tankL} kWh") else t("Depósito: ${car.tankL} L", "Tank: ${car.tankL} L"))
                NumField(tr("Total (€)"), total, { total = it }, Modifier.weight(1f), decimal = true, isError = tBad)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(tV?.let { eur(it) } ?: "— €", fontSize = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    (if (ev) t("Preço por kWh", "Price per kWh") else t("Preço por litro", "Price per litre")) + ": ${ppl?.let { fmt(it, 3) + " €/$u" } ?: "—"}" + (todayAvg?.takeIf { !ev }?.let { t(" · média hoje ", " · average today ") + "${fmt(it, 3)} €/L" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val warns = buildList {
                if (kmV != null && item == null && lastKm != null && kmV < lastKm) add(t("O km (${fint(kmV)}) é menor que o do último registo (${fint(lastKm)}). Está certo?", "The km (${fint(kmV)}) is lower than your last entry (${fint(lastKm)}). Is that right?"))
                if (kmV != null && lastKm != null && kmV - lastKm > 3000) add(t("Mais de 3 000 km desde o último registo. Esqueceste algum abastecimento?", "Over 3,000 km since the last entry. Did you miss a refuel?"))
                if (ppl != null) {
                    val lo = if (ev) 0.03 else 0.8
                    val hi = if (ev) 1.6 else 3.2
                    if (ppl < lo || ppl > hi) add(t("Preço de ${fmt(ppl, 3)} €/$u fora do normal. Confirma litros e total.", "Price of ${fmt(ppl, 3)} €/$u looks unusual. Check quantity and total."))
                }
                if (lV != null && lV > 0 && lV > maxQ) add(t("Mais do que cabe no depósito (${car.tankL}).", "More than the tank holds (${car.tankL})."))
                if (kmV != null && tV != null && others.any { it.id != (item?.id ?: -1L) && it.date == date.toEpochDay() && it.km == kmV && it.total == tV }) add(t("Já existe um abastecimento igual neste dia. É repetido?", "An identical refuel already exists on this day. Is it a duplicate?"))
            }
            warns.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        FormSection(t("Detalhes", "Details")) {
            TxtField(if (ev) t("Posto de carregamento (opcional)", "Charging station (optional)") else tr("Posto (opcional)"), station, { station = it })
            FormSwitch(
                if (ev) t("Carga completa", "Full charge") else tr("Depósito cheio"),
                tr("Opcional. Com depósito cheio o consumo é mais exato, mas também é calculado com abastecimentos parciais, à medida que andas."),
                full,
            ) { full = it }
        }
    }
}

// ---------- Intervenção ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceForm(car: Vehicle, item: Service?, onSave: (Service) -> Unit, onDelete: (Service) -> Unit, onClose: () -> Unit, onSaveMore: ((Service) -> Unit)? = null) {
    var title by remember { mutableStateOf(item?.title ?: "") }
    var cat by remember { mutableStateOf(item?.category ?: SERVICE_CATEGORIES[0]) }
    var date by remember { mutableStateOf(item?.let { LocalDate.ofEpochDay(it.date) } ?: EntryMemo.date ?: LocalDate.now()) }
    var km by remember { mutableStateOf((item?.km ?: car.km).toString()) }
    var byMe by remember { mutableStateOf(item?.byMe ?: true) }
    var shop by remember { mutableStateOf(item?.shop ?: "") }
    var parts by remember { mutableStateOf(item?.let { dec(it.parts) } ?: "") }
    var labor by remember { mutableStateOf(item?.let { dec(it.labor) } ?: "") }
    var notes by remember { mutableStateOf(item?.notes ?: "") }
    var image by remember { mutableStateOf(item?.image ?: "") }
    var tried by remember { mutableStateOf(false) }

    val kmV = km.toIntOrNull()
    val partsBad = parts.isNotBlank() && num(parts) == null
    val laborBad = labor.isNotBlank() && num(labor) == null

    val doSave: (Boolean) -> Unit = { more ->
        if (more) EntryMemo.date = date
            tried = true
            if (title.isNotBlank() && kmV != null && kmV > 0 && !partsBad && !laborBad) {
                if (item != null && item.image != image) deleteFile(item.image)
                (if (more) (onSaveMore ?: onSave) else onSave)(
                    Service(
                        item?.id ?: 0, car.id, date.toEpochDay(), kmV, title.trim(), cat, byMe,
                        if (byMe) "" else shop.trim(), num(parts) ?: 0.0, num(labor) ?: 0.0, notes.trim(), image,
                    )
                )
            }
    }
    FormSheet(
        title = if (item == null) tr("Nova intervenção") else tr("Editar intervenção"),
        onClose = onClose,
        onDelete = item?.let { { onDelete(it) } },
        onSaveNext = if (item == null && onSaveMore != null) ({ doSave(true) }) else null,
        onSave = { doSave(false) },
    ) {
        if (item == null) ReceiptButton { r ->
            r.total?.let { parts = dec(it) }
            r.date?.let { date = it }
        }
        FormSection(t("O que foi feito", "What was done")) {
            TxtField(tr("O que foi feito"), title, { title = it }, isError = tried && title.isBlank())
            PickField(tr("Tipo"), cat, SERVICE_CATEGORIES) { cat = it }
            DateField(tr("Data"), date) { date = it }
            NumField(tr("Quilómetros"), km, { km = it.filter(Char::isDigit) }, isError = tried && (kmV == null || kmV <= 0))
        }
        FormSection(t("Quem fez e custo", "Who did it and cost")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = byMe, onClick = { byMe = true }, label = { Text(tr("Feito por mim")) })
                FilterChip(selected = !byMe, onClick = { byMe = false }, label = { Text(tr("Oficina")) })
            }
            if (!byMe) TxtField(tr("Oficina"), shop, { shop = it })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumField(tr("Peças (€)"), parts, { parts = it }, Modifier.weight(1f), decimal = true, isError = partsBad)
                NumField(tr("Mão de obra (€)"), labor, { labor = it }, Modifier.weight(1f), decimal = true, isError = laborBad)
            }
        }
        FormSection(t("Extras", "Extras")) {
            TxtField(tr("Notas (opcional)"), notes, { notes = it })
            PhotoField(tr("Fatura ou foto"), image, item?.image ?: "") { image = it }
        }
    }
}

// ---------- Despesa ----------

@Composable
fun ExpenseForm(car: Vehicle, item: Expense?, onSave: (Expense) -> Unit, onDelete: (Expense) -> Unit, onClose: () -> Unit, onSaveMore: ((Expense) -> Unit)? = null) {
    var type by remember { mutableStateOf(item?.type ?: EXPENSE_TYPES[0]) }
    var date by remember { mutableStateOf(item?.let { LocalDate.ofEpochDay(it.date) } ?: EntryMemo.date ?: LocalDate.now()) }
    var amount by remember { mutableStateOf(item?.let { dec(it.amount) } ?: "") }
    var image by remember { mutableStateOf(item?.image ?: "") }
    var tried by remember { mutableStateOf(false) }
    val v = num(amount)

    val doSave: (Boolean) -> Unit = { more ->
        if (more) EntryMemo.date = date
            tried = true
            if (v != null && v > 0) {
                if (item != null && item.image != image) deleteFile(item.image)
                (if (more) (onSaveMore ?: onSave) else onSave)(Expense(item?.id ?: 0, car.id, date.toEpochDay(), type, v, image))
            }
    }
    FormSheet(
        title = if (item == null) tr("Nova despesa") else tr("Editar despesa"),
        onClose = onClose,
        onDelete = item?.let { { onDelete(it) } },
        onSaveNext = if (item == null && onSaveMore != null) ({ doSave(true) }) else null,
        onSave = { doSave(false) },
    ) {
        if (item == null) ReceiptButton { r ->
            r.total?.let { amount = dec(it) }
            r.date?.let { date = it }
        }
        FormSection {
            PickField(tr("Tipo"), type, EXPENSE_TYPES) { type = it }
            DateField(tr("Data"), date) { date = it }
            NumField(tr("Valor (€)"), amount, { amount = it }, decimal = true, isError = tried && (v == null || v <= 0))
        }
        FormSection(t("Comprovativo", "Proof")) {
            PhotoField(tr("Fatura ou foto"), image, item?.image ?: "") { image = it }
        }
    }
}

// ---------- Aviso de manutenção ----------

@Composable
fun ReminderForm(car: Vehicle, item: Reminder?, onSave: (Reminder) -> Unit, onDelete: (Reminder) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var everyKm by remember { mutableStateOf(item?.everyKm?.takeIf { it > 0 }?.toString() ?: "") }
    var everyMonths by remember { mutableStateOf(item?.everyMonths?.takeIf { it > 0 }?.toString() ?: "") }
    var lastKm by remember { mutableStateOf((item?.lastKm ?: car.km).toString()) }
    var lastDate by remember { mutableStateOf(item?.let { LocalDate.ofEpochDay(it.lastDate) } ?: LocalDate.now()) }
    var cat by remember { mutableStateOf(item?.category ?: "") }
    var tried by remember { mutableStateOf(false) }

    val ek = everyKm.toIntOrNull() ?: 0
    val em = everyMonths.toIntOrNull() ?: 0
    val lk = lastKm.toIntOrNull()

    FormSheet(
        title = if (item == null) tr("Novo aviso") else tr("Editar aviso"),
        onClose = onClose,
        onDelete = item?.let { { onDelete(it) } },
        onSave = {
            tried = true
            if (name.isNotBlank() && (ek > 0 || em > 0) && lk != null) {
                onSave(Reminder(item?.id ?: 0, car.id, name.trim(), cat, ek, em, lk, lastDate.toEpochDay()))
            }
        },
    ) {
        if (item == null) FormSection(tr("Atalhos")) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REMINDER_PRESETS.forEach { p ->
                    AssistChip(onClick = {
                        name = p.name; cat = p.category
                        everyKm = if (p.km > 0) p.km.toString() else ""
                        everyMonths = if (p.months > 0) p.months.toString() else ""
                    }, label = { Text(p.name) })
                }
            }
        }
        FormSection(t("O aviso", "The reminder")) {
            TxtField(tr("Nome (ex.: óleo e filtro)"), name, { name = it }, isError = tried && name.isBlank())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumField(tr("A cada (km)"), everyKm, { everyKm = it.filter(Char::isDigit) }, Modifier.weight(1f), isError = tried && ek == 0 && em == 0)
                NumField(tr("A cada (meses)"), everyMonths, { everyMonths = it.filter(Char::isDigit) }, Modifier.weight(1f))
            }
        }
        FormSection(t("Última vez", "Last time")) {
            NumField(tr("Feito aos (km)"), lastKm, { lastKm = it.filter(Char::isDigit) }, isError = tried && lk == null)
            DateField(tr("Feito em"), lastDate) { lastDate = it }
            PickField(tr("Atualiza ao registar"), cat, listOf("") + SERVICE_CATEGORIES + "Inspeção") { cat = it }
            Text(
                tr("Se registares uma intervenção deste tipo, o aviso recomeça sozinho."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- Carro (o tema muda ao escolher a marca) ----------

@Composable
fun VehicleForm(item: Vehicle?, canDelete: Boolean, onSave: (Vehicle) -> Unit, onDelete: (Vehicle) -> Unit, onClose: () -> Unit) {
    var brand by remember { mutableStateOf(Brand.of(item?.brand ?: "peugeot")) }
    var name by remember { mutableStateOf(item?.name ?: "") }
    var model by remember { mutableStateOf(item?.model ?: "") }
    var year by remember { mutableStateOf(item?.year?.takeIf { it > 0 }?.toString() ?: "") }
    var engine by remember { mutableStateOf(item?.engine ?: "") }
    var fuel by remember { mutableStateOf(item?.fuelType ?: "Gasóleo") }
    var tank by remember { mutableStateOf(item?.tankL?.toString() ?: "") }
    var price by remember { mutableStateOf(item?.purchasePrice?.takeIf { it > 0 }?.let { dec(it) } ?: "") }
    var pKm by remember { mutableStateOf(item?.purchaseKm?.takeIf { it > 0 }?.toString() ?: "") }
    var value by remember { mutableStateOf(item?.currentValue?.takeIf { it > 0 }?.let { dec(it) } ?: "") }
    var budget by remember { mutableStateOf(item?.monthlyBudget?.takeIf { it > 0 }?.let { dec(it) } ?: "") }
    var km by remember { mutableStateOf(item?.km?.toString() ?: "") }
    var plate by remember { mutableStateOf(item?.plate ?: "") }
    var tried by remember { mutableStateOf(false) }

    val tankV = tank.toIntOrNull()
    val kmV = km.toIntOrNull()

    // A pré-visualização da marca aplica-se já ao formulário
    GarageTheme(brand) {
        FormSheet(
            title = if (item == null) tr("Novo carro") else tr("Carro e aspeto"),
            onClose = onClose,
            onDelete = if (item != null && canDelete) ({ onDelete(item) }) else null,
            onSave = {
                tried = true
                if (name.isNotBlank() && model.isNotBlank() && tankV != null && tankV > 0 && kmV != null) {
                    onSave(
                        Vehicle(
                            item?.id ?: 0, name.trim(), brand.key, model.trim(), year.toIntOrNull() ?: 0,
                            engine.trim(), fuel, tankV, plate.trim().uppercase(), kmV,
                            purchasePrice = num(price) ?: 0.0, purchaseKm = pKm.toIntOrNull() ?: 0,
                            currentValue = num(value) ?: 0.0, monthlyBudget = num(budget) ?: 0.0,
                        )
                    )
                }
            },
        ) {
            FormSection(t("Identificação", "Identity")) {
                PickField(tr("Marca (define as cores)"), brand.label, Brand.entries.map { it.label }) { l ->
                    brand = Brand.entries.first { it.label == l }
                }
                TxtField(tr("Nome na garagem"), name, { name = it }, isError = tried && name.isBlank())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TxtField(tr("Modelo"), model, { model = it }, Modifier.weight(1f), isError = tried && model.isBlank())
                    NumField(tr("Ano"), year, { year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f))
                }
                TxtField(tr("Matrícula"), plate, { plate = it })
            }
            FormSection(t("Motor e depósito", "Engine and tank")) {
                TxtField(tr("Motor"), engine, { engine = it })
                PickField(t("Combustível", "Fuel"), fuel, listOf("Gasóleo", "Gasolina 95", "Gasolina 98", "GPL", "Elétrico", "Híbrido (Gasolina 95)", "Híbrido (Gasóleo)", "Híbrido plug-in (Gasolina 95)", "Híbrido plug-in (Gasóleo)")) { fuel = it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumField(if (fuel == "Elétrico") t("Bateria (kWh)", "Battery (kWh)") else tr("Depósito (L)"), tank, { tank = it.filter(Char::isDigit) }, Modifier.weight(1f), isError = tried && (tankV == null || tankV <= 0))
                    NumField(tr("Km atuais"), km, { km = it.filter(Char::isDigit) }, Modifier.weight(1f), isError = tried && kmV == null)
                }
            }
            FormSection(t("Compra e orçamento (opcional)", "Purchase and budget (optional)")) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumField(t("Preço de compra (€)", "Purchase price (€)"), price, { price = it }, Modifier.weight(1f), decimal = true)
                    NumField(t("Km na compra", "Km at purchase"), pKm, { pKm = it.filter(Char::isDigit) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumField(t("Valor atual estimado (€)", "Estimated value now (€)"), value, { value = it }, Modifier.weight(1f), decimal = true)
                    NumField(t("Orçamento mensal (€)", "Monthly budget (€)"), budget, { budget = it }, Modifier.weight(1f), decimal = true)
                }
            }
        }
    }
}
