package dev.nocturn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.nocturn.app.ui.util.artworkSeed
import dev.nocturn.app.ui.util.resolvedImageUrl
import kotlin.math.abs

/** Curated expressive Material Rounded icons used for placeholder generation. */
private val DefaultPlaceholderIcons = listOf(
    Icons.Rounded.MusicNote,
    Icons.AutoMirrored.Rounded.QueueMusic,
    Icons.Rounded.Album,
    Icons.Rounded.Headphones,
    Icons.Rounded.Favorite,
    Icons.Rounded.GraphicEq,
    Icons.Rounded.Audiotrack,
)

/**
 * Cover artwork that always renders something on-brand: a deterministic Material 3 Monet
 * container background with a tinted Material Rounded icon is painted first, and the remote
 * image fades in above it once loaded. That keeps offline states, loading states and Robolectric
 * screenshots free of blank or raw placeholders.
 */
@Composable
fun CoverArt(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    seed: Int = artworkSeed(imageUrl),
    contentDescription: String? = null,
    placeholderIcon: ImageVector? = null,
) {
    val context = LocalContext.current
    val resolved = remember(imageUrl) { resolvedImageUrl(imageUrl, widthPx = 480) }
    val request = remember(resolved, context) {
        resolved?.let {
            ImageRequest.Builder(context).data(it).crossfade(220).build()
        }
    }
    val fallbackIcon = placeholderIcon ?: if (shape == CircleShape) {
        Icons.Rounded.Person
    } else {
        null
    }
    Box(
        modifier = modifier
            .clip(shape)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        GeneratedCover(
            seed = seed,
            modifier = Modifier.fillMaxSize(),
            icon = fallbackIcon,
        )
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Material 3 Monet tonal container pair (solid container color and matching icon content color).
 */
private data class MonetCoverTheme(
    val container: Color,
    val iconTint: Color,
)

/**
 * Maps a seed and semantic icon to a Material Design 3 Monet tonal palette slot.
 *
 * All pairs are flat solid Monet container colors from [MaterialTheme.colorScheme]
 * (which dynamically tracks Android 12+ wallpaper Monet colors or Nocturn's calibrated
 * Material 3 palette). No gradients are used.
 */
private fun monetCoverTheme(
    colors: ColorScheme,
    seed: Int,
    icon: ImageVector?,
): MonetCoverTheme {
    return when (icon) {
        Icons.Rounded.Favorite -> MonetCoverTheme(
            container = colors.errorContainer,
            iconTint = colors.onErrorContainer,
        )
        Icons.Rounded.Person -> MonetCoverTheme(
            container = colors.surfaceContainerHighest,
            iconTint = colors.onSurfaceVariant,
        )
        Icons.Rounded.Album -> MonetCoverTheme(
            container = colors.tertiaryContainer,
            iconTint = colors.onTertiaryContainer,
        )
        Icons.Rounded.AutoAwesome -> MonetCoverTheme(
            container = colors.secondaryContainer,
            iconTint = colors.onSecondaryContainer,
        )
        Icons.Rounded.Leaderboard -> MonetCoverTheme(
            container = colors.primaryContainer,
            iconTint = colors.onPrimaryContainer,
        )
        Icons.Rounded.GraphicEq -> MonetCoverTheme(
            container = colors.tertiaryContainer,
            iconTint = colors.onTertiaryContainer,
        )
        else -> {
            val palette = listOf(
                MonetCoverTheme(colors.primaryContainer, colors.onPrimaryContainer),
                MonetCoverTheme(colors.secondaryContainer, colors.onSecondaryContainer),
                MonetCoverTheme(colors.tertiaryContainer, colors.onTertiaryContainer),
                MonetCoverTheme(colors.surfaceVariant, colors.onSurfaceVariant),
                MonetCoverTheme(colors.surfaceContainerHigh, colors.primary),
                MonetCoverTheme(colors.surfaceContainerHighest, colors.secondary),
                MonetCoverTheme(colors.surfaceContainerLow, colors.tertiary),
                MonetCoverTheme(colors.errorContainer, colors.onErrorContainer),
            )
            palette[abs(seed) % palette.size]
        }
    }
}

/**
 * Resolution-independent cover illustration used when no artwork is available or
 * while artwork is loading. Renders a flat Material 3 Monet container background
 * with a tinted Material Rounded icon in the center.
 */
@Composable
fun GeneratedCover(
    seed: Int,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val displayIcon = icon ?: remember(seed) {
        DefaultPlaceholderIcons[abs(seed) % DefaultPlaceholderIcons.size]
    }
    val theme = remember(colors, seed, displayIcon) {
        monetCoverTheme(colors, seed, displayIcon)
    }

    Box(
        modifier = modifier
            .background(theme.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = displayIcon,
            contentDescription = null,
            tint = theme.iconTint,
            modifier = Modifier
                .fillMaxSize(0.48f)
                .aspectRatio(1f),
        )
    }
}
