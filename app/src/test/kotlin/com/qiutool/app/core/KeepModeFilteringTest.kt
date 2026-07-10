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
            "redirectUnkeptTokenFields",
            ByteArray::class.java,
            FlatRecord::class.java,
            java.util.Set::class.java,
            String::class.java,
        )
        method.isAccessible = true

        method.invoke(Filtering, payload, record, setOf("PiFu_100"), "PiFu_100")

        assertEquals(0, readLe32(payload, unkeptStringOffset))
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
