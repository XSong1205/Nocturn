package com.nocturn.music.model

import androidx.compose.runtime.Immutable
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import kotlinx.serialization.Serializable

@Immutable
data class LyricWord(
    val startMs: Long,
    val durationMs: Long,
    val text: String
)

@Immutable
data class LyricLine(
    val timeMs: Long,
    val durationMs: Long = 0L,
    val text: String,
    val translation: String = "",
    val romalrc: String = "",
    val words: List<LyricWord> = emptyList()
) {
    val hasWordTiming: Boolean
        get() = words.isNotEmpty()
}

@Immutable
data class SongLyric(
    val lines: List<LyricLine> = emptyList(),
    val rawLrc: String = "",
    val syncedLyrics: SyncedLyrics? = null
) {
    fun findCurrentIndex(currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= currentPositionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}

enum class ApiMode(val label: String) {
    EMBEDDED("内置原生 API (直连网易云)"),
    CUSTOM("自定义远程 API")
}

enum class PlayMode(val label: String) {
    LIST_LOOP("列表循环"),
    SINGLE_LOOP("单曲循环"),
    RANDOM("随机播放");

    fun next(): PlayMode = when (this) {
        LIST_LOOP -> SINGLE_LOOP
        SINGLE_LOOP -> RANDOM
        RANDOM -> LIST_LOOP
    }
}

enum class AudioQuality(val label: String, val level: String, val bitrate: String) {
    STANDARD("标准音质", "standard", "128Kbps"),
    HIGH("极高音质", "exhigh", "320Kbps"),
    LOSSLESS("无损音质", "lossless", "FLAC / 990Kbps"),
    HI_RES("Hi-Res 高解析", "hires", "24bit / 192KHz")
}

@Immutable
@Serializable
data class UserProfile(
    val userId: Long = 0L,
    val nickname: String = "未登录",
    val avatarUrl: String = "",
    val vipType: Int = 0,
    val signature: String = "登录体验更多功能与云村红心歌单",
    val isLogin: Boolean = false,
    val cookie: String = ""
)
