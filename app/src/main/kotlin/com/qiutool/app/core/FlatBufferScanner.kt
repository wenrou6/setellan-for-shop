package com.qiutool.app.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class VTableInfo(
    val tableOffset: Int,
    val vtableOffset: Int,
    val vtableSize: Int,
    val objectSize: Int,
    val fields: List<Int>
)

data class FlatStringField(
    val fieldIndex: Int,
    val fieldOffset: Int,
    val fieldPosition: Int,
    val stringOffset: Int,
    val dataOffset: Int,
    val length: Int,
    val text: String,
    // Pre-computed metadata — avoid repeated classification downstream
    val category: String? = null,
    val isToken: Boolean = false,
    val isDate: Boolean = false,
    val isNumeric: Boolean = false,
    val isLabel: Boolean = false
)

data class FlatRecord(
    val index: Int,
    val slotOffset: Int,
    val tableOffset: Int,
    val vtableOffset: Int,
    val objectSize: Int,
    val stringFields: List<FlatStringField>
) {
    val strings: List<String> get() = stringFields.map { it.text }
    val stringOffsets: List<Int> get() = stringFields.map { it.dataOffset }
}

data class StringFieldResult(
    val stringOffset: Int,
    val dataOffset: Int,
    val length: Int,
    val text: String
)

data class RecordIndex(
    val vectorOffset: Int,
    val originalCount: Int,
    val records: List<FlatRecord>
)

object FlatBufferScanner {

    fun buildRecordIndex(payload: ByteArray): RecordIndex {
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val (vectorOffset, count) = discoverRecordVector(buf)

        // Phase 1: Probe first ~20 records to discover which field indices contain strings
        val stringFieldIndices = discoverStringFieldIndices(buf, vectorOffset, count)

        // Phase 2: Only read the discovered string fields for all records
        val records = ArrayList<FlatRecord>(count)

        for (index in 0 until count) {
            val slotOffset = vectorOffset + 4 + index * 4
            if (slotOffset + 4 > payload.size) break
            val tableOffset = slotOffset + buf.getInt(slotOffset)
            val info = readVtable(buf, tableOffset) ?: continue

            val stringFields = readKnownStringFields(buf, info, stringFieldIndices)
            if (stringFields.isEmpty()) continue

            val record = FlatRecord(index, slotOffset, tableOffset, info.vtableOffset, info.objectSize, stringFields)
            records.add(record)
        }

        return RecordIndex(vectorOffset, count, records)
    }

    /**
     * 探测「哪些字段索引是字符串」。
     *
     * 旧版只看前 20 条 record，问题：
     *  - FlatBuffer vtable 可以省略尾部 0 offset 字段，前 20 条短 vtable 没出现的字段会漏；
     *  - 若某字段在前 20 条都是 0 offset，后面才填充，也会漏。
     *
     * 新版分两步：
     *  1) 扫所有 record 的 vtable，求 fieldIndex 并集（vtable 已经全部读了，无额外开销）；
     *  2) 在样本（最多 256 条均匀分布）里对每个候选字段调 quickStringCheck，命中一次就算字符串字段。
     */
    private fun discoverStringFieldIndices(buf: ByteBuffer, vectorOffset: Int, totalCount: Int): Set<Int> {
        val payload = buf.array()

        // Phase 1: 所有 record 的 fieldIndex 并集
        val candidateFields = HashSet<Int>()
        for (index in 0 until totalCount) {
            val slotOffset = vectorOffset + 4 + index * 4
            if (slotOffset + 4 > payload.size) break
            val tableOffset = slotOffset + buf.getInt(slotOffset)
            val info = readVtable(buf, tableOffset) ?: continue
            for ((fieldIndex, fieldOffset) in info.fields.withIndex()) {
                if (fieldOffset != 0) candidateFields.add(fieldIndex)
            }
        }
        if (candidateFields.isEmpty()) return emptySet()

        // Phase 2: 在均匀采样的 record 上验证哪些是字符串
        //
        // 注意：采样结果依赖 record 总数，所以源 payload（6 万+ 条）和导出后的 payload（压缩到几千条）
        // 会得出不同的字段集合。这里刻意不改成全量扫描——全量会让 4 条 record 的 primary 变化
        // （行内出现第二个 Gift token 时 giftTokens.last() 选了后面那个），用户可见的条目列表会凭空少 4 项、
        // 原本能勾选的 Gift_1617 直接消失。导出的正确性改在 Filtering 里保证：
        // scrubUnkeptRecordStrings 按完整物理字段视图把保留行内所有 token 字段都指向 primary，
        // validateExport 也按完整物理字段视图校验，两边都不受采样影响。
        val sampleCount = minOf(totalCount, 256)
        val step = if (sampleCount > 0) maxOf(1, totalCount / sampleCount) else 1
        val confirmed = HashSet<Int>()
        var index = 0
        while (index < totalCount && confirmed.size < candidateFields.size) {
            val slotOffset = vectorOffset + 4 + index * 4
            if (slotOffset + 4 > payload.size) break
            val tableOffset = slotOffset + buf.getInt(slotOffset)
            val info = readVtable(buf, tableOffset)
            if (info != null) {
                for ((fieldIndex, fieldOffset) in info.fields.withIndex()) {
                    if (fieldOffset == 0 || fieldIndex in confirmed) continue
                    if (fieldIndex !in candidateFields) continue
                    val fieldPosition = tableOffset + fieldOffset
                    if (quickStringCheck(buf, fieldPosition)) {
                        confirmed.add(fieldIndex)
                    }
                }
            }
            index += step
        }
        return confirmed
    }

