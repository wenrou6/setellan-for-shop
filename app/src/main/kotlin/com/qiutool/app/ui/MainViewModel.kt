package com.qiutool.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qiutool.app.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.MessageDigest

data class UiState(
    val currentTab: Int = 0,
    val verXmlUrl: String = "http://ver.battleofballs.com/res300/android/ver.xml",
    val resources: List<ResourceItem> = emptyList(),
    val filteredResources: List<ResourceItem> = emptyList(),
    val resourceFilter: String = "",
    val analysis: AnalysisResult? = null,
    val currentSourceCategory: String = "",
    val selectedTokens: Set<String> = emptySet(),
    val searchQuery: String = "",
    val categoryFilter: String = "",
    val exportMode: String = "exclude",
    val exportProgress: Int = 0,
    val exportMessage: String = "",
    val isExporting: Boolean = false,
    val isLoading: Boolean = false,
    val error: String = "",
    // 导出模板
    val lastTemplate: ExportTemplate? = null,
    val templates: List<ExportTemplate> = emptyList(),
    val templateMessage: String = "",
    val templateMissing: List<String> = emptyList(),
    // 设置项
    val analyzeAllFiles: Boolean = false,
    val shopconfigOutputDir: String = DEFAULT_SHOPCONFIG_DIR,
    val otherOutputDir: String = DEFAULT_OTHER_DIR,
    val permissionMethod: String = "all_files",
) {
    companion object {
        const val DEFAULT_SHOPCONFIG_DIR =
            "/storage/emulated/0/Android/data/com.ztgame.bob/files/vercache2022/android/common/data/"
        const val DEFAULT_OTHER_DIR = "/storage/emulated/0/定制去皮/"
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("qiutool_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val cacheDir = File(application.cacheDir, "qiutool").apply { mkdirs() }
    private val analysisCacheDir = File(cacheDir, "analysis_cache").apply { mkdirs() }

    private fun loadInitialState(): UiState = UiState(
        verXmlUrl = prefs.getString("ver_xml_url", null)
            ?: "http://ver.battleofballs.com/res300/android/ver.xml",
        analyzeAllFiles = prefs.getBoolean("analyze_all_files", false),
        shopconfigOutputDir = prefs.getString("shopconfig_output_dir", null)
            ?: UiState.DEFAULT_SHOPCONFIG_DIR,
        otherOutputDir = prefs.getString("other_output_dir", null)
            ?: UiState.DEFAULT_OTHER_DIR,
        permissionMethod = prefs.getString("permission_method", null) ?: "all_files",
        lastTemplate = ExportTemplates.decodeOne(prefs.getString("last_template", null)),
        templates = ExportTemplates.decode(prefs.getString("saved_templates", null)),
    )

    private fun persist(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
    }

    fun setTab(index: Int) {
        _uiState.value = _uiState.value.copy(currentTab = index)
    }

    fun setVerXmlUrl(url: String) {
        _uiState.value = _uiState.value.copy(verXmlUrl = url)
        persist { putString("ver_xml_url", url) }
    }

    fun setAnalyzeAllFiles(enabled: Boolean) {
        val state = _uiState.value
        val filtered = applyResourceFilter(state.resources, state.resourceFilter, enabled)
        _uiState.value = state.copy(analyzeAllFiles = enabled, filteredResources = filtered)
        persist { putBoolean("analyze_all_files", enabled) }
    }

    fun setShopconfigOutputDir(dir: String) {
        _uiState.value = _uiState.value.copy(shopconfigOutputDir = dir)
        persist { putString("shopconfig_output_dir", dir) }
    }

    fun setOtherOutputDir(dir: String) {
        _uiState.value = _uiState.value.copy(otherOutputDir = dir)
        persist { putString("other_output_dir", dir) }
    }

    fun setPermissionMethod(method: String) {
        _uiState.value = _uiState.value.copy(permissionMethod = method)
        persist { putString("permission_method", method) }
    }

    fun setResourceFilter(filter: String) {
        val state = _uiState.value
        val filtered = applyResourceFilter(state.resources, filter, state.analyzeAllFiles)
        _uiState.value = state.copy(resourceFilter = filter, filteredResources = filtered)
    }

    private fun applyResourceFilter(
        items: List<ResourceItem>,
        filter: String,
        showAll: Boolean,
    ): List<ResourceItem> {
        var result = items
        if (!showAll) {
            result = result.filter { it.category == "shopconfig" }
        }
        if (filter.isNotEmpty()) {
            result = result.filter {
                it.file.contains(filter, ignoreCase = true) ||
                    it.category.contains(filter, ignoreCase = true)
            }
        }
        return result
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setCategoryFilter(category: String) {
        _uiState.value = _uiState.value.copy(categoryFilter = category)
    }

    fun setExportMode(mode: String) {
        _uiState.value = _uiState.value.copy(exportMode = mode)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = "")
    }

    fun fetchVerXml() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = "")
            try {
                val (_, items) = CdnFetcher.fetchVerXml(_uiState.value.verXmlUrl.ifEmpty { null })
                val state = _uiState.value
                val filtered = applyResourceFilter(items, state.resourceFilter, state.analyzeAllFiles)
                _uiState.value = state.copy(
                    resources = items,
                    filteredResources = filtered,
                    isLoading = false,
                )
            } catch (e: Exception) {
                AppLogger.w("QiuTool", "fetch ver.xml failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to fetch ver.xml: ${e.message}"
                )
            }
        }
    }

    fun analyzeResource(item: ResourceItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = "")
            try {
                val destFile = File(cacheDir, item.cacheName)
                if (!destFile.exists()) {
                    val dlStart = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        CdnFetcher.downloadResource(item, cacheDir)
                    }
                    AppLogger.d("QiuTool", "download=${System.currentTimeMillis()-dlStart}ms size=${destFile.length()}B")
                }
                val result = analyzeBundleCached(destFile, expectedMd5 = item.md5)
                _uiState.value = _uiState.value.copy(
                    analysis = result,
                    currentSourceCategory = item.category,
                    selectedTokens = emptySet(),
                    isLoading = false,
                    currentTab = 2,
                    templateMessage = "",
                    templateMissing = emptyList(),
                )
            } catch (e: Exception) {
                AppLogger.w("QiuTool", "analyze resource failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Analysis failed: ${e.message}"
                )
            }
        }
    }

    fun analyzeLocalFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = "")
            try {
                val app = getApplication<Application>()
                val realName = resolveUriDisplayName(uri) ?: "local_upload.unity3d"
                val destFile = File(cacheDir, realName)
                withContext(Dispatchers.IO) {
                    val inputStream = app.contentResolver.openInputStream(uri)
                        ?: throw RuntimeException("Cannot open file")
                    destFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                }
                val result = analyzeBundleCached(destFile)
                val name = destFile.name.lowercase()
                val cat = when {
                    "shopconfig" in name -> "shopconfig"
                    "creatskinconfig" in name -> "creatskinconfig"
                    "artconfig" in name -> "artconfig"
                    "previewconfig" in name -> "previewconfig"
                    else -> "other"
                }
                _uiState.value = _uiState.value.copy(
                    analysis = result,
                    currentSourceCategory = cat,
                    selectedTokens = emptySet(),
                    isLoading = false,
                    currentTab = 2,
                    templateMessage = "",
                    templateMissing = emptyList(),
                )
            } catch (e: Exception) {
                AppLogger.w("QiuTool", "analyze local file failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Analysis failed: ${e.message}"
                )
            }
        }
    }

    /** 从 content uri 取 OpenableColumns.DISPLAY_NAME；拿不到回退到 lastPathSegment。 */
    private fun resolveUriDisplayName(uri: Uri): String? {
        val app = getApplication<Application>()
        try {
            app.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val raw = cursor.getString(idx)
                        if (!raw.isNullOrBlank()) {
                            return sanitizeFileName(raw)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("QiuTool", "resolveUriDisplayName query failed: ${e.message}")
        }
        val last = uri.lastPathSegment?.substringAfterLast('/')
        return last?.let { sanitizeFileName(it) }
    }

    /** 仅保留文件名部分（去掉目录分隔符），防 path traversal。 */
    private fun sanitizeFileName(name: String): String {
        val basename = name.substringAfterLast('/').substringAfterLast('\\')
        return basename.ifBlank { "local_upload.unity3d" }
    }

    private suspend fun analyzeBundleCached(file: File, expectedMd5: String? = null): AnalysisResult =
        withContext(Dispatchers.Default) {
            val md5 = expectedMd5?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{32}")) }
                ?: withContext(Dispatchers.IO) { fileMd5(file) }
            val cacheFile = File(analysisCacheDir, "$md5.bin")
            loadCachedAnalysis(cacheFile)?.let { cached ->
                AppLogger.d("QiuTool", "analysis cache hit md5=$md5 items=${cached.items.size}")
                return@withContext cached.copy(sourceBundleName = file.name)
            }

            val result = Analyzer.analyzeBundle(file)
            withContext(Dispatchers.IO) {
                runCatching { saveCachedAnalysis(cacheFile, result) }
                    .onFailure { AppLogger.w("QiuTool", "save analysis cache failed: ${it.message}") }
                trimAnalysisCache(maxFiles = 12)
            }
            result
        }

    private fun loadCachedAnalysis(cacheFile: File): AnalysisResult? {
        if (!cacheFile.isFile) return null
        return try {
            ObjectInputStream(BufferedInputStream(cacheFile.inputStream())).use { input ->
                input.readObject() as? AnalysisResult
            }
        } catch (e: Exception) {
            AppLogger.w("QiuTool", "read analysis cache failed: ${e.message}")
            null
        }
    }

    private fun saveCachedAnalysis(cacheFile: File, analysis: AnalysisResult) {
        cacheFile.parentFile?.mkdirs()
        val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        ObjectOutputStream(BufferedOutputStream(tmp.outputStream())).use { output ->
            output.writeObject(analysis)
        }
        if (!tmp.renameTo(cacheFile)) {
            tmp.copyTo(cacheFile, overwrite = true)
            tmp.delete()
        }
    }

    private fun trimAnalysisCache(maxFiles: Int) {
        val files = analysisCacheDir.listFiles { f -> f.isFile && f.name.endsWith(".bin") }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
        files.drop(maxFiles).forEach { it.delete() }
    }

    private fun fileMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun toggleToken(token: String) {
        val state = _uiState.value
        val selected = state.selectedTokens.toMutableSet()
        if (token in selected) selected.remove(token) else selected.add(token)
        _uiState.value = state.copy(selectedTokens = selected.toSet())
    }

    fun selectAll() {
        val state = _uiState.value
        if (state.analysis == null) return
        val filteredItems = getFilteredItems()
        val allSelected = filteredItems.isNotEmpty() && filteredItems.all { it.token in state.selectedTokens }
        val selected = state.selectedTokens.toMutableSet()
        if (allSelected) {
            filteredItems.forEach { selected.remove(it.token) }
        } else {
            filteredItems.forEach { selected.add(it.token) }
        }
        _uiState.value = state.copy(selectedTokens = selected.toSet())
    }

    /** 把当前勾选与导出模式另存为命名模板。 */
    fun saveTemplate(name: String) {
        val state = _uiState.value
        if (state.selectedTokens.isEmpty()) {
            _uiState.value = state.copy(error = "未选中任何项目，无法保存模板")
            return
        }
        val finalName = name.trim().ifEmpty { ExportTemplates.defaultName(state.templates) }
        if (finalName == ExportTemplates.LAST_TEMPLATE_NAME) {
            _uiState.value = state.copy(error = "「${ExportTemplates.LAST_TEMPLATE_NAME}」是保留名称，请换一个")
            return
        }
        val template = buildTemplate(finalName, state)
        val templates = ExportTemplates.upsert(state.templates, template)
        _uiState.value = state.copy(
            templates = templates,
            templateMessage = "已保存模板「$finalName」：${template.tokens.size} 项",
            templateMissing = emptyList(),
        )
        persist { putString("saved_templates", ExportTemplates.encode(templates)) }
    }

    fun deleteTemplate(name: String) {
        val state = _uiState.value
        val templates = ExportTemplates.remove(state.templates, name)
        _uiState.value = state.copy(templates = templates)
        persist { putString("saved_templates", ExportTemplates.encode(templates)) }
    }

    /** 套用模板：只勾选当前 Bundle 里存在的 token，缺失项回报给界面。 */
    fun applyTemplate(template: ExportTemplate) {
        val state = _uiState.value
        val analysis = state.analysis
        if (analysis == null) {
            _uiState.value = state.copy(error = "请先分析一个 Bundle 再套用模板")
            return
        }
        val result = ExportTemplates.applyTo(template, analysis.items)
        val message = buildString {
            append("已套用模板「${template.name}」：匹配 ${result.matched.size} 项")
            if (result.missing.isNotEmpty()) {
                append("，缺失 ${result.missing.size} 项")
            }
        }
        _uiState.value = state.copy(
            selectedTokens = result.matched,
            exportMode = template.mode,
            templateMessage = message,
            templateMissing = result.missing,
        )
    }

    fun clearTemplateMessage() {
        _uiState.value = _uiState.value.copy(templateMessage = "", templateMissing = emptyList())
    }

    private fun buildTemplate(name: String, state: UiState): ExportTemplate = ExportTemplate(
        name = name,
        mode = state.exportMode,
        tokens = state.selectedTokens.toList(),
        sourceCategory = state.currentSourceCategory,
        savedAt = System.currentTimeMillis(),
    )

    fun getFilteredItems(): List<ItemRecord> {
        val state = _uiState.value
        val analysis = state.analysis ?: return emptyList()
        var items = analysis.items
        if (state.searchQuery.isNotEmpty()) {
            val q = state.searchQuery.lowercase()
            items = items.filter {
                it.token.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.keyword.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.itemId.contains(q)
            }
        }
        if (state.categoryFilter.isNotEmpty()) {
            items = items.filter { it.category == state.categoryFilter }
        }
        return items
    }

    fun getCategories(): List<String> {
        val analysis = _uiState.value.analysis ?: return emptyList()
        return analysis.items.map { it.category }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    private fun resolveOutputDir(state: UiState): File {
        val dir = if (state.currentSourceCategory == "shopconfig") {
            state.shopconfigOutputDir
        } else {
            state.otherOutputDir
        }
        return File(dir)
    }

    private fun createStagingDir(method: String): File? {
        // Shizuku newProcess 通常以 shell 身份运行，读不到 App 私有 cache：
        // /data/user/0/<pkg>/cache/...
        // 所以 Shizuku 暂存必须放到 App 外部缓存目录，让 App 可写且 shell 可读。
        val root = if (method == PermissionMethod.SHIZUKU) {
            getApplication<Application>().externalCacheDir ?: return null
        } else {
            cacheDir
        }
        return File(root, "export_staging").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    fun exportBundle() {
        val state = _uiState.value
        val analysis = state.analysis ?: return
        if (state.selectedTokens.isEmpty()) {
            _uiState.value = state.copy(error = "未选中任何项目")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isExporting = true, exportProgress = 0, error = "")
            try {
                val sourceFile = File(cacheDir, analysis.sourceBundleName)
                if (!sourceFile.exists()) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        error = "源文件不存在，请重新下载"
                    )
                    return@launch
                }
                val targetDir = resolveOutputDir(state)
                val method = state.permissionMethod
                // Root / Shizuku 都是「先写到 cache 暂存再用特权 shell 拷过去」
                val usePrivileged = method == PermissionMethod.ROOT || method == PermissionMethod.SHIZUKU
                val methodLabel = when (method) {
                    PermissionMethod.ROOT -> "Root"
                    PermissionMethod.SHIZUKU -> "Shizuku"
                    else -> "直写"
                }
                val stagingDir = if (usePrivileged) createStagingDir(method) else null
                if (usePrivileged && stagingDir == null) {
                    AppLogger.w("QiuTool", "$methodLabel staging dir unavailable: external cache dir is null")
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        error = "$methodLabel 暂存目录不可用：外部存储未挂载，无法让 Shizuku 读取待导出文件。"
                    )
                    return@launch
                }
                if (!usePrivileged) {
                    val mkOk = try {
                        targetDir.mkdirs() || targetDir.isDirectory
                    } catch (e: Exception) {
                        AppLogger.w("QiuTool", "mkdirs failed for $targetDir: ${e.message}")
                        false
                    }
                    if (!mkOk) {
                        AppLogger.w("QiuTool", "cannot create export dir: ${targetDir.absolutePath}")
                        _uiState.value = _uiState.value.copy(
                            isExporting = false,
                            error = "无法创建导出目录：${targetDir.absolutePath}\n请在「设置」里切换权限方式（Root / Shizuku）或为本应用授予「所有文件访问」。"
                        )
                        return@launch
                    }
                }
                val writeDir = stagingDir ?: targetDir
                val result = withContext(Dispatchers.Default) {
                    Filtering.exportFilteredBundle(
                        sourceBundle = sourceFile,
                        analysis = analysis,
                        mode = state.exportMode,
                        selectedTokens = state.selectedTokens,
                        outputDir = writeDir,
                        onProgress = { progress, message ->
                            _uiState.value = _uiState.value.copy(
                                exportProgress = progress,
                                exportMessage = message
                            )
                        }
                    )
                }
                val summary = result.summary
                val sourceSize = sourceFile.length()
                val outputSize = result.file.length()
                if (usePrivileged) {
                    val activeStagingDir = stagingDir ?: return@launch
                    _uiState.value = _uiState.value.copy(
                        exportProgress = 100,
                        exportMessage = "$methodLabel 拷贝到 ${targetDir.absolutePath}…"
                    )
                    val files = activeStagingDir.listFiles().orEmpty()
                    val failed = withContext(Dispatchers.IO) {
                        files.filterNot { f ->
                            PermissionMethod.copyTo(f, File(targetDir, f.name), method)
                        }
                    }
                    if (failed.isNotEmpty()) {
                        AppLogger.w(
                            "QiuTool",
                            "$methodLabel copy failed files=${failed.joinToString { it.name }} staging=${activeStagingDir.absolutePath} target=${targetDir.absolutePath}"
                        )
                        _uiState.value = _uiState.value.copy(
                            isExporting = false,
                            error = "$methodLabel 拷贝失败：${failed.joinToString { it.name }}\n请确认权限已授予。导出文件暂存于 ${activeStagingDir.absolutePath}"
                        )
                        return@launch
                    }
                    activeStagingDir.deleteRecursively()
                }
                val summaryMsg = buildString {
                    if (summary.mode == "keep") {
                        append("导出完成（保留模式）：源 ${state.selectedTokens.size} 项 → 文件含 ${summary.actualKeptCount} 个主 token；样例 ${summary.samplePresent}")
                    } else {
                        append("导出完成（排除模式）：移除 ${state.selectedTokens.size} 项后，文件含 ${summary.actualKeptCount} 个主 token")
                    }
                    if (outputSize > sourceSize) {
                        append("\n⚠️ 导出文件比原始大 ${outputSize - sourceSize} 字节（${outputSize}/${sourceSize}），游戏可能因 ver.xml 体积校验拒绝加载。建议减少保留项或换排除模式。")
                    }
                }
                val lastTemplate = buildTemplate(ExportTemplates.LAST_TEMPLATE_NAME, state)
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportProgress = 100,
                    exportMessage = "已导出到 ${targetDir.absolutePath}\n$summaryMsg",
                    lastTemplate = lastTemplate,
                )
                persist { putString("last_template", ExportTemplates.encodeOne(lastTemplate)) }
            } catch (e: Exception) {
                AppLogger.e("QiuTool", "export failed: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    error = "导出失败：${e.message}"
                )
            }
        }
    }
}
