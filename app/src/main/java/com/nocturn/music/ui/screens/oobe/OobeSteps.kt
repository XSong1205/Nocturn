package com.nocturn.music.ui.screens.oobe

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nocturn.music.data.api.NcmApiClient
import com.nocturn.music.data.repository.MusicRepository
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.ApiMode
import com.nocturn.music.model.AudioQuality
import com.nocturn.music.ui.components.AsyncImage
import com.nocturn.music.ui.theme.HyperBlue
import com.nocturn.music.ui.theme.squircleCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import com.nocturn.music.ui.theme.AppIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 步骤 1: 欢迎与品牌核心亮点
 */
@Composable
fun OobeWelcomeStep(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Nocturn 标志性徽标
        Box(
            modifier = Modifier
                .size(92.dp)
                .squircleCard(28.dp)
                .background(MiuixTheme.colorScheme.primaryContainer)
                .border(
                    width = 2.dp,
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Music,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Nocturn",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "跨越星系，让音乐与灵感随行",
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 3 大核心亮点卡片
        OobeFeatureHighlightRow(
            icon = MiuixIcons.Music,
            title = "逐字动效歌词",
            description = "原生支持 YRC 逐字逐句毫秒级动感渲染与手势吸附交互"
        )

        Spacer(modifier = Modifier.height(12.dp))

        OobeFeatureHighlightRow(
            icon = MiuixIcons.Settings,
            title = "原生直连双模引擎",
            description = "本地逆向加密直连网易云官方网关，告别 30 秒试听与服务失效"
        )

        Spacer(modifier = Modifier.height(12.dp))

        OobeFeatureHighlightRow(
            icon = MiuixIcons.Favorites,
            title = "HyperOS 美学质感",
            description = "连续平滑超椭圆 Squircle 拟物质感与自适应高斯毛玻璃"
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "欢迎体验现代化开源网易云音乐客户端 · 点击下一步开始个性化配置",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun OobeFeatureHighlightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .squircleCard(16.dp)
            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.65f))
            .border(
                width = 1.dp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * 步骤 2: API 引擎选择 (双模网络引擎)
 */
@Composable
fun OobeEngineStep(
    modifier: Modifier = Modifier
) {
    val apiMode by SettingsRepository.apiMode.collectAsState()
    val customApiUrl by SettingsRepository.customApiUrl.collectAsState()

    var inputUrl by remember { mutableStateOf(customApiUrl) }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "选择 API 引擎架构",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Nocturn 原生支持双模引擎，保障全天候网络高可用性",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 内置引擎
        OobeSelectableCard(
            selected = apiMode == ApiMode.EMBEDDED,
            onClick = { SettingsRepository.setApiMode(ApiMode.EMBEDDED) },
            title = "内置原生引擎 (推荐)",
            subtitle = "本地加解密直连网易云官方网关，免外部依赖，速度最快且支持 VIP 会员无缝播放",
            icon = MiuixIcons.Settings,
            badgeText = "官方直连"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 自定义远程 API
        OobeSelectableCard(
            selected = apiMode == ApiMode.CUSTOM,
            onClick = { SettingsRepository.setApiMode(ApiMode.CUSTOM) },
            title = "自定义远程 API 代理",
            subtitle = "连接第三方或自建的 NeteaseCloudMusicApi 服务实例，支持自定义地址与连通性测速",
            icon = MiuixIcons.Refresh,
            badgeText = "自建扩展",
            extraContent = {
                AnimatedVisibility(
                    visible = apiMode == ApiMode.CUSTOM,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        TextField(
                            value = inputUrl,
                            onValueChange = {
                                inputUrl = it
                                SettingsRepository.setCustomApiUrl(it)
                            },
                            label = "API 服务器地址",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isTesting = true
                                        pingResult = "正在测试连接..."
                                        val (ok, latency) = NcmApiClient.ping(inputUrl)
                                        isTesting = false
                                        pingResult = if (ok) "✓ 连接成功 (${latency}ms)" else "✗ 连接超时或失败"
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = if (isTesting) "测速中..." else "测试连通性", fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    inputUrl = "https://ncmapi.rpixel.online"
                                    SettingsRepository.setCustomApiUrl(inputUrl)
                                    pingResult = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "恢复默认", fontSize = 13.sp)
                            }
                        }

                        if (pingResult != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = pingResult ?: "",
                                fontSize = 12.sp,
                                color = if (pingResult?.startsWith("✓") == true) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * 步骤 3: 网易云音乐账号登录 (扫码 / Cookie 同步)
 */
@Composable
fun OobeLoginStep(
    modifier: Modifier = Modifier
) {
    val userProfile by SettingsRepository.userProfile.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0: 扫码登录, 1: Cookie 导入

    // 扫码状态
    var qrKey by remember { mutableStateOf("") }
    var qrImgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrStatusText by remember { mutableStateOf("正在获取二维码...") }
    var isQrLoading by remember { mutableStateOf(false) }
    var qrJob by remember { mutableStateOf<Job?>(null) }

    // Cookie 状态
    var cookieInput by remember { mutableStateOf("") }
    var cookieMessage by remember { mutableStateOf<String?>(null) }
    var isVerifyingCookie by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun startQrFlow() {
        qrJob?.cancel()
        qrJob = scope.launch {
            isQrLoading = true
            qrStatusText = "正在生成二维码..."
            qrImgBitmap = null
            val key = MusicRepository.getQrKey()
            if (key.isNullOrBlank()) {
                qrStatusText = "获取二维码失败，请检查网络后点击刷新"
                isQrLoading = false
                return@launch
            }
            qrKey = key
            val qrCreate = MusicRepository.getQrCreate(key)
            if (qrCreate != null && qrCreate.second.isNotBlank()) {
                qrImgBitmap = decodeBase64ToBitmap(qrCreate.second)
            }
            isQrLoading = false
            qrStatusText = "请使用网易云音乐 APP 扫码"

            while (isActive) {
                delay(1300L)
                val (code, cookie) = MusicRepository.checkQrStatus(key)
                when (code) {
                    800 -> {
                        qrStatusText = "二维码已过期，点击重新获取"
                        break
                    }
                    801 -> {
                        qrStatusText = "请使用网易云音乐 APP 扫码"
                    }
                    802 -> {
                        qrStatusText = "✓ 已扫码，请在手机上点击「确认登录」"
                    }
                    803 -> {
                        qrStatusText = "✓ 登录成功，正在同步用户信息..."
                        val profile = if (cookie.isNotBlank()) {
                            MusicRepository.getUserAccount(cookie)
                        } else null
                        if (profile != null) {
                            SettingsRepository.saveUserProfile(profile)
                            MusicRepository.syncCloudFavorites()
                        }
                        break
                    }
                }
            }
        }
    }

    LaunchedEffect(userProfile.isLogin) {
        if (!userProfile.isLogin && selectedTab == 0) {
            startQrFlow()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            qrJob?.cancel()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "登录网易云音乐",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "登录以同步您的云端红心歌单、个人收藏与 VIP 会员特权",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (userProfile.isLogin) {
            // 已登录状态展示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleCard(20.dp)
                    .background(MiuixTheme.colorScheme.surface)
                    .border(
                        width = 1.5.dp,
                        color = MiuixTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primaryContainer)
                    ) {
                        if (userProfile.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                url = userProfile.avatarUrl,
                                contentDescription = "头像",
                                modifier = Modifier.size(72.dp)
                            )
                        } else {
                            Icon(
                                imageVector = MiuixIcons.Contacts,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .size(40.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = userProfile.nickname.ifBlank { "已登录用户" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )

                        if (userProfile.vipType > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF3A3A))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "VIP",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "UID: ${userProfile.userId} · 云端歌曲已自动同步",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            MusicRepository.logout()
                            startQrFlow()
                        }
                    ) {
                        Text(text = "切换其他账号", fontSize = 13.sp)
                    }
                }
            }
        } else {
            // 未登录：扫码与 Cookie 选项
            TabRow(
                tabs = listOf("扫码登录", "Cookie 导入"),
                selectedTabIndex = selectedTab,
                onTabSelected = { index ->
                    selectedTab = index
                    if (index == 0 && qrImgBitmap == null && !isQrLoading) {
                        startQrFlow()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // 扫码模式
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .squircleCard(18.dp)
                        .background(MiuixTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White)
                                .clickable {
                                    if (qrStatusText.contains("过期") || qrStatusText.contains("失败")) {
                                        startQrFlow()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrImgBitmap != null) {
                                Image(
                                    bitmap = qrImgBitmap!!.asImageBitmap(),
                                    contentDescription = "登录二维码",
                                    modifier = Modifier.size(154.dp)
                                )
                            } else {
                                Text(
                                    text = if (isQrLoading) "生成中..." else "点击重新获取",
                                    color = Color.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = qrStatusText,
                            fontSize = 13.sp,
                            color = if (qrStatusText.startsWith("✓")) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Cookie 导入模式
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .squircleCard(18.dp)
                        .background(MiuixTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = cookieInput,
                            onValueChange = { cookieInput = it },
                            label = "粘贴 MUSIC_U 或完整 Cookie",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (cookieInput.isBlank()) return@Button
                                scope.launch {
                                    isVerifyingCookie = true
                                    cookieMessage = "正在验证 Cookie..."
                                    val profile = MusicRepository.getUserAccount(cookieInput.trim())
                                    isVerifyingCookie = false
                                    if (profile != null && profile.isLogin) {
                                        SettingsRepository.saveUserProfile(profile)
                                        MusicRepository.syncCloudFavorites()
                                        cookieMessage = "✓ 登录成功！"
                                    } else {
                                        cookieMessage = "✗ Cookie 无效或已过期"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (isVerifyingCookie) "验证中..." else "验证并登录", color = Color.White)
                        }

                        if (cookieMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = cookieMessage ?: "",
                                fontSize = 12.sp,
                                color = if (cookieMessage?.startsWith("✓") == true) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "提示：未登录亦可正常搜索与试听免费曲目，可随时在「我的」界面登录",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * 步骤 4: 音质与外观偏好定制
 */
@Composable
fun OobePreferencesStep(
    modifier: Modifier = Modifier
) {
    val audioQuality by SettingsRepository.audioQuality.collectAsState()
    val themeMode by SettingsRepository.themeMode.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "音质与外观偏好",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "定制您的专属音频播放规格与界面显示风格",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "默认在线音频规格",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        val qualities = listOf(
            Triple(AudioQuality.STANDARD, "标准音质 (128kbps)", "流量友好，适合移动网络环境"),
            Triple(AudioQuality.HIGH, "极高音质 (320kbps)", "均衡选择，兼顾高保真与网络流畅度"),
            Triple(AudioQuality.LOSSLESS, "无损音质 (FLAC)", "CD 级母带音质，VIP 会员推荐"),
            Triple(AudioQuality.HI_RES, "Hi-Res 高解析", "24bit/96kHz 极致微动态听感体验")
        )

        qualities.forEach { (quality, label, desc) ->
            OobeSelectableCard(
                selected = audioQuality == quality,
                onClick = { SettingsRepository.setAudioQuality(quality) },
                title = label,
                subtitle = desc,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "主题外观模式",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val themes = listOf(
                0 to "跟随系统",
                1 to "浅色模式",
                2 to "深色模式"
            )

            themes.forEach { (mode, name) ->
                val isSelected = themeMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .squircleCard(14.dp)
                        .background(
                            if (isSelected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surface
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { SettingsRepository.setThemeMode(mode) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 步骤 5: 系统媒体集成与权限
 */
@Composable
fun OobePermissionsStep(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "系统媒体集成",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "开启关键权限以解锁 HyperOS 系统控制中心与锁屏播控体验",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 通知权限卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .squircleCard(18.dp)
                .background(MiuixTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = if (hasNotifPermission) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (hasNotifPermission) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Music,
                            contentDescription = null,
                            tint = if (hasNotifPermission) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "系统媒体控制中心 (通知权限)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "用于展示锁屏海报、滑动时间轴、上一曲/下一曲及红心控制",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (hasNotifPermission) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Check,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "已授予通知权限，控制中心联动就绪",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "立即授权通知权限", color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 后台播放保活卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .squircleCard(18.dp)
                .background(MiuixTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "后台持续播放 (电池优化)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "避免熄屏切到后台听歌时被系统内存优化策略意外清理",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "前往应用详情设置电池策略", fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * 步骤 6: 准备就绪
 */
@Composable
fun OobeCompleteStep(
    modifier: Modifier = Modifier
) {
    val apiMode by SettingsRepository.apiMode.collectAsState()
    val audioQuality by SettingsRepository.audioQuality.collectAsState()
    val userProfile by SettingsRepository.userProfile.collectAsState()
    val themeMode by SettingsRepository.themeMode.collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(18.dp))

        // 动感完成圆环
        Box(
            modifier = Modifier
                .size(88.dp)
                .squircleCard(26.dp)
                .background(MiuixTheme.colorScheme.primaryContainer)
                .border(
                    width = 2.dp,
                    color = MiuixTheme.colorScheme.primary,
                    shape = RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "一切准备就绪！",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "您的个性化视听体验已配置完毕",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 配置清单摘要卡片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .squircleCard(18.dp)
                .background(MiuixTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OobeSummaryItem(
                    label = "API 引擎架构",
                    value = if (apiMode == ApiMode.EMBEDDED) "内置官方原生直连" else "自定义远程 API"
                )
                OobeSummaryItem(
                    label = "网易云账号",
                    value = if (userProfile.isLogin) "${userProfile.nickname} (已登录)" else "未登录 (可随时在设置中登录)"
                )
                OobeSummaryItem(
                    label = "默认音质规格",
                    value = "${audioQuality.label} (${audioQuality.bitrate})"
                )
                OobeSummaryItem(
                    label = "主题外观",
                    value = when (themeMode) {
                        1 -> "浅色模式"
                        2 -> "深色模式"
                        else -> "跟随系统"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "戴上耳机，即刻跨越星系，沉浸于旋律之中 🪐",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OobeSummaryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

private fun decodeBase64ToBitmap(dataStr: String): Bitmap? {
    return try {
        val clean = if (dataStr.contains(",")) dataStr.substringAfter(",") else dataStr
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}
