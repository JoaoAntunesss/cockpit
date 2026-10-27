package com.diariobordo

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Cada marca dá a cor base da app. domain = Logo.dev; slug = Simple Icons. */
enum class Brand(
    val key: String, val label: String, val seed: Color, val seedDark: Color, val mono: String,
    val domain: String? = null, val slug: String? = null,
) {
    PEUGEOT("peugeot", "Peugeot", Color(0xFF0A3D91), Color(0xFF8EB4FF), "P", "peugeot.com", "peugeot"),
    OPEL("opel", "Opel", Color(0xFFF7D600), Color(0xFFFFE23B), "O", "opel.com", "opel"),
    CITROEN("citroen", "Citroën", Color(0xFFC8102E), Color(0xFFFF8A96), "C", "citroen.com", "citroen"),
    RENAULT("renault", "Renault", Color(0xFFFFCC00), Color(0xFFFFD84A), "R", "renault.com", "renault"),
    VW("vw", "Volkswagen", Color(0xFF0B2E6F), Color(0xFF8FB3FF), "VW", "volkswagen.com", "volkswagen"),
    BMW("bmw", "BMW", Color(0xFF1C69D4), Color(0xFF86B4FF), "B", "bmw.com", "bmw"),
    MERCEDES("mercedes", "Mercedes-Benz", Color(0xFF2B2D31), Color(0xFFCFD3DA), "M", "mercedes-benz.com", null),
    TOYOTA("toyota", "Toyota", Color(0xFFD90A1E), Color(0xFFFF8F98), "T", "toyota.com", "toyota"),
    FORD("ford", "Ford", Color(0xFF0B3A82), Color(0xFF8FB1FF), "F", "ford.com", "ford"),
    FIAT("fiat", "Fiat", Color(0xFFA8142D), Color(0xFFFF93A5), "F", "fiat.com", "fiat"),
    AUDI("audi", "Audi", Color(0xFFBB0A30), Color(0xFFFF8A9E), "A", "audi.com", "audi"),
    SEAT("seat", "SEAT", Color(0xFFB5121B), Color(0xFFFF8F96), "S", "seat.com", "seat"),
    CUPRA("cupra", "Cupra", Color(0xFF8C5A3C), Color(0xFFE0A47E), "C", "cupraofficial.com", null),
    SKODA("skoda", "Škoda", Color(0xFF1E7A4C), Color(0xFF6FD6A0), "Š", "skoda-auto.com", "skoda"),
    NISSAN("nissan", "Nissan", Color(0xFFC3002F), Color(0xFFFF8CA5), "N", "nissan-global.com", "nissan"),
    HONDA("honda", "Honda", Color(0xFFCC0000), Color(0xFFFF8A8A), "H", "honda.com", "honda"),
    HYUNDAI("hyundai", "Hyundai", Color(0xFF002C5F), Color(0xFF8FB4F0), "H", "hyundai.com", "hyundai"),
    KIA("kia", "Kia", Color(0xFF05141F), Color(0xFFFF6F61), "K", "kia.com", "kia"),
    MAZDA("mazda", "Mazda", Color(0xFF6E1F2E), Color(0xFFE58EA0), "M", "mazda.com", "mazda"),
    VOLVO("volvo", "Volvo", Color(0xFF003057), Color(0xFF8DB8E8), "V", "volvocars.com", "volvo"),
    DACIA("dacia", "Dacia", Color(0xFF1A4F7A), Color(0xFF8CC4EE), "D", "dacia.com", "dacia"),
    MINI("mini", "MINI", Color(0xFF1B1B1B), Color(0xFFE0E3E8), "M", "mini.com", "mini"),
    MITSUBISHI("mitsubishi", "Mitsubishi", Color(0xFFE60012), Color(0xFFFF8A8A), "M", "mitsubishi-motors.com", "mitsubishi"),
    SUZUKI("suzuki", "Suzuki", Color(0xFF1B4DA1), Color(0xFF8FB0F0), "S", "suzuki.com", "suzuki"),
    PORSCHE("porsche", "Porsche", Color(0xFF9E0E1B), Color(0xFFE6B25A), "P", "porsche.com", "porsche"),
    JEEP("jeep", "Jeep", Color(0xFF3E5A2B), Color(0xFFA6D17E), "J", "jeep.com", "jeep"),
    TESLA("tesla", "Tesla", Color(0xFFCC0000), Color(0xFFFF6B6B), "T", "tesla.com", "tesla"),
    SUBARU("subaru", "Subaru", Color(0xFF13348A), Color(0xFF8FAEF5), "S", "subaru.com", "subaru"),
    CHEVROLET("chevrolet", "Chevrolet", Color(0xFFD1A02B), Color(0xFFF2CB65), "C", "chevrolet.com", "chevrolet"),
    DS("ds", "DS Automobiles", Color(0xFF2A2A2A), Color(0xFFD8C79A), "DS", "driveds.com", "dsautomobiles"),
    MG("mg", "MG", Color(0xFFC8102E), Color(0xFFFF8A96), "MG", "mgmotor.eu", "mg"),
    LANDROVER("landrover", "Land Rover", Color(0xFF005A2B), Color(0xFF7FD3A2), "LR", "landrover.com", null),
    JAGUAR("jaguar", "Jaguar", Color(0xFF1B1B1B), Color(0xFFD9C27A), "J", "jaguar.com", null),
    ALFA("alfa", "Alfa Romeo", Color(0xFF8B0F1F), Color(0xFFFF8A9A), "AR", "alfaromeo.com", null),
    LANCIA("lancia", "Lancia", Color(0xFF1A3A6B), Color(0xFF8FB0E8), "L", "lancia.com", null),
    LEXUS("lexus", "Lexus", Color(0xFF2B2D31), Color(0xFFC9CED6), "L", "lexus.com", null),
    BYD("byd", "BYD", Color(0xFFB5121B), Color(0xFFFF8F96), "BYD", "byd.com", null),
    LAMBORGHINI("lamborghini", "Lamborghini", Color(0xFF9A7B00), Color(0xFFF2C94C), "L", "lamborghini.com", "lamborghini"),
    FERRARI("ferrari", "Ferrari", Color(0xFFC00000), Color(0xFFFF6B5E), "F", "ferrari.com", "ferrari"),
    MASERATI("maserati", "Maserati", Color(0xFF0A2A5C), Color(0xFF8FB0F0), "M", "maserati.com", "maserati"),
    BENTLEY("bentley", "Bentley", Color(0xFF1F3D2B), Color(0xFF8FD6A8), "B", "bentleymotors.com", "bentley"),
    OTHER("outra", "Outra marca", Color(0xFF2E6B4F), Color(0xFF8FD6B3), "");

    companion object {
        fun of(key: String?): Brand = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** Segunda e terceira cores do logótipo de cada marca (para fundos escuros). */
private fun c(v: Long) = Color(v)
private val EXTRA: Map<Brand, Pair<Color, Color>> = mapOf(
    Brand.PEUGEOT to (c(0xFFE3E8F0) to c(0xFF5B8DEF)),
    Brand.OPEL to (c(0xFFF5F5F5) to c(0xFFFFFFFF)),
    Brand.CITROEN to (c(0xFFF5F5F5) to c(0xFFC9CED6)),
    Brand.RENAULT to (c(0xFFF5F5F5) to c(0xFFC9CED6)),
    Brand.VW to (c(0xFFF5F5F5) to c(0xFF00B0F0)),
    Brand.BMW to (c(0xFFFFFFFF) to c(0xFFE22718)),
    Brand.MERCEDES to (c(0xFFFFFFFF) to c(0xFF00D2BE)),
    Brand.TOYOTA to (c(0xFFF2F2F2) to c(0xFFC0C0C0)),
    Brand.FORD to (c(0xFFFFFFFF) to c(0xFF00A1E0)),
    Brand.FIAT to (c(0xFFF5F5F5) to c(0xFFE6D3B0)),
    Brand.AUDI to (c(0xFFD9DDE3) to c(0xFFFFFFFF)),
    Brand.SEAT to (c(0xFFD9DDE3) to c(0xFFFFFFFF)),
    Brand.CUPRA to (c(0xFFE8C8A8) to c(0xFFFFFFFF)),
    Brand.SKODA to (c(0xFFFFFFFF) to c(0xFF78FAAE)),
    Brand.NISSAN to (c(0xFFE4E7EC) to c(0xFFFFFFFF)),
    Brand.HONDA to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.HYUNDAI to (c(0xFFE4E7EC) to c(0xFF8FB4F0)),
    Brand.KIA to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.MAZDA to (c(0xFFE4E7EC) to c(0xFFFFFFFF)),
    Brand.VOLVO to (c(0xFFE4E7EC) to c(0xFF8DB8E8)),
    Brand.DACIA to (c(0xFFD6D9DE) to c(0xFFA0B46A)),
    Brand.MINI to (c(0xFFFF3B30) to c(0xFFFFFFFF)),
    Brand.MITSUBISHI to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.SUZUKI to (c(0xFFFF3B3B) to c(0xFFFFFFFF)),
    Brand.PORSCHE to (c(0xFFFF4D4D) to c(0xFFFFFFFF)),
    Brand.JEEP to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.TESLA to (c(0xFFF2F2F2) to c(0xFFC9CED6)),
    Brand.SUBARU to (c(0xFFFFD966) to c(0xFFFFFFFF)),
    Brand.CHEVROLET to (c(0xFF3F7FDB) to c(0xFFFFFFFF)),
    Brand.DS to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.MG to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.LANDROVER to (c(0xFFFFFFFF) to c(0xFFFFD54A)),
    Brand.JAGUAR to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.ALFA to (c(0xFF00A859) to c(0xFFFFFFFF)),
    Brand.LANCIA to (c(0xFFE4E7EC) to c(0xFF8FB0E8)),
    Brand.LEXUS to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.BYD to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.LAMBORGHINI to (c(0xFFFFFFFF) to c(0xFFC9CED6)),
    Brand.FERRARI to (c(0xFFFFD500) to c(0xFFFFFFFF)),
    Brand.MASERATI to (c(0xFFE4E7EC) to c(0xFFFF4D4D)),
    Brand.BENTLEY to (c(0xFFE4E7EC) to c(0xFFFFFFFF)),
)

/** Segunda cor da marca (ponteiro, brilhos). */
val Brand.second: Color get() = EXTRA[this]?.first ?: Color.White

/** Terceira cor da marca (detalhes). */
val Brand.third: Color get() = EXTRA[this]?.second ?: Color(0xFFC9CED6)

/** Preferências de aspeto: modo (0 sistema, 1 escuro, 2 claro) e cor dos dígitos LED (0 = cor da marca). */
object UiPrefs {
    var mode by mutableIntStateOf(1)
    var led by mutableIntStateOf(0xFFFF2A2A.toInt())
    var fav by mutableStateOf("")
    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("diario", 0)
        mode = p.getInt("ui_mode", 1)
        led = p.getInt("led_color", 0xFFFF2A2A.toInt())
        fav = p.getString("fav_fuel", "") ?: ""
    }
    fun setMode(ctx: Context, v: Int) { mode = v; ctx.getSharedPreferences("diario", 0).edit().putInt("ui_mode", v).apply() }
    fun setFav(ctx: Context, v: String) { fav = v; ctx.getSharedPreferences("diario", 0).edit().putString("fav_fuel", v).apply() }
    fun setLed(ctx: Context, v: Int) { led = v; ctx.getSharedPreferences("diario", 0).edit().putInt("led_color", v).apply() }
}

/** Curva de movimento: desacelera até parar (cubic-bezier 0.22, 1, 0.36, 1). */
val AppEase = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

fun onColor(c: Color): Color = if (c.luminance() > 0.18f) Color(0xFF14161A) else Color.White

// Cores dos gráficos (fixas por categoria, nunca mudam com a marca)
data class ChartColors(val fuel: Color, val mech: Color, val fixed: Color)

val LightChart = ChartColors(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A))
val DarkChart = ChartColors(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70))

