package com.qiutool.app.core

import net.jpountz.lz4.LZ4Factory
import org.tukaani.xz.LZMAInputStream
import org.tukaani.xz.LZMAOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File

class ShopConfigNotFoundError(message: String) : RuntimeException(message)

data class BlockEntry(
    val uncompressedSize: Int,
    val compressedSize: Int,
    val flags: Int
)

data class DirectoryEntry(
    val offset: Long,
    val size: Long,
    val flags: Int,
    val path: String
)

data class BundleLayout(
    val signature: String,
    val version: Int,
    val versionPlayer: String,
    val versionEngine: String,
    val fileSize: Long,
    val flags: Int,
    val headerEnd: Int,
    val blockInfoOffset: Int,
    val blockInfoCompressedSize: Int,
    val blockInfoUncompressedSize: Int,
    val blockDataOffset: Int,
    val blocksInfoAtEnd: Boolean,
    val blocks: List<BlockEntry>,
    val nodes: List<DirectoryEntry>
)

object BundleIO {

    private const val COMPRESSION_MASK = 0x3F
    private const val BLOCKS_INFO_AT_END = 0x80
    private const val BLOCK_INFO_NEED_PADDING_AT_START = 0x200

    private const val COMP_NONE = 0
    private const val COMP_LZMA = 1
    private const val COMP_LZ4 = 2
    private const val COMP_LZ4HC = 3

    private val lz4Factory = LZ4Factory.fastestInstance()

    fun parseBundleLayout(bundleBytes: ByteArray): BundleLayout {
        var offset = 0
        val signature = readCString(bundleBytes, offset)
        offset += signature.length + 1
        if (signature != "UnityFS") throw NotImplementedError("Only UnityFS supported, got: $signature")

        val version = be32(bundleBytes, offset); offset += 4
        val versionPlayer = readCString(bundleBytes, offset); offset += versionPlayer.length + 1
        val versionEngine = readCString(bundleBytes, offset); offset += versionEngine.length + 1

        val fileSize = be64(bundleBytes, offset); offset += 8
        val blockInfoCompressedSize = be32(bundleBytes, offset); offset += 4
        val blockInfoUncompressedSize = be32(bundleBytes, offset); offset += 4
        val flags = be32(bundleBytes, offset); offset += 4

        if (version >= 7) offset = align(offset, 16)

        val headerEnd = offset
        val blocksInfoAtEnd = (flags and BLOCKS_INFO_AT_END) != 0
        val blockInfoOffset = if (blocksInfoAtEnd) {
            bundleBytes.size - blockInfoCompressedSize
        } else {
            headerEnd
        }

        val blockInfoComp = bundleBytes.copyOfRange(blockInfoOffset, blockInfoOffset + blockInfoCompressedSize)
        val blockInfoRaw = decompressBuffer(blockInfoComp, blockInfoUncompressedSize, flags)

        var cursor = 16
        val blockCount = be32(blockInfoRaw, cursor); cursor += 4

        val blocks = mutableListOf<BlockEntry>()
        for (i in 0 until blockCount) {
            val uncompressedSize = be32(blockInfoRaw, cursor); cursor += 4
            val compressedSize = be32(blockInfoRaw, cursor); cursor += 4
            val blockFlags = be16(blockInfoRaw, cursor); cursor += 2
            blocks.add(BlockEntry(uncompressedSize, compressedSize, blockFlags))
        }

        val nodeCount = be32(blockInfoRaw, cursor); cursor += 4
        val nodes = mutableListOf<DirectoryEntry>()
        for (i in 0 until nodeCount) {
            val nodeOffset = be64(blockInfoRaw, cursor); cursor += 8
            val nodeSize = be64(blockInfoRaw, cursor); cursor += 8
            val nodeFlags = be32(blockInfoRaw, cursor); cursor += 4
            val nodePath = readCString(blockInfoRaw, cursor); cursor += nodePath.length + 1
            nodes.add(DirectoryEntry(nodeOffset, nodeSize, nodeFlags, nodePath))
        }

        var blockDataOffset = if (blocksInfoAtEnd) headerEnd else blockInfoOffset + blockInfoCompressedSize
        if ((flags and BLOCK_INFO_NEED_PADDING_AT_START) != 0) {
            blockDataOffset = align(blockDataOffset, 16)
        }

        return BundleLayout(
            signature = signature,
            version = version,
            versionPlayer = versionPlayer,
            versionEngine = versionEngine,
            fileSize = fileSize,
            flags = flags,
            headerEnd = headerEnd,
            blockInfoOffset = blockInfoOffset,
            blockInfoCompressedSize = blockInfoCompressedSize,
            blockInfoUncompressedSize = blockInfoUncompressedSize,
            blockDataOffset = blockDataOffset,
            blocksInfoAtEnd = blocksInfoAtEnd,
            blocks = blocks,
            nodes = nodes
        )
    }

