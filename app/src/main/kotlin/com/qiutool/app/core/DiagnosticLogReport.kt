package com.qiutool.app.core

data class DiagnosticLogSnapshot(
    val timestamp: String,
    val packageName: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val androidRelease: String,
    val sdkInt: Int,
    val device: String,
    val permissionMethod: String,
    val verXmlUrl: String,
    val shopconfigOutputDir: String,
    val otherOutputDir: String,
    val currentTab: String,
    val analysisSummary: String,
    val exportSummary: String,
    val lastError: String,
)

object DiagnosticLogReport {
    fun build(
        snapshot: DiagnosticLogSnapshot,
        existingLog: List<String>,
    ): String = buildString {
        appendLine("QiuTool 诊断日志")
        appendLine("=".repeat(32))
        appendLine("生成时间: ${snapshot.timestamp}")
        appendLine("包名: ${snapshot.packageName}")
        appendLine("版本: ${snapshot.appVersionName} (${snapshot.appVersionCode})")
        appendLine("系统: Android ${snapshot.androidRelease} / SDK ${snapshot.sdkInt}")
        appendLine("设备: ${snapshot.device}")
        appendLine()

        appendLine("[设置]")
        appendLine("权限方式: ${snapshot.permissionMethod.ifBlank { "未选择" }}")
        appendLine("ver.xml 地址: ${snapshot.verXmlUrl.ifBlank { "未设置" }}")
        appendLine("ShopConfig 导出目录: ${snapshot.shopconfigOutputDir.ifBlank { "未设置" }}")
        appendLine("其他文件导出目录: ${snapshot.otherOutputDir.ifBlank { "未设置" }}")
        appendLine()

        appendLine("[当前状态]")
        appendLine("当前页: ${snapshot.currentTab}")
        appendLine("分析状态: ${snapshot.analysisSummary.ifBlank { "未分析" }}")
        appendLine("导出状态: ${snapshot.exportSummary.ifBlank { "未导出" }}")
        appendLine("最近错误: ${snapshot.lastError.ifBlank { "无" }}")
        appendLine()

        appendLine("[应用内日志]")
        if (existingLog.isEmpty()) {
            appendLine("暂无应用内日志")
        } else {
            existingLog.forEach { line ->
                appendLine(line)
            }
        }
    }
}
