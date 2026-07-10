package com.qiutool.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qiutool.app.R
import com.qiutool.app.core.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showPermissionPicker by remember { mutableStateOf(false) }
    val tabs = listOf(
        "官方" to state.resources.size,
        "本地" to 0,
        "结果" to (state.analysis?.items?.size ?: 0),
    )

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        bottomBar = {
            // 权限选择页占满时隐藏底栏，避免遮住激活按钮
            if (!showPermissionPicker) {
                Column {
                    if (state.analysis != null) {
                        ExportBar(
                            mode = state.exportMode,
                            isExporting = state.isExporting,
                            progress = state.exportProgress,
                            message = state.exportMessage,
                            selectedCount = state.selectedTokens.size,
                            totalCount = viewModel.getFilteredItems().size,
                            onModeChange = { viewModel.setExportMode(it) },
                            onExport = { viewModel.exportBundle() },
                        )
                    }
                    CopyrightFooter()
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            // 顶部一行：搜索 + 设置（仅在结果页可输入）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Tab 按钮组（紧凑版）
                    Row(modifier = Modifier.weight(1f)) {
                        tabs.forEachIndexed { index, (title, count) ->
                            val selected = state.currentTab == index
                            Surface(
                                onClick = { viewModel.setTab(index) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) Color(0xFFE0F7FA) else Color.Transparent,
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Text(
                                    text = if (count > 0) "$title $count" else title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) Color(0xFF00B4D8) else Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // 仅结果页显示搜索框（云端/本地页不需要它）
            if (state.currentTab == 2) {
                Surface(modifier = Modifier.fillMaxWidth(), color = Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .height(34.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(17.dp))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (state.searchQuery.isEmpty()) {
                                Text(
                                    "搜索 Token / 名称 / ID / 分类",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                )
                            }
                            BasicTextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF0F172A)),
                                cursorBrush = SolidColor(Color(0xFF00B4D8)),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // 错误提示
            if (state.error.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF2F2),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.error,
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { viewModel.clearError() },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("关闭", fontSize = 12.sp, color = Color(0xFFEF4444))
                        }
                    }
                }
            }

            // 加载条
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFF00B4D8),
                    trackColor = Color(0xFFE2E8F0),
                )
            }

            // 内容面板
            when (state.currentTab) {
                0 -> CloudPanel(
                    resources = state.filteredResources,
                    isLoading = state.isLoading,
                    onFetch = { viewModel.fetchVerXml() },
                    onAnalyze = { viewModel.analyzeResource(it) },
                    filter = state.resourceFilter,
                    onFilterChange = { viewModel.setResourceFilter(it) },
                )
                1 -> UploadPanel(
                    isLoading = state.isLoading,
                    onFileSelected = { viewModel.analyzeLocalFile(it) },
                )
                2 -> ResultPanel(
                    items = viewModel.getFilteredItems(),
                    categories = viewModel.getCategories(),
                    selectedTokens = state.selectedTokens,
                    categoryFilter = state.categoryFilter,
                    isExporting = state.isExporting,
                    onToggle = { viewModel.toggleToken(it) },
                    onSelectAll = { viewModel.selectAll() },
                    onCategoryChange = { viewModel.setCategoryFilter(it) },
                )
            }
        }

        if (showSettings) {
            SettingsDialog(
                state = state,
                onDismiss = { showSettings = false },
                onUrlChange = { viewModel.setVerXmlUrl(it) },
                onAnalyzeAllChange = { viewModel.setAnalyzeAllFiles(it) },
                onShopconfigDirChange = { viewModel.setShopconfigOutputDir(it) },
                onOtherDirChange = { viewModel.setOtherOutputDir(it) },
                onPickPermission = {
                    showSettings = false
                    showPermissionPicker = true
                },
                onShareLogs = {
                    scope.launch {
                        val fileResult = runCatching {
                            withContext(Dispatchers.IO) {
                                DiagnosticLogExporter.createReportFile(context, state)
                            }
                        }
                        fileResult
                            .onSuccess { file ->
                                val share = DiagnosticLogExporter.shareIntent(context, file)
                                runCatching {
                                    context.startActivity(Intent.createChooser(share, "分享日志"))
                                }.onFailure { error ->
                                    AppLogger.w("QiuTool", "share diagnostic log failed: ${error.message}", error)
                                    Toast.makeText(context, "无法打开分享：${error.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                            .onFailure { error ->
                                AppLogger.w("QiuTool", "create diagnostic log failed: ${error.message}", error)
                                Toast.makeText(context, "保存日志失败：${error.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                },
            )
        }

        if (showPermissionPicker) {
            PermissionScreen(
                current = state.permissionMethod,
                onConfirm = { method ->
                    viewModel.setPermissionMethod(method)
                    showPermissionPicker = false
                },
                onSkip = { showPermissionPicker = false },
            )
        }
        }
    }
}

@Composable
private fun CopyrightFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.copyright),
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun permissionLabel(method: String): String = when (method) {
    "all_files" -> "所有文件访问"
    "shizuku" -> "Shizuku"
    "root" -> "Root (su)"
    else -> "未选择"
}

@Composable
private fun SettingsDialog(
    state: UiState,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onAnalyzeAllChange: (Boolean) -> Unit,
    onShopconfigDirChange: (String) -> Unit,
    onOtherDirChange: (String) -> Unit,
    onPickPermission: () -> Unit,
    onShareLogs: () -> Unit,
) {
    var editing by remember { mutableStateOf<EditField?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 分析全部文件
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("分析全部文件", fontSize = 13.sp, color = Color(0xFF0F172A))
                        Text(
                            text = if (state.analyzeAllFiles)
                                "已显示所有 .unity3d"
                            else
                                "默认仅显示 ShopConfig",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                        )
                    }
                    Switch(
                        checked = state.analyzeAllFiles,
                        onCheckedChange = onAnalyzeAllChange,
                    )
                }

                ConfigRow(
                    label = "权限方式",
                    value = permissionLabel(state.permissionMethod),
                    onClick = onPickPermission,
                )
                ConfigRow(
                    label = "ver.xml 地址",
                    value = state.verXmlUrl,
                    onClick = { editing = EditField.URL },
                )
                ConfigRow(
                    label = "ShopConfig 导出目录",
                    value = state.shopconfigOutputDir,
                    onClick = { editing = EditField.SHOPCONFIG_DIR },
                )
                ConfigRow(
                    label = "其他文件 导出目录",
                    value = state.otherOutputDir,
                    onClick = { editing = EditField.OTHER_DIR },
                )
                ConfigRow(
                    label = "保存日志",
                    value = "生成诊断日志并弹出分享",
                    onClick = onShareLogs,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = Color(0xFF00B4D8))
            }
        },
    )

    editing?.let { field ->
        val (label, current, save) = when (field) {
            EditField.URL -> Triple("ver.xml 地址", state.verXmlUrl, onUrlChange)
            EditField.SHOPCONFIG_DIR -> Triple("ShopConfig 导出目录", state.shopconfigOutputDir, onShopconfigDirChange)
            EditField.OTHER_DIR -> Triple("其他文件 导出目录", state.otherOutputDir, onOtherDirChange)
        }
        EditValueDialog(
            label = label,
            current = current,
            onDismiss = { editing = null },
            onSave = {
                save(it)
                editing = null
            },
        )
    }
}

private enum class EditField { URL, SHOPCONFIG_DIR, OTHER_DIR }

@Composable
private fun ConfigRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, color = Color(0xFF0F172A))
            Text(
                text = value,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Text(">", fontSize = 16.sp, color = Color(0xFFCBD5E1))
    }
}

@Composable
private fun EditValueDialog(
    label: String,
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF0F172A)),
                    cursorBrush = SolidColor(Color(0xFF00B4D8)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("保存", color = Color(0xFF00B4D8))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF94A3B8))
            }
        },
    )
}