    fun extractUncompressedData(bundleBytes: ByteArray, layout: BundleLayout): ByteArray {
        var cursor = layout.blockDataOffset
        val chunks = mutableListOf<ByteArray>()
        for (block in layout.blocks) {
            val blockComp = bundleBytes.copyOfRange(cursor, cursor + block.compressedSize)
            cursor += block.compressedSize
            chunks.add(decompressBuffer(blockComp, block.uncompressedSize, block.flags))
        }
        val totalSize = chunks.sumOf { it.size }
        val result = ByteArray(totalSize)
        var pos = 0
        for (chunk in chunks) {
            chunk.copyInto(result, pos)
            pos += chunk.size
        }
        return result
    }

    fun extractOriginalBlockPayloads(bundleBytes: ByteArray, layout: BundleLayout): List<ByteArray> {
        var cursor = layout.blockDataOffset
        val payloads = mutableListOf<ByteArray>()
        for (block in layout.blocks) {
            payloads.add(bundleBytes.copyOfRange(cursor, cursor + block.compressedSize))
            cursor += block.compressedSize
        }
        return payloads
    }

    fun extractShopconfigPayload(bundlePath: File): ByteArray {
        val t0 = System.currentTimeMillis()
        val bundleBytes = bundlePath.readBytes()
        val t1 = System.currentTimeMillis()
        val layout = parseBundleLayout(bundleBytes)
        val t2 = System.currentTimeMillis()
        val uncompressedData = extractUncompressedData(bundleBytes, layout)
        val t3 = System.currentTimeMillis()
        val payload = findShopConfigPayload(uncompressedData)
        val t4 = System.currentTimeMillis()
        android.util.Log.d("QiuTool", "read=${t1-t0}ms parse=${t2-t1}ms decompress=${t3-t2}ms search=${t4-t3}ms uncompressed=${uncompressedData.size}B blocks=${layout.blocks.size}")
        if (payload == null) {
            val found = listTextAssetNames(uncompressedData)
            val hint = if (found.isEmpty()) "No TextAsset objects found" else "Found TextAssets: ${found.joinToString(", ")}"
            throw ShopConfigNotFoundError("ShopConfig TextAsset not found in ${bundlePath.name} ($hint)")
        }
        return payload
    }

    private fun listTextAssetNames(data: ByteArray): List<String> {
        val names = mutableListOf<String>()
        var offset = 0
        while (offset <= data.size - 8) {
            val nameLen = le32(data, offset)
            if (nameLen in 1..80 && offset + 4 + nameLen <= data.size) {
                try {
                    val name = String(data, offset + 4, nameLen, Charsets.UTF_8)
                    if (name.all { it.isLetterOrDigit() || it in "_- ./\\" }) {
                        val padding = (4 - (nameLen % 4)) % 4
                        val scriptLenOff = offset + 4 + nameLen + padding
                        if (scriptLenOff + 4 <= data.size) {
                            val scriptLen = le32(data, scriptLenOff)
                            if (scriptLen > 64) names.add("'$name'(${scriptLen}b)")
                        }
                    }
                } catch (_: Exception) { /* skip */ }
            }
            offset++
            if (names.size >= 10) break
        }
        return names
    }

    private fun findShopConfigPayload(data: ByteArray): ByteArray? {
        // Try 1: Exact match "ShopConfig" (fast raw bytes comparison)
        extractByExactName(data, "ShopConfig", 0)?.let { return it }

        // Try 2: Case-insensitive match (raw byte comparison, no String allocation)
        extractByCaseInsensitiveName(data, "ShopConfig", 0)?.let { return it }

        // Try 3: Search data section (skip SerializedFile header)
        val dataOffset = detectDataOffset(data)
        if (dataOffset > 48 && dataOffset < data.size) {
            extractByCaseInsensitiveName(data, "ShopConfig", dataOffset)?.let { return it }
        }

        // Try 4: Find any TextAsset with a large FlatBuffer payload (data section only)
        val startScan = if (dataOffset > 48) dataOffset else 0
        return findLargestTextAssetPayload(data, startScan)
    }

