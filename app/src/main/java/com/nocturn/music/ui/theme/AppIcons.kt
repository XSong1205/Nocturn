package com.nocturn.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort

object AppIcons {
    val Play: ImageVector get() = MiuixIcons.Play
    val Pause: ImageVector get() = MiuixIcons.Pause
    val Playlist: ImageVector get() = MiuixIcons.Playlist
    val PlaylistAdd: ImageVector get() = MiuixIcons.Playlist
    val More: ImageVector get() = MiuixIcons.More
    val Favorites: ImageVector get() = MiuixIcons.Favorites
    val FavoritesFill: ImageVector get() = MiuixIcons.FavoritesFill
    val Sort: ImageVector get() = MiuixIcons.Sort
    val Search: ImageVector get() = MiuixIcons.Search
    val Album: ImageVector get() = MiuixIcons.Album
    val Messages: ImageVector get() = MiuixIcons.Messages
    val Music: ImageVector get() = MiuixIcons.Music
    val Close: ImageVector get() = MiuixIcons.Close
    val Contacts: ImageVector get() = MiuixIcons.Contacts
    val Artist: ImageVector get() = MiuixIcons.Contacts
    val Settings: ImageVector get() = MiuixIcons.Settings
    val Refresh: ImageVector get() = MiuixIcons.Refresh

    val SkipNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipNext",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 18f)
                lineTo(14.5f, 12f)
                lineTo(6f, 6f)
                close()
                moveTo(16f, 6f)
                horizontalLineTo(18f)
                verticalLineTo(18f)
                horizontalLineTo(16f)
                close()
            }
        }.build()
    }

    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder(
            name = "SkipPrevious",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 6f)
                horizontalLineTo(8f)
                verticalLineTo(18f)
                horizontalLineTo(6f)
                close()
                moveTo(9.5f, 12f)
                lineTo(18f, 18f)
                verticalLineTo(6f)
                close()
            }
        }.build()
    }

    val Repeat: ImageVector by lazy {
        ImageVector.Builder(
            name = "Repeat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17f, 1f)
                lineTo(21f, 5f)
                lineTo(17f, 9f)
                moveTo(3f, 11f)
                verticalLineTo(9f)
                curveTo(3f, 6.79f, 4.79f, 5f, 7f, 5f)
                horizontalLineTo(21f)
                moveTo(7f, 23f)
                lineTo(3f, 19f)
                lineTo(7f, 15f)
                moveTo(21f, 13f)
                verticalLineTo(15f)
                curveTo(21f, 17.21f, 19.21f, 19f, 17f, 19f)
                horizontalLineTo(3f)
            }
        }.build()
    }

    val RepeatOne: ImageVector by lazy {
        ImageVector.Builder(
            name = "RepeatOne",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(17f, 1f)
                lineTo(21f, 5f)
                lineTo(17f, 9f)
                moveTo(3f, 11f)
                verticalLineTo(9f)
                curveTo(3f, 6.79f, 4.79f, 5f, 7f, 5f)
                horizontalLineTo(21f)
                moveTo(7f, 23f)
                lineTo(3f, 19f)
                lineTo(7f, 15f)
                moveTo(21f, 13f)
                verticalLineTo(15f)
                curveTo(21f, 17.21f, 19.21f, 19f, 17f, 19f)
                horizontalLineTo(3f)
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(11f, 10f)
                lineTo(12.5f, 9f)
                verticalLineTo(15f)
            }
        }.build()
    }

    val Shuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "Shuffle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16f, 3f)
                horizontalLineTo(21f)
                verticalLineTo(8f)
                moveTo(4f, 20f)
                lineTo(21f, 3f)
                moveTo(21f, 16f)
                verticalLineTo(21f)
                horizontalLineTo(16f)
                moveTo(15f, 15f)
                lineTo(21f, 21f)
                moveTo(4f, 4f)
                lineTo(9f, 9f)
            }
        }.build()
    }

    val ArrowDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowDown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 12.5f)
                lineTo(9.5f, 17f)
                lineTo(19f, 7.5f)
            }
        }.build()
    }

    val Share: ImageVector by lazy {
        ImageVector.Builder(
            name = "Share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(8.59f, 13.51f)
                lineTo(15.42f, 17.49f)
                moveTo(15.41f, 6.51f)
                lineTo(8.59f, 10.49f)
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(18f, 5f)
                moveTo(6f, 12f)
                moveTo(18f, 19f)
            }
        }.build()
    }
}
