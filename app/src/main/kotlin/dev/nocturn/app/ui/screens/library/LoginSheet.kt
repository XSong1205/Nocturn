@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.nocturn.app.ui.screens.library

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.QrLoginState
import dev.nocturn.app.data.model.ThemeMode
import dev.nocturn.app.ui.components.BlurBehindWindow
import dev.nocturn.app.ui.components.rememberHaptics
import dev.nocturn.app.ui.state.LoginUiState
import dev.nocturn.app.ui.state.LoginMode
import dev.nocturn.app.ui.theme.NocturnTheme

/**
 * Account login sheet hosted by the app shell.
 *
 * Renders the QR challenge or phone credentials. Dismissal is delegated to [onClose], and password
 * and verification code text are held in composition memory only.
 */
@Composable
fun LoginSheet(
    state: LoginUiState,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onModeChange: (LoginMode) -> Unit = {},
    onSendCode: (String, String) -> Unit = { _, _ -> },
    onSubmitPhone: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    // The sheet only needs two states: hidden and fully expanded. QR content is always worth the
    // full canvas, and skipping the partially-expanded stop keeps the primary action reachable.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = {
            if (state.mode == LoginMode.Qr || (!state.loading && !state.sendingCode)) onClose()
        },
        modifier = modifier,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        BlurBehindWindow()
        LoginSheetContent(
            state = state,
            onClose = onClose,
            onRefresh = onRefresh,
            onModeChange = onModeChange,
            onSendCode = onSendCode,
            onSubmitPhone = onSubmitPhone,
        )
    }
}