    private fun extractByExactName(data: ByteArray, name: String, startFrom: Int): ByteArray? {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val nameLen = nameBytes.size
        var offset = startFrom
        val limit = data.size - 4 - nameLen - 8
        while (offset <= limit) {
            if (le32(data, offset) == nameLen && matchBytes(data, offset + 4, nameBytes)) {
                val payload = readPayload(data, offset, nameLen)
                if (payload != null) return payload
            }
            offset++
        }
        return null
    }

    private fun extractByCaseInsensitiveName(data: ByteArray, name: String, startFrom: Int): ByteArray? {
        val lowerBytes = name.lowercase().toByteArray(Charsets.UTF_8)
        val upperBytes = name.uppercase().toByteArray(Charsets.UTF_8)
        val nameLen = lowerBytes.size
        var offset = startFrom
        val limit = data.size - 4 - nameLen - 8
        while (offset <= limit) {
            if (le32(data, offset) == nameLen && matchBytesCI(data, offset + 4, lowerBytes, upperBytes)) {
                val payload = readPayload(data, offset, nameLen)
                if (payload != null) return payload
            }
            offset++
        }
        return null
    }

    private fun matchBytes(data: ByteArray, offset: Int, pattern: ByteArray): Boolean {
        for (i in pattern.indices) {
            if (data[offset + i] != pattern[i]) return false
        }
        return true
    }

    private fun matchBytesCI(data: ByteArray, offset: Int, lower: ByteArray, upper: ByteArray): Boolean {
        for (i in lower.indices) {
            val b = data[offset + i]
            if (b != lower[i] && b != upper[i]) return false
        }
        return true
    }

    private fun readPayload(data: ByteArray, nameOffset: Int, nameLen: Int): ByteArray? {
        val padding = (4 - (nameLen % 4)) % 4
        val scriptLenOff = nameOffset + 4 + nameLen + padding
        if (scriptLenOff + 4 > data.size) return null
        val scriptLen = le32(data, scriptLenOff)
        val scriptOff = scriptLenOff + 4
        if (scriptLen <= 0 || scriptOff + scriptLen > data.size) return null
        return data.copyOfRange(scriptOff, scriptOff + scriptLen)
    }

    private fun detectDataOffset(data: ByteArray): Int {
        if (data.size < 20) return 0
        try {
            val sfVersion = be32(data, 8)
            return if (sfVersion >= 22) be64(data, 12).toInt() else be32(data, 12)
        } catch (_: Exception) { return 0 }
    }

    private fun findLargestTextAssetPayload(data: ByteArray, startFrom: Int): ByteArray? {
        var bestOffset = -1
        var bestSize = 0
        var offset = startFrom
        val limit = data.size - 8
        while (offset <= limit) {
            val nameLen = le32(data, offset)
            if (nameLen in 1..80 && offset + 4 + nameLen + 8 <= data.size && isPrintableAscii(data, offset + 4, nameLen)) {
                val padding = (4 - (nameLen % 4)) % 4
                val scriptLenOff = offset + 4 + nameLen + padding
                if (scriptLenOff + 4 <= data.size) {
                    val scriptLen = le32(data, scriptLenOff)
                    if (scriptLen > bestSize && scriptLen > 256) {
                        val scriptOff = scriptLenOff + 4
                        if (scriptOff + scriptLen <= data.size && looksLikeFlatBuffer(data, scriptOff)) {
                            bestOffset = scriptOff
                            bestSize = scriptLen
                        }
                    }
                }
            }
            offset += 4 // skip 4 bytes at a time since nameLen is 4-byte aligned
        }
        return if (bestOffset >= 0) data.copyOfRange(bestOffset, bestOffset + bestSize) else null
    }

    private fun isPrintableAscii(data: ByteArray, offset: Int, len: Int): Boolean {
        for (i in 0 until len) {
            val b = data[offset + i].toInt() and 0xFF
            if (b < 0x20 || b > 0x7E) {
                // Allow common non-ASCII UTF-8 (Chinese chars etc)
                if (b < 0x80) return false
            }
        }
        return true
    }

