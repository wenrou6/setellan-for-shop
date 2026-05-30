package com.qiutool.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiutool.app.core.PermissionMethod
import com.qiutool.app.core.ShizukuRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 权限激活页。三张卡片，每张点击就立即跑「探测/请求」流程，
 * 卡片自身展示状态（已就绪 / 未授权 / 不可用 / 检查中）。
 *
 * @param current 已选方式（用于初始展示）
 * @param onConfirm 选定并完成激活
 * @param onSkip 仅设置入口允许跳过；首次启动传 null
 */
@Composable
fun PermissionScreen(
    current: String,
    onConfirm: (method: String) -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allFilesState by remember { mutableStateOf(probeAllFiles()) }
    var shizukuState by remember { mutableStateOf(probeShizuku(context)) }
    var rootState by remember { mutableStateOf(MethodState.UNCHECKED) }

    var dialog by remember { mutableStateOf<String?>(null) }
    var busyMethod by remember { mutableStateOf<String?>(null) }

    // 进入时主动探测三种方式 — root 探测会触发 su 弹窗，所以仅在 current==ROOT 或之前已选过 root 时静默探测
    LaunchedEffect(Unit) {
        allFilesState = probeAllFiles()
        shizukuState = probeShizuku(context)
        if (current == PermissionMethod.ROOT) {
            rootState = MethodState.UNCHECKED  // 显示加载占位
            val ok = withContext(Dispatchers.IO) { PermissionMethod.isRootAvailable() }
            rootState = if (ok) MethodState.READY else MethodState.UNAVAILABLE
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8FAFC),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text("选择文件写入方式", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "导出皮肤包到 /Android/data/com.ztgame.bob/… 需要其中一种权限。点击卡片即开始激活。",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
            )

            Spacer(modifier = Modifier.height(20.dp))

            MethodCard(
                title = "所有文件访问",
                desc = "标准方式，跳转系统设置开启「所有文件访问」。Android 11+ 才需要此权限。",
                state = allFilesState,
                isCurrent = current == PermissionMethod.ALL_FILES,
                isBusy = busyMethod == PermissionMethod.ALL_FILES,
                onClick = {
                    scope.launch {
                        busyMethod = PermissionMethod.ALL_FILES
                        if (PermissionMethod.isAllFilesGranted()) {
                            allFilesState = MethodState.READY
                            busyMethod = null
                            onConfirm(PermissionMethod.ALL_FILES)
                        } else {
                            PermissionMethod.openAllFilesSettings(context)
                            allFilesState = MethodState.PENDING
                            busyMethod = null
                            dialog = "已跳转到系统设置。请在「所有文件访问」中开启本应用，然后回到此页再次点击该卡片。"
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            MethodCard(
                title = "Shizuku",
                desc = "通过 Shizuku（adb 或 root 激活）以系统权限写文件。需先安装并启动 Shizuku 应用。",
                state = shizukuState,
                isCurrent = current == PermissionMethod.SHIZUKU,
                isBusy = busyMethod == PermissionMethod.SHIZUKU,
                onClick = {
                    scope.launch {
                        busyMethod = PermissionMethod.SHIZUKU
                        val s = activateShizuku(context)
                        shizukuState = s.state
                        busyMethod = null
                        if (s.state == MethodState.READY) onConfirm(PermissionMethod.SHIZUKU)
                        else dialog = s.message
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            MethodCard(
                title = "Root (su)",
                desc = "通过 su 直接执行写入。仅在已 root 的设备/模拟器上可用。",
                state = rootState,
                isCurrent = current == PermissionMethod.ROOT,
                isBusy = busyMethod == PermissionMethod.ROOT,
                onClick = {
                    scope.launch {
                        busyMethod = PermissionMethod.ROOT
                        val ok = withContext(Dispatchers.IO) { PermissionMethod.isRootAvailable() }
                        rootState = if (ok) MethodState.READY else MethodState.UNAVAILABLE
                        busyMethod = null
                        if (ok) onConfirm(PermissionMethod.ROOT)
                        else dialog = "未检测到 root 权限。\n\n· 请确认设备/模拟器已 root\n· 请在 root 管理器中允许本应用使用 su\n· 模拟器（如 MuMu）默认 root，但可能首次需在通知栏点允许"
                    }
                },
            )

            if (onSkip != null) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("跳过（暂不选择）", fontSize = 13.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }

    dialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("提示", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
            text = { Text(msg, fontSize = 13.sp, color = Color(0xFF334155)) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) {
                    Text("我知道了", color = Color(0xFF00B4D8))
                }
            },
        )
    }
}

private enum class MethodState { UNCHECKED, READY, PENDING, UNAVAILABLE }

private data class ShizukuActivation(val state: MethodState, val message: String?)

private fun probeAllFiles(): MethodState =
    if (PermissionMethod.isAllFilesGranted()) MethodState.READY else MethodState.PENDING

private fun probeShizuku(context: android.content.Context): MethodState {
    if (!PermissionMethod.isShizukuInstalled(context)) return MethodState.UNAVAILABLE
    if (!ShizukuRunner.isBinderAlive()) return MethodState.UNAVAILABLE
    return if (ShizukuRunner.isPermissionGranted()) MethodState.READY else MethodState.PENDING
}

private suspend fun activateShizuku(context: android.content.Context): ShizukuActivation {
    if (!PermissionMethod.isShizukuInstalled(context)) {
        return ShizukuActivation(
            MethodState.UNAVAILABLE,
            "未安装 Shizuku。请先到酷安/GitHub 下载并启动 Shizuku 后重试。"
        )
    }
    if (!ShizukuRunner.isBinderAlive()) {
        return ShizukuActivation(
            MethodState.UNAVAILABLE,
            "Shizuku 未运行。请打开 Shizuku 应用并通过 adb 或 root 启动服务，然后回到此页重试。"
        )
    }
    if (ShizukuRunner.isPermissionGranted()) {
        return ShizukuActivation(MethodState.READY, null)
    }
    val granted = ShizukuRunner.requestPermission()
    return if (granted) {
        ShizukuActivation(MethodState.READY, null)
    } else {
        ShizukuActivation(
            MethodState.PENDING,
            "Shizuku 授权未通过。请在弹窗中点击允许，或前往 Shizuku 应用「已授权应用」中检查。"
        )
    }
}

@Composable
private fun MethodCard(
    title: String,
    desc: String,
    state: MethodState,
    isCurrent: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    val border = when {
        isBusy -> BorderStroke(1.dp, Color(0xFF00B4D8))
        isCurrent && state == MethodState.READY -> BorderStroke(2.dp, Color(0xFF00B4D8))
        else -> BorderStroke(1.dp, Color(0xFFE2E8F0))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isBusy, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = border,
        shadowElevation = if (isCurrent) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.width(8.dp))
                    StateBadge(state)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(desc, fontSize = 11.sp, color = Color(0xFF64748B), lineHeight = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF00B4D8),
                )
            } else {
                Text(">", fontSize = 18.sp, color = Color(0xFFCBD5E1))
            }
        }
    }
}

@Composable
private fun StateBadge(state: MethodState) {
    val (text, bg, fg) = when (state) {
        MethodState.READY -> Triple("已就绪", Color(0xFFDCFCE7), Color(0xFF166534))
        MethodState.PENDING -> Triple("未授权", Color(0xFFFEF3C7), Color(0xFF92400E))
        MethodState.UNAVAILABLE -> Triple("不可用", Color(0xFFFEE2E2), Color(0xFFB91C1C))
        MethodState.UNCHECKED -> Triple("点击检测", Color(0xFFE0F2FE), Color(0xFF0369A1))
    }
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