    // Quick check: can this field position point to a valid string? No String allocation.
    private fun quickStringCheck(buf: ByteBuffer, fieldPosition: Int): Boolean {
        val payload = buf.array()
        if (fieldPosition < 0 || fieldPosition + 4 > payload.size) return false
        val relOffset = buf.getInt(fieldPosition) and 0x7FFFFFFF
        val stringOffset = fieldPosition + relOffset
        if (stringOffset % 4 != 0) return false
        if (stringOffset < 0 || stringOffset + 4 > payload.size) return false
        val length = buf.getInt(stringOffset) and 0x7FFFFFFF
        if (length < 1 || length > 512) return false
        val dataOffset = stringOffset + 4
        val dataEnd = dataOffset + length
        if (dataEnd > payload.size) return false
        // Check first few bytes look like text
        val first = payload[dataOffset].toInt() and 0xFF
        return first >= 0x20 // printable ASCII or multi-byte UTF-8
    }

    // Pre-classification regexes (shared with Analyzer, defined once)
    private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$""")
    private val DATE_COMPACT_RE = Regex("""^\d{4},\d{1,2},\d{1,2}(,\d{1,2},\d{1,2}){0,3}$""")
    private val NUMERIC_RE = Regex("""^[\d:|.,-]+$""")
    private val COLOR_CODE_RE = Regex("""^[0-9a-fA-F]{6}$""")

    // Read only the known string fields for a record, with pre-classification
    private fun readKnownStringFields(buf: ByteBuffer, info: VTableInfo, indices: Set<Int>): List<FlatStringField> {
        if (indices.isEmpty()) return emptyList()
        val payload = buf.array()
        val result = mutableListOf<FlatStringField>()
        for (fieldIndex in indices) {
            if (fieldIndex >= info.fields.size) continue
            val fieldOffset = info.fields[fieldIndex]
            if (fieldOffset == 0) continue
            val fieldPosition = info.tableOffset + fieldOffset
            val value = tryReadStringFieldFast(buf, payload, fieldPosition) ?: continue
            val text = value.text
            // Cheap pre-checks to skip the regex engine for the obvious cases.
            val firstChar = text[0]
            val firstIsLetter = (firstChar in 'A'..'Z') || (firstChar in 'a'..'z')
            val firstIsDigit = firstChar in '0'..'9'

            // Token category — only attempt for alphabetic-leading strings (matches Python rules)
            val category = if (firstIsLetter) TokenClassifier.classify(text) else null
            val isToken = category != null

            // Date — only check for digit-leading strings
            val isDate = !isToken && firstIsDigit && (DATE_RE.matches(text) || DATE_COMPACT_RE.matches(text))
            val isNumeric = !isToken && !isDate && looksNumeric(text)
            val isColor = !isToken && !isDate && !isNumeric && text.length == 6 && COLOR_CODE_RE.matches(text)
            // Label = plain text candidate that's not any of the above structured kinds.
            // Match Python: must contain at least one alphanumeric or CJK char.
            val isLabel = !isToken && !isDate && !isNumeric && !isColor &&
                text.length >= 2 && hasAlnumOrCjk(text)
            result.add(FlatStringField(fieldIndex, fieldOffset, fieldPosition,
                value.stringOffset, value.dataOffset, value.length, text,
                category, isToken, isDate, isNumeric, isLabel))
        }
        return result
    }

    private fun looksNumeric(text: String): Boolean {
        // Fast path: pure digits
        var allDigits = true
        for (ch in text) {
            if (ch !in '0'..'9') { allDigits = false; break }
        }
        if (allDigits) return true
        // Slow path: digit + separator chars
        return NUMERIC_RE.matches(text)
    }

    private fun hasAlnumOrCjk(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (code in 0x30..0x39) return true       // 0-9
            if (code in 0x41..0x5A) return true       // A-Z
            if (code in 0x61..0x7A) return true       // a-z
            if (code in 0x4E00..0x9FFF) return true   // CJK
        }
        return false
    }

    fun discoverRecordVector(buf: ByteBuffer): Pair<Int, Int> {
        val known = validateRecordVectorCandidate(buf, 60, minCount = 1)
        if (known != null) return known

        val payload = buf.array()
        val searchLimit = minOf(payload.size - 4, 4096)
        var bestScore = 0f
        var bestOffset = 0
        var bestCount = 0

        var offset = 0
        while (offset < searchLimit) {
            val candidate = validateRecordVectorCandidate(buf, offset, minCount = 1000)
            if (candidate != null) {
                val (_, count) = candidate
                val ratio = recordVectorValidRatio(buf, offset, count)
                val score = count * ratio
                if (score > bestScore) {
                    bestScore = score
                    bestOffset = offset
                    bestCount = count
                }
            }
            offset += 4
        }

        if (bestScore == 0f) throw IllegalArgumentException("ShopConfig main record vector not found")
        return Pair(bestOffset, bestCount)
    }

    private fun validateRecordVectorCandidate(buf: ByteBuffer, offset: Int, minCount: Int): Pair<Int, Int>? {
        val payload = buf.array()
        if (offset < 0 || offset + 4 > payload.size) return null
        val count = buf.getInt(offset) and 0x7FFFFFFF
        if (count < minCount || count > 200000) return null
        if (offset + 4 + count * 4 > payload.size) return null
        val ratio = recordVectorValidRatio(buf, offset, count)
        if (ratio < 0.75f) return null
        return Pair(offset, count)
    }

    private fun recordVectorValidRatio(buf: ByteBuffer, offset: Int, count: Int): Float {
        val sampleIndices = sampleIndices(count)
        var valid = 0
        for (index in sampleIndices) {
            val slotOffset = offset + 4 + index * 4
            val tableOffset = slotOffset + (buf.getInt(slotOffset) and 0x7FFFFFFF)
            if (readVtable(buf, tableOffset) != null) valid++
        }
        return valid.toFloat() / maxOf(sampleIndices.size, 1)
    }

    fun readVtable(buf: ByteBuffer, tableOffset: Int): VTableInfo? {
        val payload = buf.array()
        if (tableOffset < 0 || tableOffset + 4 > payload.size) return null
        val soffset = buf.getInt(tableOffset)
        val vtableOffset = tableOffset - soffset
        if (vtableOffset < 0 || vtableOffset + 4 > payload.size) return null

        val vtableSize = buf.getShort(vtableOffset).toInt() and 0xFFFF
        val objectSize = buf.getShort(vtableOffset + 2).toInt() and 0xFFFF
        if (vtableSize < 4 || vtableSize > 512 || vtableSize % 2 != 0) return null
        if (objectSize < 4 || objectSize > 8192) return null
        if (vtableOffset + vtableSize > payload.size) return null
        if (tableOffset + objectSize > payload.size) return null

        val numFields = (vtableSize - 4) / 2
        val fields = ArrayList<Int>(numFields)
        var pos = vtableOffset + 4
        val vtableEnd = vtableOffset + vtableSize
        while (pos < vtableEnd) {
            val fieldOffset = buf.getShort(pos).toInt() and 0xFFFF
            if (fieldOffset != 0 && (fieldOffset < 4 || fieldOffset >= objectSize)) return null
            fields.add(fieldOffset)
            pos += 2
        }

        return VTableInfo(tableOffset, vtableOffset, vtableSize, objectSize, fields)
    }

    // Fast string reader - avoids creating intermediate objects
    private fun tryReadStringFieldFast(buf: ByteBuffer, payload: ByteArray, fieldPosition: Int): StringFieldResult? {
        if (fieldPosition < 0 || fieldPosition + 4 > payload.size) return null
        val relOffset = buf.getInt(fieldPosition) and 0x7FFFFFFF
        val stringOffset = fieldPosition + relOffset
        if (stringOffset % 4 != 0) return null
        if (stringOffset < 0 || stringOffset + 4 > payload.size) return null

        val length = buf.getInt(stringOffset) and 0x7FFFFFFF
        if (length < 1 || length > 1024) return null
        val dataOffset = stringOffset + 4
        val dataEnd = dataOffset + length
        if (dataEnd > payload.size) return null

        // Check padding
        val padding = (4 - (length % 4)) % 4
        if (dataEnd + padding > payload.size) return null
        if (padding != 0) {
            var i = dataEnd
            val padEnd = dataEnd + padding
            while (i < padEnd) {
                if (payload[i].toInt() != 0) return null
                i++
            }
        }

        // Check plausibility on raw bytes first (no String allocation)
        if (!isPlausibleBytes(payload, dataOffset, length)) return null

        val text = String(payload, dataOffset, length, Charsets.UTF_8)
        return StringFieldResult(stringOffset, dataOffset, length, text)
    }

    // Byte-level plausibility check - much cheaper than creating a String
    private fun isPlausibleBytes(data: ByteArray, offset: Int, length: Int): Boolean {
        if (length == 0) return false
        var i = 0
        while (i < length) {
            val b = data[offset + i].toInt() and 0xFF
            if (b < 0x09) return false // control chars except \t
            if (b in 0x0B..0x0C) return false // \v, \f
            if (b in 0x0E..0x1F) return false // other control chars
            i++
        }
        return true
    }

    fun iterStringFields(payload: ByteArray, info: VTableInfo): List<FlatStringField> {
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val result = mutableListOf<FlatStringField>()
        for ((fieldIndex, fieldOffset) in info.fields.withIndex()) {
            if (fieldOffset == 0) continue
            val fieldPosition = info.tableOffset + fieldOffset
            val value = tryReadStringFieldFast(buf, payload, fieldPosition) ?: continue
            result.add(FlatStringField(fieldIndex, fieldOffset, fieldPosition,
                value.stringOffset, value.dataOffset, value.length, value.text))
        }
        return result
    }

    fun tryReadStringField(payload: ByteArray, fieldPosition: Int): StringFieldResult? {
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return tryReadStringFieldFast(buf, payload, fieldPosition)
    }

    fun rewriteRecordVector(payload: ByteArray, index: RecordIndex, keepTableOffsets: Set<Int>): Int {
        val kept = index.records.filter { it.tableOffset in keepTableOffsets }
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(index.vectorOffset, kept.size)
        for ((newIndex, record) in kept.withIndex()) {
            val slotOffset = index.vectorOffset + 4 + newIndex * 4
            val relative = record.tableOffset - slotOffset
            if (relative <= 0 || relative > 0xFFFFFFFFL.toInt()) {
                throw IllegalArgumentException("Record ${record.tableOffset} cannot fit in slot $slotOffset")
            }
            buf.putInt(slotOffset, relative)
        }
        return kept.size
    }

    fun primaryTokenOfRecord(record: FlatRecord, classifier: ((String) -> String?)? = null): String {
        val tokenFields = mutableListOf<Triple<Int, String, String>>()
        for (field in record.stringFields) {
            // 传了 classifier 就以它为准：它返回 null 表示「这不是 token」，不能再退回
            // defaultTokenCategory 的宽松 startsWith 判断。否则像 'bz_sf_1318'（多一个下划线，
            // 过不了 ^bz_sf\d+$）会被宽松规则认成 bz_sf 类并当上 primary，把同一行里真正的
            // token 'bz_sf1318' 挡住——该条目于是永远不可能被保留，导出必然报 missing kept items。
            // Analyzer 侧本来就只认严格分类，这里跟它保持一致。
            val category = if (classifier != null) {
                classifier.invoke(field.text)
            } else {
                defaultTokenCategory(field.text)
            }
            if (category != null) {
                tokenFields.add(Triple(field.fieldIndex, category, field.text))
            }
        }
        if (tokenFields.isEmpty()) return ""

        val giftTokens = tokenFields.filter { it.second == "Gift" }.map { it.third }
        if (giftTokens.isNotEmpty()) return giftTokens.last()

        val preferred = tokenFields.filter { it.second !in setOf("Gift_Effect", "qqliwu", "bza") }
        if (preferred.isNotEmpty()) return preferred.last().third
        return tokenFields.last().third
    }

    private fun defaultTokenCategory(text: String): String? = when {
        text.startsWith("keyskin_") -> "keyskin"
        text.startsWith("bz_sf") -> "bz_sf"
        text.startsWith("Gift_Effect_") -> "Gift_Effect"
        text.startsWith("Gift_") -> "Gift"
        text.startsWith("qqliwu_") -> "qqliwu"
        text.startsWith("bza_") -> "bza"
        text.startsWith("PiFu_") -> "PiFu"
        else -> null
    }

    private fun sampleIndices(count: Int): List<Int> {
        val raw = setOf(0, 1, 2, 3, count / 8, count / 4, count / 2, count * 3 / 4, count - 4, count - 3, count - 2, count - 1)
        return raw.filter { it in 0 until count }.sorted()
    }
}