@Composable
fun chartColors(): ChartColors = if (isDark()) DarkChart else LightChart

@Composable
fun isDark(): Boolean = when (UiPrefs.mode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }

/** Estilo "cockpit": escuro por defeito (ou claro/sistema), com a cor da marca como luz de painel. */
@Composable
fun GarageTheme(brand: Brand, content: @Composable () -> Unit) {
    val dark = isDark()
    val ctx = LocalContext.current
    SideEffect {
        (ctx as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
    }
    val scheme = if (dark) {
        val accent = brand.seedDark
        val base = Color(0xFF08090B)
        val ink = Color(0xFFF1F2F5)
        val container = lerp(base, accent, 0.22f)
        val bg = lerp(base, accent, 0.025f)
        darkColorScheme().copy(
            primary = accent, onPrimary = onColor(accent),
            primaryContainer = container, onPrimaryContainer = ink,
            secondaryContainer = container, onSecondaryContainer = ink,
            background = bg, onBackground = ink, surface = bg, onSurface = ink,
            surfaceVariant = lerp(base, accent, 0.10f), onSurfaceVariant = ink.copy(alpha = 0.80f),
            surfaceContainer = lerp(Color(0xFF0F1115), accent, 0.05f),
            surfaceContainerHigh = lerp(Color(0xFF14171C), accent, 0.08f),
            surfaceContainerHighest = lerp(Color(0xFF191C22), accent, 0.10f),
            outline = ink.copy(alpha = 0.55f), outlineVariant = lerp(Color(0xFF2A2E36), accent, 0.22f),
            secondary = brand.second, tertiary = brand.third,
        )
    } else {
        // Marcas amarelas ficam ilegíveis em fundo claro: escurecemos o tom.
        val accent = if (brand.seed.luminance() > 0.4f) lerp(brand.seed, Color.Black, 0.45f) else brand.seed
        val base = Color(0xFFF6F7F9)
        val ink = Color(0xFF14161A)
        val container = lerp(Color.White, accent, 0.14f)
        val bg = lerp(base, accent, 0.03f)
        lightColorScheme().copy(
            primary = accent, onPrimary = onColor(accent),
            primaryContainer = container, onPrimaryContainer = ink,
            secondaryContainer = container, onSecondaryContainer = ink,
            background = bg, onBackground = ink, surface = bg, onSurface = ink,
            surfaceVariant = lerp(Color(0xFFE9ECF0), accent, 0.06f), onSurfaceVariant = ink.copy(alpha = 0.74f),
            surfaceContainer = Color.White,
            surfaceContainerHigh = lerp(Color.White, accent, 0.05f),
            surfaceContainerHighest = lerp(Color(0xFFEEF0F3), accent, 0.06f),
            outline = ink.copy(alpha = 0.45f), outlineVariant = lerp(Color(0xFFD9DDE3), accent, 0.15f),
            secondary = accent, tertiary = accent,
        )
    }
    val d = Typography()
    val type = d.copy(
        headlineSmall = d.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        titleLarge = d.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        titleMedium = d.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
        labelSmall = d.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp),
    )
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}
