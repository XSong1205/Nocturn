package dev.nocturn.app.ui.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.AccountProfile
import dev.nocturn.app.ui.components.CoverArt
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.state.AppTab

/**
 * Modern desktop navigation sidebar for PC / wide-screen experiences.
 *
 * Provides grouped navigation:
 * - Brand header ("Nocturn" + subhead)
 * - Online music tabs (Discover / Search)
 * - Personal library shortcuts (My Library / Liked Songs / Recent Play history)
 * - Footer with app Settings and User profile / login portal
 */
@Composable
fun DesktopSidebar(
    selectedTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onOpenLikedSongs: () -> Unit,
    onOpenRecentSongs: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogin: () -> Unit,
    userProfile: AccountProfile?,
    modifier: Modifier = Modifier,
    isCurrentScreenSettings: Boolean = false,
) {
    val haptics = rememberHaptics()

    Surface(
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .testTag("desktop-sidebar"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp, horizontal = 12.dp),
        ) {
            // ==================== Brand Header ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = "Nocturn",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column {
                    Text(
                        text = "Nocturn",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "网易云音乐",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ==================== Navigation Sections ====================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Section 1: Online
                SidebarSectionHeader(title = "在线音乐")

                DesktopSidebarItem(
                    label = "发现音乐",
                    icon = Icons.Filled.Home,
                    selected = selectedTab == AppTab.Home && !isCurrentScreenSettings,
                    testTag = "desktop-nav-home",
                    onClick = {
                        haptics.tap()
                        onSelectTab(AppTab.Home)
                    },
                )

                DesktopSidebarItem(
                    label = "搜索音乐",
                    icon = Icons.Filled.Search,
                    selected = selectedTab == AppTab.Search && !isCurrentScreenSettings,
                    testTag = "desktop-nav-search",
                    onClick = {
                        haptics.tap()
                        onSelectTab(AppTab.Search)
                    },
                )

                Spacer(Modifier.height(12.dp))

                // Section 2: Personal Library
                SidebarSectionHeader(title = "我的音乐")

                DesktopSidebarItem(
                    label = "音乐库",
                    icon = Icons.Filled.LibraryMusic,
                    selected = selectedTab == AppTab.Library && !isCurrentScreenSettings,
                    testTag = "desktop-nav-library",
                    onClick = {
                        haptics.tap()
                        onSelectTab(AppTab.Library)
                    },
                )

                DesktopSidebarItem(
                    label = "我喜欢的音乐",
                    icon = Icons.Filled.Favorite,
                    selected = false,
                    testTag = "desktop-nav-liked",
                    onClick = {
                        haptics.tap()
                        onOpenLikedSongs()
                    },
                )

                DesktopSidebarItem(
                    label = "最近播放",
                    icon = Icons.Filled.History,
                    selected = false,
                    testTag = "desktop-nav-recent",
                    onClick = {
                        haptics.tap()
                        onOpenRecentSongs()
                    },
                )
            }

            // ==================== Footer Section (Settings & Profile) ====================
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 10.dp),
            )

            DesktopSidebarItem(
                label = "设置",
                icon = Icons.Filled.Settings,
                selected = isCurrentScreenSettings,
                testTag = "desktop-nav-settings",
                onClick = {
                    haptics.tap()
                    onOpenSettings()
                },
            )

            Spacer(Modifier.height(6.dp))

            // User Profile Row / Login prompt
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = {
                        haptics.tap()
                        if (userProfile == null) onOpenLogin() else onSelectTab(AppTab.Library)
                    })
                    .testTag("desktop-sidebar-profile"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (userProfile != null) {
                        CoverArt(
                            imageUrl = userProfile.avatarUrl,
                            contentDescription = userProfile.nickname,
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.nickname,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (userProfile.vipLevel > 0) {
                                Text(
                                    text = "VIP 会员",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "未登录",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "未登录",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "点击登录账号",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Login,
                            contentDescription = "登录",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun DesktopSidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "",
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "item-bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "item-color",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
            )
        }
    }
}