    private fun looksLikeFlatBuffer(data: ByteArray, offset: Int): Boolean {
        if (offset + 8 > data.size) return false
        val rootOffset = le32(data, offset).toLong().toInt()
        if (rootOffset >= 0 || -rootOffset > data.size - offset - 4) return false
        val vtablePos = offset + (-rootOffset)
        if (vtablePos + 4 > data.size) return false
        val vtableSize = le32(data, vtablePos) and 0xFFFF
        return vtableSize in 4..200
    }

    fun replaceShopconfigPayload(
        sourceBundle: File,
        newPayload: ByteArray,
        outputDir: File,
        preserveSourceSize: Boolean = true
    ): File {
        outputDir.mkdirs()
        val bundleBytes = sourceBundle.readBytes()
        val layout = parseBundleLayout(bundleBytes)
        val uncompressedData = extractUncompressedData(bundleBytes, layout)
        val originalBlockPayloads = extractOriginalBlockPayloads(bundleBytes, layout)

        // Find ShopConfig TextAsset raw range in uncompressedData
        val rawInfo = findShopConfigRawInfo(uncompressedData)
            ?: throw ShopConfigNotFoundError("ShopConfig TextAsset not found")
        val (rawOffset, _) = rawInfo

        // Compute the exact byte range the payload occupies (no need to copy raw out)
        val nameLen = le32(uncompressedData, rawOffset)
        val scriptLenOffset = rawOffset + 4 + nameLen + ((4 - (nameLen % 4)) % 4)
        val originalPayloadLen = le32(uncompressedData, scriptLenOffset)
        if (newPayload.size != originalPayloadLen) {
            throw IllegalArgumentException(
                "Equal-length replacement required: original=$originalPayloadLen, new=${newPayload.size}"
            )
        }
        val payloadOffset = scriptLenOffset + 4
        val payloadEnd = payloadOffset + newPayload.size

        // In-place modification — avoids 3 redundant 21MB allocations (originalRaw/modifiedRaw/originalUncompressedData)
        newPayload.copyInto(uncompressedData, payloadOffset)

        // Rebuild blocks: a block changed iff its uncompressed range intersects [payloadOffset, payloadEnd)
        val newBlocks = mutableListOf<BlockEntry>()
        val newBlockPayloads = mutableListOf<ByteArray>()
        var cursor = 0
        for ((index, block) in layout.blocks.withIndex()) {
            val nextCursor = cursor + block.uncompressedSize
            val blockChanged = !(nextCursor <= payloadOffset || cursor >= payloadEnd)
            if (!blockChanged) {
                newBlocks.add(block)
                newBlockPayloads.add(originalBlockPayloads[index])
            } else {
                val blockBytes = uncompressedData.copyOfRange(cursor, nextCursor)
                val (newBlock, packedBlock) = buildBlockEntry(block.flags, blockBytes)
                newBlocks.add(newBlock)
                newBlockPayloads.add(packedBlock)
            }
            cursor = nextCursor
        }

        // Rebuild block info
        val rebuiltBlockInfoRaw = buildBlockInfoBytes(newBlocks, layout.nodes)

        // Try different block info compression flags
        var candidateBytes: ByteArray? = null
        var candidateLength = Int.MAX_VALUE
        for (blockInfoFlags in candidateBlockInfoFlags(layout.flags)) {
            val rebuiltBlockInfoComp = compressBuffer(rebuiltBlockInfoRaw, blockInfoFlags)
            val built = serializeBundle(layout, blockInfoFlags, rebuiltBlockInfoComp, rebuiltBlockInfoRaw, newBlockPayloads)
            if (built.size < candidateLength) {
                candidateBytes = built
                candidateLength = built.size
            }
        }

        val result = candidateBytes ?: throw RuntimeException("Cannot build bundle with preserved size")
        val out = if (preserveSourceSize && result.size < bundleBytes.size) {
            result + ByteArray(bundleBytes.size - result.size)
        } else {
            result
        }

        val outputDir2 = outputDir
        outputDir2.mkdirs()
        val outputPath = File(outputDir2, sourceBundle.name)
        outputPath.writeBytes(out)

        // Verify
        val verifyPayload = extractShopconfigPayload(outputPath)
        if (!verifyPayload.contentEquals(newPayload)) {
            throw RuntimeException("Export verification failed: ShopConfig payload mismatch")
        }
        return outputPath
    }

