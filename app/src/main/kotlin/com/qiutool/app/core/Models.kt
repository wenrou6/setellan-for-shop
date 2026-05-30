package com.qiutool.app.core

import java.io.Serializable

data class StringSpan(
    val offset: Int,
    val text: String,
    val length: Int
) : Serializable

data class ItemRecord(
    val token: String,
    val category: String,
    val itemId: String,
    val name: String = "",
    val keyword: String = "",
    val group: String = "",
    val timeStart: String = "",
    val timeEnd: String = "",
    val variants: List<String> = emptyList(),
    val variantRecords: List<Map<String, Any>> = emptyList(),
    val variantCount: Int = 0,
    val recordCount: Int = 0,
    val tokenOffsets: List<Int> = emptyList(),
    val recordOffsets: List<Int> = emptyList(),
    val relatedTokens: List<String> = emptyList(),
    val associatedTokens: List<String> = emptyList(),
    val recordOffset: Int = 0,
    val status: String = "main"
) : Serializable

data class AnalysisResult(
    val sourceBundleName: String,
    val payloadSize: Int,
    val items: List<ItemRecord>
) : Serializable {
    fun buildStats(): Map<String, Any> {
        val categoryCounts = items.groupBy { it.category }.mapValues { it.value.size }
        val variantGroups = items.filter { it.recordCount > 1 || it.variants.size > 1 }
        return mapOf(
            "total_items" to items.size,
            "named_items" to items.count { it.name.isNotEmpty() },
            "keyword_items" to items.count { it.keyword.isNotEmpty() },
            "variant_group_count" to variantGroups.size,
            "variant_record_count" to variantGroups.sumOf { maxOf(it.recordCount, 1) },
            "category_counts" to categoryCounts.toSortedMap()
        )
    }
}

data class ResourceItem(
    val file: String,
    val md5: String,
    val size: Int,
    val weight: Int = 0,
    val check: Boolean = false
) : Serializable {
    val cacheName: String
        get() {
            val name = file.substringAfterLast("/")
            return "${name}_u_${md5}"
        }

    val cdnUrl: String
        get() = "http://cdn1.bobworld.cn/res300/android/${file}_u_${md5}"

    val category: String
        get() {
            val name = file.substringAfterLast("/").lowercase()
            return when {
                "shopconfig" in name -> "shopconfig"
                "creatskinconfig" in name -> "creatskinconfig"
                "artconfig" in name -> "artconfig"
                "previewconfig" in name -> "previewconfig"
                else -> "other"
            }
        }
}

data class DownloadProgress(
    val total: Int = 0,
    val downloaded: Int = 0,
    val currentFile: String = "",
    val speed: Double = 0.0,
    val status: String = "idle",
    val error: String = ""
) : Serializable
