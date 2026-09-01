package com.qiutool.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class KeepModeFilteringTest {

    @Test
    fun expandSelectedTokensDoesNotKeepSameNameItemsFromOtherCategories() {
        val items = listOf(
            ItemRecord(token = "PiFu_100", category = "PiFu", itemId = "100", name = "同名奖励"),
            ItemRecord(token = "Gift_200", category = "Gift", itemId = "200", name = "同名奖励"),
            ItemRecord(token = "PiFu_101", category = "PiFu", itemId = "101", name = "同名奖励"),
        )

        val expanded = Analyzer.expandSelectedTokens(setOf("PiFu_100"), items)

        assertEquals(setOf("PiFu_100", "PiFu_101"), expanded)
    }

    /**
     * 'bz_sf_1318' 多一个下划线，过不了 TokenClassifier 的 ^bz_sf\d+$。传了 classifier 时不能退回
     * defaultTokenCategory 的宽松 startsWith，否则它会当上 primary，把同一行里真正的 token
     * 'bz_sf1318' 挡住，该条目永远无法保留，导出必报 missing kept items。
     */
    @Test
    fun primaryTokenIgnoresStringsTheClassifierRejects() {
        val record = recordOf(
            fieldIndex = 4 to "bz_sf1318",
            extra = listOf(22 to "bz_sf_1318"),
        )

        val primary = FlatBufferScanner.primaryTokenOfRecord(record) { TokenClassifier.classify(it) }

        assertEquals("bz_sf1318", primary)
    }

    /** 没传 classifier 时保持原来的宽松行为，避免影响其它调用点。 */
    @Test
    fun primaryTokenWithoutClassifierKeepsLooseBehaviour() {
        val record = recordOf(
            fieldIndex = 4 to "bz_sf1318",
            extra = listOf(22 to "bz_sf_1318"),
        )

        assertEquals("bz_sf_1318", FlatBufferScanner.primaryTokenOfRecord(record))
    }

    /**
     * 同一行里除 primary 以外的 token 字段必须全部让位，即使它也在勾选集合里。
     * 否则 giftTokens.last() 可能选中它，这一行的身份就从 Gift_1617 变成 Gift_1628。
     */
    @Test
    fun foreignKeptTokenCannotOutrankPrimary() {
        val payload = ByteArray(256)
        val primaryPos = 16
        val foreignPos = 20
        val primaryOffset = 64
        val foreignOffset = 96
        putLe32(payload, primaryPos, primaryOffset - primaryPos)
        putLe32(payload, foreignPos, foreignOffset - foreignPos)
        writeStringObject(payload, primaryOffset, "Gift_1617")
        writeStringObject(payload, foreignOffset, "Gift_1628")

        val fields = listOf(
            stringField(88, primaryPos, primaryOffset, "Gift_1617"),
            stringField(125, foreignPos, foreignOffset, "Gift_1628"),
        )

        val method = Filtering::class.java.getDeclaredMethod(
            "redirectForeignTokenFields",
            ByteArray::class.java,
            List::class.java,
            String::class.java,
            java.util.Set::class.java,
        )
        method.isAccessible = true
        // Gift_1628 也在保留集合里，但它不是本行 primary，仍然必须被改指向 Gift_1617。
        method.invoke(Filtering, payload, fields, "Gift_1617", setOf(primaryOffset))

        val redirected = foreignPos + readLe32(payload, foreignPos)
        assertEquals(primaryOffset, redirected)
    }

    @Test
    fun redirectUnkeptTokenFieldsBlanksUnredirectableToken() {
        val payload = ByteArray(96)
        val fieldPosition = 16
        val unkeptStringOffset = 40
        val unkeptToken = "Gift_Effect_200"
        putLe32(payload, fieldPosition, unkeptStringOffset - fieldPosition)
        writeStringObject(payload, unkeptStringOffset, unkeptToken)

        val record = FlatRecord(
            index = 0,
            slotOffset = 0,
            tableOffset = 0,
            vtableOffset = 0,
            objectSize = 64,
            stringFields = listOf(
                FlatStringField(
                    fieldIndex = 0,
                    fieldOffset = fieldPosition,
                    fieldPosition = fieldPosition,
                    stringOffset = unkeptStringOffset,
                    dataOffset = unkeptStringOffset + 4,
                    length = unkeptToken.length,
                    text = unkeptToken,
                )
            )
        )

        val method = Filtering::class.java.getDeclaredMethod(
            "redirectForeignTokenFields",
            ByteArray::class.java,
            List::class.java,
            String::class.java,
            java.util.Set::class.java,
        )
        method.isAccessible = true

        method.invoke(Filtering, payload, record.stringFields, "PiFu_100", emptySet<Int>())

        assertEquals(0, readLe32(payload, unkeptStringOffset))
    }

    private fun stringField(fieldIndex: Int, fieldPosition: Int, stringOffset: Int, text: String) =
        FlatStringField(
            fieldIndex = fieldIndex,
            fieldOffset = fieldPosition,
            fieldPosition = fieldPosition,
            stringOffset = stringOffset,
            dataOffset = stringOffset + 4,
            length = text.toByteArray(Charsets.UTF_8).size,
            text = text,
        )

    private fun recordOf(
        fieldIndex: Pair<Int, String>,
        extra: List<Pair<Int, String>> = emptyList(),
    ): FlatRecord {
        val fields = (listOf(fieldIndex) + extra).mapIndexed { i, (idx, text) ->
            stringField(idx, 16 + i * 4, 64 + i * 32, text)
        }
        return FlatRecord(
            index = 0,
            slotOffset = 0,
            tableOffset = 0,
            vtableOffset = 0,
            objectSize = 256,
            stringFields = fields,
        )
    }

    private fun writeStringObject(payload: ByteArray, offset: Int, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        putLe32(payload, offset, bytes.size)
        bytes.copyInto(payload, offset + 4)
    }

    private fun putLe32(payload: ByteArray, offset: Int, value: Int) {
        payload[offset] = (value and 0xFF).toByte()
        payload[offset + 1] = ((value shr 8) and 0xFF).toByte()
        payload[offset + 2] = ((value shr 16) and 0xFF).toByte()
        payload[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun readLe32(payload: ByteArray, offset: Int): Int =
        (payload[offset].toInt() and 0xFF) or
            ((payload[offset + 1].toInt() and 0xFF) shl 8) or
            ((payload[offset + 2].toInt() and 0xFF) shl 16) or
            ((payload[offset + 3].toInt() and 0xFF) shl 24)
}
