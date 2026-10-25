package com.diariobordo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Pincel que "brilha" da esquerda para a direita: placeholder enquanto os dados chegam. */
@Composable
fun shimmerBrush(base: Color = MaterialTheme.colorScheme.onSurface): Brush {
    val tr = rememberInfiniteTransition(label = "shimmer")
    val x by tr.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    return Brush.linearGradient(
        colors = listOf(base.copy(alpha = 0.06f), base.copy(alpha = 0.16f), base.copy(alpha = 0.06f)),
        start = Offset(x * 1400f - 400f, 0f),
        end = Offset(x * 1400f, 160f),
    )
}

/** Linhas de lista em esqueleto (círculo + duas linhas). */
@Composable
fun ShimmerRows(n: Int = 4, modifier: Modifier = Modifier, base: Color = MaterialTheme.colorScheme.onSurface) {
    val b = shimmerBrush(base)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(n) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(b))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(b))
                    Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(b))
                }
                Box(Modifier.width(48.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(b))
            }
        }
    }
}

/** Deslizar para a esquerda apaga. Usar dentro de key(id) para o estado não passar para outra linha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(Color(0xFFB3261E)).padding(end = 22.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Filled.Delete, t("Apagar", "Delete"), tint = Color.White)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) { content() }
    }
}

/** Entrada suave: o bloco sobe e aparece, com um pequeno atraso por posição. */
@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        a.animateTo(1f, tween(420))
    }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 28.dp.toPx() }) { content() }
}
