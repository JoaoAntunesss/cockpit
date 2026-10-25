package com.diariobordo

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Todas as palavras escritas têm de aparecer em algum dos textos (sem maiúsculas nem acentos). */
fun matchesQuery(q: String, vararg parts: String): Boolean {
    if (q.isBlank()) return true
    fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    val hay = norm(parts.joinToString(" "))
    return q.trim().split(Regex("\\s+")).all { hay.contains(norm(it)) }
}

@Composable
fun SearchBar(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier.fillMaxWidth(), singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text(t("Procurar (posto, oficina, mês, ano...)", "Search (station, shop, month, year...)")) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, t("Limpar", "Clear")) } },
    )
}