    private fun findShopConfigRawInfo(data: ByteArray): Pair<Int, Int>? {
        // Try exact "ShopConfig" (raw bytes, no String allocation)
        rawInfoByExactName(data, "ShopConfig", 0)?.let { return it }
        // Try case-insensitive (raw bytes)
        rawInfoByCaseInsensitive(data, "ShopConfig", 0)?.let { return it }
        // Try data section only
        val dataOffset = detectDataOffset(data)
        if (dataOffset > 48 && dataOffset < data.size) {
            rawInfoByCaseInsensitive(data, "ShopConfig", dataOffset)?.let { return it }
        }
        // Fallback: largest FlatBuffer TextAsset
        val startScan = if (dataOffset > 48) dataOffset else 0
        return findLargestTextAssetRawInfo(data, startScan)
    }

    private fun rawInfoByExactName(data: ByteArray, name: String, startFrom: Int): Pair<Int, Int>? {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val nameLen = nameBytes.size
        var offset = startFrom
        val limit = data.size - 4 - nameLen - 8
        while (offset <= limit) {
            if (le32(data, offset) == nameLen && matchBytes(data, offset + 4, nameBytes)) {
                readRawInfo(data, offset, nameLen)?.let { return it }
            }
            offset++
        }
        return null
    }

    private fun rawInfoByCaseInsensitive(data: ByteArray, name: String, startFrom: Int): Pair<Int, Int>? {
        val lowerBytes = name.lowercase().toByteArray(Charsets.UTF_8)
        val upperBytes = name.uppercase().toByteArray(Charsets.UTF_8)
        val nameLen = lowerBytes.size
        var offset = startFrom
        val limit = data.size - 4 - nameLen - 8
        while (offset <= limit) {
            if (le32(data, offset) == nameLen && matchBytesCI(data, offset + 4, lowerBytes, upperBytes)) {
                readRawInfo(data, offset, nameLen)?.let { return it }
            }
            offset++
        }
        return null
    }

    private fun readRawInfo(data: ByteArray, nameOffset: Int, nameLen: Int): Pair<Int, Int>? {
        val padding = (4 - (nameLen % 4)) % 4
        val scriptLenOff = nameOffset + 4 + nameLen + padding
        if (scriptLenOff + 4 > data.size) return null
        val scriptLen = le32(data, scriptLenOff)
        val scriptOff = scriptLenOff + 4
        if (scriptLen <= 0 || scriptOff + scriptLen > data.size) return null
        return Pair(nameOffset, scriptOff + scriptLen - nameOffset)
    }

    private fun findLargestTextAssetRawInfo(data: ByteArray, startFrom: Int): Pair<Int, Int>? {
        var bestNameOffset = -1
        var bestEndOffset = -1
        var bestSize = 0
        var offset = startFrom
        val limit = data.size - 8
        while (offset <= limit) {
            val nameLen = le32(data, offset)
            if (nameLen in 1..80 && offset + 4 + nameLen + 8 <= data.size && isPrintableAscii(data, offset + 4, nameLen)) {
                val padding = (4 - (nameLen % 4)) % 4
                val scriptLenOff = offset + 4 + nameLen + padding
                if (scriptLenOff + 4 <= data.size) {
                    val scriptLen = le32(data, scriptLenOff)
                    if (scriptLen > bestSize && scriptLen > 256) {
                        val scriptOff = scriptLenOff + 4
                        if (scriptOff + scriptLen <= data.size && looksLikeFlatBuffer(data, scriptOff)) {
                            bestNameOffset = offset
                            bestEndOffset = scriptOff + scriptLen
                            bestSize = scriptLen
                        }
                    }
                }
            }
            offset += 4
        }
        return if (bestNameOffset >= 0) Pair(bestNameOffset, bestEndOffset - bestNameOffset) else null
    }

    private fun buildModifiedRaw(originalRaw: ByteArray, newPayload: ByteArray): ByteArray {
        val nameLen = le32(originalRaw, 0)
        val scriptLenOffset = 4 + nameLen + ((4 - (nameLen % 4)) % 4)
        val originalPayloadLen = le32(originalRaw, scriptLenOffset)
        if (newPayload.size != originalPayloadLen) {
            throw IllegalArgumentException(
                "Equal-length replacement required: original=$originalPayloadLen, new=${newPayload.size}"
            )
        }
        val scriptOffset = scriptLenOffset + 4
        val result = originalRaw.copyOf()
        newPayload.copyInto(result, scriptOffset)
        return result
    }

