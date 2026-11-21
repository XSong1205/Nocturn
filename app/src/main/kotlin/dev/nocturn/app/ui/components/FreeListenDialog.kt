package dev.nocturn.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nocturn.app.data.model.FreeListenRights
import dev.nocturn.app.ui.util.formatCountdown
import dev.nocturn.app.ui.util.formatDateTime
import kotlinx.coroutines.delay

@Composable
fun FreeListenDialog(
    visible: Boolean,
    rights: FreeListenRights?,
    isLoading: Boolean,
    autoClaim: Boolean,
    onAutoClaimChange: (Boolean) -> Unit,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    // Dynamic countdown timer
    var currentRemainingMs by remember(rights?.remainingMs) {
        mutableLongStateOf(rights?.remainingMs ?: 0L)
    }

    LaunchedEffect(rights?.remainingMs) {
        currentRemainingMs = rights?.remainingMs ?: 0L
        while (currentRemainingMs > 0) {
            delay(1000)
            currentRemainingMs = (currentRemainingMs - 1000).coerceAtLeast(0)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("free-listen-dialog"),
        icon = {
            Icon(
                Icons.Rounded.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            val titleText = remember(rights?.title) {
                val t = rights?.title?.trim().orEmpty()
                when {
                    t.isEmpty() || t.contains("看视频") || t.contains("免费听") -> "免费听"
                    else -> t
                }
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Timer display card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(vertical = 18.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (currentRemainingMs > 0) "剩余可用时长" else "免费听",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isLoading && rights == null) {
                                "查询中..."
                            } else if (currentRemainingMs > 0) {
                                formatCountdown(currentRemainingMs)
                            } else {
                                "当前未激活"
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (currentRemainingMs > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Detail info
                rights?.let { r ->
                    if (r.vipInfoContent.isNotBlank()) {
                        Text(
                            text = r.vipInfoContent,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (r.endTimeMs > 0) {
                        formatDateTime(r.endTimeMs)?.let { timeStr ->
                            Text(
                                text = "权益到期时间：$timeStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    if (r.upperLimit) {
                        Text(
                            text = "今日领取已达上限",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (r.coverToday) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "今日权益已覆盖",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Auto-claim checkbox row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = autoClaim,
                        onCheckedChange = onAutoClaimChange,
                        modifier = Modifier.testTag("free-listen-autoclaim-checkbox"),
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动静默领取",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "时长不足15分钟或启动时自动领取",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClaim,
                enabled = !isLoading && (rights == null || !rights.upperLimit),
                modifier = Modifier.testTag("free-listen-claim-button"),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (rights?.upperLimit == true) {
                        "今日已达上限"
                    } else {
                        val action = rights?.actionTitle?.trim().orEmpty()
                        when {
                            action.isEmpty() ||
                                action == "领免费听时长" ||
                                action == "领取30分钟" ||
                                action == "点击领取30分钟免费听" ||
                                action.contains("30分钟") -> "领取 30 分钟"
                            action.contains("看视频") -> "免费听"
                            else -> action
                        }
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("free-listen-dismiss-button"),
            ) {
                Text("关闭")
            }
        },
    )
}
