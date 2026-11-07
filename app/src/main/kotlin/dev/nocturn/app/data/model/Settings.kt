package dev.nocturn.app.data.model

import dev.nocturn.localapi.client.AudioQuality

enum class ThemeMode { System, Light, Dark }

fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.System -> "跟随系统"
    ThemeMode.Light -> "浅色"
    ThemeMode.Dark -> "深色"
}

fun AudioQuality.displayName(): String = when (this) {
    AudioQuality.Standard -> "标准"
    AudioQuality.Higher -> "较高"
    AudioQuality.Exhigh -> "极高"
    AudioQuality.Lossless -> "无损"
    AudioQuality.Hires -> "Hi-Res"
    AudioQuality.Jyeffect -> "鲸云音效"
    AudioQuality.Sky -> "沉浸环绕声"
    AudioQuality.Dolby -> "杜比全景声"
    AudioQuality.Jymaster -> "超清母带"
}

/** The quality choices offered in settings, best-effort: availability depends on the account. */
val selectableQualities: List<AudioQuality> = listOf(
    AudioQuality.Standard,
    AudioQuality.Higher,
    AudioQuality.Exhigh,
    AudioQuality.Lossless,
    AudioQuality.Hires,
)
