package dev.nocturn.app.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MultiPreview annotation for canonical adaptive screen sizes:
 * - Phone (Compact): 412dp x 915dp
 * - Foldable (Medium): 673dp x 841dp
 * - Tablet (Expanded): 1024dp x 768dp
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Phone (Compact)", widthDp = 412, heightDp = 915, showBackground = true)
@Preview(name = "Foldable (Medium)", widthDp = 673, heightDp = 841, showBackground = true)
@Preview(name = "Tablet (Expanded)", widthDp = 1024, heightDp = 768, showBackground = true)
annotation class DevicePreviews

/**
 * Material Design 3 Window Width Size Classes.
 *
 * - [Compact]: < 600dp (standard portrait phones)
 * - [Medium]: 600dp .. 839dp (foldables unfolded, small tablets, landscape phones)
 * - [Expanded]: >= 840dp (large tablets, desktops, expanded windows)
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded;

    val isCompact: Boolean get() = this == Compact
    val isMedium: Boolean get() = this == Medium
    val isExpanded: Boolean get() = this == Expanded
    val isAtLeastMedium: Boolean get() = this != Compact
}

/**
 * Target application runtime platform.
 */
enum class AppPlatform {
    Android,
    Windows,
    Other;

    val isWindows: Boolean get() = this == Windows
    val isAndroid: Boolean get() = this == Android
    val isMobile: Boolean get() = this == Android
    val isDesktop: Boolean get() = this == Windows || this == Other
}

/**
 * Auto-detects the current runtime platform.
 */
fun detectAppPlatform(): AppPlatform {
    // 1. Android runtime check (Dalvik/ART or Android environment)
    val isAndroidRuntime = runCatching {
        val vmName = System.getProperty("java.vm.name").orEmpty()
        val vendor = System.getProperty("java.vendor").orEmpty()
        vmName.contains("Dalvik", ignoreCase = true) ||
            vmName.contains("ART", ignoreCase = true) ||
            vendor.contains("Android", ignoreCase = true)
    }.getOrDefault(false)

    if (isAndroidRuntime) {
        return AppPlatform.Android
    }

    // 2. OS check for Windows
    val osName = runCatching { System.getProperty("os.name").orEmpty() }.getOrDefault("")
    if (osName.contains("Windows", ignoreCase = true)) {
        // Under Robolectric tests, the host OS may be Windows while the test simulates Android.
        val isRobolectric = runCatching {
            android.os.Build.FINGERPRINT == "robolectric"
        }.getOrDefault(false)
        return if (isRobolectric) AppPlatform.Android else AppPlatform.Windows
    }

    return AppPlatform.Other
}

/**
 * Ambient window width class. Defaults to [WindowWidthClass.Compact] so previews and isolated
 * tests retain deterministic compact behavior unless explicitly provided.
 */
val LocalWindowWidthClass = compositionLocalOf { WindowWidthClass.Compact }

/**
 * Ambient runtime platform. Defaults to [detectAppPlatform] but can be overridden
 * in previews, tests, or platform-specific shells.
 */
val LocalAppPlatform = compositionLocalOf { detectAppPlatform() }

/**
 * Optional override for desktop UI. When null, [shouldUseDesktopUi] evaluates based on
 * the current platform and window width class.
 */
val LocalDesktopUiEnabled = compositionLocalOf<Boolean?> { null }

/**
 * Derives the [WindowWidthClass] from a given container or window width in [Dp].
 */
fun windowWidthClassOf(width: Dp): WindowWidthClass = when {
    width < 600.dp -> WindowWidthClass.Compact
    width < 840.dp -> WindowWidthClass.Medium
    else -> WindowWidthClass.Expanded
}

/**
 * Reads the current [WindowWidthClass] based on [LocalConfiguration.current.screenWidthDp].
 */
@Composable
fun currentWindowWidthClass(): WindowWidthClass {
    val configuration = LocalConfiguration.current
    return windowWidthClassOf(configuration.screenWidthDp.dp)
}

/**
 * Determines whether the Desktop UI (e.g. `DesktopScaffold`) should be activated.
 *
 * Rules:
 * - Windows platform: Desktop UI is forced enabled.
 * - Android and other mobile platforms: Desktop UI is forced disabled, ensuring tablets
 *   (even with high resolution / width >= 840dp) use the touch-friendly mobile/tablet
 *   navigation rail rather than being misjudged as desktop.
 * - Other platforms: Fallback to [WindowWidthClass.isExpanded].
 */
fun shouldUseDesktopUi(
    platform: AppPlatform = detectAppPlatform(),
    widthClass: WindowWidthClass = WindowWidthClass.Compact,
): Boolean = when {
    platform == AppPlatform.Windows -> true
    platform.isMobile -> false
    else -> widthClass.isExpanded
}

/**
 * Reads whether Desktop UI should be used in the current Composable context.
 */
@Composable
fun isDesktopUi(): Boolean {
    val override = LocalDesktopUiEnabled.current
    if (override != null) return override
    val platform = LocalAppPlatform.current
    val widthClass = LocalWindowWidthClass.current
    return shouldUseDesktopUi(platform, widthClass)
}

