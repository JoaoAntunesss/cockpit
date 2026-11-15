package com.diariobordo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Introdução de 4 ecrãs, só na primeira vez. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val pages = listOf(
        Triple(Icons.Filled.DirectionsCar, t("Bem-vindo ao Cockpit", "Welcome to Cockpit"),
            t("O diário de bordo do teu carro: consumos, manutenção, despesas e prazos num só sítio. As cores seguem a marca do carro.", "Your car's logbook: fuel, maintenance, expenses and deadlines in one place. Colours follow your car's brand.")),
        Triple(Icons.Filled.Build, t("Regista tudo", "Log everything"),
            t("Abastecimentos, intervenções, pneus, seguro, IUC... Tens histórico antigo? Adiciona vários seguidos ou importa um ficheiro CSV nas Definições.", "Refuels, repairs, tyres, insurance, road tax... Got old records? Add several in a row or import a CSV file in Settings.")),
        Triple(Icons.Filled.NotificationsActive, t("Nunca te esqueças", "Never forget"),
            t("Avisos de óleo, inspeção, seguro e IUC, mesmo com a app fechada. Dá licença para as notificações quando o telemóvel pedir.", "Reminders for oil, inspection, insurance and road tax, even when the app is closed. Allow notifications when your phone asks.")),
        Triple(Icons.Filled.EvStation, t("Postos, cartões e comunidade", "Stations, cards and community"),
            t("Preços perto de ti, carregadores para elétricos, os teus cartões de combustível e, se quiseres, falar com outros donos de carros. Tudo opcional.", "Prices near you, chargers for electric cars, your fuel cards and, if you like, chatting with other car owners. All optional.")),
    )
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = state.currentPage == pages.lastIndex
    Surface(
        Modifier.fillMaxSize().pointerInput(Unit) {},
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone, modifier = Modifier.alpha(if (last) 0f else 1f)) { Text(t("Saltar", "Skip")) }
            }
            HorizontalPager(state, Modifier.weight(1f)) { i ->
                val sel = state.currentPage == i
                val sc by animateFloatAsState(if (sel) 1f else 0.7f, tween(450), label = "ob_scale")
                val al by animateFloatAsState(if (sel) 1f else 0f, tween(350), label = "ob_alpha")
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(132.dp).scale(sc).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(pages[i].first, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.height(32.dp))
                    Text(pages[i].second, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.alpha(al))
                    Spacer(Modifier.height(12.dp))
                    Text(pages[i].third, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(al))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                pages.indices.forEach { i ->
                    val w by animateFloatAsState(if (state.currentPage == i) 26f else 8f, tween(300), label = "dot")
                    Box(
                        Modifier.padding(horizontal = 4.dp).height(8.dp).width(w.dp)
                            .background(if (state.currentPage == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                }
            }
            Button(
                onClick = { if (last) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) { Text(if (last) t("Começar", "Get started") else t("Seguinte", "Next")) }
        }
    }
}



/** Ecrã de entrada: o Cockpit pede a conta Google antes de começar. Os dados continuam no telemóvel. */
@Composable
fun SignInGate(acc: AccountState, onSignIn: () -> Unit) {
    Surface(Modifier.fillMaxSize().pointerInput(Unit) {}, color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(132.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AccountCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(32.dp))
            Text(t("Entra com a tua conta Google", "Sign in with your Google account"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                t("Assim podes guardar uma cópia na nuvem e passar tudo para outro telemóvel. Os teus registos ficam neste telemóvel; só vão para a nuvem se ligares a cópia.",
                    "So you can keep a cloud backup and move everything to another phone. Your records stay on this phone; they only go to the cloud if you turn on the backup."),
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            acc.message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onSignIn, enabled = !acc.busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                if (acc.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(t("Continuar com Google", "Continue with Google"))
            }
        }
    }
}
