package com.qiutool.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTemplatesTest {

    @Test
    fun applyToKeepsExistingTokensAndReportsMissing() {
        val items = listOf(
            ItemRecord(token = "PiFu_100", category = "PiFu", itemId = "100"),
            ItemRecord(token = "PiFu_101", category = "PiFu", itemId = "101"),
        )
        val template = ExportTemplate(
            name = "旧方案",
            mode = "keep",
            tokens = listOf("PiFu_100", "PiFu_999", "PiFu_101", "Gift_1"),
        )

        val result = ExportTemplates.applyTo(template, items)

        assertEquals(setOf("PiFu_100", "PiFu_101"), result.matched)
        assertEquals(listOf("PiFu_999", "Gift_1"), result.missing)
    }

    @Test
    fun encodeDecodeRoundTripPreservesDelimiterCharacters() {
        val templates = listOf(
            ExportTemplate(
                name = "含,逗号\t与\\反斜杠",
                mode = "keep",
                tokens = listOf("A,1", "B\tTab", "C\\Slash"),
                sourceCategory = "shopconfig",
                savedAt = 1700000000000L,
            ),
            ExportTemplate(
                name = "第二个",
                mode = "exclude",
                tokens = listOf("D_2"),
            ),
        )

        val decoded = ExportTemplates.decode(ExportTemplates.encode(templates))

        assertEquals(templates, decoded)
    }

    @Test
    fun decodeIgnoresBlankAndMalformedRecords() {
        assertEquals(emptyList<ExportTemplate>(), ExportTemplates.decode(null))
        assertEquals(emptyList<ExportTemplate>(), ExportTemplates.decode(""))
        assertNull(ExportTemplates.decodeOne("只有一个字段"))
    }

    @Test
    fun upsertOverwritesSameNameAndCapsAtLimit() {
        val existing = ExportTemplate(name = "A", mode = "exclude", tokens = listOf("old"))
        val updated = ExportTemplate(name = "A", mode = "keep", tokens = listOf("new"))

        val result = ExportTemplates.upsert(listOf(existing), updated)

        assertEquals(listOf(updated), result)

        val many = (1..ExportTemplates.MAX_TEMPLATES).map {
            ExportTemplate(name = "T$it", mode = "exclude", tokens = listOf("t$it"))
        }
        val capped = ExportTemplates.upsert(many, ExportTemplate("新", "keep", listOf("x")))

        assertEquals(ExportTemplates.MAX_TEMPLATES, capped.size)
        assertEquals("新", capped.first().name)
        assertTrue(capped.none { it.name == "T${ExportTemplates.MAX_TEMPLATES}" })
    }

    @Test
    fun defaultNameSkipsAlreadyUsedNames() {
        val templates = listOf(
            ExportTemplate(name = "模板 1", mode = "exclude", tokens = listOf("a")),
            ExportTemplate(name = "模板 2", mode = "exclude", tokens = listOf("b")),
        )

        assertEquals("模板 3", ExportTemplates.defaultName(templates))
    }
}
