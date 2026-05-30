package com.qiutool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiutool.app.core.ItemRecord

@Composable
fun ResultPanel(
    items: List<ItemRecord>,
    categories: List<String>,
    selectedTokens: Set<String>,
    categoryFilter: String,
    isExporting: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onCategoryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // 控制栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分类下拉
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = categoryFilter.ifEmpty { "全部分类" },
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部分类", fontSize = 13.sp) },
                            onClick = {
                                onCategoryChange("")
                                expanded = false
                            }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category, fontSize = 13.sp) },
                                onClick = {
                                    onCategoryChange(category)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 全选按钮
                val allSelected = items.isNotEmpty() && items.all { it.token in selectedTokens }
                Surface(
                    onClick = onSelectAll,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(16.dp),
                    color = if (allSelected) Color(0xFF00B4D8) else Color(0xFFF1F5F9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (allSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (allSelected) "取消全选" else "全选",
                            fontSize = 12.sp,
                            color = if (allSelected) Color.White else Color(0xFF475569)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 选中计数
                Text(
                    text = "${selectedTokens.size}/${items.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF00B4D8)
                )
            }
        }

        // 物品列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            items(items, key = { it.token }) { item ->
                SkinRow(
                    item = item,
                    isSelected = item.token in selectedTokens,
                    isEnabled = !isExporting,
                    onToggle = { onToggle(item.token) }
                )
            }
        }
    }
}
