package com.nocturn.music.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nocturn.music.data.repository.MusicRepository
import com.nocturn.music.data.repository.SettingsRepository
import com.nocturn.music.model.Playlist
import com.nocturn.music.model.UserProfile
import com.nocturn.music.player.NocturnPlayer
import com.nocturn.music.ui.components.AsyncImage
import com.nocturn.music.ui.components.SongListItem
import com.nocturn.music.ui.theme.HyperBlue
import com.nocturn.music.ui.theme.HyperRed
import com.nocturn.music.ui.theme.squircleCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.nocturn.music.ui.navigation.SecondaryRoute

@Composable
fun MyScreen(
    onNavigateToSettings: () -> Unit,
    onOpenRoute: (SecondaryRoute) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val userProfile by SettingsRepository.userProfile.collectAsState()
    val favoriteSongs by SettingsRepository.favoriteSongs.collectAsState()
    val currentPlayingSong by NocturnPlayer.currentSong.collectAsState()
    val isPlaying by NocturnPlayer.isPlaying.collectAsState()

    var showLoginDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var loginTab by remember { mutableStateOf(0) } // 0: 验证码登录, 1: 扫码登录, 2: Cookie 登录

    // 手机号验证码状态
    var phoneInput by remember { mutableStateOf("") }
    var captchaInput by remember { mutableStateOf("") }
    var countdownSeconds by remember { mutableStateOf(0) }
    var isSendingCaptcha by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var smsStatusMessage by remember { mutableStateOf<String?>(null) }
    var smsIsError by remember { mutableStateOf(false) }

    // 扫码登录状态
    var qrKey by remember { mutableStateOf("") }
    var qrImgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var qrStatusText by remember { mutableStateOf("正在获取二维码...") }
    var isQrLoading by remember { mutableStateOf(false) }
    var qrPollingJob by remember { mutableStateOf<Job?>(null) }

    // Cookie 状态
    var cookieInput by remember { mutableStateOf("") }
    var cookieStatusMessage by remember { mutableStateOf<String?>(null) }

    // 用户歌单
    var userPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }

    val scope = rememberCoroutineScope()

    var isSyncingFavorites by remember { mutableStateOf(false) }

    // 加载用户歌单与自动同步云端红心歌曲
    LaunchedEffect(userProfile.userId, userProfile.isLogin) {
        if (userProfile.isLogin && userProfile.userId > 0) {
            userPlaylists = MusicRepository.getUserPlaylists(userProfile.userId)
            // 自动同步云端红心歌单
            MusicRepository.syncCloudFavorites()
        } else {
            userPlaylists = emptyList()
        }
    }

    // 启动扫码流程
    fun startQrLogin() {
        qrPollingJob?.cancel()
        qrPollingJob = scope.launch {
            isQrLoading = true
            qrStatusText = "正在生成二维码..."
            qrImgBitmap = null
            val key = MusicRepository.getQrKey()
            if (key.isNullOrBlank()) {
                qrStatusText = "获取二维码失败，请重试"
                isQrLoading = false
                return@launch
            }
            qrKey = key
            val qrCreate = MusicRepository.getQrCreate(key)
            if (qrCreate != null && qrCreate.second.isNotBlank()) {
                qrImgBitmap = decodeBase64ToBitmap(qrCreate.second)
            }
            isQrLoading = false
            qrStatusText = "请使用网易云音乐 APP 扫码登录"

            while (isActive) {
                delay(1200L)
                val (code, cookie) = MusicRepository.checkQrStatus(key)
                when (code) {
                    800 -> {
                        qrStatusText = "二维码已过期，点击重新获取"
                        break
                    }
                    801 -> {
                        qrStatusText = "请使用网易云音乐 APP 扫码登录"
                    }
                    802 -> {
                        qrStatusText = "✓ 已扫码，请在手机上点击「确认登录」"
                    }
                    803 -> {
                        qrStatusText = "✓ 登录成功，正在获取用户信息..."
                        val profile = if (cookie.isNotBlank()) {
                            MusicRepository.getUserAccount(cookie)
                        } else null
                        if (profile != null) {
                            SettingsRepository.saveUserProfile(profile)
                        } else {
                            SettingsRepository.saveUserProfile(
                                UserProfile(nickname = "云村村民", isLogin = true, cookie = cookie)
                            )
                        }
                        delay(600L)
                        showLoginDialog = false
                        // 后台自动同步红心歌单与用户歌单
                        scope.launch {
                            MusicRepository.syncCloudFavorites()
                        }
                        break
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, bottom = 100.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!userProfile.isLogin) {
                            showLoginDialog = true
                        } else {
                            showAccountDialog = true
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        if (userProfile.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                url = userProfile.avatarUrl,
                                contentDescription = userProfile.nickname,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(text = "👤", fontSize = 26.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = userProfile.nickname,
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (userProfile.vipType > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(HyperRed)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
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

                        Text(
                            text = if (userProfile.isLogin) {
                                if (userProfile.signature.isNotBlank()) userProfile.signature else "UID: ${userProfile.userId}"
                            } else {
                                "点击登录网易云账号，同步红心歌单"
                            },
                            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (!userProfile.isLogin) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(HyperBlue)
                                .clickable { showLoginDialog = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(text = "登录", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                                .clickable { showAccountDialog = true }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(text = "管理", color = MiuixTheme.colorScheme.onSurfaceSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        item {
            val favPlaylist = userPlaylists.firstOrNull { it.name.contains("喜欢的音乐") } ?: userPlaylists.firstOrNull()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .squircleCard(20.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFE53935), Color(0xFFFF7043))
                        )
                    )
                    .clickable {
                        if (favPlaylist != null) {
                            onOpenRoute(SecondaryRoute.Playlist(favPlaylist.id, favPlaylist.name, favPlaylist.coverUrl))
                        } else {
                            onOpenRoute(SecondaryRoute.Favorites)
                        }
                    }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "♥", color = Color.White, fontSize = 24.sp)
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "我喜欢的音乐",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "共 ${favoriteSongs.size} 首歌曲",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                                if (userProfile.isLogin) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.25f))
                                            .clickable {
                                                if (!isSyncingFavorites) {
                                                    scope.launch {
                                                        isSyncingFavorites = true
                                                        MusicRepository.syncCloudFavorites()
                                                        userPlaylists = MusicRepository.getUserPlaylists(userProfile.userId)
                                                        isSyncingFavorites = false
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isSyncingFavorites) "同步中..." else "⟳ 同步云端",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                if (favoriteSongs.isNotEmpty()) {
                                    NocturnPlayer.playQueue(favoriteSongs, 0)
                                } else if (favPlaylist != null) {
                                    scope.launch {
                                        val songs = MusicRepository.syncCloudFavorites()
                                        if (songs.isNotEmpty()) {
                                            NocturnPlayer.playQueue(songs, 0)
                                        } else {
                                            onOpenRoute(SecondaryRoute.Playlist(favPlaylist.id, favPlaylist.name, favPlaylist.coverUrl))
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "▶", color = Color(0xFFE53935), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }

        // 3. 用户创建与收藏的歌单（登录后显示）
        if (userProfile.isLogin && userPlaylists.isNotEmpty()) {
            item {
                SmallTitle(
                    text = "我的歌单",
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(userPlaylists) { playlist ->
                        Card(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable { onOpenRoute(SecondaryRoute.Playlist(playlist.id, playlist.name, playlist.coverUrl)) }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(114.dp)
                                        .squircleCard(12.dp)
                                ) {
                                    AsyncImage(
                                        url = playlist.coverUrl,
                                        contentDescription = playlist.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = playlist.name,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${playlist.trackCount} 首",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallTitle(
                    text = "收藏歌曲",
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (favoriteSongs.isNotEmpty()) {
                    Text(
                        text = "播放全部",
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                NocturnPlayer.playQueue(favoriteSongs, 0)
                            }
                            .padding(4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (favoriteSongs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无收藏歌曲\n在播放器点击红心即可添加",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(favoriteSongs) { song ->
                SongListItem(
                    song = song,
                    onClick = { NocturnPlayer.playSong(song) },
                    isPlaying = currentPlayingSong?.id == song.id && isPlaying,
                    showCover = true
                )
            }
        }
    }

    // 登录弹窗 (短信验证码 / 扫码 / Cookie)
    OverlayDialog(
        show = showLoginDialog,
        onDismissRequest = {
            qrPollingJob?.cancel()
            showLoginDialog = false
        },
        title = "登录网易云音乐"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            val loginTabs = listOf("验证码登录", "扫码登录", "Cookie 登录")
            TabRow(
                tabs = loginTabs,
                selectedTabIndex = loginTab,
                onTabSelected = { idx ->
                    loginTab = idx
                    if (idx == 1) {
                        startQrLogin()
                    } else {
                        qrPollingJob?.cancel()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tab 0: 短信验证码登录
            if (loginTab == 0) {
                        Text(
                            text = "输入手机号获取短信验证码，快速安全登录",
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // 手机号输入框
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                                    .padding(horizontal = 10.dp, vertical = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+86",
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it.filter { c -> c.isDigit() } },
                                label = "手机号码",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 验证码输入框与发送按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = captchaInput,
                                onValueChange = { captchaInput = it.filter { c -> c.isDigit() } },
                                label = "短信验证码",
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (countdownSeconds == 0 && !isSendingCaptcha) {
                                        if (phoneInput.length != 11) {
                                            smsStatusMessage = "请输入 11 位手机号码"
                                            smsIsError = true
                                            return@Button
                                        }
                                        scope.launch {
                                            isSendingCaptcha = true
                                            smsStatusMessage = "正在发送验证码..."
                                            smsIsError = false
                                            val (success, msg) = MusicRepository.sendCaptcha(phoneInput)
                                            isSendingCaptcha = false
                                            if (success) {
                                                smsStatusMessage = "验证码已发送至 $phoneInput"
                                                smsIsError = false
                                                countdownSeconds = 60
                                                while (countdownSeconds > 0) {
                                                    delay(1000L)
                                                    countdownSeconds--
                                                }
                                            } else {
                                                smsStatusMessage = msg
                                                smsIsError = true
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.height(50.dp)
                            ) {
                                Text(
                                    text = when {
                                        countdownSeconds > 0 -> "${countdownSeconds}s"
                                        isSendingCaptcha -> "发送中"
                                        else -> "获取验证码"
                                    },
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // 状态提示文字
                        if (smsStatusMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = smsStatusMessage ?: "",
                                color = if (smsIsError) HyperRed else HyperBlue,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // 立即登录按钮
                        Button(
                            onClick = {
                                if (isLoggingIn) return@Button
                                if (phoneInput.length != 11) {
                                    smsStatusMessage = "请输入正确的 11 位手机号码"
                                    smsIsError = true
                                    return@Button
                                }
                                if (captchaInput.length < 4) {
                                    smsStatusMessage = "请输入收到的验证码"
                                    smsIsError = true
                                    return@Button
                                }
                                scope.launch {
                                    isLoggingIn = true
                                    smsStatusMessage = "正在登录..."
                                    smsIsError = false
                                    val (success, result) = MusicRepository.loginWithCaptcha(phoneInput, captchaInput)
                                    isLoggingIn = false
                                    val (profile, msg) = result
                                    if (success && profile != null) {
                                        SettingsRepository.saveUserProfile(profile)
                                        showLoginDialog = false
                                        scope.launch {
                                            MusicRepository.syncCloudFavorites()
                                        }
                                    } else {
                                        smsStatusMessage = msg
                                        smsIsError = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isLoggingIn) "正在验证登录..." else "立即登录",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Tab 1: 扫码登录
                    if (loginTab == 1) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "打开网易云音乐 APP 扫一扫即可登录",
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .size(190.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrImgBitmap != null) {
                                    Image(
                                        bitmap = qrImgBitmap!!.asImageBitmap(),
                                        contentDescription = "登录二维码",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = if (isQrLoading) "正在生成二维码..." else "二维码加载中",
                                        color = Color.DarkGray,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = qrStatusText,
                                color = if (qrStatusText.contains("成功")) HyperBlue else MiuixTheme.colorScheme.onSurfaceSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = { startQrLogin() },
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                Text(text = "刷新二维码")
                            }
                        }
                    }

                    // Tab 2: Cookie 登录
                    if (loginTab == 2) {
                        Text(
                            text = "直接粘贴包含 MUSIC_U 的网易云 Cookie 字符串：",
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        TextField(
                            value = cookieInput,
                            onValueChange = { cookieInput = it },
                            label = "粘贴 MUSIC_U=... 或完整 Cookie",
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (cookieStatusMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = cookieStatusMessage ?: "", color = HyperBlue, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (cookieInput.isNotBlank()) {
                                    scope.launch {
                                        cookieStatusMessage = "正在验证账号..."
                                        val trimmed = cookieInput.trim().trim('"', '\'')
                                        val normalizedCookie = if (!trimmed.contains("=") && trimmed.length > 20) {
                                            "MUSIC_U=$trimmed"
                                        } else {
                                            trimmed
                                        }
                                        val profile = MusicRepository.getUserAccount(normalizedCookie)
                                        if (profile != null) {
                                            SettingsRepository.saveUserProfile(profile)
                                            showLoginDialog = false
                                            scope.launch {
                                                MusicRepository.syncCloudFavorites()
                                            }
                                        } else {
                                            SettingsRepository.saveUserProfile(
                                                UserProfile(
                                                    nickname = "云村村民",
                                                    isLogin = true,
                                                    cookie = normalizedCookie
                                                )
                                            )
                                            showLoginDialog = false
                                            scope.launch {
                                                MusicRepository.syncCloudFavorites()
                                            }
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "保存并登录", fontWeight = FontWeight.Bold)
                        }
                    }
        }
    }

    // 账号详情与注销弹窗
    OverlayDialog(
        show = showAccountDialog,
        onDismissRequest = { showAccountDialog = false },
        title = "账号详情"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (userProfile.avatarUrl.isNotBlank()) {
                    AsyncImage(
                        url = userProfile.avatarUrl,
                        contentDescription = userProfile.nickname,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(text = "👤", fontSize = 32.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = userProfile.nickname,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "UID: ${userProfile.userId}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )

            if (userProfile.signature.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = userProfile.signature,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        MusicRepository.logout()
                        showAccountDialog = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "退出登录", color = MiuixTheme.colorScheme.error)
                }

                Button(
                    onClick = { showAccountDialog = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "关闭")
                }
            }
        }
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
