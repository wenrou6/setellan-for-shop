package com.qiutool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import com.qiutool.app.core.ResourceItem

@Composable
fun CloudPanel(
    resources: List<ResourceItem>,
    isLoading: Boolean,
    onFetch: () -> Unit,
    onAnalyze: (ResourceItem) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部：过滤 + 获取按钮 同一行
        Surface(modifier = Modifier.fillMaxWidth(), color = Color.White) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (filter.isEmpty()) {
                            Text(
                                "过滤资源…",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        BasicTextField(
                            value = filter,
                            onValueChange = onFilterChange,
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF0F172A)),
                            cursorBrush = SolidColor(Color(0xFF00B4D8)),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onFetch,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00B4D8),
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("获取", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // 资源列表
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        ) {
            items(resources, key = { it.file }) { item ->
                ResourceRow(item = item, onClick = { onAnalyze(item) })
            }
        }
    }
}

@Composable
private fun ResourceRow(item: ResourceItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.file.substringAfterLast("/"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.md5.take(16) + "…",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                )
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFF1F5F9),
            ) {
                Text(
                    text = item.category,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatSize(item.size),
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
            )
        }
    }
}

private fun formatSize(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> "%.1fMB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1fKB".format(bytes / 1024.0)
    else -> "$bytes B"
}
