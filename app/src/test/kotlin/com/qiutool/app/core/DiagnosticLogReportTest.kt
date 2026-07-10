package com.qiutool.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogReportTest {
    @Test
    fun buildsReportWithMetadataAndExistingLogLines() {
        val report = DiagnosticLogReport.build(
            snapshot = DiagnosticLogSnapshot(
                timestamp = "2026-07-11 05:30:00",
                packageName = "com.qiutool.app",
                appVersionName = "2.0-native",
                appVersionCode = 2,
                androidRelease = "12",
                sdkInt = 32,
                device = "MuMu / emulator",
                permissionMethod = "Shizuku",
                verXmlUrl = "https://example.test/ver.xml",
                shopconfigOutputDir = "/sdcard/Android/data/com.ztgame.bob/files/vercache2022/android/common/data",
                otherOutputDir = "/sdcard/Android/data/com.ztgame.bob/files/vercache2022/android/common/data",
                currentTab = "结果",
                analysisSummary = "shopconfig.unity3d，28777 项",
                exportSummary = "未导出中，进度 100%，导出完成",
                lastError = "Shizuku 拷贝失败",
            ),
            existingLog = listOf(
                "2026-07-11 05:29:50.001 D/QiuTool exportFilteredBundle mode=exclude selectedRaw=1",
                "2026-07-11 05:29:52.333 W/QiuTool shizuku copy failed: Permission denied",
            ),
        )

        assertTrue(report.startsWith("QiuTool 诊断日志"))
        assertTrue(report.contains("生成时间: 2026-07-11 05:30:00"))
        assertTrue(report.contains("包名: com.qiutool.app"))
        assertTrue(report.contains("版本: 2.0-native (2)"))
        assertTrue(report.contains("系统: Android 12 / SDK 32"))
        assertTrue(report.contains("设备: MuMu / emulator"))
        assertTrue(report.contains("权限方式: Shizuku"))
        assertTrue(report.contains("当前页: 结果"))
        assertTrue(report.contains("分析状态: shopconfig.unity3d，28777 项"))
        assertTrue(report.contains("导出状态: 未导出中，进度 100%，导出完成"))
        assertTrue(report.contains("最近错误: Shizuku 拷贝失败"))
        assertTrue(report.contains("exportFilteredBundle mode=exclude"))
        assertTrue(report.contains("Permission denied"))
        assertFalse(report.contains("null"))
    }

    @Test
    fun writesPlaceholderWhenThereAreNoExistingLogLines() {
        val report = DiagnosticLogReport.build(
            snapshot = DiagnosticLogSnapshot(
                timestamp = "2026-07-11 05:31:00",
                packageName = "com.qiutool.app",
                appVersionName = "2.0-native",
                appVersionCode = 2,
                androidRelease = "12",
                sdkInt = 32,
                device = "MuMu / emulator",
                permissionMethod = "未选择",
                verXmlUrl = "",
                shopconfigOutputDir = "",
                otherOutputDir = "",
                currentTab = "官方",
                analysisSummary = "未分析",
                exportSummary = "未导出",
                lastError = "",
            ),
            existingLog = emptyList(),
        )

        assertTrue(report.contains("最近错误: 无"))
        assertTrue(report.contains("暂无应用内日志"))
    }
}