    private fun buildBlockEntry(originalFlags: Int, uncompressedBlock: ByteArray): Pair<BlockEntry, ByteArray> {
        val compression = originalFlags and COMPRESSION_MASK
        if (compression == COMP_NONE) {
            return Pair(
                BlockEntry(uncompressedBlock.size, uncompressedBlock.size, originalFlags),
                uncompressedBlock
            )
        }
        val compressedBlock = compressBuffer(uncompressedBlock, originalFlags)
        if (compressedBlock.size > uncompressedBlock.size) {
            return Pair(
                BlockEntry(uncompressedBlock.size, uncompressedBlock.size, originalFlags xor compression),
                uncompressedBlock
            )
        }
        return Pair(
            BlockEntry(uncompressedBlock.size, compressedBlock.size, originalFlags),
            compressedBlock
        )
    }

    private fun buildBlockInfoBytes(blocks: List<BlockEntry>, nodes: List<DirectoryEntry>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ByteArray(16)) // 16 zero bytes
        writeBe32(out, blocks.size)
        for (block in blocks) {
            writeBe32(out, block.uncompressedSize)
            writeBe32(out, block.compressedSize)
            writeBe16(out, block.flags)
        }
        writeBe32(out, nodes.size)
        for (node in nodes) {
            writeBe64(out, node.offset)
            writeBe64(out, node.size)
            writeBe32(out, node.flags)
            out.write(node.path.toByteArray(Charsets.UTF_8))
            out.write(0)
        }
        return out.toByteArray()
    }

    private fun serializeBundle(
        layout: BundleLayout,
        blockInfoFlags: Int,
        blockInfoComp: ByteArray,
        blockInfoRaw: ByteArray,
        blockPayloads: List<ByteArray>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(layout.signature.toByteArray(Charsets.UTF_8)); out.write(0)
        writeBe32(out, layout.version)
        out.write(layout.versionPlayer.toByteArray(Charsets.UTF_8)); out.write(0)
        out.write(layout.versionEngine.toByteArray(Charsets.UTF_8)); out.write(0)
        writeBe64(out, 0L) // file_size placeholder
        writeBe32(out, blockInfoComp.size)
        writeBe32(out, blockInfoRaw.size)
        writeBe32(out, blockInfoFlags)

        if (layout.version >= 7) {
            val padding = align(out.size(), 16) - out.size()
            if (padding > 0) out.write(ByteArray(padding))
        }

        if (layout.blocksInfoAtEnd) {
            if ((blockInfoFlags and BLOCK_INFO_NEED_PADDING_AT_START) != 0) {
                val padding = align(out.size(), 16) - out.size()
                if (padding > 0) out.write(ByteArray(padding))
            }
            for (payload in blockPayloads) out.write(payload)
            out.write(blockInfoComp)
        } else {
            out.write(blockInfoComp)
            if ((blockInfoFlags and BLOCK_INFO_NEED_PADDING_AT_START) != 0) {
                val padding = align(out.size(), 16) - out.size()
                if (padding > 0) out.write(ByteArray(padding))
            }
            for (payload in blockPayloads) out.write(payload)
        }

        // Patch file_size in header
        val result = out.toByteArray()
        val fileSizeOffset = layout.signature.length + 1 + 4 + layout.versionPlayer.length + 1 + layout.versionEngine.length + 1
        val bb = ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN)
        bb.putLong(fileSizeOffset, result.size.toLong())
        return result
    }

    private fun candidateBlockInfoFlags(originalFlags: Int): List<Int> {
        val base = originalFlags and COMPRESSION_MASK.inv()
        val originalCompression = originalFlags and COMPRESSION_MASK
        val ordered = listOf(originalCompression, COMP_LZMA, COMP_LZ4, COMP_NONE)
        val seen = mutableSetOf<Int>()
        val result = mutableListOf<Int>()
        for (compression in ordered) {
            val flags = base or compression
            if (flags !in seen) {
                result.add(flags)
                seen.add(flags)
            }
        }
        return result
    }

    private fun decompressBuffer(data: ByteArray, uncompressedSize: Int, flags: Int): ByteArray {
        return when (val compression = flags and COMPRESSION_MASK) {
            COMP_NONE -> data
            COMP_LZMA -> decompressUnityLzma(data, uncompressedSize)
            COMP_LZ4, COMP_LZ4HC -> {
                val decompressor = lz4Factory.fastDecompressor()
                val result = ByteArray(uncompressedSize)
                decompressor.decompress(data, 0, result, 0, uncompressedSize)
                result
            }
            else -> throw NotImplementedError("Unsupported UnityFS compression: $compression")
        }
    }

    private fun compressBuffer(data: ByteArray, flags: Int): ByteArray {
        return when (val compression = flags and COMPRESSION_MASK) {
            COMP_NONE -> data
            COMP_LZMA -> compressUnityLzma(data)
            COMP_LZ4, COMP_LZ4HC -> {
                val compressor = lz4Factory.fastCompressor()
                compressor.compress(data)
            }
            else -> throw NotImplementedError("Unsupported UnityFS compression: $compression")
        }
    }

    /**
     * Unity bundle 的 LZMA 帧布局：
     *   1 字节 properties (lc/lp/pb 编码)
     *   4 字节 dict_size (little-endian)
     *   接 LZMA1 raw 比特流（**没有** 8 字节 uncompressed_size，**没有** end marker）
     * 解码靠外部 blockInfoUncompressedSize 知道停在哪。
     *
     * xz-java 的 `LZMAOutputStream` 始终写 5+8 字节头，所以我们写完后把 5..13 这 8 字节抠掉。
     */
    private fun compressUnityLzma(data: ByteArray): ByteArray {
        // 反编译 xz-1.9 LZMAOutputStream 字节码看到:
        //   * (out, options, long inputSize)     → writeHeader=TRUE, 写 5+8 字节头
        //   * (out, options, boolean useEndMarker) → writeHeader=FALSE, **不写任何头**, 只输出 LZMA 数据
        // 我们要 Unity 的 5 字节头（props + dict_size LE）+ 裸 LZMA, 所以用 boolean 构造器拿裸数据再手动拼头。
        val options = org.tukaani.xz.LZMA2Options()
        val raw = ByteArrayOutputStream()
        LZMAOutputStream(raw, options, false).use { it.write(data) }
        val body = raw.toByteArray()

        val propsByte = ((options.pb * 5 + options.lp) * 9 + options.lc).toByte()
        val dictSize = options.dictSize
        val out = ByteArray(5 + body.size)
        out[0] = propsByte
        out[1] = (dictSize and 0xFF).toByte()
        out[2] = ((dictSize shr 8) and 0xFF).toByte()
        out[3] = ((dictSize shr 16) and 0xFF).toByte()
        out[4] = ((dictSize shr 24) and 0xFF).toByte()
        System.arraycopy(body, 0, out, 5, body.size)
        return out
    }

    private fun decompressUnityLzma(data: ByteArray, uncompressedSize: Int): ByteArray {
        if (data.size < 5) throw RuntimeException("LZMA frame too short: ${data.size}")
        val propsByte = data[0]
        val dictSize = (data[1].toInt() and 0xFF) or
            ((data[2].toInt() and 0xFF) shl 8) or
            ((data[3].toInt() and 0xFF) shl 16) or
            ((data[4].toInt() and 0xFF) shl 24)
        val rawIn = java.io.ByteArrayInputStream(data, 5, data.size - 5)
        // 4 参数构造器：把 propsByte / dictSize / uncompSize 显式喂进来，不再读取流里的头部
        val lzma = LZMAInputStream(rawIn, uncompressedSize.toLong(), propsByte, dictSize)
        return lzma.use { it.readBytes() }
    }

    private fun readCString(data: ByteArray, offset: Int): String {
        var end = offset
        while (end < data.size && data[end].toInt() != 0) end++
        if (end >= data.size) throw IllegalArgumentException("No null terminator found")
        return String(data, offset, end - offset, Charsets.UTF_8)
    }

    private fun align(value: Int, boundary: Int): Int = (value + boundary - 1) / boundary * boundary

    private fun be16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun be32(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
                ((data[offset + 1].toInt() and 0xFF) shl 16) or
                ((data[offset + 2].toInt() and 0xFF) shl 8) or
                (data[offset + 3].toInt() and 0xFF)
    }

    private fun be64(data: ByteArray, offset: Int): Long {
        return (be32(data, offset).toLong() shl 32) or (be32(data, offset + 4).toLong() and 0xFFFFFFFFL)
    }

    private fun le32(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeBe16(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeBe32(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeBe64(out: ByteArrayOutputStream, value: Long) {
        writeBe32(out, (value shr 32).toInt())
        writeBe32(out, value.toInt())
    }
}
