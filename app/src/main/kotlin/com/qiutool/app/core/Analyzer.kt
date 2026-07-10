package com.qiutool.app.core

import java.io.File

object Analyzer {

    fun analyzeBundle(bundlePath: File): AnalysisResult {
        val t0 = System.currentTimeMillis()
        val payload = BundleIO.extractShopconfigPayload(bundlePath)
        val t1 = System.currentTimeMillis()
        val items = analyzePayload(payload)
        val t2 = System.currentTimeMillis()
        AppLogger.d("QiuTool", "extract=${t1-t0}ms analyze=${t2-t1}ms payload=${payload.size}B")
        return AnalysisResult(
            sourceBundleName = bundlePath.name,
            payloadSize = payload.size,
            items = items
        )
    }

    fun analyzePayload(payload: ByteArray): List<ItemRecord> {
        val t0 = System.currentTimeMillis()
        val index = FlatBufferScanner.buildRecordIndex(payload)
        val t1 = System.currentTimeMillis()

        // Group records by token — use pre-computed field.category
        val grouped = mutableMapOf<String, MutableList<FlatRecord>>()
        val sortOffsets = mutableMapOf<String, Int>()

        for (record in index.records) {
            val token = primaryTokenOfRecord(record)
            if (token.isEmpty()) continue
            grouped.getOrPut(token) { mutableListOf() }.add(record)
            // Inline min offset calculation — no temp list
            var minOffset = sortOffsets[token] ?: Int.MAX_VALUE
            for (field in record.stringFields) {
                if (field.text == token && field.dataOffset < minOffset) {
                    minOffset = field.dataOffset
                }
            }
            if (minOffset == Int.MAX_VALUE) minOffset = record.tableOffset
            sortOffsets[token] = minOffset
        }
        val t2 = System.currentTimeMillis()
        AppLogger.d("QiuTool", "index=${t1-t0}ms group=${t2-t1}ms tokens=${grouped.size} records=${index.records.size}")

        val items = grouped.keys.sortedBy { sortOffsets[it] ?: Int.MAX_VALUE }.map { token ->
            buildItemRecord(token, grouped[token]!!)
        }
        val t3 = System.currentTimeMillis()
        AppLogger.d("QiuTool", "build=${t3-t2}ms items=${items.size}")

        val associationMap = buildAssociationMap(items)
        val result = items.map { item ->
            item.copy(associatedTokens = associationMap[item.token] ?: emptyList())
        }
        val t4 = System.currentTimeMillis()
        AppLogger.d("QiuTool", "assoc=${t4-t3}ms total=${t4-t0}ms")
        return result
    }

    // Use pre-computed field.category — no TokenClassifier.classify call
    private fun primaryTokenOfRecord(record: FlatRecord): String {
        var lastGift = ""
        var lastPreferred = ""
        var lastAny = ""
        for (field in record.stringFields) {
            val cat = field.category ?: continue
            lastAny = field.text
            if (cat == "Gift") {
                lastGift = field.text
            } else if (cat !in setOf("Gift_Effect", "qqliwu", "bza")) {
                lastPreferred = field.text
            }
        }
        return lastGift.ifEmpty { lastPreferred.ifEmpty { lastAny } }
    }

    // Single-pass buildItemRecord — merges extractNameAndKeyword, collectLabelVariants,
    // extractDates, collectRelatedTokens, collectVariantRecords into one iteration
    private fun buildItemRecord(token: String, records: List<FlatRecord>): ItemRecord {
        // Classifying the token directly is cheaper than searching for the token field in
        // the first record, and avoids edge cases where the token was selected by
        // primaryTokenOfRecord but doesn't satisfy `field.text == token` in record[0].
        val category = TokenClassifier.classify(token) ?: ""

        // Single-pass collectors
        val nameCandidates = mutableListOf<String>()
        val allLabels = mutableListOf<String>()
        val dates = mutableListOf<String>()
        val relatedTokens = mutableListOf<String>()
        val recordOffsets = mutableListOf<Int>()
        val tokenOffsets = mutableListOf<Int>()
        var minRecordOffset = Int.MAX_VALUE
        val seenDates = mutableSetOf<String>()
        val seenRelated = mutableSetOf<String>()
        val seenLabels = mutableSetOf<String>()

        for (record in records) {
            recordOffsets.add(record.tableOffset)
            if (record.tableOffset < minRecordOffset) minRecordOffset = record.tableOffset

            var foundPrimary = false
            var leadingTokens = false

            for (field in record.stringFields) {
                // Track token offsets
                if (field.text == token) {
                    tokenOffsets.add(field.dataOffset)
                    foundPrimary = true
                }

                // Before primary token: collect name candidates (leading labels)
                if (!foundPrimary && !leadingTokens) {
                    if (field.isToken) {
                        leadingTokens = true
                    } else if (field.isLabel && field.text !in seenLabels) {
                        nameCandidates.add(field.text)
                        seenLabels.add(field.text)
                    }
                }

                // Dates (use pre-computed flag)
                if (field.isDate && field.text !in seenDates) {
                    dates.add(field.text)
                    seenDates.add(field.text)
                }

                // Related tokens (use pre-computed flag)
                if (field.isToken && field.text != token && field.text !in seenRelated) {
                    relatedTokens.add(field.text)
                    seenRelated.add(field.text)
                }

                // All labels for variants
                if (field.isLabel && field.text !in seenLabels) {
                    allLabels.add(field.text)
                    seenLabels.add(field.text)
                }
            }
        }

        val (name, keyword) = nameKeywordFromCandidates(nameCandidates)
        val variants = orderedUnique(allLabels)

        // Build variant records (limited sample, single-pass per record)
        val variantRecords = buildVariantRecords(records, token, 12)

        return ItemRecord(
            token = token,
            category = category,
            itemId = TokenClassifier.extractItemId(token),
            name = name,
            keyword = keyword,
            group = guessGroup(keyword),
            timeStart = dates.firstOrNull() ?: "",
            timeEnd = dates.lastOrNull() ?: "",
            variants = variants,
            variantRecords = variantRecords,
            variantCount = variants.size,
            recordCount = records.size,
            tokenOffsets = tokenOffsets.sorted(),
            recordOffsets = recordOffsets.sorted().distinct(),
            relatedTokens = orderedUnique(relatedTokens),
            recordOffset = minRecordOffset,
            status = "main"
        )
    }

