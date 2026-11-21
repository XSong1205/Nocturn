package dev.nocturn.app.data.model

import dev.nocturn.localapi.client.AudioQuality
import dev.nocturn.localapi.client.ImmerseType

enum class ThemeMode { System, Light, Dark }

fun ThemeMode.displayName(): String = when (this) {
    ThemeMode.System -> "跟随系统"
    ThemeMode.Light -> "浅色"
    ThemeMode.Dark -> "深色"
}

enum class ColorSchemeKey {
    M3eDynamic,
    M3Dynamic,
    M3eOfficial,
    M3Baseline,
    Amber,
    Ocean,
    Mint,
    Rose,
    NocturnClassic,
    Custom,
}

val ColorSchemeKey.isDynamic: Boolean
    get() = this == ColorSchemeKey.M3eDynamic || this == ColorSchemeKey.M3Dynamic

fun ColorSchemeKey.displayName(): String = when (this) {
    ColorSchemeKey.M3eDynamic -> "M3E 动态取色"
    ColorSchemeKey.M3Dynamic -> "M3 动态取色"
    ColorSchemeKey.M3eOfficial -> "M3 Expressive"
    ColorSchemeKey.M3Baseline -> "Material 3 标准"
    ColorSchemeKey.Amber -> "暖阳金"
    ColorSchemeKey.Ocean -> "晴空蓝"
    ColorSchemeKey.Mint -> "翡翠绿"
    ColorSchemeKey.Rose -> "樱花粉"
    ColorSchemeKey.NocturnClassic -> "Nocturn 经典"
    ColorSchemeKey.Custom -> "自定义"
}

fun ColorSchemeKey.description(): String = when (this) {
    ColorSchemeKey.M3eDynamic -> "基于系统壁纸与 Material 3 Expressive 动态高对比度提取"
    ColorSchemeKey.M3Dynamic -> "基于系统壁纸与标准 Material 3 Tonal Spot 色调提取"
    ColorSchemeKey.M3eOfficial -> "Material 3 Expressive 最新官方默认基线配色"
    ColorSchemeKey.M3Baseline -> "Material 3 经典官方基线紫色系"
    ColorSchemeKey.Amber -> "明媚温润的暖阳琥珀金调色板"
    ColorSchemeKey.Ocean -> "清透辽阔的晴空碧海蓝调色板"
    ColorSchemeKey.Mint -> "自然清新的翡翠薄荷绿调色板"
    ColorSchemeKey.Rose -> "浪漫轻柔的樱花玫瑰粉调色板"
    ColorSchemeKey.NocturnClassic -> "Nocturn 原版珊瑚暖光与夜幕高对比度经典配色"
    ColorSchemeKey.Custom -> "自定义种子色彩生成的 Material 3 调色板"
}

enum class DynamicVariant {
    Expressive,
    Content,
    TonalSpot,
    Vibrant,
    FruitSalad,
    Monochrome,
}

fun DynamicVariant.displayName(): String = when (this) {
    DynamicVariant.Expressive -> "表现力"
    DynamicVariant.Content -> "内容主题"
    DynamicVariant.TonalSpot -> "标准平衡"
    DynamicVariant.Vibrant -> "鲜艳活力"
    DynamicVariant.FruitSalad -> "缤纷果盘"
    DynamicVariant.Monochrome -> "单色黑白"
}

fun DynamicVariant.description(): String = when (this) {
    DynamicVariant.Expressive -> "高饱和度与丰富对比，凸显 Material 3 Expressive 层次感"
    DynamicVariant.Content -> "忠实提取并重现提取源的色彩特征"
    DynamicVariant.TonalSpot -> "官方标准平衡色调阶梯（Android 默认）"
    DynamicVariant.Vibrant -> "最高色彩鲜活度与活泼动感的视觉张力"
    DynamicVariant.FruitSalad -> "趣味的多色相交织渐变色盘"
    DynamicVariant.Monochrome -> "克制纯粹的无彩色黑白灰度调色板"
}

fun AudioQuality.displayName(): String = when (this) {
    AudioQuality.Standard -> "标准"
    AudioQuality.Higher -> "较高"
    AudioQuality.Exhigh -> "极高"
    AudioQuality.Lossless -> "无损"
    AudioQuality.Hires -> "Hi-Res"
    AudioQuality.Jyeffect -> "高清臻音"
    AudioQuality.Dolby -> "杜比全景声"
    AudioQuality.Vivid -> "臻音全景声"
    AudioQuality.Jymaster -> "超清母带"
    AudioQuality.Sky -> "沉浸环绕声"
}

fun ImmerseType.displayName(): String = when (this) {
    ImmerseType.C512 -> "新版 c51 类型"
    ImmerseType.Ste2 -> "新版环绕立体声"
    ImmerseType.Aac2 -> "新版 aac 类型"
    ImmerseType.C51 -> "c51 类型 (默认)"
    ImmerseType.Ste -> "环绕立体声"
    ImmerseType.Aac -> "aac 类型"
}

/** The quality choices offered in settings, best-effort: availability depends on the account and device. */
val selectableQualities: List<AudioQuality> = listOf(
    AudioQuality.Standard,
    AudioQuality.Higher,
    AudioQuality.Exhigh,
    AudioQuality.Lossless,
    AudioQuality.Hires,
    AudioQuality.Jyeffect,
    AudioQuality.Dolby,
    AudioQuality.Vivid,
    AudioQuality.Jymaster,
    AudioQuality.Sky,
)

val selectableImmerseTypes: List<ImmerseType> = listOf(
    ImmerseType.C51,
    ImmerseType.C512,
    ImmerseType.Ste,
    ImmerseType.Ste2,
    ImmerseType.Aac,
    ImmerseType.Aac2,
)
