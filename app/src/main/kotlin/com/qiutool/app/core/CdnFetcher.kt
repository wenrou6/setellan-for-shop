package com.qiutool.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object CdnFetcher {

    private const val VER_XML_URL = "http://ver.battleofballs.com/res300/android/ver.xml"
    private const val CDN_BASE = "http://cdn1.bobworld.cn/res300/android/"
    private const val UNITY_UA = "UnityPlayer/2022.3.62f3 (UnityWebRequest/1.0, libcurl/8.10.1-DEV)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fetchVerXml(url: String? = null): Pair<String, List<ResourceItem>> = withContext(Dispatchers.IO) {
        val targetUrl = url ?: VER_XML_URL
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", UNITY_UA)
            .header("X-Unity-Version", "2022.3.62f3")
            .header("Accept", "*/*")
            .build()

        val xmlText = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            response.body!!.string()
        }

        val items = parseVerXml(xmlText)
        xmlText to items
    }

    private fun parseVerXml(xml: String): List<ResourceItem> {
        val items = mutableListOf<ResourceItem>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                val file = parser.getAttributeValue(null, "file") ?: ""
                val md5 = parser.getAttributeValue(null, "md5") ?: ""
                val size = (parser.getAttributeValue(null, "size") ?: "0").toIntOrNull() ?: 0
                val weight = (parser.getAttributeValue(null, "weight") ?: "0").toIntOrNull() ?: 0
                val check = (parser.getAttributeValue(null, "check") ?: "false").lowercase() == "true"
                if (file.isNotEmpty() && md5.isNotEmpty()) {
                    items.add(ResourceItem(file = file, md5 = md5, size = size, weight = weight, check = check))
                }
            }
            eventType = parser.next()
        }
        return items
    }

    suspend fun downloadFile(
        url: String,
        dest: File,
        expectedMd5: String? = null,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UNITY_UA)
            .header("X-Unity-Version", "2022.3.62f3")
            .header("Accept", "*/*")
            .build()

        val prog = DownloadProgress(status = "downloading", currentFile = dest.name)
        onProgress?.invoke(prog)

        val startTime = System.nanoTime()
        val data = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            val body = response.body!!
            val total = body.contentLength().toInt()
            val bytes = body.bytes()
            val elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0
            val speed = if (elapsed > 0) bytes.size / elapsed else 0.0
            onProgress?.invoke(prog.copy(
                total = total,
                downloaded = bytes.size,
                speed = speed
            ))
            bytes
        }

        if (expectedMd5 != null) {
            val actualMd5 = md5(data)
            if (actualMd5 != expectedMd5) {
                onProgress?.invoke(prog.copy(status = "error", error = "MD5 mismatch: expected $expectedMd5, got $actualMd5"))
                throw RuntimeException("MD5 mismatch")
            }
        }

        dest.writeBytes(data)
        onProgress?.invoke(prog.copy(status = "done", downloaded = data.size))
        dest
    }

    suspend fun downloadResource(
        item: ResourceItem,
        destDir: File,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): File {
        val dest = File(destDir, item.cacheName)
        return downloadFile(item.cdnUrl, dest, expectedMd5 = item.md5, onProgress = onProgress)
    }

    private fun md5(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(data)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