@Composable
internal fun LoginSheetContent(
    state: LoginUiState,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onModeChange: (LoginMode) -> Unit = {},
    onSendCode: (String, String) -> Unit = { _, _ -> },
    onSubmitPhone: (String, String, String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var countryCode by remember { mutableStateOf("86") }
    var phone by remember { mutableStateOf("") }
    var credential by remember(state.mode) { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
            .testTag("login-sheet"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "登录网易云音乐",
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when (state.mode) {
                        LoginMode.Qr -> "使用网易云音乐 App 扫描二维码"
                        LoginMode.Code -> "使用短信验证码登录"
                        LoginMode.Password -> "使用手机号和密码登录"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    haptics.tap()
                    onClose()
                },
                enabled = state.mode == LoginMode.Qr || (!state.loading && !state.sendingCode),
                modifier = Modifier.testTag("login-close"),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭登录")
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            for (mode in LoginMode.entries) {
                val label = when (mode) {
                    LoginMode.Qr -> "扫码"
                    LoginMode.Code -> "验证码"
                    LoginMode.Password -> "密码"
                }
                TextButton(
                    onClick = { onModeChange(mode) },
                    modifier = Modifier.weight(1f).testTag("login-mode-${mode.name.lowercase()}"),
                    enabled = state.mode == LoginMode.Qr || (!state.loading && !state.sendingCode),
                ) {
                    Text(label, color = if (state.mode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.mode == LoginMode.Qr) {
            QrCodePanel(state = state, onRefresh = onRefresh)
            Spacer(Modifier.height(18.dp))
            LoginStatus(state = state)
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = countryCode,
                    onValueChange = { countryCode = it.filter(Char::isDigit).take(4) },
                    label = { Text("区号") },
                    prefix = { Text("+") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(104.dp).testTag("login-country-code"),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(15) },
                    label = { Text("手机号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f).testTag("login-phone"),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = credential,
                onValueChange = { input ->
                    credential = if (state.mode == LoginMode.Code) input.filter(Char::isDigit).take(8) else input.take(128)
                },
                label = { Text(if (state.mode == LoginMode.Code) "短信验证码" else "密码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (state.mode == LoginMode.Code) KeyboardType.NumberPassword else KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("login-credential"),
            )
            Spacer(Modifier.height(12.dp))
            if (state.mode == LoginMode.Code) {
                TextButton(
                    onClick = { onSendCode(phone, countryCode) },
                    enabled = !state.loading && !state.sendingCode && state.cooldownSeconds == 0,
                    modifier = Modifier.testTag("login-send-code"),
                ) {
                    Text(when {
                        state.sendingCode -> "正在发送…"
                        state.cooldownSeconds > 0 -> "${state.cooldownSeconds} 秒后重发"
                        else -> "发送验证码"
                    })
                }
            }
            Button(
                onClick = { onSubmitPhone(phone, countryCode, credential) },
                enabled = !state.loading && !state.sendingCode,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login-submit"),
            ) { Text(if (state.loading) "正在登录…" else "登录") }
        }

        val errorText = state.error?.takeIf { it.isNotBlank() }
        if (errorText != null) {
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login-error"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = errorText.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        if (state.mode == LoginMode.Qr) {
            val needsRefresh = state.state == QrLoginState.Expired ||
                state.state == QrLoginState.Rejected || state.error != null
            if (needsRefresh) {
                Button(
                    onClick = {
                        haptics.tap()
                        onRefresh()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("login-refresh"),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("刷新二维码")
                }
            } else {
                TextButton(
                    onClick = {
                        haptics.tap()
                        onRefresh()
                    },
                    modifier = Modifier.testTag("login-refresh"),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重新获取二维码")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "密码只用于本次登录，登录后仅在本机加密保存会话",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QrCodePanel(
    state: LoginUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val qr: ImageBitmap? = remember(state.challenge?.imageBase64) {
        decodeQrBitmap(state.challenge?.imageBase64)
    }
    Surface(
        modifier = modifier
            .size(236.dp)
            .testTag("login-qr"),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val bitmap = qr
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "登录二维码",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
            } else if (state.loading || state.challenge == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.testTag("login-qr-loading"),
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "正在获取二维码…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.testTag("login-qr-failed"),
                ) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "二维码加载失败",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = {
                        haptics.tap()
                        onRefresh()
                    }) { Text("重新获取") }
                }
            }

            if (state.state == QrLoginState.Expired || state.state == QrLoginState.Rejected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .testTag("login-qr-mask"),
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (state.state == QrLoginState.Expired) {
                                "二维码已过期"
                            } else {
                                "已取消登录"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "刷新后重新扫码",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.state == QrLoginState.Authorized || state.authorizedProfile != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f))
                        .testTag("login-qr-authorized"),
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "登录成功",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginStatus(
    state: LoginUiState,
    modifier: Modifier = Modifier,
) {
    val (title, description) = when (state.state) {
        QrLoginState.WaitingForScan -> "等待扫码" to "打开网易云音乐 App，扫描上方二维码"
        QrLoginState.WaitingForConfirmation -> "扫码成功" to "请在手机上确认登录"
        QrLoginState.Authorized -> "登录成功" to (
            state.authorizedProfile?.let { "欢迎回来，${it.nickname}" } ?: "正在同步你的音乐库"
            )
        QrLoginState.Expired -> "二维码已过期" to "刷新二维码后重新扫码"
        QrLoginState.Rejected -> "已取消登录" to "如需继续，请重新扫码"
    }
    Column(
        modifier = modifier.testTag("login-status"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Decodes the LocalAPI QR challenge, tolerating raw Base64 and `data:` URI payloads alike. */
private fun decodeQrBitmap(base64: String?): ImageBitmap? {
    if (base64.isNullOrBlank()) return null
    val payload = base64.substringAfter("base64,", base64).trim()
    if (payload.isEmpty()) return null
    return runCatching {
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(name = "扫码登录 · 深色", showBackground = true)
@Composable
private fun LoginSheetPreviewDark() {
    NocturnTheme(themeMode = ThemeMode.Dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LoginSheetContent(
                state = LoginUiState(visible = true, loading = true),
                onClose = {},
                onRefresh = {},
            )
        }
    }
}

@Preview(name = "扫码登录 · 已过期 · 浅色", showBackground = true)
@Composable
private fun LoginSheetExpiredPreviewLight() {
    NocturnTheme(themeMode = ThemeMode.Light) {
        Surface(color = MaterialTheme.colorScheme.background) {
            LoginSheetContent(
                state = LoginUiState(
                    visible = true,
                    state = QrLoginState.Expired,
                ),
                onClose = {},
                onRefresh = {},
            )
        }
    }
}
