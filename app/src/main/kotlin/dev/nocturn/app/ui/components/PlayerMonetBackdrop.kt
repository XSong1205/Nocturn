package dev.nocturn.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import dev.nocturn.app.data.model.ColorSchemeKey
import dev.nocturn.app.data.model.DynamicVariant
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.ui.theme.LocalIsDarkTheme
import dev.nocturn.app.ui.theme.derivePlayerBackgroundColor
import dev.nocturn.app.ui.theme.extractDominantSeedColor
import dev.nocturn.app.ui.util.artworkSeed
import dev.nocturn.app.ui.util.resolvedImageUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Reads a small software bitmap; Coil's hardware bitmaps do not support getPixels. */
private suspend fun fetchCoverSeedColor(
    context: Context,
    imageLoader: ImageLoader,
    imageUrl: String?,
): Color? {
    val resolved = resolvedImageUrl(imageUrl, widthPx = 128) ?: return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(resolved)
            .size(128, 128)
            .allowHardware(false)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build()
        val result = imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = (result.image as? BitmapImage)?.bitmap ?: return null
        withContext(Dispatchers.Default) { extractDominantSeedColor(bitmap) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

/**
 * Keeps the previous artwork color while loading, then commits this song's color or fallback.
 * Identity is part of the key so missing/shared artwork cannot pin a previous song's background.
 */
@Composable
internal fun rememberPlayerArtworkSeed(
    song: Song?,
    fallback: Color,
    imageLoader: ImageLoader = LocalContext.current.imageLoader,
): State<Color> {
    val context = LocalContext.current
    val seed = remember { mutableStateOf(fallback) }
    LaunchedEffect(context, imageLoader, song?.id, song?.cover, fallback) {
        val extracted = fetchCoverSeedColor(context, imageLoader, song?.cover)
        // A cancelled/late request must never repaint the currently playing song.
        coroutineContext.ensureActive()
        seed.value = extracted ?: fallback
    }
    return seed
}

/** One artwork-derived Monet surface, following the app's color scheme and extraction style. */
@Composable
fun PlayerMonetBackdrop(
    song: Song?,
    modifier: Modifier = Modifier,
    dark: Boolean = LocalIsDarkTheme.current,
    colorSchemeKey: ColorSchemeKey = ColorSchemeKey.M3eDynamic,
    variant: DynamicVariant = DynamicVariant.Expressive,
) {
    val colors = MaterialTheme.colorScheme
    val fallback = remember(song?.id, song?.name, song?.cover, colors) {
        val accents = listOf(colors.primary, colors.secondary, colors.tertiary)
        accents[Math.floorMod(artworkSeed(song?.id, song?.name, song?.cover), accents.size)]
    }
    val seed by rememberPlayerArtworkSeed(song, fallback)
    PlayerArtworkBackground(
        seedColor = seed,
        modifier = modifier,
        dark = dark,
        colorSchemeKey = colorSchemeKey,
        variant = variant,
    )
}

@Composable
internal fun PlayerArtworkBackground(
    seedColor: Color,
    modifier: Modifier = Modifier,
    dark: Boolean = LocalIsDarkTheme.current,
    colorSchemeKey: ColorSchemeKey,
    variant: DynamicVariant,
) {
    val target = remember(seedColor, dark, colorSchemeKey, variant) {
        derivePlayerBackgroundColor(seedColor, dark, colorSchemeKey, variant)
    }
    // Compose honors the system MotionDurationScale, including instant reduced-motion updates.
    val spec = tween<Color>(durationMillis = 750, easing = FastOutSlowInEasing)
    val background by animateColorAsState(target, spec, label = "player-monet-surface")
    Box(modifier = modifier.fillMaxSize().background(background))
}
