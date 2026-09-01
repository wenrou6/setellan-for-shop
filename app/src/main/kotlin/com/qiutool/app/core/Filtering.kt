package com.qiutool.app.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object Filtering {

    fun exportFilteredBundle(
        sourceBundle: File,
        analysis: AnalysisResult,
        mode: String,
        selectedTokens: Set<String>,
        outputDir: File,
        onProgress: ((Int, String) -> Unit)? = null
    ): ExportResult {
        require(mode in listOf("keep", "exclude")) { "mode must be keep or exclude" }
        AppLogger.d("QiuTool", "exportFilteredBundle mode=$mode selectedRaw=${selectedTokens.size}")

        onProgress?.invoke(6, "Reading ShopConfig payload")
        val payload = BundleIO.extractShopconfigPayload(sourceBundle)

        onProgress?.invoke(18, "Building record index")
        val recordIndex = FlatBufferScanner.buildRecordIndex(payload)
        val (recordByPrimary, primaryByTable) = buildPrimaryRecordMaps(recordIndex)
        AppLogger.d(
            "QiuTool",
            "source records=${recordIndex.records.size} uniquePrimaries=${recordByPrimary.size}"
        )

        onProgress?.invoke(30, "Expanding selection set")
        val expandedTokens = Analyzer.expandSelectedTokens(selectedTokens.filter { it.isNotEmpty() }.toSet(), analysis.items)
        AppLogger.d(
            "QiuTool",
            "expandedTokens count=${expandedTokens.size} sample=${expandedTokens.take(6).toList()}"
        )

        onProgress?.invoke(44, "Computing retention closure")
        val keepTableOffsets: Set<Int>
        val keepTokens: Set<String>
        if (mode == "keep") {
            keepTokens = expandedTokens
            keepTableOffsets = recordIndex.records.filter {
                (primaryByTable[it.tableOffset] ?: "") in keepTokens
            }.map { it.tableOffset }.toSet()
        } else {
            val excludeTokens = expandedTokens
            keepTokens = recordByPrimary.keys - excludeTokens
            keepTableOffsets = recordIndex.records.filter {
                (primaryByTable[it.tableOffset] ?: "") !in excludeTokens
            }.map { it.tableOffset }.toSet()
        }
        AppLogger.d(
            "QiuTool",
            "keepTableOffsets=${keepTableOffsets.size} (mode=$mode, totalRecords=${recordIndex.records.size})"
        )

        // Work on a mutable copy of the payload
        val modPayload = payload.copyOf()

        if (mode == "keep") {
            onProgress?.invoke(58, "Scrubbing unkept record residuals")
            scrubUnkeptRecordStrings(
                payload = modPayload,
                records = recordIndex.records,
                keepTableOffsets = keepTableOffsets,
                keepTokens = keepTokens,
                primaryByTable = primaryByTable,
                aggressive = true
            )
        } else {
            onProgress?.invoke(58, "Masking removed identities")
            maskRemovedPrimaryTokenStrings(
                payload = modPayload,
                records = recordIndex.records,
                removedTokens = expandedTokens,
                primaryByTable = primaryByTable
            )
        }

        onProgress?.invoke(72, "Rewriting record vector")
        if (mode == "keep") {
            rewriteRecordVectorCompact(modPayload, recordIndex, keepTableOffsets)
        } else {
            rewriteRecordVectorByReplacingRemoved(modPayload, recordIndex.records, keepTableOffsets)
        }

        onProgress?.invoke(88, "Repacking Unity bundle")
        val outputBundle = BundleIO.replaceShopconfigPayload(sourceBundle, modPayload, outputDir)

        onProgress?.invoke(96, "Verifying export")
        val summary = validateExport(outputBundle, mode, expandedTokens)

        onProgress?.invoke(100, "Export complete")
        return ExportResult(file = outputBundle, summary = summary)
    }

    data class ExportResult(val file: File, val summary: ExportSummary)

    private fun buildPrimaryRecordMaps(recordIndex: RecordIndex): Pair<Map<String, FlatRecord>, Map<Int, String>> {
        val recordByPrimary = mutableMapOf<String, FlatRecord>()
        val primaryByTable = mutableMapOf<Int, String>()
        for (record in recordIndex.records) {
            val primary = FlatBufferScanner.primaryTokenOfRecord(record) { TokenClassifier.classify(it) }
            primaryByTable[record.tableOffset] = primary
            if (primary.isNotEmpty()) {
                recordByPrimary[primary] = record
            }
        }
        return Pair(recordByPrimary, primaryByTable)
    }

    private fun maskRemovedPrimaryTokenStrings(
        payload: ByteArray,
        records: List<FlatRecord>,
        removedTokens: Set<String>,
        primaryByTable: Map<Int, String>
    ) {
        if (removedTokens.isEmpty()) return
        val masked = mutableSetOf<Int>()
        for (record in records) {
            val primary = primaryByTable[record.tableOffset] ?: ""
            if (primary !in removedTokens) continue
            for (field in record.stringFields) {
                if (field.text !in removedTokens) continue
                if (field.stringOffset in masked) continue
                maskStringObject(payload, field.stringOffset)
                masked.add(field.stringOffset)
            }
        }
    }

    private fun scrubUnkeptRecordStrings(
        payload: ByteArray,
        records: List<FlatRecord>,
        keepTableOffsets: Set<Int>,
        keepTokens: Set<String>,
        primaryByTable: Map<Int, String>,
        aggressive: Boolean
    ) {
        // record.stringFields 只包含 buildRecordIndex 采样确认过的字段索引。源 payload 有 6 万+ record，
        // 采样（最多 256 条）可能整个漏掉某个只在少数 record 上才有值的字段；而导出后 record 被压缩到
        // 几千甚至 1 条，重新 buildRecordIndex 会把这些字段全部暴露出来。这些字段没被 scrub 过，于是
        // 既留下别的 token 字符串，又可能顶掉本行的 primary（同一行里出现第二个 Gift token 时，
        // primaryTokenOfRecord 取最后一个）。所以保留行必须按 vtable 的完整物理字段视图处理。
        val keptRecords = records.filter { it.tableOffset in keepTableOffsets }
        val physicalFields = HashMap<Int, List<FlatStringField>>(keptRecords.size)
        for (record in keptRecords) {
            physicalFields[record.tableOffset] =
                readPhysicalStringFields(payload, record.tableOffset) ?: record.stringFields
        }

        // 多行可能共享同一个字符串对象，保留行 primary 所在的偏移一律不许清零。
        val protectedOffsets = HashSet<Int>()
        for (record in keptRecords) {
            val primary = primaryByTable[record.tableOffset] ?: continue
            if (primary.isEmpty()) continue
            physicalFields[record.tableOffset]?.forEach { field ->
                if (field.text == primary) protectedOffsets.add(field.stringOffset)
            }
        }

        for (record in keptRecords) {
            redirectForeignTokenFields(
                payload = payload,
                fields = physicalFields[record.tableOffset] ?: continue,
                primary = primaryByTable[record.tableOffset] ?: "",
                protectedOffsets = protectedOffsets,
            )
        }

        val referencedOffsets = collectKeptStringOffsets(payload, records, keepTableOffsets)
        val scrubbed = mutableSetOf<Int>()
        for (record in records) {
            if (!aggressive && record.tableOffset in keepTableOffsets) continue
            for (field in record.stringFields) {
                if (field.stringOffset in referencedOffsets || field.stringOffset in scrubbed) continue
                if (field.stringOffset in protectedOffsets) continue
                if (aggressive) {
                    blankStringObject(payload, field.stringOffset, field.length)
                } else {
                    maskStringObject(payload, field.stringOffset)
                }
                scrubbed.add(field.stringOffset)
            }
        }
    }

    private fun readPhysicalStringFields(payload: ByteArray, tableOffset: Int): List<FlatStringField>? {
        val info = FlatBufferScanner.readVtable(
            java.nio.ByteBuffer.wrap(payload).order(java.nio.ByteOrder.LITTLE_ENDIAN),
            tableOffset
        ) ?: return null
        return FlatBufferScanner.iterStringFields(payload, info)
    }

    /**
     * 保留行里除 primary 以外的 token 字段全部改指向 primary（改不了才清零）。
     *
     * 不只处理「未保留」的 token：同一行里如果还留着**另一个也被保留的** Gift token，
     * primaryTokenOfRecord 取 giftTokens.last()，导出后可能由它胜出，这一行的身份就从
     * Gift_1617 变成 Gift_1628，validateExport 于是报 missing kept items [Gift_1617]。
     * 让 primary 成为行内唯一的 token 字符串，身份就跟字段发现顺序无关了。
     */
    private fun redirectForeignTokenFields(
        payload: ByteArray,
        fields: List<FlatStringField>,
        primary: String,
        protectedOffsets: Set<Int>,
    ) {
        val blanked = mutableSetOf<Int>()
        for (field in fields) {
            TokenClassifier.classify(field.text) ?: continue
            if (primary.isNotEmpty() && field.text == primary) continue
            val replacement = findReplacementStringOffset(fields, field.fieldPosition, primary)
            if (replacement != null) {
                val relative = replacement - field.fieldPosition
                if (relative > 0 && relative <= 0x7FFFFFFF) {
                    writeLe32(payload, field.fieldPosition, relative)
                    continue
                }
            }
            if (field.stringOffset in protectedOffsets) continue
            if (field.stringOffset !in blanked) {
                blankStringObject(payload, field.stringOffset, field.length)
                blanked.add(field.stringOffset)
            }
        }
    }

    private fun findReplacementStringOffset(
        fields: List<FlatStringField>,
        fieldPosition: Int,
        primary: String
    ): Int? {
        val preferred = mutableListOf<Int>()
        val fallback = mutableListOf<Int>()
        for (candidate in fields) {
            if (candidate.stringOffset <= fieldPosition) continue
            if (primary.isNotEmpty() && candidate.text == primary) {
                preferred.add(candidate.stringOffset)
                continue
            }
            if (TokenClassifier.classify(candidate.text) == null) {
                fallback.add(candidate.stringOffset)
            }
        }
        return preferred.minOrNull() ?: fallback.minOrNull()
    }

    private fun collectKeptStringOffsets(
        payload: ByteArray,
        records: List<FlatRecord>,
        keepTableOffsets: Set<Int>
    ): Set<Int> {
        // 必须重新解析 payload，因为 redirectUnkeptTokenFields 已经改写过保留行的字段指向，
        // 缓存的 record.stringFields 还是 redirect 之前的旧目标。用旧目标会把保留行
        // 现在真正引用的字符串误判为「没人用」而被清零。
        val offsets = mutableSetOf<Int>()
        for (record in records) {
            if (record.tableOffset !in keepTableOffsets) continue
            val info = FlatBufferScanner.readVtable(
                java.nio.ByteBuffer.wrap(payload).order(java.nio.ByteOrder.LITTLE_ENDIAN),
                record.tableOffset
            ) ?: continue
            for (field in FlatBufferScanner.iterStringFields(payload, info)) {
                offsets.add(field.stringOffset)
            }
        }
        return offsets
    }

    private fun blankStringObject(payload: ByteArray, stringOffset: Int, originalLength: Int) {
        if (stringOffset < 0 || stringOffset + 4 > payload.size) return
        val length = minOf(originalLength, maxOf(0, payload.size - stringOffset - 4))
        writeLe32(payload, stringOffset, 0)
        if (length > 0) {
            payload.fill(0, stringOffset + 4, stringOffset + 4 + length)
        }
    }

    private fun maskStringObject(payload: ByteArray, stringOffset: Int) {
        if (stringOffset < 0 || stringOffset + 4 >= payload.size) return
        payload[stringOffset + 4] = 0
    }

    private fun rewriteRecordVectorCompact(
        payload: ByteArray,
        index: RecordIndex,
        keepTableOffsets: Set<Int>
    ) {
        val kept = index.records.filter { it.tableOffset in keepTableOffsets }
        writeLe32(payload, index.vectorOffset, kept.size)
        for ((newIndex, record) in kept.withIndex()) {
            val slotOffset = index.vectorOffset + 4 + newIndex * 4
            val relative = record.tableOffset - slotOffset
            if (relative <= 0 || relative > 0x7FFFFFFF) {
                throw IllegalArgumentException("Record ${record.tableOffset} cannot fit in slot $slotOffset")
            }
            writeLe32(payload, slotOffset, relative)
        }
    }

    private fun rewriteRecordVectorByReplacingRemoved(
        payload: ByteArray,
        records: List<FlatRecord>,
        keepTableOffsets: Set<Int>
    ) {
        val kept = records.filter { it.tableOffset in keepTableOffsets }
        if (kept.isEmpty()) return
        val keptByIndex = kept.associateBy { it.index }
        val keptIndices = keptByIndex.keys.sorted()

        for (record in records) {
            if (record.tableOffset in keepTableOffsets) continue
            val replacement = nearestKeptRecord(record.index, keptByIndex, keptIndices) ?: continue
            val relative = replacement.tableOffset - record.slotOffset
            if (relative <= 0 || relative > 0x7FFFFFFF) continue
            writeLe32(payload, record.slotOffset, relative)
        }
    }

    private fun nearestKeptRecord(
        removedIndex: Int,
        keptByIndex: Map<Int, FlatRecord>,
        keptIndices: List<Int>
    ): FlatRecord? {
        val after = keptIndices.filter { it > removedIndex }
        if (after.isNotEmpty()) return keptByIndex[after.first()]
        val before = keptIndices.filter { it < removedIndex }
        if (before.isNotEmpty()) return keptByIndex[before.last()]
        return null
    }

    private fun validateExport(outputPath: File, mode: String, selectedTokens: Set<String>): ExportSummary {
        val payload = BundleIO.extractShopconfigPayload(outputPath)
        val recordIndex = FlatBufferScanner.buildRecordIndex(payload)

        // 按完整物理字段视图校验，不用 record.stringFields。后者只含 buildRecordIndex 采样确认过的
        // 字段索引，而采样落点取决于 record 总数——导出后 record 被压缩，采样结果跟源 payload 不一致，
        // 校验就会看到一份跟 scrub 阶段不同的字段集合，误报 missing kept items。
        val fieldsByTable = recordIndex.records.associate { record ->
            record.tableOffset to (readPhysicalStringFields(payload, record.tableOffset) ?: record.stringFields)
        }
        val primaries = recordIndex.records.map { record ->
            val fields = fieldsByTable[record.tableOffset] ?: record.stringFields
            FlatBufferScanner.primaryTokenOfRecord(record.copy(stringFields = fields)) {
                TokenClassifier.classify(it)
            }
        }.filter { it.isNotEmpty() }
        val primarySet = primaries.toSet()
        val tokenStrings = fieldsByTable.values
            .flatMap { fields ->
                fields.mapNotNull { field ->
                    if (TokenClassifier.classify(field.text) != null) field.text else null
                }
            }
            .toSet()

        AppLogger.d(
            "QiuTool",
            "validateExport mode=$mode outputSize=${outputPath.length()} recordCount=${recordIndex.records.size} uniquePrimaries=${primarySet.size} tokenStrings=${tokenStrings.size}"
        )

        if (mode == "keep") {
            val missing = selectedTokens.filter { it.isNotEmpty() && it !in primarySet }
            val unexpected = primarySet.filter { it !in selectedTokens }
            val unexpectedTokenStrings = tokenStrings.filter { it !in selectedTokens }
            AppLogger.d(
                "QiuTool",
                "keep mode: selected=${selectedTokens.size} foundOfSelected=${selectedTokens.size - missing.size} missing=${missing.take(8)} unexpectedPrimaries=${unexpected.take(8)} unexpectedTokenStrings=${unexpectedTokenStrings.take(8)}"
            )
            if (missing.isNotEmpty()) {
                throw RuntimeException("Export verification failed: missing kept items ${missing.take(6)}")
            }
            if (unexpectedTokenStrings.isNotEmpty()) {
                throw RuntimeException("Export verification failed: unexpected token strings ${unexpectedTokenStrings.take(6)}")
            }
            return ExportSummary(
                mode = mode,
                expectedCount = selectedTokens.size,
                actualKeptCount = primarySet.size,
                samplePresent = (selectedTokens intersect primarySet).take(6).toList(),
            )
        } else {
            val remained = selectedTokens.filter { it.isNotEmpty() && it in primarySet }
            AppLogger.d(
                "QiuTool",
                "exclude mode: removedRequested=${selectedTokens.size} stillPresent=${remained.size} totalAfter=${primarySet.size}"
            )
            if (remained.isNotEmpty()) {
                throw RuntimeException("Export verification failed: removed items still present ${remained.take(6)}")
            }
            return ExportSummary(
                mode = mode,
                expectedCount = selectedTokens.size,
                actualKeptCount = primarySet.size,
                samplePresent = primarySet.take(6).toList(),
            )
        }
    }

    data class ExportSummary(
        val mode: String,
        val expectedCount: Int,
        val actualKeptCount: Int,
        val samplePresent: List<String>,
    )

    private fun writeLe32(payload: ByteArray, offset: Int, value: Int) {
        payload[offset] = (value and 0xFF).toByte()
        payload[offset + 1] = ((value shr 8) and 0xFF).toByte()
        payload[offset + 2] = ((value shr 16) and 0xFF).toByte()
        payload[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
