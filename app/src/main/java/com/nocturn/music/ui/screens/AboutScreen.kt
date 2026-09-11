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

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "关于应用",
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (HyperOS Edition)",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                SmallTitle(text = "开源与社区")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "项目开源主页",
                        summary = "GitHub: XSong1205/Nocturn",
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
                SmallTitle(text = "技术架构与致谢")
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "UI 设计系统",
                        summary = "MIUIX KMP 0.9.3 (HyperOS 风格)",
                        onClick = { uriHandler.openUri("https://github.com/compose-miuix-ui/miuix") }
                    )
                    ArrowPreference(
                        title = "逐字歌词技术",
                        summary = "YRC 逐字逐句毫秒级动效"
                    )
                    ArrowPreference(
                        title = "流媒体数据引擎",
                        summary = "内置原生加解密 (WEAPI / EAPI)"
                    )
                    ArrowPreference(
                        title = "网络与并发",
                        summary = "OkHttp 5 · Kotlin Coroutines"
                    )
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}
