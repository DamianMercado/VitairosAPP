package cl.vitairos.app.ui.theme

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------- Paleta VITAIROS Industrial AI
object Vitairos {
    val VerdeNoche = Color(0xFF081D16)
    val VerdeProfundo = Color(0xFF102B23)
    val VerdeMineral = Color(0xFF1B342D)
    val Hueso = Color(0xFFFBF7F1)
    val Niebla = Color(0xFFE8EFE9)
    val Cobre = Color(0xFFDD7A52)
    val Oro = Color(0xFFD9B46C)
    val Acero = Color(0xFFAAB6B7)

    // Derivados para estados (contraste AA sobre su contenedor)
    val ErrorRojo = Color(0xFF9E2A1E)
    val ErrorFondo = Color(0xFFF9DEDA)
    val OkVerde = Color(0xFF1C6B3F)
    val OkFondo = Color(0xFFD9EFE1)
    val OroOscuro = Color(0xFF6B4E12)
    val OroFondo = Color(0xFFF6EBD3)
    val CobreOscuro = Color(0xFF8A3A1A)
    val CobreFondo = Color(0xFFF8E2D7)
    val AceroOscuro = Color(0xFF3B4647)
    val AceroFondo = Color(0xFFE6EBEB)
    val Blanco = Color(0xFFFFFFFF)
}

private val VitairosLightColors = lightColorScheme(
    primary = Vitairos.VerdeProfundo,
    onPrimary = Vitairos.Hueso,
    primaryContainer = Vitairos.Niebla,
    onPrimaryContainer = Vitairos.VerdeNoche,
    secondary = Vitairos.Cobre,
    onSecondary = Vitairos.VerdeNoche,
    secondaryContainer = Vitairos.CobreFondo,
    onSecondaryContainer = Vitairos.CobreOscuro,
    tertiary = Vitairos.Oro,
    onTertiary = Vitairos.VerdeNoche,
    tertiaryContainer = Vitairos.OroFondo,
    onTertiaryContainer = Vitairos.OroOscuro,
    background = Vitairos.Hueso,
    onBackground = Vitairos.VerdeNoche,
    surface = Vitairos.Hueso,
    onSurface = Vitairos.VerdeNoche,
    surfaceVariant = Vitairos.Niebla,
    onSurfaceVariant = Vitairos.VerdeMineral,
    surfaceContainerLowest = Vitairos.Blanco,
    surfaceContainerLow = Vitairos.Blanco,
    surfaceContainer = Vitairos.Hueso,
    surfaceContainerHigh = Vitairos.Niebla,
    surfaceContainerHighest = Vitairos.Niebla,
    outline = Vitairos.Acero,
    outlineVariant = Vitairos.Niebla,
    error = Vitairos.ErrorRojo,
    onError = Vitairos.Blanco,
    errorContainer = Vitairos.ErrorFondo,
    onErrorContainer = Vitairos.ErrorRojo,
)

// ---------------------------------------------------------------- Tipografía
/**
 * Titulares: Space Grotesk · Etiquetas técnicas: IBM Plex Mono · Formularios: Roboto (nativa).
 * Las fuentes se resuelven por nombre: si no se copiaron a res/font (ver scripts/descargar_fuentes),
 * la app usa la tipografía del sistema sin fallar.
 */
data class VitairosFonts(val heading: FontFamily, val mono: FontFamily)

private fun loadFonts(context: Context): VitairosFonts {
    fun id(name: String): Int = context.resources.getIdentifier(name, "font", context.packageName)
    val grotesk = id("space_grotesk")
    val monoRegular = id("ibm_plex_mono_regular")
    val monoMedium = id("ibm_plex_mono_medium")
    val heading = if (grotesk != 0) {
        FontFamily(
            Font(grotesk, FontWeight.Medium),
            Font(grotesk, FontWeight.SemiBold),
            Font(grotesk, FontWeight.Bold),
        )
    } else {
        FontFamily.SansSerif
    }
    val mono = if (monoRegular != 0) {
        FontFamily(
            buildList<Font> {
                add(Font(monoRegular, FontWeight.Normal))
                if (monoMedium != 0) add(Font(monoMedium, FontWeight.Medium))
            }
        )
    } else {
        FontFamily.Monospace
    }
    return VitairosFonts(heading, mono)
}

val LocalMonoFont = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

private fun typography(h: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = h, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = h, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = h, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = h, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = h, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = h, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    )
}

private val VitairosShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun VitairosTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val fonts = remember { loadFonts(context) }
    CompositionLocalProvider(LocalMonoFont provides fonts.mono) {
        MaterialTheme(
            colorScheme = VitairosLightColors,
            typography = typography(fonts.heading),
            shapes = VitairosShapes,
            content = content,
        )
    }
}

/** Estilo para identificadores técnicos (folio, UUID, hash). */
@Composable
fun monoStyle(size: Int = 13): TextStyle =
    TextStyle(fontFamily = LocalMonoFont.current, fontSize = size.sp, letterSpacing = 0.2.sp)
