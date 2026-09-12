package com.nocturn.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.BuildConfig
import com.nocturn.music.data.api.EmbeddedHttpServer
import com.nocturn.music.data.api.NcmApiClient
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.ApiMode
import com.nocturn.music.model.AudioQuality
import com.nocturn.music.ui.navigation.LocalBottomBarPadding
import com.nocturn.music.ui.navigation.LocalTopBarPadding
import com.nocturn.music.ui.navigation.SecondaryRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    onOpenRoute: (SecondaryRoute) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeMode by SettingsRepository.themeMode.collectAsState()
    val audioQuality by SettingsRepository.audioQuality.collectAsState()
    val apiMode by SettingsRepository.apiMode.collectAsState()
    val customApiUrl by SettingsRepository.customApiUrl.collectAsState()
    val lyriconEnabled by SettingsRepository.lyriconEnabled.collectAsState()

    val isBlurEnabled by SettingsRepository.isBlurEnabled.collectAsState()
    val isAutoLosslessVip by SettingsRepository.isAutoLosslessVip.collectAsState()
    val isCrossfadeEnabled by SettingsRepository.isCrossfadeEnabled.collectAsState()
    val lyricOffsetMs by SettingsRepository.lyricOffsetMs.collectAsState()
    val isYrcHighlightEnabled by SettingsRepository.isYrcHighlightEnabled.collectAsState()
    val isCellularDataSaver by SettingsRepository.isCellularDataSaver.collectAsState()

    var showApiDialog by remember { mutableStateOf(false) }
    var apiUrlInput by remember { mutableStateOf(customApiUrl) }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var cacheActionMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cacheBytes = SettingsRepository.getCacheSizeBytes()
        }
    }

    val bottomBarPadding = LocalBottomBarPadding.current
    val topBarPadding = LocalTopBarPadding.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topBarPadding + 12.dp,
            bottom = maxOf(bottomBarPadding + 16.dp, 100.dp)
        )
    ) {
        // =========================================================================
        // 1. 外观与显示动效
        // =========================================================================
        item {
            SmallTitle(text = "外观与动效")

            Card(modifier = Modifier.fillMaxWidth()) {
                val themeOptions = listOf("跟随系统", "浅色模式", "深色模式")
                OverlayDropdownPreference(
                    title = "主题外观",
                    summary = "当前为: ${themeOptions.getOrElse(themeMode) { "跟随系统" }}",
                    items = themeOptions,
                    selectedIndex = themeMode,
                    onSelectedIndexChange = { SettingsRepository.setThemeMode(it) }
                )

                SwitchPreference(
                    title = "界面动态毛玻璃效果",
                    summary = if (isBlurEnabled) "开启背景实时着色器模糊，呈现 HyperOS 通透质感" else "已关闭动态模糊以降低功耗并提高流畅度",
                    checked = isBlurEnabled,
                    onCheckedChange = { SettingsRepository.setBlurEnabled(it) }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // =========================================================================
        // 2. 播放与音质控制
        // =========================================================================
        item {
            SmallTitle(text = "播放与音质")

            Card(modifier = Modifier.fillMaxWidth()) {
                val qualities = AudioQuality.values()
                val qualityOptions = qualities.map { "${it.label} (${it.bitrate})" }
                OverlayDropdownPreference(
                    title = "在线播放音质",
                    summary = "默认音质: ${audioQuality.label} (${audioQuality.bitrate})",
                    items = qualityOptions,
                    selectedIndex = audioQuality.ordinal,
                    onSelectedIndexChange = { SettingsRepository.setAudioQuality(qualities[it]) }
                )

                SwitchPreference(
                    title = "VIP 会员歌曲无损优先",
                    summary = if (isAutoLosslessVip) "登录后优先通过官方 EAPI 鉴权调度最高可用码率" else "按默认选定音质播放",
                    checked = isAutoLosslessVip,
                    onCheckedChange = { SettingsRepository.setAutoLosslessVip(it) }
                )

                SwitchPreference(
                    title = "切歌与暂停淡入淡出",
                    summary = if (isCrossfadeEnabled) "切歌与暂停时平滑过渡音量，消除爆音" else "立即切换",
                    checked = isCrossfadeEnabled,
                    onCheckedChange = { SettingsRepository.setCrossfadeEnabled(it) }
                )

                SwitchPreference(
                    title = "逐字动态歌词高亮 (YRC)",
                    summary = if (isYrcHighlightEnabled) "使用 Canvas 逐字平滑渐进染色渲染" else "使用普通逐行平滑高亮",
                    checked = isYrcHighlightEnabled,
                    onCheckedChange = { SettingsRepository.setYrcHighlightEnabled(it) }
                )

                SliderPreference(
                    title = "歌词时间轴微调 (毫秒)",
                    summary = if (lyricOffsetMs == 0) "当前时间轴精准同步 (0ms)" else "偏移: ${if (lyricOffsetMs > 0) "+$lyricOffsetMs" else "$lyricOffsetMs"}ms",
                    value = lyricOffsetMs.toFloat(),
                    valueRange = -1000f..1000f,
                    onValueChange = { SettingsRepository.setLyricOffsetMs(it.toInt()) }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // =========================================================================
        // 3. 网络与存储缓存
        // =========================================================================
        item {
            SmallTitle(text = "网络与存储")

            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "移动网络省流模式",
                    summary = if (isCellularDataSaver) "蜂窝网络下自动将音质调整为标准 128k 节省流量" else "始终使用选定音质",
                    checked = isCellularDataSaver,
                    onCheckedChange = { SettingsRepository.setCellularDataSaver(it) }
                )

                SwitchPreference(
                    title = "内置原生引擎 (推荐)",
                    summary = if (apiMode == ApiMode.EMBEDDED) "直连网易云官方网关，无需外部第三方服务器" else "已切换为自定义远程 API 代理",
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
                    title = "清理图片与媒体缓存",
                    summary = cacheActionMessage ?: "已占用磁盘空间: ${SettingsRepository.formatCacheSize(cacheBytes)}",
                    onClick = {
                        scope.launch {
                            val cleared = withContext(Dispatchers.IO) {
                                SettingsRepository.clearAppCache()
                            }
                            cacheBytes = 0L
                            cacheActionMessage = "已释放 ${SettingsRepository.formatCacheSize(cleared)} 磁盘空间"
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // =========================================================================
        // 4. 扩展与系统集成
        // =========================================================================
        item {
            SmallTitle(text = "扩展与系统联动")

            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "词幕 (Lyricon) 歌词联动",
                    summary = if (lyriconEnabled) "已启用状态栏/灵动岛/悬浮窗歌词与播放状态实时同步" else "未启用",
                    checked = lyriconEnabled,
                    onCheckedChange = { checked ->
                        SettingsRepository.setLyriconEnabled(checked)
                    }
                )

                ArrowPreference(
                    title = "开机引导向导 (OOBE)",
                    summary = "重新体验 HyperOS 风格初始化欢迎流程",
                    onClick = { SettingsRepository.setOobeCompleted(false) }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // =========================================================================
        // 5. 关于应用
        // =========================================================================
        item {
            SmallTitle(text = "关于")

            Card(modifier = Modifier.fillMaxWidth()) {
                val formattedVersion = if (BuildConfig.VERSION_NAME.startsWith("v") || BuildConfig.VERSION_NAME.startsWith("alpha")) {
                    BuildConfig.VERSION_NAME
                } else {
                    "v${BuildConfig.VERSION_NAME}"
                }
                ArrowPreference(
                    title = "关于 Nocturn",
                    summary = "版本 $formattedVersion / HyperOS 音乐客户端 / 开源致谢",
                    onClick = { onOpenRoute(SecondaryRoute.About) }
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    OverlayDialog(
        show = showApiDialog,
        onDismissRequest = { showApiDialog = false },
        title = "自定义网易云 API 地址",
        summary = "支持自定义第三方网易云 API 代理服务"
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
