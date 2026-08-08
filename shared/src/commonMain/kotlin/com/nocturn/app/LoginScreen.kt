package com.nocturn.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LoginScreen() {
    val phoneState = remember { TextFieldState() }
    val passwordState = remember { TextFieldState() }

    Scaffold(
        topBar = {
            Text(
                text = "登录",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "登录网易云音乐",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "使用手机号登录，同步你的音乐数据",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )

            TextField(
                state = phoneState,
                modifier = Modifier.fillMaxWidth(),
                label = "手机号",
                useLabelAsPlaceholder = true,
            )
            TextField(
                state = passwordState,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                label = "密码",
                useLabelAsPlaceholder = true,
            )

            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 32.dp),
                cornerRadius = 16.dp,
            ) {
                Text(
                    text = "登录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
