package com.qiutool.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qiutool.app.core.ItemRecord

@Composable
fun SkinRow(
    item: ItemRecord,
    isSelected: Boolean,
    isEnabled: Boolean,
    onToggle: () -> Unit,
) {
    val bgColor = if (isSelected) Color(0xFFE0F7FA) else Color.White
    val titleColor = if (isSelected) Color(0xFF006064) else Color(0xFF0F172A)
    val subTextColor = Color(0xFF64748B)
    val tagBg = if (isSelected) Color(0xFFB2EBF2) else Color(0xFFF1F5F9)
    val tagText = if (isSelected) Color(0xFF006064) else Color(0xFF475569)
    val border = if (isSelected) {
        BorderStroke(1.dp, Color(0xFF00B4D8))
    } else {
        BorderStroke(1.dp, Color(0xFFE2E8F0))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(enabled = isEnabled) { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        border = border,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左：3 行 — token+#ID / 名称 / 关键词
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.token,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.itemId.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "#${item.itemId}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) Color(0xFF00B4D8) else Color(0xFF94A3B8),
                            maxLines = 1,
                        )
                    }
                }

                if (item.name.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.name,
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (item.keyword.isNotEmpty() && item.keyword != item.name) {
                    Text(
                        text = item.keyword,
                        fontSize = 11.sp,
                        color = subTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 中：分类 Tag
            if (item.category.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = tagBg) {
                    Text(
                        text = item.category,
                        fontSize = 10.sp,
                        color = tagText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右：勾选框
            Surface(
                modifier = Modifier.size(22.dp),
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) Color(0xFF00B4D8) else Color.Transparent,
                border = if (!isSelected) BorderStroke(1.5.dp, Color(0xFFCBD5E1)) else null,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
        }
    }
}
