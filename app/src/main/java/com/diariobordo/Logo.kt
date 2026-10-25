package com.diariobordo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.compose.SubcomposeAsyncImageContent

fun Brand.domain(): String? = domain
fun Brand.iconSlug(): String? = slug

/**
 * Símbolo da marca, por esta ordem:
 * 1. ficheiro local res/drawable/logo_<chave> (se o puseres lá);
 * 2. logótipo online do Logo.dev (se definires logoDevKey no gradle.properties);
 * 3. inicial da marca nas cores dela (também serve de reserva sem rede).
 */
@Composable
fun BrandMark(brand: Brand, size: Dp, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val res = remember(brand) { ctx.resources.getIdentifier("logo_${brand.key}", "drawable", ctx.packageName) }
    val domain = brand.domain()
    val key = BuildConfig.LOGO_DEV_KEY
    val url = if (res == 0 && domain != null && key.isNotBlank()) "https://img.logo.dev/$domain?size=128&format=png&token=$key" else null

    @Composable
    fun Mono() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (brand.mono.isBlank()) Icon(Icons.Filled.DirectionsCar, null, Modifier.size(size * 0.55f), tint = fg)
            else Text(brand.mono, color = fg, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
        }
    }

    val loader = remember { ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build() }
    val slug = brand.iconSlug()
    val hex = String.format("%06x", fg.toArgb() and 0xFFFFFF)

    // Símbolo simples da cor do texto (Simple Icons). Se falhar, a inicial.
    @Composable
    fun Glyph() {
        if (slug == null) Mono()
        else SubcomposeAsyncImage(
            model = "https://cdn.simpleicons.org/$slug/$hex",
            imageLoader = loader,
            contentDescription = brand.label,
            modifier = Modifier.fillMaxSize().padding(size * 0.2f),
            loading = { Mono() },
            error = { Mono() },
        )
    }

    Box(modifier.size(size).clip(CircleShape).background(bg)) {
        when {
            res != 0 -> Box(Modifier.fillMaxSize().background(Color.White).padding(size * 0.14f)) {
                Image(painterResource(res), brand.label, Modifier.fillMaxSize())
            }
            url != null -> SubcomposeAsyncImage(
                model = url,
                contentDescription = brand.label,
                modifier = Modifier.fillMaxSize(),
                loading = { Mono() },
                error = { Glyph() },
                success = {
                    SubcomposeAsyncImageContent(Modifier.fillMaxSize().background(Color.White).padding(size * 0.14f))
                },
            )
            else -> Glyph()
        }
    }
}
