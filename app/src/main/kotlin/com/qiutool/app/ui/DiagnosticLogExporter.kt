package com.qiutool.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.qiutool.app.core.AppLogger
import com.qiutool.app.core.DiagnosticLogReport
import com.qiutool.app.core.DiagnosticLogSnapshot
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiagnosticLogExporter {
    private val fileNameFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
    private val displayTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    fun createReportFile(context: Context, state: UiState): File {
        AppLogger.i("QiuTool", "diagnostic log export requested")
        val now = Date()
        val report = DiagnosticLogReport.build(
            snapshot = buildSnapshot(context, state, now),
            existingLog = AppLogger.readRecentLines(),
        )
        val dir = File(context.cacheDir, "qiutool/shared_logs").apply { mkdirs() }
        val file = File(dir, "qiutool-log-${checkNotNull(fileNameFormat.get()).format(now)}.txt")
        file.writeText(report, Charsets.UTF_8)
        AppLogger.i("QiuTool", "diagnostic log saved to ${file.absolutePath} size=${file.length()}B")
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "QiuTool 诊断日志")
            putExtra(Intent.EXTRA_TEXT, "QiuTool 诊断日志见附件：${file.name}")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun buildSnapshot(context: Context, state: UiState, now: Date): DiagnosticLogSnapshot {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return DiagnosticLogSnapshot(
            timestamp = checkNotNull(displayTimeFormat.get()).format(now),
            packageName = context.packageName,
            appVersionName = packageInfo.versionName ?: "unknown",
            appVersionCode = versionCode,
            androidRelease = Build.VERSION.RELEASE ?: "unknown",
            sdkInt = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            permissionMethod = permissionLabelForLog(state.permissionMethod),
            verXmlUrl = state.verXmlUrl,
            shopconfigOutputDir = state.shopconfigOutputDir,
            otherOutputDir = state.otherOutputDir,
            currentTab = tabLabel(state.currentTab),
            analysisSummary = analysisSummary(state),
            exportSummary = exportSummary(state),
            lastError = state.error,
        )
    }

    private fun tabLabel(index: Int): String = when (index) {
        0 -> "官方"
        1 -> "本地"
        2 -> "结果"
        else -> "未知($index)"
    }

    private fun analysisSummary(state: UiState): String {
        val analysis = state.analysis ?: return "未分析"
        return "${analysis.sourceBundleName}，${analysis.items.size} 项，来源分类 ${state.currentSourceCategory.ifBlank { "未知" }}"
    }

    private fun exportSummary(state: UiState): String {
        val status = if (state.isExporting) "导出中" else "未导出中"
        val msg = state.exportMessage.ifBlank { "无导出消息" }
        return "$status，模式 ${state.exportMode}，进度 ${state.exportProgress}%，已选 ${state.selectedTokens.size} 项，$msg"
    }

    private fun permissionLabelForLog(method: String): String = when (method) {
        "all_files" -> "所有文件访问"
        "shizuku" -> "Shizuku"
        "root" -> "Root (su)"
        "none" -> "直写"
        else -> method.ifBlank { "未选择" }
    }
}
