package me.rerere.rikkahub.ui.pages.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Mic01
import me.rerere.hugeicons.stroke.MicOff01
import me.rerere.rikkahub.service.VoiceCallService
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import kotlin.uuid.Uuid

private const val TAG = "VoiceCallPage"

/**
 * 语音通话页面 (ChatGPT 独立语音模式风格)
 *
 * - 纯色深色背景 + 流动光球
 * - 底部只有两个按钮: 静音 / 挂断
 * - 返回键 = 切后台继续通话 (不挂断)
 * - 业务逻辑全部跑在 VoiceCallService 里, 页面只负责 bind + 显示 uiState
 */
@Composable
fun VoiceCallPage(
    conversationId: Uuid,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var boundService by remember { mutableStateOf<VoiceCallService?>(null) }

    // 录音权限
    val asrPermission = rememberPermissionState(PermissionRecordAudio)

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                boundService = (binder as? VoiceCallService.LocalBinder)?.getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }
    }

    // bind/unbind Service. 关键: onDispose 只解绑, 绝不调用 endCall/stopService
    DisposableEffect(conversationId) {
        // 如果 Service 还没在跑这个对话的通话, 先 start 再 bind
        // 如果已经在跑 (用户是从通知点回来的), 只 bind, 不重复 start
        if (VoiceCallService.activeConversationId.value != conversationId.toString()) {
            // 权限检查: 没权限先请求, 拿到权限后再 start (见下方 LaunchedEffect)
            if (asrPermission.allRequiredPermissionsGranted) {
                VoiceCallService.start(context, conversationId.toString())
            }
        }
        val intent = Intent(context, VoiceCallService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.e(TAG, "unbindService 失败", e)
            }
        }
    }

    // 权限授予后启动 Service (如果还没启动)
    LaunchedEffect(asrPermission.allRequiredPermissionsGranted) {
        if (asrPermission.allRequiredPermissionsGranted &&
            VoiceCallService.activeConversationId.value == null
        ) {
            VoiceCallService.start(context, conversationId.toString())
        }
    }

    // 进入页面时, 如果还没权限, 请求权限
    LaunchedEffect(Unit) {
        if (!asrPermission.allRequiredPermissionsGranted) {
            asrPermission.requestPermissions()
        }
    }

    // boundService 为 null (绑定还没完成) 时, 显示默认空状态
    val uiState by (boundService?.uiState
        ?: MutableStateFlow(VoiceCallUiState()).asStateFlow())
        .collectAsStateWithLifecycle(initialValue = VoiceCallUiState())

    // 返回键 = 切后台继续通话, 不挂断. 这是这次改动最核心的行为变化.
    BackHandler {
        onBack()
    }

    // 纯色深色背景, 不随状态大幅变换色相
    val backgroundColor = Color(0xFF0A0A0F)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部: 只有一行很淡的小字提示当前状态
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 80.dp)
            ) {
                Text(
                    text = statusText(uiState.status),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light
                )
            }

            // 中部: 流动光球 (不显示对话文字)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                VoiceOrb(
                    amplitudes = uiState.amplitudes,
                    status = uiState.status,
                    size = 240.dp
                )

                // 绑定还没完成时, 显示一个小的加载指示器
                if (boundService == null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.5f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 错误信息 (保留, 方便调试)
                uiState.errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }

            // 底部: 只有两个按钮 (ChatGPT 风格)
            // 左: 静音, 右: 挂断. 删除中间状态切换按钮和自动发送开关.
            Row(
                horizontalArrangement = Arrangement.spacedBy(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 64.dp)
            ) {
                // 静音按钮
                val canControl = boundService != null
                ControlButton(
                    icon = if (uiState.isMuted) HugeIcons.MicOff01 else HugeIcons.Mic01,
                    contentDescription = "静音",
                    onClick = {
                        boundService?.toggleMute()
                    },
                    backgroundColor = if (uiState.isMuted) {
                        Color.White.copy(alpha = 0.3f)
                    } else {
                        Color.White.copy(alpha = 0.15f)
                    },
                    iconTint = Color.White,
                    enabled = canControl
                )

                // 挂断按钮
                ControlButton(
                    icon = HugeIcons.Cancel01,
                    contentDescription = "挂断",
                    onClick = {
                        VoiceCallService.stop(context)
                        onBack()
                    },
                    backgroundColor = MaterialTheme.colorScheme.error,
                    iconTint = Color.White,
                    enabled = true // 挂断始终可点, 即使 service 还没绑定
                )
            }
        }
    }
}

/**
 * 控制按钮 (圆形)
 */
@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    iconTint: Color,
    size: Dp = 64.dp,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.3f),
        modifier = Modifier.size(size),
        enabled = enabled
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) iconTint else iconTint.copy(alpha = 0.5f),
                modifier = Modifier.size(size * 0.4f)
            )
        }
    }
}

private fun statusText(status: VoiceCallStatus): String = when (status) {
    VoiceCallStatus.Idle -> "准备就绪"
    VoiceCallStatus.Listening -> "聆听中"
    VoiceCallStatus.Processing -> "思考中"
    VoiceCallStatus.Speaking -> "说话中"
    VoiceCallStatus.Error -> "出错了"
}