    // Single-pass variant record builder
    private fun buildVariantRecords(records: List<FlatRecord>, token: String, limit: Int): List<Map<String, Any>> {
        val sample = mutableListOf<Map<String, Any>>()
        val seenOffsets = mutableSetOf<Int>()
        for (record in records) {
            if (record.tableOffset in seenOffsets) continue
            seenOffsets.add(record.tableOffset)

            val labels = mutableListOf<String>()
            val recDates = mutableListOf<String>()
            val recRelated = mutableListOf<String>()
            val recTokenOffsets = mutableListOf<Int>()
            var foundPrimary = false
            var leadingTokens = false
            var nameCandidate = ""

            for (field in record.stringFields) {
                if (field.text == token) {
                    recTokenOffsets.add(field.dataOffset)
                    foundPrimary = true
                }
                if (!foundPrimary && !leadingTokens) {
                    if (field.isToken) {
                        leadingTokens = true
                    } else if (field.isLabel) {
                        if (nameCandidate.isEmpty()) nameCandidate = field.text
                        labels.add(field.text)
                    }
                }
                if (field.isDate) recDates.add(field.text)
                if (field.isToken && field.text != token) recRelated.add(field.text)
                if (field.isLabel && field.text !in labels) labels.add(field.text)
            }

            sample.add(mapOf(
                "record_offset" to record.tableOffset,
                "name" to nameCandidate,
                "keyword" to "",
                "labels" to labels.take(12),
                "dates" to recDates.take(4),
                "related_tokens" to recRelated.take(12),
                "token_offsets" to recTokenOffsets.sorted()
            ))
            if (sample.size >= limit) break
        }
        return sample
    }

    private fun nameKeywordFromCandidates(candidates: List<String>): Pair<String, String> {
        val ordered = orderedUnique(candidates)
        if (ordered.isEmpty()) return Pair("", "")
        if (ordered.size == 1) return Pair(ordered[0], "")
        return Pair(ordered.last(), if (ordered.first() != ordered.last()) ordered.first() else "")
    }

    private fun guessGroup(keyword: String): String {
        if (keyword.isEmpty()) return ""
        return if ("-" in keyword) keyword.substringBefore("-") else keyword
    }

    private fun orderedUnique(values: List<String>): List<String> {
        val out = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (value in values) {
            if (value.isNotEmpty() && value !in seen) {
                seen.add(value)
                out.add(value)
            }
        }
        return out
    }

    // Association map
    fun buildAssociationMap(items: List<ItemRecord>): Map<String, List<String>> {
        val itemByToken = items.filter { it.token.isNotEmpty() }.associateBy { it.token }
        val graph = itemByToken.keys.associateWith { mutableSetOf<String>() }.toMutableMap()

        for (tokens in groupTokensByExactName(items).values) {
            if (tokens.size < 2) continue
            val ordered = tokens.sorted().distinct()
            for (token in ordered) {
                graph[token]?.addAll(ordered.filter { it != token })
            }
        }

        return graph.filter { it.value.isNotEmpty() }
            .mapValues { it.value.sorted() }
    }

    fun expandSelectedTokens(selectedTokens: Set<String>, items: List<ItemRecord>): Set<String> {
        if (selectedTokens.isEmpty()) return emptySet()

        val itemByToken = items.filter { it.token.isNotEmpty() }.associateBy { it.token }
        val associations = buildAssociationMap(items)
        val expanded = selectedTokens.toMutableSet()
        val queue = ArrayDeque(selectedTokens.sorted())

        while (queue.isNotEmpty()) {
            val token = queue.removeFirst()
            for (neighbor in associations[token] ?: emptyList()) {
                if (neighbor in expanded) continue
                expanded.add(neighbor)
                queue.add(neighbor)
            }
        }

        for (token in selectedTokens.toList()) {
            val item = itemByToken[token] ?: continue
            for (related in item.relatedTokens) {
                val relatedItem = itemByToken[related] ?: continue
                if (relatedItem.category == item.category) {
                    expanded.add(related)
                }
            }
        }

        return expanded
    }

    private val NAME_BLOCKLIST = listOf(
        "获得", "参与", "开启", "登顶", "完成", "礼包", "自选包", "兑换券",
        "基础包", "进阶包", "特级包", "极品包", "幸运礼盒"
    )

    private fun groupTokensByExactName(items: List<ItemRecord>): Map<String, List<String>> {
        val byName = mutableMapOf<String, MutableList<String>>()
        for (item in items) {
            val name = associationNameKey(item.name)
            if (name.isNotEmpty()) {
                byName.getOrPut(name) { mutableListOf() }.add(item.token)
            }
        }
        return byName
    }

    private fun associationNameKey(name: String): String {
        val cleaned = name.trim()
        if (cleaned.length < 2 || cleaned.length > 18) return ""
        if (NAME_BLOCKLIST.any { it in cleaned }) return ""
        if ("[" in cleaned || "]" in cleaned) return ""
        if (cleaned.all { it.isDigit() }) return ""
        return cleaned
    }
}
