package com.qiutool.app.core

import java.io.Serializable

data class ExportTemplate(
    val name: String,
    val mode: String,
    val tokens: List<String>,
    val sourceCategory: String = "",
    val savedAt: Long = 0L,
) : Serializable

data class TemplateApplyResult(
    val matched: Set<String>,
    val missing: List<String>,
)

object ExportTemplates {
    const val MAX_TEMPLATES = 20
    const val LAST_TEMPLATE_NAME = "上次操作"

    /**
     * 模板存的是用户当时勾选的原始 token，导出时 Filtering 会再展开关联项，
     * 所以这里只按主 token 精确匹配，不做模糊或按名称回退。
     */
    fun applyTo(template: ExportTemplate, items: List<ItemRecord>): TemplateApplyResult {
        val available = items.mapTo(HashSet()) { it.token }
        val matched = LinkedHashSet<String>()
        val missing = mutableListOf<String>()
        template.tokens.forEach { token ->
            if (token in available) matched.add(token) else missing.add(token)
        }
        return TemplateApplyResult(matched = matched, missing = missing)
    }

    /** 同名视为覆盖，最新的排前面，超出上限丢弃最旧的。 */
    fun upsert(templates: List<ExportTemplate>, template: ExportTemplate): List<ExportTemplate> =
        (listOf(template) + templates.filterNot { it.name == template.name }).take(MAX_TEMPLATES)

    fun remove(templates: List<ExportTemplate>, name: String): List<ExportTemplate> =
        templates.filterNot { it.name == name }

    /** 生成不与现有模板重名的默认名字。 */
    fun defaultName(templates: List<ExportTemplate>): String {
        val used = templates.mapTo(HashSet()) { it.name }
        var index = templates.size + 1
        while ("模板 $index" in used) index++
        return "模板 $index"
    }

    fun encode(templates: List<ExportTemplate>): String =
        templates.joinToString(RECORD_SEP) { encodeOne(it) }

    fun decode(raw: String?): List<ExportTemplate> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD_SEP).mapNotNull { decodeOne(it) }
    }

    fun encodeOne(template: ExportTemplate): String = listOf(
        escape(template.name),
        escape(template.mode),
        escape(template.sourceCategory),
        template.savedAt.toString(),
        template.tokens.joinToString(TOKEN_SEP) { escape(it) },
    ).joinToString(FIELD_SEP)

    fun decodeOne(raw: String?): ExportTemplate? {
        if (raw.isNullOrEmpty()) return null
        val fields = raw.split(FIELD_SEP)
        if (fields.size < 5) return null
        val name = unescape(fields[0])
        if (name.isEmpty()) return null
        val tokens = fields[4]
            .split(TOKEN_SEP)
            .map { unescape(it) }
            .filter { it.isNotEmpty() }
        return ExportTemplate(
            name = name,
            mode = unescape(fields[1]).ifEmpty { "exclude" },
            tokens = tokens,
            sourceCategory = unescape(fields[2]),
            savedAt = fields[3].toLongOrNull() ?: 0L,
        )
    }

    private const val RECORD_SEP = "\n"
    private const val FIELD_SEP = "\t"
    private const val TOKEN_SEP = ","

    // token 与模板名都可能含分隔符，先转义再拼接，避免读回来串行。
    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                ',' -> append("\\c")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch != '\\' || i == value.length - 1) {
                out.append(ch)
                i++
                continue
            }
            when (val next = value[i + 1]) {
                '\\' -> out.append('\\')
                't' -> out.append('\t')
                'n' -> out.append('\n')
                'c' -> out.append(',')
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }
}
