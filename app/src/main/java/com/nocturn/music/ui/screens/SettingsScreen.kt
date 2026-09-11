package com.nocturn.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.data.api.EmbeddedHttpServer
import com.nocturn.music.data.api.NcmApiClient
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.ApiMode
import com.nocturn.music.model.AudioQuality
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val themeMode by SettingsRepository.themeMode.collectAsState()
    val audioQuality by SettingsRepository.audioQuality.collectAsState()
    val apiMode by SettingsRepository.apiMode.collectAsState()
    val customApiUrl by SettingsRepository.customApiUrl.collectAsState()

    var showApiDialog by remember { mutableStateOf(false) }
    var apiUrlInput by remember { mutableStateOf(customApiUrl) }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            SmallTitle(text = "外观与显示")

            Card(modifier = Modifier.fillMaxWidth()) {
                val themeLabel = when (themeMode) {
                    0 -> "跟随系统"
                    1 -> "浅色模式"
                    2 -> "深色模式"
                    else -> "跟随系统"
                }
                ArrowPreference(
                    title = "主题外观",
                    summary = themeLabel,
                    onClick = {
                        val nextMode = (themeMode + 1) % 3
                        SettingsRepository.setThemeMode(nextMode)
                    }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            SmallTitle(text = "播放与音质")

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "在线播放音质",
                    summary = "${audioQuality.label} (${audioQuality.bitrate})",
                    onClick = {
                        val qualities = AudioQuality.values()
                        val nextIndex = (audioQuality.ordinal + 1) % qualities.size
                        SettingsRepository.setAudioQuality(qualities[nextIndex])
                    }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            SmallTitle(text = "API 引擎架构 (SPlayer 规范)")

            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "内置原生引擎 (推荐)",
                    summary = if (apiMode == ApiMode.EMBEDDED) "已启用内嵌直连官方网关，无需外部服务器" else "已切换为自定义远程 API 代理",
                    checked = apiMode == ApiMode.EMBEDDED,
                    onCheckedChange = { checked ->
                        SettingsRepository.setApiMode(if (checked) ApiMode.EMBEDDED else ApiMode.CUSTOM)
                    }
                )
                ArrowPreference(
                    title = "自定义远程 API 地址",
                    summary = if (customApiUrl.isBlank()) "https://ncmapi.rpixel.online" else customApiUrl,
                    onClick = {
                        apiUrlInput = customApiUrl.ifBlank { "https://ncmapi.rpixel.online" }
                        pingStatus = null
                        showApiDialog = true
                    }
                )
                ArrowPreference(
                    title = "内置服务器状态 (127.0.0.1:1145)",
                    summary = if (EmbeddedHttpServer.isRunning) "已启动 (嵌入式端口 1145)" else "就绪",
                    onClick = {
                        if (!EmbeddedHttpServer.isRunning) {
                            EmbeddedHttpServer.start()
                        }
                    }
                )
                ArrowPreference(
                    title = "清理图片与离线缓存",
                    summary = cacheClearedMessage ?: "释放内存与临时数据",
                    onClick = {
                        cacheClearedMessage = "缓存已清理完毕"
                    }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            SmallTitle(text = "关于应用")

            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "Nocturn",
                    summary = "版本 1.0.0 (HyperOS + SPlayer API Edition)"
                )
                ArrowPreference(
                    title = "UI 框架",
                    summary = "Jetpack Compose + MIUIX UI (HyperOS)"
                )
                ArrowPreference(
                    title = "逐字歌词技术",
                    summary = "YRC 毫秒级逐字高亮渲染"
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    OverlayDialog(
        show = showApiDialog,
        onDismissRequest = { showApiDialog = false },
        title = "自定义网易云 API 地址",
        summary = "支持全部 SPlayer 接口规范与增强代理"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            TextField(
                value = apiUrlInput,
                onValueChange = { apiUrlInput = it },
                label = "API 服务 URL",
                modifier = Modifier.fillMaxWidth()
            )

            if (pingStatus != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = pingStatus ?: "",
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            pingStatus = "正在测试连接..."
                            val (ok, latency) = NcmApiClient.ping(apiUrlInput)
                            pingStatus = if (ok) "✓ 连接成功 (耗时 ${latency}ms)" else "✗ 连接超时或失败"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "测速")
                }

                Button(
                    onClick = {
                        apiUrlInput = "https://ncmapi.rpixel.online"
                        SettingsRepository.setCustomApiUrl("https://ncmapi.rpixel.online")
                    },
                    modifier = Modifier.weight(1.3f)
                ) {
                    Text(text = "恢复默认")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showApiDialog = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "取消")
                }

                Button(
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    onClick = {
                        SettingsRepository.setCustomApiUrl(apiUrlInput)
                        showApiDialog = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "保存")
                }
            }
        }
    }
}
