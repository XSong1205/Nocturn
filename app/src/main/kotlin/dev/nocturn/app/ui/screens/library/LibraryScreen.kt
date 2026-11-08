@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.screens.library

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.data.model.AccountStats
import dev.nocturn.app.data.model.Song
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.GlassScaffold
import dev.nocturn.app.ui.components.GlassTopBar
import dev.nocturn.app.ui.components.LikedCollectionCover
import dev.nocturn.app.ui.components.MetricLabel
import dev.nocturn.app.ui.components.PlaylistCard
import dev.nocturn.app.ui.components.RecentCollectionCover
import dev.nocturn.app.ui.components.ScreenError
import dev.nocturn.app.ui.components.ScreenLoading
import dev.nocturn.app.ui.components.SectionHeader
import dev.nocturn.app.ui.components.VipTag
import dev.nocturn.app.ui.components.bottomContentPadding
import dev.nocturn.app.ui.components.pressScale
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.preview.PreviewData
import dev.nocturn.app.ui.state.LibraryUiState
import dev.nocturn.app.ui.theme.NocturnTheme
import dev.nocturn.app.ui.util.formatCount

/**
 * "我的" tab: a single expressive scroll surface that adapts to the session.
 *
 * Signed out it is an invitation: big brand hero, an explicit "third-party client" disclosure and
 * the QR login button, followed by the recent plays entry. Signed in the hero becomes the profile card (avatar, nickname,
 * signature, stats), followed by the liked-songs entry, recent plays entry, the account's playlists and a
 * guarded logout action. Loading, error and retry reuse the shared components so every state looks
 * the same as the rest of the app.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onLikedSongsClick: () -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onRecentSongsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    onLikeClick: (Song) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var lastLikedClickTime by remember { mutableLongStateOf(0L) }
    var lastRecentClickTime by remember { mutableLongStateOf(0L) }
    val onDebouncedLikedClick = {
        val now = SystemClock.elapsedRealtime()
        if (lastLikedClickTime == 0L || now - lastLikedClickTime >= 500L) {
            lastLikedClickTime = now
            onLikedSongsClick()
        }
    }
    val onDebouncedRecentClick = {
        val now = SystemClock.elapsedRealtime()
        if (lastRecentClickTime == 0L || now - lastRecentClickTime >= 500L) {
            lastRecentClickTime = now
            onRecentSongsClick()
        }
    }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val haptics = rememberHaptics()

    GlassScaffold(
        modifier = modifier,
        topBar = {
            GlassTopBar(scrolled = scrolled) {
                LibraryBarTitle()
                IconButton(
                    onClick = {
                        haptics.tap()
                        onSettingsClick()
                    },
                    modifier = Modifier.testTag("library-settings-button"),
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { topPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("library-screen"),
            contentPadding = PaddingValues(top = topPadding + 4.dp, bottom = bottomContentPadding()),
        ) {

            if (state.loading) {
                item(key = "loading") {
                    ScreenLoading(
                        label = "正在加载你的音乐库",
                        modifier = Modifier.testTag("library-loading"),
                    )
                }
            } else {
                val errorMessage = state.error
                if (errorMessage != null) {
                    item(key = "error") {
                        ScreenError(
                            message = errorMessage.orEmpty(),
                            onRetry = onRetry,
                            modifier = Modifier.testTag("library-error"),
                        )
                    }
                }
                val profile = state.profile
                if (state.loggedIn && profile != null) {
                    item(key = "profile") {
                        SignedInHero(profile = profile, stats = state.stats)
                    }
                    item(key = "liked-entry") {
                        LikedSongsEntry(
                            likedCount = state.likedCount,
                            onClick = onDebouncedLikedClick,
                        )
                    }
                } else {
                    item(key = "signed-out") { SignedOutHero(onLoginClick = onLoginClick) }
                }

                item(key = "recent-entry") {
                    RecentSongsEntry(
                        recentCount = state.recentSongs.size,
                        onClick = onDebouncedRecentClick,
                    )
                }

                if (state.loggedIn && state.playlists.isNotEmpty()) {
                    item(key = "playlists-header") {
                        Spacer(Modifier.height(12.dp))
                        SectionHeader(
                            title = "我的歌单",
                            subtitle = "${state.playlists.size} 个歌单",
                            modifier = Modifier.testTag("library-playlists"),
                        )
                    }
                    items(
                        state.playlists.chunked(2),
                        key = { row -> "playlists-${row.first().id}" },
                    ) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    onClick = { onPlaylistClick(playlist.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("library-playlist"),
                                    showCreator = true,
                                )
                            }
                            if (row.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (state.loggedIn) {
                item(key = "logout") {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            showLogoutDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(52.dp)
                            .testTag("library-logout-button"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("退出登录")
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text("退出登录") },
            text = { Text("退出后将清除保存在本机的加密会话。确定要退出登录吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        haptics.confirm()
                        showLogoutDialog = false
                        onLogout()
                    },
                    modifier = Modifier.testTag("library-logout-confirm"),
                ) {
                    Text("退出登录")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.tap()
                    showLogoutDialog = false
                }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun RowScope.LibraryBarTitle() {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(start = 16.dp, end = 16.dp),
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "账号、收藏与播放偏好",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Signed out hero
// ---------------------------------------------------------------------------------------------

@Composable
private fun SignedOutHero(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("library-signed-out"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialShapes.Cookie4Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = "登录网易云音乐",
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "登录后同步你的歌单、收藏与播放记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                haptics.confirm()
                onLoginClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("library-login-button"),
        ) {
            Icon(
                Icons.Filled.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text("选择登录方式", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(14.dp))
        ThirdPartyNotice()
    }
}

/** The disclosure the product needs: this is not the official client and credentials stay local. */
@Composable
private fun ThirdPartyNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("library-third-party-notice"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "第三方客户端",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "本应用为第三方客户端，与网易云音乐官方无关联。登录信息会提交给网易云音乐；本机仅加密保存登录会话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Signed in hero
// ---------------------------------------------------------------------------------------------

@Composable
private fun SignedInHero(
    profile: AccountProfile,
    stats: AccountStats?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("library-profile"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(
                    imageUrl = profile.avatarUrl,
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    seed = profile.id.hashCode(),
                    contentDescription = "${profile.nickname} 的头像",
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.nickname,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (profile.vipLevel > 0) {
                            Spacer(Modifier.width(8.dp))
                            VipTag()
                        }
                    }
                    profile.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = signature,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricLabel(
                    value = formatCount((stats?.playlistCount ?: 0).toLong()),
                    label = "歌单",
                )
                MetricLabel(
                    value = formatCount((stats?.followingCount ?: 0).toLong()),
                    label = "关注",
                )
                MetricLabel(
                    value = formatCount((stats?.followerCount ?: 0).toLong()),
                    label = "粉丝",
                )
            }
        }
    }
}

@Composable
private fun LikedSongsEntry(
    likedCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.tap()
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .pressScale(0.97f)
            .testTag("library-liked-entry"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LikedCollectionCover(size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "我喜欢的音乐",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (likedCount != null && likedCount > 0) {
                        "$likedCount 首歌曲"
                    } else {
                        "收藏的每一首都在这里"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentSongsEntry(
    recentCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.tap()
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .pressScale(0.97f)
            .testTag("library-recent-entry"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecentCollectionCover(size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "最近播放",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (recentCount > 0) {
                        "$recentCount 首歌曲"
                    } else {
                        "听过的每一首都在这里"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(name = "我的 · 未登录 · 浅色", showBackground = true)
@Composable
private fun LibrarySignedOutPreviewLight() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        LibraryScreen(state = PreviewData.libraryAnonymous)
    }
}

@Preview(name = "我的 · 已登录 · 深色", showBackground = true)
@Composable
private fun LibrarySignedInPreviewDark() {
    NocturnTheme(themeMode = ThemeMode.Dark) {
        LibraryScreen(state = PreviewData.libraryLoggedIn.copy(themeMode = ThemeMode.Dark))
    }
}
