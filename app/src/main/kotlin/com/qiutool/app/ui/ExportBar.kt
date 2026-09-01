package com.qiutool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExportBar(
    mode: String,
    isExporting: Boolean,
    progress: Int,
    message: String,
    selectedCount: Int,
    totalCount: Int,
    templateMessage: String,
    templateMissing: List<String>,
    onModeChange: (String) -> Unit,
    onExport: () -> Unit,
    onOpenTemplates: () -> Unit,
    onDismissTemplateMessage: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // 进度条 / 完成状态
            if (isExporting) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFF00B4D8),
                    trackColor = Color(0xFFE2E8F0)
                )
                Text(
                    text = "$message ($progress%)",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else if (message.isNotEmpty()) {
                Text(
                    text = message,
                    fontSize = 11.sp,
                    color = Color(0xFF00B4D8),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // 模板套用结果（缺失项可展开）
            if (templateMessage.isNotEmpty()) {
                var showMissing by remember(templateMessage) { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = templateMessage,
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            modifier = Modifier.weight(1f)
                        )
                        if (templateMissing.isNotEmpty()) {
                            TextButton(
                                onClick = { showMissing = !showMissing },
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = if (showMissing) "收起" else "查看缺失",
                                    fontSize = 11.sp,
                                    color = Color(0xFF00B4D8)
                                )
                            }
                        }
                        TextButton(
                            onClick = onDismissTemplateMessage,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("关闭", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    if (showMissing) {
                        Text(
                            text = templateMissing.joinToString("、"),
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 控制行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 模式选择
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        onClick = { expanded = true },
                        enabled = !isExporting,
                        shape = RoundedCornerShape(16.dp),
                        color = if (mode == "keep") Color(0xFFE0F7FA) else Color(0xFFFEF2F2)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (mode == "keep") "保留选中" else "排除选中",
                                fontSize = 12.sp,
                                color = if (mode == "keep") Color(0xFF00B4D8) else Color(0xFFEF4444)
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = if (mode == "keep") Color(0xFF00B4D8) else Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("保留选中", fontSize = 13.sp) },
                            onClick = {
                                onModeChange("keep")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("排除选中", fontSize = 13.sp) },
                            onClick = {
                                onModeChange("exclude")
                                expanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 模板入口
                Surface(
                    onClick = onOpenTemplates,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "模板",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // 选中数量
                Text(
                    text = "$selectedCount/$totalCount",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                // 导出按钮
                Button(
                    onClick = onExport,
                    enabled = !isExporting && selectedCount > 0,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B4D8),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE2E8F0),
                        disabledContentColor = Color(0xFF94A3B8)
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "导出中 $progress%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "一键导出",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
