package com.qiutool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiutool.app.core.ExportTemplate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TemplateDialog(
    lastTemplate: ExportTemplate?,
    templates: List<ExportTemplate>,
    selectedCount: Int,
    canApply: Boolean,
    onApply: (ExportTemplate) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出模板", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SectionLabel("上次操作")
                if (lastTemplate == null) {
                    EmptyHint("还没有导出记录，导出一次后会自动记录")
                } else {
                    TemplateRow(
                        template = lastTemplate,
                        canApply = canApply,
                        showName = false,
                        onApply = { onApply(lastTemplate) },
                        onDelete = null,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel("我的模板 ${templates.size}")
                if (templates.isEmpty()) {
                    EmptyHint("勾选项目后在下方另存为模板")
                } else {
                    templates.forEach { template ->
                        TemplateRow(
                            template = template,
                            canApply = canApply,
                            onApply = { onApply(template) },
                            onDelete = { onDelete(template.name) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel("另存当前勾选（$selectedCount 项）")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        if (newName.isEmpty()) {
                            Text("模板名称（可留空自动命名）", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }
                        BasicTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF0F172A)),
                            cursorBrush = SolidColor(Color(0xFF00B4D8)),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = {
                            onSave(newName)
                            newName = ""
                        },
                        enabled = selectedCount > 0,
                    ) {
                        Text(
                            text = "保存",
                            fontSize = 12.sp,
                            color = if (selectedCount > 0) Color(0xFF00B4D8) else Color(0xFF94A3B8),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", color = Color(0xFF00B4D8))
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF64748B),
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(text = text, fontSize = 11.sp, color = Color(0xFF94A3B8))
}

@Composable
private fun TemplateRow(
    template: ExportTemplate,
    canApply: Boolean,
    onApply: () -> Unit,
    onDelete: (() -> Unit)?,
    showName: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (showName) {
                Text(
                    text = template.name,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = templateSubtitle(template),
                fontSize = if (showName) 11.sp else 12.sp,
                color = if (showName) Color(0xFF94A3B8) else Color(0xFF475569),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onApply, enabled = canApply) {
            Text(
                text = "套用",
                fontSize = 12.sp,
                color = if (canApply) Color(0xFF00B4D8) else Color(0xFF94A3B8),
            )
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text("删除", fontSize = 12.sp, color = Color(0xFFEF4444))
            }
        }
    }
}

private fun templateSubtitle(template: ExportTemplate): String = buildString {
    append(if (template.mode == "keep") "保留选中" else "排除选中")
    append(" · ${template.tokens.size} 项")
    if (template.sourceCategory.isNotEmpty()) {
        append(" · ${template.sourceCategory}")
    }
    if (template.savedAt > 0L) {
        append(" · ${formatTimestamp(template.savedAt)}")
    }
}

private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
