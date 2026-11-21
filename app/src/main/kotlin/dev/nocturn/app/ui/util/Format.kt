package dev.nocturn.app.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "3:26" for songs, "1:02:03" for long content. */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** Compact, Chinese-friendly count used for plays, fans and track totals. */
fun formatCount(count: Long): String = when {
    count >= 100_000_000 -> trimDecimal(count / 100_000_000.0) + "亿"
    count >= 10_000 -> trimDecimal(count / 10_000.0) + "万"
    count >= 1_000 -> String.format(Locale.ROOT, "%,d", count)
    else -> count.toString()
}

private fun trimDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", rounded)
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

fun formatDateTime(epochMillis: Long): String? {
    if (epochMillis <= 0) return null
    return runCatching {
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)
    }.getOrNull()
}

/** Formats remaining milliseconds into "DD天 HH时 mm分 ss秒" or "mm分ss秒". */
fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0) return "00分00秒"
    val totalSec = remainingMs / 1000
    val days = totalSec / 86400
    val hours = (totalSec % 86400) / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return buildString {
        if (days > 0) append("${days}天 ")
        if (days > 0 || hours > 0) append(String.format(Locale.ROOT, "%02d时 ", hours))
        append(String.format(Locale.ROOT, "%02d分%02d秒", minutes, seconds))
    }
}

fun formatDate(epochMillis: Long): String? {
    if (epochMillis <= 0) return null
    return runCatching {
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(dateFormatter)
    }.getOrNull()
}

fun formatMonthDay(epochMillis: Long): String? {
    if (epochMillis <= 0) return null
    return runCatching {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        "${date.monthValue}月${date.dayOfMonth}日"
    }.getOrNull()
}

/** Time-of-day greeting, rendered above the home hero. */
fun greetingForHour(hour: Int = LocalTime.now().hour): String = when (hour) {
    in 5..8 -> "早上好"
    in 9..11 -> "上午好"
    in 12..13 -> "中午好"
    in 14..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "夜深了"
}

fun todayLabel(): String {
    val date = LocalDate.now()
    val weekday = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
    return "${date.monthValue}月${date.dayOfMonth}日 · $weekday"
}

/**
 * NetEase image CDNs add resize parameters; the app's network security config allows cleartext
 * only for loopback, so every remote artwork URL is upgraded to HTTPS.
 */
fun resolvedImageUrl(raw: String?, widthPx: Int? = null): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    var url = value.replaceFirst("http://", "https://")
    if (widthPx != null && !url.contains("param=")) {
        url += if (url.contains('?')) "&param=$widthPx" + "y$widthPx" else "?param=${widthPx}y$widthPx"
    }
    return url
}

/** A pseudo-random but stable visual seed so generated cover art never flickers. */
fun artworkSeed(vararg keys: Any?): Int = keys.fold(0x9E3779B9.toInt()) { acc, key ->
    acc * 31 + (key?.hashCode() ?: 0)
}

/** Formats byte counts into human-readable disk and memory sizes. */
fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
