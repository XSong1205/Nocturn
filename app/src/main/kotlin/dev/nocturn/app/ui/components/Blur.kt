package dev.nocturn.app.ui.components

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.nocturn.app.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.textureBlur

/**
 * Whether backdrop blur materials can render. miuix-blur builds its effects from `RuntimeShader`,
 * which needs Android 13 (API 33); below that every glass surface falls back to an opaque tint.
 */
val blurAvailable: Boolean = isRuntimeShaderSupported()

/**
 * The shell's captured page layer. Chrome that floats over the routed screens (status bar band,
 * bottom chrome, mini player) blurs this. `null` outside the shell or when [blurAvailable] is false,
 * so standalone renders and older devices take the opaque fallback.
 */
val LocalShellBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * One frosted material: blur radius, the colour layers blended over the blurred backdrop (tint plus
 * saturation boost, the HyperOS recipe), an optional glass edge highlight and the opaque [fallback]
 * used when blur is unavailable.
 */
@Immutable
data class GlassMaterial(
    val blurRadius: Float,
    val colors: BlurColors,
    val fallback: Color,
    val highlight: Highlight? = null,
)

object GlassMaterials {
    /** Navigation bar and the bottom chrome strip. */
    @Composable
    fun chrome(): GlassMaterial {
        val dark = LocalIsDarkTheme.current
        val base = MaterialTheme.colorScheme.surfaceContainer
        return remember(dark, base) {
            GlassMaterial(
                blurRadius = 28f,
                colors = BlurColors(
                    blendColors = listOf(BlendColorEntry(base.copy(alpha = if (dark) 0.74f else 0.68f))),
                    saturation = if (dark) 1.25f else 1.5f,
                ),
                fallback = base,
            )
        }
    }

    /** Raised capsules over the chrome, such as the mini player. Lighter tint plus an edge stroke. */
    @Composable
    fun capsule(): GlassMaterial {
        val dark = LocalIsDarkTheme.current
        val base = MaterialTheme.colorScheme.surfaceContainerHighest
        val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
        return remember(dark, base, fallback) {
            GlassMaterial(
                blurRadius = 32f,
                colors = BlurColors(
                    blendColors = listOf(BlendColorEntry(base.copy(alpha = if (dark) 0.72f else 0.78f))),
                    saturation = if (dark) 1.3f else 1.5f,
                ),
                fallback = fallback,
                highlight = if (dark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight,
            )
        }
    }

    /**
     * Full-coverage top bars that content scrolls under. Tinted with the page background rather
     * than a container tone, so at rest the bar reads as the page itself and only frosts what
     * passes beneath it.
     */
    @Composable
    fun bar(): GlassMaterial {
        val dark = LocalIsDarkTheme.current
        val base = MaterialTheme.colorScheme.background
        return remember(dark, base) {
            GlassMaterial(
                blurRadius = 30f,
                colors = BlurColors(
                    blendColors = listOf(BlendColorEntry(base.copy(alpha = if (dark) 0.66f else 0.62f))),
                    saturation = if (dark) 1.3f else 1.6f,
                ),
                fallback = base.copy(alpha = 0.96f),
            )
        }
    }
}

/**
 * Paints [material] behind this element: frosted backdrop blur when [backdrop] is available,
 * otherwise the material's opaque fallback colour. [enabled] = false skips the blur pass entirely
 * (use it while the surface is fully transparent).
 */
fun Modifier.glass(
    backdrop: Backdrop?,
    shape: Shape,
    material: GlassMaterial,
    enabled: Boolean = true,
): Modifier = if (backdrop != null) {
    textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = material.blurRadius,
        colors = material.colors,
        highlight = material.highlight,
        enabled = enabled,
    )
} else {
    background(material.fallback, shape)
}

/**
 * Progressive edge: full blur and tint at the [ProgressiveBlur] strong end, fading to clear. Used
 * where a glass surface meets scrolling content so the boundary melts instead of cutting.
 */
fun Modifier.glassEdge(
    backdrop: Backdrop,
    material: GlassMaterial,
    gradient: ProgressiveBlur,
): Modifier = progressiveTextureBlur(
    backdrop = backdrop,
    shape = RectangleShape,
    blurRadius = material.blurRadius,
    gradient = gradient,
    colors = material.colors,
)

/**
 * Blurs the app window behind the dialog or sheet this is composed in (Android 12+, where the
 * device supports cross-window blur). Elsewhere it is a no-op and the window's normal dim applies.
 */
@Composable
fun BlurBehindWindow(radius: Dp = 28.dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    DisposableEffect(view, radiusPx) {
        val window = view.findDialogWindow()
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = radiusPx }
        }
        onDispose { }
    }
}

private fun View.findDialogWindow(): Window? {
    var current: Any? = parent
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = (current as? android.view.ViewParent)?.parent
    }
    return null
}
