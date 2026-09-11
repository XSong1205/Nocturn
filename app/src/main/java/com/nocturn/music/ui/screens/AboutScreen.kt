package com.nocturn.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.BuildConfig
import com.nocturn.music.ui.navigation.LocalBottomBarPadding
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val bottomBarPadding = LocalBottomBarPadding.current

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于 Nocturn",
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = maxOf(bottomBarPadding + 24.dp, 96.dp)
            )
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MiuixTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Music,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MiuixTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Nocturn",
                        color = MiuixTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val formattedVersion = if (BuildConfig.VERSION_NAME.startsWith("v") || BuildConfig.VERSION_NAME.startsWith("alpha")) {
                        BuildConfig.VERSION_NAME
                    } else {
                        "v${BuildConfig.VERSION_NAME}"
                    }
                    Text(
                        text = formattedVersion,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "基于 HyperOS 风格的现代高保真网易云音乐播放器",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                SmallTitle(text = "项目与开源")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "GitHub 开源主页",
                        summary = "XSong1205/Nocturn",
                        onClick = { uriHandler.openUri("https://github.com/XSong1205/Nocturn") }
                    )
                    ArrowPreference(
                        title = "开源许可协议",
                        summary = "Apache License 2.0",
                        onClick = { uriHandler.openUri("https://www.apache.org/licenses/LICENSE-2.0.txt") }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            item {
                SmallTitle(text = "开源鸣谢")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "miuix",
                        summary = "组件库 · HyperOS 风格跨平台 Compose 组件系统",
                        onClick = { uriHandler.openUri("https://github.com/compose-miuix-ui/miuix") }
                    )
                    ArrowPreference(
                        title = "accompanist-lyrics-ui",
                        summary = "歌词显示 · 逐字逐句毫秒级动态歌词渲染引擎",
                        onClick = { uriHandler.openUri("https://github.com/MochaRealm/accompanist-lyrics") }
                    )
                    ArrowPreference(
                        title = "Lyricon (词幕)",
                        summary = "状态栏歌词 · 现代 Android 状态栏与悬浮窗逐字歌词框架",
                        onClick = { uriHandler.openUri("https://github.com/tomakino/lyricon") }
                    )
                    ArrowPreference(
                        title = "NCMApiEnhanced",
                        summary = "API 服务 · 网易云音乐 Node.js API 增强服务",
                        onClick = { uriHandler.openUri("https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced") }
                    )
                    ArrowPreference(
                        title = "splayer-android",
                        summary = "内置 API 思路 · 原生逆向与官方接口鉴权设计参考",
                        onClick = { uriHandler.openUri("https://github.com/SPlayer-Dev/SPlayer-for-Android") }
                    )
                    ArrowPreference(
                        title = "OkHttp",
                        summary = "网络引擎 · Square 现代 HTTP/2 连接池与网络库",
                        onClick = { uriHandler.openUri("https://github.com/square/okhttp") }
                    )
                    ArrowPreference(
                        title = "KotlinX",
                        summary = "并发与序列化 · 官方协程架构与轻量级高效 JSON 编解码",
                        onClick = { uriHandler.openUri("https://github.com/Kotlin/kotlinx.coroutines") }
                    )
                    ArrowPreference(
                        title = "Compose Multiplatform",
                        summary = "声明式 UI · JetBrains 现代响应式用户界面框架",
                        onClick = { uriHandler.openUri("https://github.com/JetBrains/compose-multiplatform") }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            item {
                SmallTitle(text = "技术架构与核心特性")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "UI 设计语言",
                        summary = "MIUIX KMP 0.9.3 (HyperOS 风格 · 连续平滑超椭圆)"
                    )
                    ArrowPreference(
                        title = "双模 API 引擎",
                        summary = "内置原生加解密直连官方网关 / 增强型自建 API 双模"
                    )
                    ArrowPreference(
                        title = "VIP 高保真播放",
                        summary = "原生 EAPI + WEAPI 会员鉴权与防 30 秒试听链路"
                    )
                    ArrowPreference(
                        title = "逐字动效歌词",
                        summary = "YRC 逐字逐句毫秒级动态平滑高亮与手势交互吸附"
                    )
                    ArrowPreference(
                        title = "系统媒体集成",
                        summary = "MediaSessionCompat · 锁屏海报与控制中心深度联动"
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
